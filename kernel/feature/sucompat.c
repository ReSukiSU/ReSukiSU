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

#include "arch.h"
#include "policy/allowlist.h"
#include "policy/feature.h"
#include "klog.h" // IWYU pragma: keep
#include "runtime/ksud.h"
#include "feature/sucompat.h"
#include "policy/app_profile.h"
#ifdef CONFIG_KSU_TRACEPOINT_HOOK
#include "hook/syscall_hook.h"
#else
#include "feature/adb_root.h"
#endif
#include "sulog/event.h"
#include "compat/kernel_compat.h"
#include "ksu.h"

#define SU_PATH "/system/bin/su"
#define SH_PATH "/system/bin/sh"

#ifdef KSU_COMPAT_USE_STATIC_KEY
DEFINE_STATIC_KEY_TRUE(ksu_su_compat_enabled);
#else
bool ksu_su_compat_enabled __read_mostly = true;
#endif

static int su_compat_feature_get(u64 *value)
{
#ifdef KSU_COMPAT_USE_STATIC_KEY
    if (static_key_enabled(&ksu_su_compat_enabled))
        *value = 1;
    else
        *value = 0;
#else
    *value = ksu_su_compat_enabled ? 1 : 0;
#endif
    return 0;
}

static int su_compat_feature_set(u64 value)
{
    bool enable = value != 0;
#ifdef KSU_COMPAT_USE_STATIC_KEY
    if (enable)
        static_branch_enable(&ksu_su_compat_enabled);
    else
        static_branch_disable(&ksu_su_compat_enabled);
#else
    ksu_su_compat_enabled = enable;
#endif
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

static const char su_path[] = SU_PATH;
static const char sh_path[] = SH_PATH;
static const char ksud_path[] = KSUD_PATH;

static char __user *ksud_user_path(void)
{
    return userspace_stack_buffer(ksud_path, sizeof(ksud_path));
}

static char __user *sh_user_path(void)
{
    return userspace_stack_buffer(sh_path, sizeof(sh_path));
}

static char __user *empty_user_path(void)
{
    return userspace_stack_buffer("", sizeof(""));
}

static bool is_ksud_exists()
{
    struct path path;

    if (kern_path(KSUD_PATH, 0, &path) < 0) {
        return false;
    }
    path_put(&path);
    return true;
}

extern bool ksu_kernel_umount_enabled;

#ifdef CONFIG_KSU_TRACEPOINT_HOOK
#include <linux/file.h>
#include <linux/namei.h>
#include <linux/fcntl.h>

long ksu_handle_faccessat_sucompat_internal(int orig_nr, struct pt_regs *regs)
{
    const char __user **filename_user, *orig_filename;
    long ret;
    const struct cred *old_cred;

    if (!ksu_is_allow_uid_for_current(ksu_get_uid_t(current_uid()))) {
        goto do_orig_facessat;
    }

    filename_user = (const char __user **)&PT_REGS_PARM2(regs);

    char path[sizeof(su_path) + 1];
    memset(path, 0, sizeof(path));
    strncpy_from_user_nofault(path, *filename_user, sizeof(path));

    if (unlikely(!memcmp(path, su_path, sizeof(su_path)))) {
        old_cred = override_creds(ksu_cred);
        if (is_ksud_exists()) {
            pr_info("faccessat su->ksud!\n");
            orig_filename = *filename_user;
            *filename_user = ksud_user_path();
            ret = ksu_syscall_table[orig_nr](regs);
            revert_creds(old_cred);
            *filename_user = orig_filename;
            return ret;
        } else {
            revert_creds(old_cred);
        }
    }

do_orig_facessat:
    return ksu_syscall_table[orig_nr](regs);
}

long ksu_handle_stat_sucompat_internal(int orig_nr, struct pt_regs *regs)
{
    const char __user **filename_user, *orig_filename;
    long ret;
    const struct cred *old_cred;

    if (!ksu_is_allow_uid_for_current(ksu_get_uid_t(current_uid()))) {
        goto do_orig_stat;
    }

    filename_user = (const char __user **)&PT_REGS_PARM2(regs);

    char path[sizeof(su_path) + 1];
    memset(path, 0, sizeof(path));
    strncpy_from_user_nofault(path, *filename_user, sizeof(path));

    if (unlikely(!memcmp(path, su_path, sizeof(su_path)))) {
        old_cred = override_creds(ksu_cred);
        if (is_ksud_exists()) {
            pr_info("newfstatat su->ksud!\n");
            orig_filename = *filename_user;
            *filename_user = ksud_user_path();
            ret = ksu_syscall_table[orig_nr](regs);
            revert_creds(old_cred);
            *filename_user = orig_filename;
            return ret;
        } else {
            revert_creds(old_cred);
        }
    }

do_orig_stat:
    return ksu_syscall_table[orig_nr](regs);
}

// ensure call from tracepoint
static long ksu_handle_execve_sucompat_common_internal(const char __user **filename_user,
                                                       const char __user *const __user *argv_user, unsigned long envp,
                                                       bool execveat, int orig_nr, struct pt_regs *regs)
{
    const char __user *fn;
    struct ksu_sulog_pending_event *pending_sucompat = NULL;
    char path[sizeof(su_path) + 1];
    long ret, orig_regs[5];
    unsigned long addr;
    int tmp_fd;
    struct file *ksud_file;
    const struct cred *old_cred;

    if (execveat && ((int)PT_REGS_PARM1(regs) != AT_FDCWD || (int)PT_REGS_PARM5(regs) != 0))
        goto do_orig_execve;

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

    if (likely(memcmp(path, su_path, sizeof(su_path))))
        goto do_orig_execve;

    pr_info("sys_execve su found\n");

    tmp_fd = get_unused_fd_flags(O_CLOEXEC);
    if (tmp_fd < 0) {
        pr_err("alloc tmp fd err: %d\n", tmp_fd);
        goto do_orig_execve;
    }

    old_cred = override_creds(ksu_cred);
    ksud_file = filp_open(KSUD_PATH, O_PATH, 0);
    revert_creds(old_cred);
    if (IS_ERR(ksud_file)) {
        pr_err("open ksud err: %ld\n", PTR_ERR(ksud_file));
        put_unused_fd(tmp_fd);
        goto do_orig_execve;
    }

    fd_install(tmp_fd, ksud_file);

    pending_sucompat = ksu_sulog_capture_sucompat_tracepoint(*filename_user, argv_user, GFP_KERNEL);
    // execve(file, argv, environ)
    // execveat(fd, file, argv, environ, flags)
    orig_regs[0] = regs->__PT_PARM1_REG;
    orig_regs[1] = regs->__PT_PARM2_REG;
    orig_regs[2] = regs->__PT_PARM3_REG;
    orig_regs[3] = regs->__PT_SYSCALL_PARM4_REG;
    orig_regs[4] = regs->__PT_PARM5_REG;
    regs->__PT_PARM5_REG = AT_EMPTY_PATH;
    regs->__PT_SYSCALL_PARM4_REG = envp;
    regs->__PT_PARM3_REG = (unsigned long)argv_user;
    regs->__PT_PARM2_REG = empty_user_path();
    regs->__PT_PARM1_REG = tmp_fd;

    ret = escape_with_root_profile();
    if (ret) {
        pr_err("escape_with_root_profile failed: %ld\n", ret);
    }
    ksu_sulog_emit_pending(pending_sucompat, ret, GFP_KERNEL);

    ret = ksu_syscall_table[__NR_execveat](regs);
    if (ret < 0) {
        ksu_close_fd(tmp_fd);
        regs->__PT_PARM1_REG = orig_regs[0];
        regs->__PT_PARM2_REG = orig_regs[1];
        regs->__PT_PARM3_REG = orig_regs[2];
        regs->__PT_SYSCALL_PARM4_REG = orig_regs[3];
        regs->__PT_PARM5_REG = orig_regs[4];
    }
    return ret;

do_orig_execve:
    return ksu_syscall_table[orig_nr](regs);
}

long ksu_handle_execve_sucompat_internal(const char __user **filename_user, int orig_nr, struct pt_regs *regs)
{
    return ksu_handle_execve_sucompat_common_internal(filename_user,
                                                      (const char __user *const __user *)PT_REGS_PARM2(regs),
                                                      PT_REGS_PARM3(regs), false, orig_nr, regs);
}

long ksu_handle_execveat_sucompat_internal(const char __user **filename_user, int orig_nr, struct pt_regs *regs)
{
    return ksu_handle_execve_sucompat_common_internal(filename_user,
                                                      (const char __user *const __user *)PT_REGS_PARM3(regs),
                                                      PT_REGS_SYSCALL_PARM4(regs), true, orig_nr, regs);
}
#endif

#if defined(CONFIG_KSU_SUSFS) || defined(CONFIG_KSU_MANUAL_HOOK)

static inline int do_ksu_handle_execveat_sucompat(int *fd, const char *filename, struct user_arg_ptr *argv)
{
    struct ksu_sulog_pending_event *pending_sucompat = NULL;
    struct path kpath;
    bool is_allowed = ksu_is_allow_uid_for_current(ksu_get_uid_t(current_uid()));

#ifdef KSU_COMPAT_USE_STATIC_KEY
    // Yep, maybe someusers love turn off sucompat <- idk how they managed to keep using it
    // But for mostly users, sucompat is enabled, so unlikely here
    if (!static_branch_unlikely(&ksu_su_compat_enabled)) {
        return 0;
    }
#else
    if (!ksu_su_compat_enabled) {
        return 0;
    }
#endif

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

#ifdef KSU_COMPAT_USE_STATIC_KEY
extern struct static_key_true ksud_execve_key;
#else
extern bool ksud_execve_key;
#endif

static inline void ksu_handle_execveat_init(const char *filename, void *envp)
{
    if (current->pid != 1 && is_init(current_cred())) {
        if (unlikely(strcmp(filename, KSUD_PATH) == 0)) {
            pr_info("hook_manager: escape to root for init executing ksud: %d\n", current->pid);
            escape_to_root_for_init();
        }
#if !defined(CONFIG_KSU_TRACEPOINT_HOOK)
        else if (likely(strstr(filename, "/app_process") == NULL && strstr(filename, "/adbd") == NULL)) {
            pr_info("mark no sucompat checks for pid: '%d', exec: '%s'\n", current->pid, filename);

            if (!ksu_is_current_proc_unprivillege())
                ksu_set_current_proc_unprivillege();
        }
#endif
        int ret = ksu_adb_root_handle_execve_manual(filename, (struct user_arg_ptr *)envp);
        if (ret) {
            pr_err("adb root failed: %d\n", (int)ret);
        }
    }
}

int ksu_handle_execve(int *fd, const char *filename, void *argv, void *envp, int *flags)
{
    struct ksu_sulog_pending_event *pending_root_execve = NULL;

#ifndef CONFIG_KSU_TRACEPOINT_HOOK
    if (ksu_is_current_proc_unprivillege()) {
        return 0;
    }
#endif

    // We only care AT_FDCWD & flags = 0
    // check int* fd and int* flags, because they might NOT ptr
    // Who exactly is so fond of type casting that even wrote it into the old version of documentation?
    if (fd == (int *)AT_FDCWD && flags == 0) {
        goto skip_check;
    }

    if (*fd != AT_FDCWD || *flags != 0) {
        return 0;
    }

skip_check:
    ksu_handle_execveat_init(filename, envp);

#ifdef KSU_COMPAT_USE_STATIC_KEY
    if (static_branch_unlikely(&ksud_execve_key)) {
        ksu_handle_execveat_ksud(filename, argv, envp, flags);
    }
#else
    if (unlikely(ksud_execve_key)) {
        ksu_handle_execveat_ksud(filename, argv, envp, flags);
    }
#endif

    if (ksu_get_uid_t(current_uid()) == 0) {
        pending_root_execve =
            ksu_sulog_capture_root_execve_manual(filename, *((struct user_arg_ptr *)argv), GFP_KERNEL);
    }

    int ret = do_ksu_handle_execveat_sucompat(fd, filename, argv);

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

// because simonpunk, he do check in hook side
// and call ksu_handle_execveat_sucompat
// we need unpack filename* in here, and pass it to ksu_handle_execveat
#ifdef CONFIG_KSU_SUSFS
int ksu_handle_execveat_sucompat(int *fd, struct filename **filename_ptr, void *argv, void *envp, int *flags)
{
    // workaround susfs codes as below
    //	if (static_branch_unlikely(&susfs_set_sdcard_android_data_decrypted_key_false))
    //		ksu_handle_execveat(&fd, &filename, &argv, &envp, &flags);
    //	else
    //		ksu_handle_execveat_sucompat(&fd, &filename, &argv, &envp, &flags);

    return ksu_handle_execveat(fd, filename_ptr, argv, envp, flags);
}
#endif
#endif

#ifdef CONFIG_KSU_SUSFS
int ksu_handle_faccessat(int *dfd, struct filename **filename, int *mode, int *__unused_flags)
{
    const struct cred *old_cred;

    // we no need harden this check, susfs already complete in caller
    // if (ksu_is_current_proc_unprivillege()) {
    //     return 0;
    // }

    if (!static_branch_unlikely(&ksu_su_compat_enabled)) {
        return 0;
    }

    if (unlikely(IS_ERR(*filename) || (*filename)->name == NULL))
        return 0;

    if (likely(memcmp((*filename)->name, su_path, sizeof(su_path))))
        return 0;

    old_cred = override_creds(ksu_cred);
    if (is_ksud_exists()) {
        pr_info("ksu_handle_faccessat su->sh!\n");
        memcpy((void *)((*filename)->name), sh_path, sizeof(sh_path));
    } else {
        pr_info("no ksud found, don't process faccessat for su!");
    }

    revert_creds(old_cred);
    return 0;
}
#else
int ksu_handle_faccessat(int *dfd, const char __user **filename_user, int *mode, int *__unused_flags)
{
    char path[sizeof(su_path) + 1] = { 0 };
    const struct cred *old_cred;

#ifndef CONFIG_KSU_TRACEPOINT_HOOK
    if (ksu_is_current_proc_unprivillege()) {
        return 0;
    }
#endif

#ifdef KSU_COMPAT_USE_STATIC_KEY
    if (!static_branch_unlikely(&ksu_su_compat_enabled)) {
        return 0;
    }
#else
    if (!ksu_su_compat_enabled) {
        return 0;
    }
#endif

    if (!ksu_is_allow_uid_for_current(ksu_get_uid_t(current_uid())))
        return 0;

    ksu_strncpy_from_user_nofault(path, *filename_user, sizeof(path));

    if (unlikely(!memcmp(path, su_path, sizeof(su_path)))) {
        old_cred = override_creds(ksu_cred);
        if (is_ksud_exists()) {
            pr_info("ksu_handle_faccessat su->sh!\n");
            *filename_user = sh_user_path();
        } else {
            pr_info("no ksud found, don't process faccessat for su!");
        }

        revert_creds(old_cred);
    }

    return 0;
}
#endif

#ifdef CONFIG_KSU_SUSFS
int ksu_handle_stat(int *dfd, struct filename **filename, int *flags)
{
    const struct cred *old_cred;

    // we no need harden this check, susfs already complete in caller
    // if (ksu_is_current_proc_unprivillege()) {
    //     return 0;
    // }

    if (!static_branch_unlikely(&ksu_su_compat_enabled)) {
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

    old_cred = override_creds(ksu_cred);
    if (is_ksud_exists()) {
        pr_info("ksu_handle_stat: su->sh!\n");
        memcpy((void *)((*filename)->name), sh_path, sizeof(sh_path));
    } else {
        pr_info("no ksud found, don't process stat for su!");
    }

    revert_creds(old_cred);
    return 0;
}
#else
int ksu_handle_stat(int *dfd, const char __user **filename_user, int *flags)
{
    const struct cred *old_cred;
    char path[sizeof(su_path) + 1] = { 0 };

#ifndef CONFIG_KSU_TRACEPOINT_HOOK
    if (ksu_is_current_proc_unprivillege()) {
        return 0;
    }
#endif

#ifdef KSU_COMPAT_USE_STATIC_KEY
    // Yep, maybe someusers love turn off sucompat <- idk how they managed to keep using it
    // But for mostly users, sucompat is enabled, so unlikely here
    if (!static_branch_unlikely(&ksu_su_compat_enabled)) {
        return 0;
    }
#else
    if (!ksu_su_compat_enabled) {
        return 0;
    }
#endif

    if (unlikely(!filename_user)) {
        return 0;
    }

    if (!ksu_is_allow_uid_for_current(ksu_get_uid_t(current_uid())))
        return 0;

    ksu_strncpy_from_user_nofault(path, *filename_user, sizeof(path));

    if (unlikely(!memcmp(path, su_path, sizeof(su_path)))) {
        old_cred = override_creds(ksu_cred);
        if (is_ksud_exists()) {
            pr_info("ksu_handle_stat su->sh!\n");
            *filename_user = sh_user_path();
        } else {
            pr_info("no ksud found, don't process stat for su!");
        }

        revert_creds(old_cred);
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
