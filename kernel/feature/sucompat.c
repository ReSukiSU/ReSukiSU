#include <linux/version.h>
#include <linux/preempt.h>
#include <linux/mm.h>
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 8, 0)
#include <linux/pgtable.h>
#else
#include <asm/pgtable.h>
#endif
#include <linux/uaccess.h>
#include <asm/current.h>
#include <linux/cred.h>
#include <linux/fs.h>
#include <linux/types.h>
#include <linux/ptrace.h>
#include <linux/namei.h>
#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 11, 0)
#include <linux/sched/task_stack.h>
#else
#include <linux/sched.h>
#endif

#ifdef CONFIG_KSU_SUSFS
#include <linux/susfs_def.h>
#include <linux/namei.h>
#include "selinux/selinux.h"
#include "objsec.h"
#endif // #ifdef CONFIG_KSU_SUSFS

#include "compat/kernel_compat.h"
#include "arch.h"
#include "policy/allowlist.h"
#include "policy/feature.h"
#include "klog.h" // IWYU pragma: keep
#include "runtime/ksud.h"
#include "feature/sucompat.h"
#include "policy/app_profile.h"
#ifdef KSU_TP_HOOK
#include "hook/syscall_hook.h"
#endif
#include "sulog/event.h"

#define SU_PATH "/system/bin/su"
#define SH_PATH "/system/bin/sh"

bool ksu_su_compat_enabled __read_mostly = true;

static int su_compat_feature_get(u64 *value)
{
    *value = ksu_su_compat_enabled ? 1 : 0;
    return 0;
}

static int su_compat_feature_set(u64 value)
{
    bool enable = value != 0;
    ksu_su_compat_enabled = enable;
    pr_info("su_compat: set to %d\n", enable);
    return 0;
}

static const struct ksu_feature_handler su_compat_handler = {
    .feature_id = KSU_FEATURE_SU_COMPAT,
    .name = "su_compat",
    .get_handler = su_compat_feature_get,
    .set_handler = su_compat_feature_set,
};

#if LINUX_VERSION_CODE >= KERNEL_VERSION(3, 8, 0)
static void __user *userspace_stack_buffer(const void *d, size_t len)
{
    // To avoid having to mmap a page in userspace, just write below the stack
    // pointer.
    char __user *p = (void __user *)current_user_stack_pointer() - len;

    return copy_to_user(p, d, len) ? NULL : p;
}
#else
static void __user *userspace_stack_buffer(const void *d, size_t len)
{
    if (!current->mm)
        return NULL;

    volatile unsigned long start_stack = current->mm->start_stack;
    unsigned int step = 32;
    char __user *p = NULL;

    do {
        p = (void __user *)(start_stack - step - len);
        if (!copy_to_user(p, d, len)) {
            /* pr_info("%s: start_stack: %lx p: %lx len: %zu\n",
				__func__, start_stack, (unsigned long)p, len ); */
            return p;
        }
        step = step + step;
    } while (step <= 2048);
    return NULL;
}
#endif

static char __user *sh_user_path(void)
{
    static const char sh_path[] = "/system/bin/sh";

    return userspace_stack_buffer(sh_path, sizeof(sh_path));
}

static char __user *ksud_user_path(void)
{
    static const char ksud_path[] = KSUD_PATH;

    return userspace_stack_buffer(ksud_path, sizeof(ksud_path));
}

static const char sh_path[] = SH_PATH;
static const char su_path[] = SU_PATH;
static const char ksud_path[] = KSUD_PATH;

extern bool ksu_kernel_umount_enabled;

#ifdef KSU_TP_HOOK

