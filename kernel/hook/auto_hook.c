/* SPDX-License-Identifier: GPL-2.0-only */

#include <linux/err.h>
#include <linux/fs.h>
#include <linux/version.h>

#include "hook/auto_hook.h"
#include "hook/inline_hook.h"
#include "infra/symbol_resolver.h"
#include "arch.h"
#include "klog.h"

#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 16, 0) && defined(__aarch64__)
typedef long (*ksu_arm64_syscall_clone_t)(const unsigned long *raw_args);
#endif

#ifdef KSU_HOOK_AUTO_REBOOT_HOOK
static struct ksu_inline_hook *ksu_reboot_hook;

#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 16, 0) && defined(__aarch64__)
static __nocfi noinline long ksu_on_sys_reboot(int magic1, int magic2, unsigned int cmd, void __user *arg,
                                               unsigned long arg4, unsigned long arg5, unsigned long *raw_args,
                                               ksu_arm64_syscall_clone_t clone)
{
    (void)arg4;
    (void)arg5;

    ksu_handle_sys_reboot(magic1, magic2, cmd, &arg);
    raw_args[3] = (unsigned long)arg;
    return clone(raw_args);
}
#else
typedef long (*ksu_sys_reboot_fn_t)(int magic1, int magic2, unsigned int cmd, void __user *arg);

static __nocfi noinline long ksu_on_sys_reboot(int magic1, int magic2, unsigned int cmd, void __user *arg)
{
    ksu_sys_reboot_fn_t clone = (ksu_sys_reboot_fn_t)READ_ONCE(ksu_reboot_hook->clone);

    ksu_handle_sys_reboot(magic1, magic2, cmd, &arg);
    return clone(magic1, magic2, cmd, arg);
}
#endif

