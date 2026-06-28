/* SPDX-License-Identifier: GPL-2.0-only */

#include <linux/err.h>
#include <linux/version.h>

#include "hook/auto_hook.h"
#include "hook/inline_hook.h"
#include "infra/symbol_resolver.h"
#include "arch.h"
#include "klog.h"

#ifdef KSU_HOOK_AUTO_REBOOT_HOOK
static struct ksu_inline_hook *ksu_reboot_hook;

static void ksu_on_sys_reboot(struct pt_regs *regs)
{
    struct pt_regs *real_regs = PT_REAL_REGS(regs);
    int magic1 = (int)PT_REGS_PARM1(real_regs);
    int magic2 = (int)PT_REGS_PARM2(real_regs);
    int cmd = (int)PT_REGS_PARM3(real_regs);
    void __user **arg = (void __user **)&PT_REGS_SYSCALL_PARM4(real_regs);

    ksu_handle_sys_reboot(magic1, magic2, cmd, arg);
}

static __init void ksu_hook_sys_reboot(void)
{
    unsigned long addr;

    addr = find_kernel_symbol_exact(SYS_REBOOT_SYMBOL);

    if (!addr) {
        pr_err("Can't find address of sys_reboot");
        return;
    }

    pr_info("%s: sys_reboot target=%px (%pS)\n", __func__, (void *)addr, (void *)addr);

    struct ksu_inline_hook_config config = { .target = addr, .before = ksu_on_sys_reboot, .after = NULL };

    if (ksu_reboot_hook)
        return;

    ksu_reboot_hook = ksu_inline_hook_register(config);
    if (IS_ERR(ksu_reboot_hook)) {
        pr_err("%s: failed to hook sys_reboot: %ld\n", __func__, PTR_ERR(ksu_reboot_hook));
        ksu_reboot_hook = NULL;
        return;
    }
}

static __exit void ksu_unhook_sys_reboot(void)
{
    ksu_inline_hook_unregister(ksu_reboot_hook);
    ksu_reboot_hook = NULL;
}
#endif

#ifdef KSU_HOOK_AUTO_EXECVE_HOOK
#include "runtime/ksud.h"

static struct ksu_inline_hook *ksu_execve_hook;

static struct user_arg_ptr ksu_get_user_arg_ptr(unsigned long first, unsigned long second)
{
    struct user_arg_ptr arg = {};

#ifdef CONFIG_COMPAT
    arg.is_compat = (bool)first;
    if (arg.is_compat) {
        arg.ptr.compat = (const compat_uptr_t __user *)second;
        return arg;
    }

    arg.ptr.native = (const char __user *const __user *)second;
#else
    arg.ptr.native = (const char __user *const __user *)first;
#endif

    return arg;
}

static unsigned long ksu_user_arg_ptr_value(struct user_arg_ptr arg)
{
#ifdef CONFIG_COMPAT
    if (arg.is_compat)
        return (unsigned long)arg.ptr.compat;
#endif

    return (unsigned long)arg.ptr.native;
}

extern int ksu_handle_execveat(int *fd, struct filename **filename_ptr, void *argv, void *envp, int *flags);
extern int ksu_handle_execve(int *fd, const char *filename, void *argv, void *envp, int *flags);

extern int ksu_handle_execveat(int *fd, struct filename **filename_ptr, void *argv, void *envp, int *flags);
extern int ksu_handle_execve(int *fd, const char *filename, void *argv, void *envp, int *flags);