// WARNING! THERE HAVE TRYING TO CALL SYSCALL INTERNALLY
// ENSURE CALL IT ONLY IN TRACEPOINT SYSCALL REDIRECT
int ksu_handle_execve_sucompat_tp_internal(const char __user **filename_user, int orig_nr, const struct pt_regs *regs)
{
    const char su[] = SU_PATH;
    const char __user *fn;
    const char __user *const __user *argv_user = (const char __user *const __user *)PT_REGS_PARM2(regs);
    struct ksu_sulog_pending_event *pending_sucompat = NULL;
    char path[sizeof(su) + 1];
    long ret;
    unsigned long addr;

    if (unlikely(!filename_user))
        goto do_orig_execve;

    if (!ksu_is_allow_uid_for_current(ksu_get_uid_t(current_uid())))
        goto do_orig_execve;

    addr = untagged_addr((unsigned long)*filename_user);
    fn = (const char __user *)addr;
    memset(path, 0, sizeof(path));

    ret = strncpy_from_user(path, fn, sizeof(path));

    if (ret < 0) {
        pr_warn("Access filename when execve failed: %ld", ret);
        goto do_orig_execve;
    }

    if (likely(memcmp(path, su, sizeof(su))))
        goto do_orig_execve;

    pr_info("sys_execve su found\n");
    pending_sucompat = ksu_sulog_capture_sucompat_tracepoint(*filename_user, argv_user, GFP_KERNEL);
    *filename_user = ksud_user_path();

    ret = escape_with_root_profile();
    if (ret) {
        pr_err("escape_with_root_profile failed: %ld\n", ret);
        ksu_sulog_emit_pending(pending_sucompat, ret, GFP_KERNEL);
        goto do_orig_execve;
    }

    ret = ksu_syscall_table[orig_nr](regs);
    if (ret < 0) {
        pr_err("failed to execve ksud as su: %ld, fallback to sh\n", ret);
        ksu_sulog_emit_pending(pending_sucompat, ret, GFP_KERNEL);
        *filename_user = sh_user_path();
    } else {
        ksu_sulog_emit_pending(pending_sucompat, ret, GFP_KERNEL);
        return ret;
    }

do_orig_execve:
    return ksu_syscall_table[orig_nr](regs);
}
#endif

#if defined(CONFIG_KSU_SUSFS) || defined(CONFIG_KSU_MANUAL_HOOK)

// This is only used when we are in manual hook/susfs inline hook
// We can't remove __never_use params, because it were using by the fucking susfs
int ksu_handle_execveat_sucompat(int *fd, const char *filename, struct user_arg_ptr *argv, void *__never_use_envp,
                                 int *__never_use_flags)
{
    struct ksu_sulog_pending_event *pending_sucompat = NULL;
    struct path kpath;
    bool is_allowed = ksu_is_allow_uid_for_current(ksu_get_uid_t(current_uid()));

    if (!ksu_su_compat_enabled) {
        return 0;
    }

    if (!is_allowed)
        return 0;

    if (likely(memcmp(filename, su_path, sizeof(su_path))))
        return 0;

    pr_info("do_execveat_common su found\n");

    escape_with_root_profile();

    pending_sucompat = ksu_sulog_capture_sucompat_manual(filename, *argv, GFP_KERNEL);

    // We are only check ksud exists
    // In manual hook, we can't try exec ksud, and detect exec success or not
    if (kern_path(KSUD_PATH, LOOKUP_FOLLOW, &kpath)) {
        pr_info("sucompat: /data/adb/ksud not found, fallback to /system/bin/sh");
        memcpy((void *)filename, sh_path, sizeof(sh_path));
        goto out;
    }

    path_put(&kpath);
    memcpy((void *)filename, ksud_path, sizeof(ksud_path));
out:
    ksu_sulog_emit_pending(pending_sucompat, 0, GFP_KERNEL);
    return 0;
}

#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 3, 0) || defined(KSU_HAS_MODERN_STATIC_KEY_INTERFACE)
extern struct static_key_true ksud_execve_key;
#else
extern bool ksud_execve_key;
#endif

static inline void ksu_handle_execveat_init(const char *name)
{
    if (current->pid != 1 && is_init(current_cred())) {
        if (unlikely(strcmp(name, KSUD_PATH) == 0)) {
            pr_info("hook_manager: escape to root for init executing ksud: %d\n", current->pid);
            escape_to_root_for_init();
        }
#ifdef CONFIG_KSU_SUSFS
        else if (likely(strstr(name, "/app_process") == NULL && strstr(name, "/adbd") == NULL) &&
                 !susfs_is_current_proc_umounted()) {
            pr_info("susfs: mark no sucompat checks for pid: '%d', exec: '%s'\n", current->pid, name);
            susfs_set_current_proc_umounted();
        }
#endif
    }
}