static __init void ksu_hook_sys_reboot(void)
{
    unsigned long addr;

    addr = find_kernel_symbol_exact(SYS_REBOOT_SYMBOL);

    if (!addr) {
        pr_err("Can't find address of sys_reboot");
        return;
    }

    pr_info("%s: sys_reboot target=%px (%pS)\n", __func__, (void *)addr, (void *)addr);

    struct ksu_inline_hook_config config = {
        .target = (void *)addr,
        .dispatcher = ksu_on_sys_reboot,
#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 16, 0) && defined(__aarch64__)
        .abi = KSU_INLINE_HOOK_ABI_ARM64_SYSCALL,
#endif
        .owner = &ksu_reboot_hook,
    };

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
#include <linux/fcntl.h>
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

#if LINUX_VERSION_CODE >= KERNEL_VERSION(3, 19, 0) || defined(KSU_COMPAT_HAVE_DO_EXECVEAT_COMMON)
typedef int (*ksu_do_execve_common_fn_t)(int fd, struct filename *filename, struct user_arg_ptr argv,
                                         struct user_arg_ptr envp, int flags);
typedef int (*ksu_do_execve_file_fn_t)(int fd, struct filename *filename, struct user_arg_ptr argv,
                                       struct user_arg_ptr envp, int flags, void *file);

static __nocfi noinline int ksu_before_do_execve_common(int fd, struct filename *filename, struct user_arg_ptr argv,
                                                        struct user_arg_ptr envp, int flags)
{
    ksu_do_execve_common_fn_t clone = (ksu_do_execve_common_fn_t)READ_ONCE(ksu_execve_hook->clone);

    if (filename)
        ksu_handle_execveat(&fd, &filename, &argv, &envp, &flags);

    return clone(fd, filename, argv, envp, flags);
}

static __nocfi noinline int ksu_before_do_execve_file(int fd, struct filename *filename, struct user_arg_ptr argv,
                                                      struct user_arg_ptr envp, int flags, void *file)
{
    ksu_do_execve_file_fn_t clone = (ksu_do_execve_file_fn_t)READ_ONCE(ksu_execve_hook->clone);

    if (filename)
        ksu_handle_execveat(&fd, &filename, &argv, &envp, &flags);

    return clone(fd, filename, argv, envp, flags, file);
}
#elif LINUX_VERSION_CODE >= KERNEL_VERSION(3, 14, 0) || defined(KSU_COMPAT_DO_EXECVE_STRUCT_FILENAME)
typedef int (*ksu_do_execve_common_fn_t)(struct filename *filename, struct user_arg_ptr argv, struct user_arg_ptr envp);

static __nocfi noinline int ksu_before_do_execve_common(struct filename *filename, struct user_arg_ptr argv,
                                                        struct user_arg_ptr envp)
{
    ksu_do_execve_common_fn_t clone = (ksu_do_execve_common_fn_t)READ_ONCE(ksu_execve_hook->clone);
    int fd = AT_FDCWD;
    int flags = 0;

    if (filename)
        ksu_handle_execveat(&fd, &filename, &argv, &envp, &flags);

    return clone(filename, argv, envp);
}
#else
typedef int (*ksu_do_execve_common_fn_t)(const char *filename, struct user_arg_ptr argv, struct user_arg_ptr envp);

static __nocfi noinline int ksu_before_do_execve_common(const char *filename, struct user_arg_ptr argv,
                                                        struct user_arg_ptr envp)
{
    ksu_do_execve_common_fn_t clone = (ksu_do_execve_common_fn_t)READ_ONCE(ksu_execve_hook->clone);
    int fd = AT_FDCWD;
    int flags = 0;

    if (filename)
        ksu_handle_execve(&fd, filename, &argv, &envp, &flags);

    return clone(filename, argv, envp);
}
#endif

// fallback of do_execveat_common/__do_execve_file/do_execve_common hook failed
#if LINUX_VERSION_CODE >= KERNEL_VERSION(3, 14, 0) || defined(KSU_COMPAT_DO_EXECVE_STRUCT_FILENAME)
typedef int (*ksu_do_execve_fn_t)(struct filename *filename, const char __user *const __user *argv,
                                  const char __user *const __user *envp);

static __nocfi noinline int ksu_before_do_execve(struct filename *filename, const char __user *const __user *__argv,
                                                 const char __user *const __user *__envp)
#else
typedef int (*ksu_do_execve_fn_t)(const char *filename, const char __user *const __user *argv,
                                  const char __user *const __user *envp);

static __nocfi noinline int ksu_before_do_execve(const char *filename, const char __user *const __user *__argv,
                                                 const char __user *const __user *__envp)
#endif
{
    ksu_do_execve_fn_t clone = (ksu_do_execve_fn_t)READ_ONCE(ksu_execve_hook->clone);
    int fd = AT_FDCWD;
    int flags = 0;
    struct user_arg_ptr argv = { .ptr.native = __argv };
    struct user_arg_ptr envp = { .ptr.native = __envp };

#if LINUX_VERSION_CODE >= KERNEL_VERSION(3, 14, 0) || defined(KSU_COMPAT_DO_EXECVE_STRUCT_FILENAME)
    ksu_handle_execveat(&fd, &filename, &argv, &envp, &flags);
#else
    ksu_handle_execve(&fd, filename, &argv, &envp, &flags);
#endif

    return clone(filename, (const char __user *const __user *)ksu_user_arg_ptr_value(argv),
                 (const char __user *const __user *)ksu_user_arg_ptr_value(envp));
}

static void __init ksu_hook_sys_execve(void)
{
    // hook do_execveat_common/__do_execve_file/do_execve_common
    unsigned long addr;
    void *dispatcher = ksu_before_do_execve_common;

    addr = find_kernel_symbol_exact("do_execve_common");

    if (!addr) {
        addr = find_kernel_symbol_exact("do_execveat_common");
    }

#if LINUX_VERSION_CODE >= KERNEL_VERSION(3, 19, 0) || defined(KSU_COMPAT_HAVE_DO_EXECVEAT_COMMON)
    if (!addr) {
        addr = find_kernel_symbol_exact("__do_execve_file");
        dispatcher = ksu_before_do_execve_file;
    }
#endif

    if (!addr) {
        pr_err("Can't find address both of do_execveat_common/__do_execve_file/do_execve_common");
        goto common_hook_failed;
    }

    pr_info("%s: do_execveat_common/__do_execve_file/do_execve_common target=%px (%pS)\n", __func__, (void *)addr,
            (void *)addr);

    struct ksu_inline_hook_config config = {
        .target = (void *)addr,
        .dispatcher = dispatcher,
        .owner = &ksu_execve_hook,
    };

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

    struct ksu_inline_hook_config execve_config = {
        .target = (void *)addr,
        .dispatcher = ksu_before_do_execve,
        .owner = &ksu_execve_hook,
    };

    if (ksu_execve_hook)
        return;

    ksu_execve_hook = ksu_inline_hook_register(execve_config);
    if (IS_ERR(ksu_execve_hook)) {
        pr_err("%s: failed to hook do_execve: %ld\n", __func__, PTR_ERR(ksu_execve_hook));
        ksu_execve_hook = NULL;
    }

#ifdef CONFIG_COMPAT
    // This shouldn't happen!
    // Or stupid compiler inline everything
    pr_alert("****************************************************************");
    pr_alert("**      NOTICE NOTICE NOTICE NOTICE NOTICE NOTICE NOTICE      **");
    pr_alert("**                                                            **");
    pr_alert("**    CONFIG_COMPAT enabled but fallback to do_execve hook    **");
    pr_alert("**                  ReSukiSU may not work                     **");
    pr_alert("**              Please submit issue to ReSukiSU               **");
    pr_alert("**        With your vmlinux, System.map, config.gz file       **");
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

#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 16, 0) && defined(__aarch64__)
static __nocfi noinline long ksu_on_sys_faccessat(int dfd, const char __user *filename, int mode, unsigned long arg3,
                                                  unsigned long arg4, unsigned long arg5, unsigned long *raw_args,
                                                  ksu_arm64_syscall_clone_t clone)
{
    (void)arg3;
    (void)arg4;
    (void)arg5;

    ksu_handle_faccessat(&dfd, &filename, &mode, NULL);
    raw_args[0] = (unsigned long)dfd;
    raw_args[1] = (unsigned long)filename;
    raw_args[2] = (unsigned long)mode;
    return clone(raw_args);
}
#else
typedef long (*ksu_sys_faccessat_fn_t)(int dfd, const char __user *filename, int mode);

static __nocfi noinline long ksu_on_sys_faccessat(int dfd, const char __user *filename, int mode)
{
    ksu_sys_faccessat_fn_t clone = (ksu_sys_faccessat_fn_t)READ_ONCE(ksu_faccessat_hook->clone);

    ksu_handle_faccessat(&dfd, &filename, &mode, NULL);
    return clone(dfd, filename, mode);
}
#endif

static __init void ksu_hook_sys_faccessat(void)
{
    unsigned long addr;

    addr = find_kernel_symbol_exact(SYS_FACCESSAT_SYMBOL);

    if (!addr) {
        pr_err("Can't find address of sys_faccessat\n");
        return;
    }

    pr_info("%s: faccessat target=%px (%pS)\n", __func__, (void *)addr, (void *)addr);

    struct ksu_inline_hook_config config = {
        .target = (void *)addr,
#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 16, 0) && defined(__aarch64__)
        .abi = KSU_INLINE_HOOK_ABI_ARM64_SYSCALL,
#endif
        .dispatcher = ksu_on_sys_faccessat,
        .owner = &ksu_faccessat_hook,
    };

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

#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 16, 0) && defined(__aarch64__)
static __nocfi noinline long ksu_on_sys_stat(int dfd, const char __user *filename, unsigned long statbuf, int flag,
                                             unsigned long arg4, unsigned long arg5, unsigned long *raw_args,
                                             ksu_arm64_syscall_clone_t clone)
{
    (void)statbuf;
    (void)arg4;
    (void)arg5;

    ksu_handle_stat(&dfd, &filename, &flag);
    raw_args[0] = (unsigned long)dfd;
    raw_args[1] = (unsigned long)filename;
    raw_args[3] = (unsigned long)flag;
    return clone(raw_args);
}
#else
typedef long (*ksu_sys_stat_fn_t)(int dfd, const char __user *filename, unsigned long statbuf, int flag);

static __nocfi noinline long ksu_on_sys_newfstatat(int dfd, const char __user *filename, unsigned long statbuf,
                                                   int flag)
{
    ksu_sys_stat_fn_t clone = (ksu_sys_stat_fn_t)READ_ONCE(ksu_newfstatat_hook->clone);

    ksu_handle_stat(&dfd, &filename, &flag);
    return clone(dfd, filename, statbuf, flag);
}

static __nocfi noinline long ksu_on_sys_fstatat64(int dfd, const char __user *filename, unsigned long statbuf, int flag)
{
    ksu_sys_stat_fn_t clone = (ksu_sys_stat_fn_t)READ_ONCE(ksu_fstatat64_hook->clone);

    ksu_handle_stat(&dfd, &filename, &flag);
    return clone(dfd, filename, statbuf, flag);
}
#endif

static __init void ksu_hook_sys_newfstatat(void)
{
    unsigned long addr;

    addr = find_kernel_symbol_exact(SYS_NEWFSTATAT_SYMBOL);
    if (!addr) {
        pr_err("Can't find address of sys_newfstatat");
        return;
    }

    pr_info("%s: ksu_newfstatat_hook target=%px (%pS)\n", __func__, (void *)addr, (void *)addr);

    struct ksu_inline_hook_config config = {
        .target = (void *)addr,
#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 16, 0) && defined(__aarch64__)
        .dispatcher = ksu_on_sys_stat,
        .abi = KSU_INLINE_HOOK_ABI_ARM64_SYSCALL,
#else
        .dispatcher = ksu_on_sys_newfstatat,
#endif
        .owner = &ksu_newfstatat_hook,
    };

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

    config.target = (void *)addr;
#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 16, 0) && defined(__aarch64__)
    config.dispatcher = ksu_on_sys_stat;
#else
    config.dispatcher = ksu_on_sys_fstatat64;
#endif
    config.owner = &ksu_fstatat64_hook;

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

#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 16, 0) && defined(__aarch64__)
static __nocfi noinline long ksu_on_sys_newfstat(unsigned int fd, struct stat __user *statbuf, unsigned long arg2,
                                                 unsigned long arg3, unsigned long arg4, unsigned long arg5,
                                                 unsigned long *raw_args, ksu_arm64_syscall_clone_t clone)
{
    long ret;

    (void)arg2;
    (void)arg3;
    (void)arg4;
    (void)arg5;

    ret = clone(raw_args);
    ksu_handle_newfstat_ret(&fd, &statbuf);
    return ret;
}
#else
typedef long (*sys_newfstat_fn_t)(unsigned int fd, struct stat __user *statbuf);

static __nocfi noinline long ksu_on_sys_newfstat(unsigned int fd, struct stat __user *statbuf)
{
    sys_newfstat_fn_t clone;
    long ret;

    clone = (sys_newfstat_fn_t)READ_ONCE(ksu_newfstat_hook->clone);
    ret = clone(fd, statbuf);

    ksu_handle_newfstat_ret(&fd, &statbuf);

    return ret;
}
#endif

static __init void ksu_hook_sys_newfstat(void)
{
    unsigned long addr;

    addr = find_kernel_symbol_exact(SYS_FSTAT_SYMBOL);

    if (!addr) {
        pr_err("Can't find address of sys_newfstat");
        return;
    }

    pr_info("%s: sys_newfstat target=%px (%pS)\n", __func__, (void *)addr, (void *)addr);

    struct ksu_inline_hook_config config = {
        .target = (void *)addr,
        .dispatcher = ksu_on_sys_newfstat,
#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 16, 0) && defined(__aarch64__)
        .abi = KSU_INLINE_HOOK_ABI_ARM64_SYSCALL,
#endif
        .owner = &ksu_newfstat_hook,
    };

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

#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 16, 0) && defined(__aarch64__)
static __nocfi noinline long ksu_on_sys_fstat64(unsigned long fd, struct stat64 __user *statbuf, unsigned long arg2,
                                                unsigned long arg3, unsigned long arg4, unsigned long arg5,
                                                unsigned long *raw_args, ksu_arm64_syscall_clone_t clone)
{
    long ret;

    (void)arg2;
    (void)arg3;
    (void)arg4;
    (void)arg5;

    ret = clone(raw_args);
    ksu_handle_fstat64_ret(&fd, &statbuf);
    return ret;
}
#else
typedef long (*sys_fstat64_fn_t)(unsigned long fd, struct stat64 __user *statbuf);

static __nocfi noinline long ksu_on_sys_fstat64(unsigned long fd, struct stat64 __user *statbuf)
{
    sys_fstat64_fn_t clone;
    long ret;

    clone = (sys_fstat64_fn_t)READ_ONCE(ksu_fstat64_hook->clone);
    ret = clone(fd, statbuf);
    ksu_handle_fstat64_ret(&fd, &statbuf);

    return ret;
}
#endif

static __init void ksu_hook_sys_fstat64(void)
{
    unsigned long addr;

    addr = find_kernel_symbol_exact(SYS_FSTAT64_SYMBOL);

    if (!addr) {
        pr_err("Can't find address of sys_fstat64");
        return;
    }

    pr_info("%s: sys_fstat64 target=%px (%pS)\n", __func__, (void *)addr, (void *)addr);

    struct ksu_inline_hook_config config = {
        .target = (void *)addr,
        .dispatcher = ksu_on_sys_fstat64,
#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 16, 0) && defined(__aarch64__)
        .abi = KSU_INLINE_HOOK_ABI_ARM64_SYSCALL,
#endif
        .owner = &ksu_fstat64_hook,
    };

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
#ifdef KSU_HOOK_AUTO_STAT_HOOK
    ksu_unhook_sys_newfstatat();
#endif
#ifdef KSU_HOOK_AUTO_NEWFSTAT_HOOK
    ksu_unhook_sys_newfstat();
#endif
#ifdef KSU_HOOK_AUTO_FSTAT64_HOOK
    ksu_unhook_sys_fstat64();
#endif
}