static void ksu_before_do_execve_common(struct pt_regs *regs)
{
    struct pt_regs *real_regs = regs;

    int fd = (int)PT_REGS_PARM1(real_regs);
    int flags;

    // https://github.com/torvalds/linux/commit/c4ad8f98bef77c7356aa6a9ad9188a6acc6b849d
#if LINUX_VERSION_CODE >= KERNEL_VERSION(3, 14, 0) || defined(KSU_COMPAT_DO_EXECVE_STRUCT_FILENAME)
    struct filename *filename = (struct filename *)PT_REGS_PARM2(real_regs);
#else
    const char *filename = (const char *)PT_REGS_PARM2(real_regs);
#endif

    if (!filename)
        // we only care the call from compat_do_execve/do_execve
        return;

#ifdef CONFIG_COMPAT
    struct user_arg_ptr argv = ksu_get_user_arg_ptr(PT_REGS_PARM3(real_regs), PT_REGS_CCALL_PARM4(real_regs));
    struct user_arg_ptr envp = ksu_get_user_arg_ptr(PT_REGS_PARM5(real_regs), PT_REGS_PARM6(real_regs));
    flags = (int)PT_REGS_PARM7(real_regs);
#else
    struct user_arg_ptr argv = ksu_get_user_arg_ptr(PT_REGS_PARM3(real_regs), 0);
    struct user_arg_ptr envp = ksu_get_user_arg_ptr(PT_REGS_CCALL_PARM4(real_regs), 0);
    flags = (int)PT_REGS_PARM5(real_regs);
#endif

#if LINUX_VERSION_CODE >= KERNEL_VERSION(3, 14, 0) || defined(KSU_COMPAT_DO_EXECVE_STRUCT_FILENAME)
    ksu_handle_execveat(&fd, &filename, &argv, &envp, &flags);
#else
    ksu_handle_execve(&fd, filename, &argv, &envp, &flags);
#endif

    PT_REGS_PARM1(real_regs) = (unsigned long)fd;
    PT_REGS_PARM2(real_regs) = (unsigned long)filename;

#ifdef CONFIG_COMPAT
    PT_REGS_PARM3(real_regs) = (unsigned long)argv.is_compat;
    PT_REGS_CCALL_PARM4(real_regs) = ksu_user_arg_ptr_value(argv);
    PT_REGS_PARM5(real_regs) = (unsigned long)envp.is_compat;
    PT_REGS_PARM6(real_regs) = ksu_user_arg_ptr_value(envp);
    PT_REGS_PARM7(real_regs) = (unsigned long)flags;
#else
    PT_REGS_PARM3(real_regs) = ksu_user_arg_ptr_value(argv);
    PT_REGS_CCALL_PARM4(real_regs) = ksu_user_arg_ptr_value(envp);
    PT_REGS_PARM5(real_regs) = (unsigned long)flags;
#endif
}

// fallback of do_execveat_common/__do_execve_file/do_execve_common hook failed
static void ksu_before_do_execve(struct pt_regs *regs)
{
    struct pt_regs *real_regs = regs;
    int fd = AT_FDCWD;
    int flags = 0;

    // https://github.com/torvalds/linux/commit/c4ad8f98bef77c7356aa6a9ad9188a6acc6b849d
#if LINUX_VERSION_CODE >= KERNEL_VERSION(3, 14, 0) || defined(KSU_COMPAT_DO_EXECVE_STRUCT_FILENAME)
    struct filename *filename = (struct filename *)PT_REGS_PARM1(real_regs);
#else
    const char *filename = (const char *)PT_REGS_PARM1(real_regs);
#endif

    const char __user *const __user *__argv = (const char __user *const __user *)PT_REGS_PARM2(real_regs);
    const char __user *const __user *__envp = (const char __user *const __user *)PT_REGS_PARM3(real_regs);

    struct user_arg_ptr argv = { .ptr.native = __argv };
    struct user_arg_ptr envp = { .ptr.native = __envp };

#if LINUX_VERSION_CODE >= KERNEL_VERSION(3, 14, 0) || defined(KSU_COMPAT_DO_EXECVE_STRUCT_FILENAME)
    ksu_handle_execveat(&fd, &filename, &argv, &envp, &flags);
#else
    ksu_handle_execve(&fd, filename, &argv, &envp, &flags);
#endif

    PT_REGS_PARM1(real_regs) = (unsigned long)filename;
    PT_REGS_PARM2(real_regs) = ksu_user_arg_ptr_value(argv);
    PT_REGS_PARM3(real_regs) = ksu_user_arg_ptr_value(envp);
}