int ksu_handle_execve(int *fd, const char *filename, void *argv, void *envp, int *flags)
{
    struct ksu_sulog_pending_event *pending_root_execve = NULL;

    ksu_handle_execveat_init(filename);

#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 3, 0) || defined(KSU_HAS_MODERN_STATIC_KEY_INTERFACE)
    if (static_branch_unlikely(&ksud_execve_key)) {
        ksu_handle_execveat_ksud(filename, argv, envp, flags);
    }
#else
    if (ksud_execve_key) {
        ksu_handle_execveat_ksud(filename, argv, envp, flags);
    }
#endif

    // Yep, we write check in there maybe cause susfs can't record sulog
    // But i don't want care about it, susfs even revert that in their's patch file
    // susfs, go to hell!
    if (ksu_get_uid_t(current_uid()) == 0) {
        pending_root_execve =
            ksu_sulog_capture_root_execve_manual(filename, *((struct user_arg_ptr *)argv), GFP_KERNEL);
    }

    int ret = ksu_handle_execveat_sucompat(fd, filename, argv, envp, flags);

    // record sulog!
    ksu_sulog_emit_pending(pending_root_execve, ret, GFP_KERNEL);

    return ret;
}

// old hook, link to ksu_handle_execve
int ksu_handle_execveat(int *fd, struct filename **filename_ptr, void *argv, void *envp, int *flags)
{
    struct filename *filename;
    filename = *filename_ptr;
    if (IS_ERR(filename)) {
        return 0;
    }

    return ksu_handle_execve(fd, filename->name, argv, envp, flags);
}
#endif

int ksu_handle_faccessat(int *dfd, const char __user **filename_user, int *mode, int *__unused_flags)
{
    char path[sizeof(su_path) + 1] = { 0 };

    if (!ksu_su_compat_enabled) {
        return 0;
    }

    if (!ksu_is_allow_uid_for_current(ksu_get_uid_t(current_uid())))
        return 0;

    ksu_strncpy_from_user_nofault(path, *filename_user, sizeof(path));

    if (unlikely(!memcmp(path, su_path, sizeof(su_path)))) {
        pr_info("faccessat su->sh!\n");
        *filename_user = sh_user_path();
    }

    return 0;
}

#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 1, 0) && defined(CONFIG_KSU_SUSFS)
int ksu_handle_stat(int *dfd, struct filename **filename, int *flags)
{
    if (!ksu_su_compat_enabled) {
        return 0;
    }

    if (!ksu_is_allow_uid_for_current(ksu_get_uid_t(current_uid())))
        return 0;

    if (unlikely(IS_ERR(*filename) || (*filename)->name == NULL)) {
        return 0;
    }

    if (likely(memcmp((*filename)->name, su_path, sizeof(su_path)))) {
        return 0;
    }

    pr_info("ksu_handle_stat: su->sh!\n");
    memcpy((void *)((*filename)->name), sh_path, sizeof(sh_path));
    return 0;
}
#else
int ksu_handle_stat(int *dfd, const char __user **filename_user, int *flags)
{
    char path[sizeof(su_path) + 1] = { 0 };

    if (!ksu_su_compat_enabled) {
        return 0;
    }

    if (unlikely(!filename_user)) {
        return 0;
    }

    if (!ksu_is_allow_uid_for_current(ksu_get_uid_t(current_uid())))
        return 0;

    ksu_strncpy_from_user_nofault(path, *filename_user, sizeof(path));

    if (unlikely(!memcmp(path, su_path, sizeof(su_path)))) {
        pr_info("ksu_handle_stat: su->sh!\n");
        *filename_user = sh_user_path();
    }

    return 0;
}
#endif // #if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 1, 0)

// dead code: devpts handling
int __maybe_unused ksu_handle_devpts(struct inode *inode)
{
    return 0;
}

// sucompat: permitted process can execute 'su' to gain root access.
void __init ksu_sucompat_init()
{
    if (ksu_register_feature_handler(&su_compat_handler)) {
        pr_err("Failed to register su_compat feature handler\n");
    }
}

void __exit ksu_sucompat_exit()
{
    ksu_unregister_feature_handler(KSU_FEATURE_SU_COMPAT);
}
