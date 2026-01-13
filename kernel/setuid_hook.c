#include <linux/compiler.h>
#include <linux/version.h>
#include <linux/slab.h>
#include <linux/task_work.h>
#include <linux/thread_info.h>
#include <linux/seccomp.h>
#include <linux/printk.h>
#include <linux/sched.h>
#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 10, 0)
#include <linux/sched/signal.h>
#endif
#include <linux/string.h>
#include <linux/types.h>
#include <linux/uaccess.h>
#include <linux/uidgid.h>
#include <linux/namei.h>

#include "allowlist.h"
#include "setuid_hook.h"
#include "klog.h" // IWYU pragma: keep
#include "manager.h"
#include "selinux/selinux.h"
#include "seccomp_cache.h"
#include "supercalls.h"
#ifdef KSU_TP_HOOK
#include "syscall_hook_manager.h"
#endif
#include "kernel_compat.h"
#include "kernel_umount.h"
#include "sulog.h"

extern void disable_seccomp(struct task_struct *tsk);

static inline void ksu_set_file_immutable(const char *path_name, bool immutable)
{
    struct path path;
    struct inode *inode;
    int error;

    error = kern_path(path_name, LOOKUP_FOLLOW, &path);
    if (error) {
        return;
    }

    inode = d_inode(path.dentry);

    error = mnt_want_write(path.mnt);
    if (error) {
        path_put(&path);
        return;
    }

    inode_lock(inode);
    if (immutable) {
        inode->i_flags |= S_IMMUTABLE;
    } else {
        inode->i_flags &= ~S_IMMUTABLE;
    }
    inode_unlock(inode);

    mnt_drop_write(path.mnt);
    path_put(&path);
}

struct ksud_status_tw {
    struct callback_head cb;
    uid_t new_uid;
};

static inline void do_ksu_set_ksud_status(uid_t new_uid)
{
    u16 appid = new_uid % PER_USER_RANGE;
    int signature_index = ksu_get_manager_signature_index_by_appid(appid);
    if (signature_index != 255) {
        ksu_set_file_immutable("/data/adb/ksud", false);
        pr_info("Mark /data/adb/ksud read write");
    } else {
        ksu_set_file_immutable("/data/adb/ksud", true);
        pr_info("Mark /data/adb/ksud read only");
    }
}

#ifdef KSU_TP_HOOK
static void ksud_status_tw_func(struct callback_head *cb)
{
    struct ksud_status_tw *tw = container_of(cb, struct ksud_status_tw, cb);
    do_ksu_set_ksud_status(tw->new_uid);
    kfree(tw);
}

#endif

static inline void ksu_set_ksud_status(uid_t new_uid)
{
#ifndef KSU_TP_HOOK
    do_ksu_set_ksud_status(new_uid);
#else
    struct ksud_status_tw *tw;

    tw = kzalloc(sizeof(*tw), GFP_ATOMIC);
    if (!tw)
        return;

    tw->cb.func = ksud_status_tw_func;
    tw->new_uid = new_uid;

    int err = task_work_add(current, &tw->cb, TWA_RESUME);
    if (err) {
        kfree(tw);
        pr_warn("ksud lock add task_work failed\n");
    }
#endif
}

int ksu_handle_setuid(uid_t new_uid, uid_t old_uid, uid_t euid) // (new_euid)
{
    // We only interest in process spwaned by zygote
    if (!is_zygote(current_cred())) {
        return 0;
    }

    if (old_uid != new_uid) {
        pr_info("handle_setresuid from %d to %d\n", old_uid, new_uid);
        ksu_sulog_report_syscall(new_uid, NULL, "setuid", NULL);
    }

#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 10, 0)
    if (ksu_is_manager_uid(new_uid)) {
        pr_info("install fd for ksu manager(uid=%d)\n", new_uid);
        ksu_mark_manager(new_uid);
        ksu_set_ksud_status(new_uid);
        ksu_install_fd();
        spin_lock_irq(&current->sighand->siglock);
        ksu_seccomp_allow_cache(current->seccomp.filter, __NR_reboot);
#ifdef KSU_TP_HOOK
        ksu_set_task_tracepoint_flag(current);
#endif
        spin_unlock_irq(&current->sighand->siglock);
        return 0;
    }

    if (ksu_is_allow_uid_for_current(new_uid)) {
        if (current->seccomp.mode == SECCOMP_MODE_FILTER &&
            current->seccomp.filter) {
            spin_lock_irq(&current->sighand->siglock);
            ksu_seccomp_allow_cache(current->seccomp.filter, __NR_reboot);
            spin_unlock_irq(&current->sighand->siglock);
        }
#ifdef KSU_TP_HOOK
        ksu_set_task_tracepoint_flag(current);
#endif
    }
#ifdef KSU_TP_HOOK
    else {
        ksu_clear_task_tracepoint_flag_if_needed(current);
    }
#endif

#else // #if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 10, 0)
    if (ksu_is_allow_uid_for_current(new_uid)) {
        spin_lock_irq(&current->sighand->siglock);
        disable_seccomp(current);
        spin_unlock_irq(&current->sighand->siglock);

        if (ksu_is_manager_uid(new_uid)) {
            pr_info("install fd for ksu manager(uid=%d)\n", new_uid);
            ksu_mark_manager(new_uid);
            ksu_set_ksud_status(new_uid);
            ksu_install_fd();
        }

        return 0;
    }
#endif // #if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 10, 0)

    // Handle kernel umount
    ksu_handle_umount(old_uid, new_uid);

    return 0;
}

int ksu_handle_setresuid(uid_t ruid, uid_t euid, uid_t suid)
{
#ifdef CONFIG_KSU_MANUAL_HOOK_AUTO_SETUID_HOOK
    return 0; // dummy hook here
#else
    // we rely on the fact that zygote always call setresuid(3) with same uids
    return ksu_handle_setuid(ruid, ksu_get_uid_t(current_uid()), euid);
#endif
}

void ksu_setuid_hook_init(void)
{
    ksu_kernel_umount_init();
}

void ksu_setuid_hook_exit(void)
{
    pr_info("ksu_setuid_hook_exit\n");
    ksu_kernel_umount_exit();
}