static void __init ksu_hook_sys_execve(void)
{
    // hook do_execveat_common/__do_execve_file/do_execve_common
    unsigned long addr;

    addr = find_kernel_symbol_exact("do_execve_common");

    if (!addr) {
        addr = find_kernel_symbol_exact("__do_execve_file");
    }

    if (!addr) {
        addr = find_kernel_symbol_exact("do_execveat_common");
    }

    if (!addr) {
        pr_err("Can't find address both of do_execveat_common/__do_execve_file/do_execve_common");
        goto common_hook_failed;
    }

    pr_info("%s: do_execveat_common/__do_execve_file/do_execve_common target=%px (%pS)\n", __func__, (void *)addr,
            (void *)addr);

    struct ksu_inline_hook_config config = { .target = addr, .before = ksu_before_do_execve_common, .after = NULL };

    if (ksu_execve_hook)
        return;

    ksu_execve_hook = ksu_inline_hook_register(config);
    if (IS_ERR(ksu_execve_hook)) {
        pr_err("%s: failed to hook do_execveat_common/__do_execve_file/do_execve_common: %ld\n", __func__,
               PTR_ERR(ksu_execve_hook));
        ksu_execve_hook = NULL;
        goto common_hook_failed;
    }

    return;
common_hook_failed:
    // hook do_execve(filename, argv, envp)
    // 3.4 onyx inline do_execve_common -> do_execve
    // modern kernel inline do_execve -> sys_execve
    addr = find_kernel_symbol_exact("do_execve");

    if (!addr) {
        pr_err("Can't find address of do_execve");
        return;
    }

    pr_info("%s: do_execve target=%px (%pS)\n", __func__, (void *)addr, (void *)addr);

    struct ksu_inline_hook_config execve_config = { .target = addr, .before = ksu_before_do_execve, .after = NULL };

    if (ksu_execve_hook)
        return;

    ksu_execve_hook = ksu_inline_hook_register(execve_config);
    if (IS_ERR(ksu_execve_hook)) {
        pr_err("%s: failed to hook do_execve: %ld\n", __func__, PTR_ERR(ksu_execve_hook));
        ksu_execve_hook = NULL;
    }

#ifdef CONFIG_COMPAT
    // 32bit userspace + 64bit kernel
    // mostly don't have these devices,
    // and the compiler mostly won't inline do_execveat_common when this case
    //
    // so just alert
    pr_alert("****************************************************************");
    pr_alert("**      NOTICE NOTICE NOTICE NOTICE NOTICE NOTICE NOTICE      **");
    pr_alert("**                                                            **");
    pr_alert("**   CONFIG_COMPAT enabled but compat_do_execve won't hook    **");
    pr_alert("**  ReSukiSU may not work when you are using 32bit userspace  **");
    pr_alert("**                                                            **");
    pr_alert("**      NOTICE NOTICE NOTICE NOTICE NOTICE NOTICE NOTICE      **");
    pr_alert("****************************************************************");
#endif
}

static void __exit ksu_unhook_sys_execve(void)
{
    ksu_inline_hook_unregister(ksu_execve_hook);
    ksu_execve_hook = NULL;
}
#endif

#ifdef KSU_HOOK_AUTO_FACCESSAT_HOOK
static struct ksu_inline_hook *ksu_faccessat_hook;
extern int ksu_handle_faccessat(int *dfd, const char __user **filename_user, int *mode, int *flags);

static void ksu_on_sys_faccessat(struct pt_regs *regs)
{
    struct pt_regs *real_regs = PT_REAL_REGS(regs);
    int dfd = (int)PT_REGS_PARM1(real_regs);
    const char __user *filename = (const char __user *)PT_REGS_PARM2(real_regs);
    int mode = (int)PT_REGS_PARM3(real_regs);

    ksu_handle_faccessat(&dfd, &filename, &mode, NULL);

    PT_REGS_PARM1(real_regs) = dfd;
    PT_REGS_PARM2(real_regs) = (unsigned long)filename;
    PT_REGS_PARM3(real_regs) = mode;
}

static __init void ksu_hook_sys_faccessat(void)
{
    unsigned long addr;

    addr = find_kernel_symbol_exact(SYS_FACCESSAT_SYMBOL);

    if (!addr) {
        pr_err("Can't find address of sys_faccessat");
        return;
    }

    pr_info("%s: ksu_faccessat_hook target=%px (%pS)\n", __func__, (void *)addr, (void *)addr);

    struct ksu_inline_hook_config config = { .target = addr, .before = ksu_on_sys_faccessat, .after = NULL };

    if (ksu_faccessat_hook)
        return;

    ksu_faccessat_hook = ksu_inline_hook_register(config);
    if (IS_ERR(ksu_faccessat_hook)) {
        pr_err("%s: failed to hook sys_faccessat: %ld\n", __func__, PTR_ERR(ksu_faccessat_hook));
        ksu_faccessat_hook = NULL;
        return;
    }
}

static __exit void ksu_unhook_sys_faccessat(void)
{
    ksu_inline_hook_unregister(ksu_faccessat_hook);
    ksu_faccessat_hook = NULL;
}
#endif

