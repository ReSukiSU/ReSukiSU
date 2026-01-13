#include <linux/version.h>
#include <linux/capability.h>
#include <linux/cred.h>
#include <linux/err.h>
#include <linux/fdtable.h>
#include <linux/file.h>
#include <linux/fs.h>
#include <linux/proc_ns.h>
#include <linux/pid.h>
#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 11, 0)
#include <linux/sched/signal.h> // signal_struct
#include <linux/sched/task.h>
#endif
#include <linux/sched.h>
#include <linux/seccomp.h>
#include <linux/thread_info.h>
#include <linux/uidgid.h>
#include <linux/syscalls.h>
#include "objsec.h"
#include <linux/spinlock.h>
#include <linux/tty.h>
#include <linux/security.h>

#include "allowlist.h"
#include "app_profile.h"
#include "arch.h"
#include "kernel_compat.h"
#include "klog.h" // IWYU pragma: keep
#include "selinux/selinux.h"
#include "su_mount_ns.h"
#ifdef KSU_TP_HOOK
#include "syscall_hook_manager.h"
#endif
#include "sulog.h"

#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 7, 0)
static struct group_info root_groups = { .usage = REFCOUNT_INIT(2) };
#else
static struct group_info root_groups = { .usage = ATOMIC_INIT(2) };
#endif

static void setup_groups(struct root_profile *profile, struct cred *cred)
{
    if (profile->groups_count > KSU_MAX_GROUPS) {
        pr_warn("Failed to setgroups, too large group: %d!\n", profile->uid);
        return;
    }

    if (profile->groups_count == 1 && profile->groups[0] == 0) {
        // setgroup to root and return early.
        if (cred->group_info)
            put_group_info(cred->group_info);
        cred->group_info = get_group_info(&root_groups);
        return;
    }

    u32 ngroups = profile->groups_count;
    struct group_info *group_info = groups_alloc(ngroups);
    if (!group_info) {
        pr_warn("Failed to setgroups, ENOMEM for: %d\n", profile->uid);
        return;
    }

    int i;
    for (i = 0; i < ngroups; i++) {
        gid_t gid = profile->groups[i];
        kgid_t kgid = make_kgid(current_user_ns(), gid);
        if (!gid_valid(kgid)) {
            pr_warn("Failed to setgroups, invalid gid: %d\n", gid);
            put_group_info(group_info);
            return;
        }
#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 9, 0)
        group_info->gid[i] = kgid;
#else
        GROUP_AT(group_info, i) = kgid;
#endif
    }

    groups_sort(group_info);
    set_groups(cred, group_info);
    put_group_info(group_info);
}

void disable_seccomp(struct task_struct *tsk)
{
    if (unlikely(!tsk))
        return;

    assert_spin_locked(&tsk->sighand->siglock);

    // disable seccomp
#if defined(CONFIG_GENERIC_ENTRY) &&                                           \
    LINUX_VERSION_CODE >= KERNEL_VERSION(5, 11, 0)
    clear_syscall_work(SECCOMP);
#else
    clear_thread_flag(TIF_SECCOMP);
#endif

#ifdef CONFIG_SECCOMP
    tsk->seccomp.mode = 0;
    if (tsk->seccomp.filter) {
        // 5.9+ have filter_count, but optional.
#ifdef KSU_OPTIONAL_SECCOMP_FILTER_CNT
        atomic_set(&tsk->seccomp.filter_count, 0);
#endif
        // some old kernel backport seccomp_filter_release..
#if LINUX_VERSION_CODE < KERNEL_VERSION(5, 9, 0) &&                            \
    defined(KSU_OPTIONAL_SECCOMP_FILTER_RELEASE)
        seccomp_filter_release(tsk);
#else
        // never, ever call seccomp_filter_release on 6.10+ (no effect)
#if (LINUX_VERSION_CODE >= KERNEL_VERSION(5, 9, 0) &&                          \
     LINUX_VERSION_CODE < KERNEL_VERSION(6, 10, 0))
        seccomp_filter_release(tsk);
#else
#if LINUX_VERSION_CODE < KERNEL_VERSION(5, 9, 0)
        put_seccomp_filter(tsk);
#endif
        tsk->seccomp.filter = NULL;
#endif
#endif
    }
#endif
}

void escape_with_root_profile(void)
{
    struct cred *cred;
    // a bit useless, but we just want less ifdefs
    struct task_struct *p = current;
    struct root_profile profile;

    if (ksu_get_uid_t(current_euid()) == 0) {
        pr_warn("Already root, don't escape!\n");
        return;
    }

    cred = prepare_creds();
    if (!cred) {
        pr_warn("prepare_creds failed!\n");
        return;
    }

    ksu_get_root_profile(ksu_get_uid_t(cred->uid), &profile);

    ksu_get_uid_t(cred->uid) = profile.uid;
    ksu_get_uid_t(cred->suid) = profile.uid;
    ksu_get_uid_t(cred->euid) = profile.uid;
    ksu_get_uid_t(cred->fsuid) = profile.uid;

    ksu_get_uid_t(cred->gid) = profile.gid;
    ksu_get_uid_t(cred->fsgid) = profile.gid;
    ksu_get_uid_t(cred->sgid) = profile.gid;
    ksu_get_uid_t(cred->egid) = profile.gid;
    cred->securebits = 0;

    BUILD_BUG_ON(sizeof(profile.capabilities.effective) !=
                 sizeof(kernel_cap_t));

    // setup capabilities
    // we need CAP_DAC_READ_SEARCH becuase `/data/adb/ksud` is not accessible for non root process
    // we add it here but don't add it to cap_inhertiable, it would be dropped automaticly after exec!
    u64 cap_for_ksud = profile.capabilities.effective | CAP_DAC_READ_SEARCH;
    memcpy(&cred->cap_effective, &cap_for_ksud, sizeof(cred->cap_effective));
    memcpy(&cred->cap_permitted, &profile.capabilities.effective,
           sizeof(cred->cap_permitted));
    memcpy(&cred->cap_bset, &profile.capabilities.effective,
           sizeof(cred->cap_bset));

    setup_groups(&profile, cred);
    setup_selinux(profile.selinux_domain, cred);

    commit_creds(cred);

    // Refer to kernel/seccomp.c: seccomp_set_mode_strict
    // When disabling Seccomp, ensure that current->sighand->siglock is held during the operation.
    spin_lock_irq(&p->sighand->siglock);
    disable_seccomp(p);
    spin_unlock_irq(&p->sighand->siglock);

    ksu_sulog_report_su_grant(ksu_get_uid_t(current_euid()), NULL,
                              "escape_to_root");

#ifdef KSU_TP_HOOK
    struct task_struct *t;
    for_each_thread (p, t) {
        ksu_set_task_tracepoint_flag(t);
    }
#endif
    setup_mount_ns(profile.namespaces);
}

void escape_to_root_for_init(void)
{
    struct cred *cred = prepare_creds();
    if (!cred) {
        pr_err("Failed to prepare init's creds!\n");
        return;
    }

    setup_selinux(KERNEL_SU_CONTEXT, cred);
    commit_creds(cred);
}