#ifdef KSU_HOOK_AUTO_STAT_HOOK
static struct ksu_inline_hook *ksu_newfstatat_hook;
static struct ksu_inline_hook *ksu_fstatat64_hook;
extern int ksu_handle_stat(int *dfd, const char __user **filename_user, int *flags);

static void ksu_on_sys_stat(struct pt_regs *regs)
{
    struct pt_regs *real_regs = PT_REAL_REGS(regs);
    int dfd = (int)PT_REGS_PARM1(real_regs);
    const char __user *filename = (const char __user *)PT_REGS_PARM2(real_regs);
    int flag = (int)PT_REGS_SYSCALL_PARM4(real_regs);

    ksu_handle_stat(&dfd, &filename, &flag);

    PT_REGS_PARM1(real_regs) = dfd;
    PT_REGS_PARM2(real_regs) = (unsigned long)filename;
    PT_REGS_SYSCALL_PARM4(real_regs) = flag;
}

static __init void ksu_hook_sys_newfstatat(void)
{
    unsigned long addr;

    addr = find_kernel_symbol_exact(SYS_NEWFSTATAT_SYMBOL);
    if (!addr) {
        pr_err("Can't find address of sys_newfstatat");
        return;
    }

    pr_info("%s: ksu_newfstatat_hook target=%px (%pS)\n", __func__, (void *)addr, (void *)addr);

    struct ksu_inline_hook_config config = { .target = addr, .before = ksu_on_sys_stat, .after = NULL };

    if (ksu_newfstatat_hook)
        goto ksu_fstatat64_hook;

    ksu_newfstatat_hook = ksu_inline_hook_register(config);
    if (IS_ERR(ksu_newfstatat_hook)) {
        pr_err("%s: failed to hook sys_newfstatat: %ld\n", __func__, PTR_ERR(ksu_newfstatat_hook));
        ksu_newfstatat_hook = NULL;
        return;
    }

ksu_fstatat64_hook:
    if (ksu_fstatat64_hook)
        return;

    addr = find_kernel_symbol_exact(SYS_FSTATAT64_SYMBOL);
    if (!addr) {
        pr_err("Can't find address of sys_fstatat64");
        return;
    }

    pr_info("%s: ksu_fstatat64_hook target=%px (%pS)\n", __func__, (void *)addr, (void *)addr);

    config.target = addr;

    ksu_fstatat64_hook = ksu_inline_hook_register(config);
    if (IS_ERR(ksu_fstatat64_hook)) {
        pr_err("%s: failed to hook sys_fstatat64: %ld\n", __func__, PTR_ERR(ksu_fstatat64_hook));
        ksu_fstatat64_hook = NULL;
        return;
    }
}

static __exit void ksu_unhook_sys_newfstatat(void)
{
    ksu_inline_hook_unregister(ksu_newfstatat_hook);
    ksu_inline_hook_unregister(ksu_fstatat64_hook);
    ksu_newfstatat_hook = NULL;
    ksu_fstatat64_hook = NULL;
}
#endif

#ifdef KSU_HOOK_AUTO_NEWFSTAT_HOOK
#include <linux/stat.h>
static struct ksu_inline_hook *ksu_newfstat_hook;

extern void ksu_handle_newfstat_ret(unsigned int *fd, struct stat __user **statbuf_ptr);

static void ksu_on_sys_newfstat(struct pt_regs *regs)
{
#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 16, 0) && defined(__aarch64__)
    struct pt_regs *real_regs = (struct pt_regs *)regs->orig_x0;
#else
    struct pt_regs *real_regs = PT_REAL_REGS(regs);
#endif
    unsigned int fd = (unsigned int)PT_REGS_PARM1(real_regs);
    struct stat __user *statbuf = (struct stat __user *)PT_REGS_PARM2(real_regs);

    ksu_handle_newfstat_ret(&fd, &statbuf);

    PT_REGS_PARM1(real_regs) = (unsigned long)fd;
    PT_REGS_PARM2(real_regs) = (unsigned long)statbuf;
}

static __init void ksu_hook_sys_newfstat(void)
{
    unsigned long addr;

    addr = find_kernel_symbol_exact(SYS_FSTAT_SYMBOL);

    if (!addr) {
        pr_err("Can't find address of sys_newfstat");
        return;
    }

    pr_info("%s: sys_newfstat target=%px (%pS)\n", __func__, (void *)addr, (void *)addr);

    struct ksu_inline_hook_config config = { .target = addr, .before = NULL, .after = ksu_on_sys_newfstat };

    if (ksu_newfstat_hook)
        return;

    ksu_newfstat_hook = ksu_inline_hook_register(config);
    if (IS_ERR(ksu_newfstat_hook)) {
        pr_err("%s: failed to hook sys_newfstat: %ld\n", __func__, PTR_ERR(ksu_newfstat_hook));
        ksu_newfstat_hook = NULL;
        return;
    }
}

static __exit void ksu_unhook_sys_newfstat(void)
{
    ksu_inline_hook_unregister(ksu_newfstat_hook);
    ksu_newfstat_hook = NULL;
}
#endif

#ifdef KSU_HOOK_AUTO_FSTAT64_HOOK
#include <linux/stat.h>
static struct ksu_inline_hook *ksu_fstat64_hook;

extern void ksu_handle_fstat64_ret(unsigned long *fd, struct stat64 __user **statbuf_ptr);

static void ksu_on_sys_fstat64(struct pt_regs *regs)
{
#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 16, 0) && defined(__aarch64__)
    struct pt_regs *real_regs = (struct pt_regs *)regs->orig_x0;
#else
    struct pt_regs *real_regs = PT_REAL_REGS(regs);
#endif
    unsigned long fd = (unsigned long)PT_REGS_PARM1(real_regs);
    struct stat64 __user *statbuf = (struct stat64 __user *)PT_REGS_PARM2(real_regs);

    ksu_handle_fstat64_ret(&fd, &statbuf);

    PT_REGS_PARM1(real_regs) = (unsigned long)fd;
    PT_REGS_PARM2(real_regs) = (unsigned long)statbuf;
}

static __init void ksu_hook_sys_fstat64(void)
{
    unsigned long addr;

    addr = find_kernel_symbol_exact(SYS_FSTAT64_SYMBOL);

    if (!addr) {
        pr_err("Can't find address of sys_fstat64");
        return;
    }

    pr_info("%s: sys_fstat64 target=%px (%pS)\n", __func__, (void *)addr, (void *)addr);

    struct ksu_inline_hook_config config = { .target = addr, .before = NULL, .after = ksu_on_sys_fstat64 };

    if (ksu_fstat64_hook)
        return;

    ksu_fstat64_hook = ksu_inline_hook_register(config);
    if (IS_ERR(ksu_fstat64_hook)) {
        pr_err("%s: failed to hook sys_fstat64: %ld\n", __func__, PTR_ERR(ksu_fstat64_hook));
        ksu_fstat64_hook = NULL;
        return;
    }
}

static __exit void ksu_unhook_sys_fstat64(void)
{
    ksu_inline_hook_unregister(ksu_fstat64_hook);
    ksu_fstat64_hook = NULL;
}
#endif

void __init ksu_auto_hook_init(void)
{
#ifdef KSU_HOOK_AUTO_REBOOT_HOOK
    ksu_hook_sys_reboot();
#endif
#ifdef KSU_HOOK_AUTO_EXECVE_HOOK
    ksu_hook_sys_execve();
#endif
#ifdef KSU_HOOK_AUTO_FACCESSAT_HOOK
    ksu_hook_sys_faccessat();
#endif
#ifdef KSU_HOOK_AUTO_STAT_HOOK
    ksu_hook_sys_newfstatat();
#endif
#ifdef KSU_HOOK_AUTO_NEWFSTAT_HOOK
    ksu_hook_sys_newfstat();
#endif
#ifdef KSU_HOOK_AUTO_FSTAT64_HOOK
    ksu_hook_sys_fstat64();
#endif
}

void __exit ksu_auto_hook_exit(void)
{
#ifdef KSU_HOOK_AUTO_REBOOT_HOOK
    ksu_unhook_sys_reboot();
#endif
#ifdef KSU_HOOK_AUTO_EXECVE_HOOK
    ksu_unhook_sys_execve();
#endif
#ifdef KSU_HOOK_AUTO_FACCESSAT_HOOK
    ksu_unhook_sys_faccessat();
#endif
#ifdef KSU_HOOK_AUTO_NEWFSTAT_HOOK
    ksu_unhook_sys_newfstat();
#endif
#ifdef KSU_HOOK_AUTO_FSTAT64_HOOK
    ksu_unhook_sys_fstat64();
#endif
}
