#include <linux/version.h>
#include "syscall_nr.h"

#ifdef CONFIG_COMPAT
/* Keep the legacy arm64 compat __NR_* definitions out of native syscall users. */
#if defined(__aarch64__)
#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 11, 0) || defined(KSU_COMPAT_HAS_NR_COMPAT32_SYSCALLS)
#include <asm/unistd_compat_32.h>
#define KSU_COMPAT_NR(name) __NR_compat32_##name
#else
#include <asm/unistd32.h>
#define KSU_COMPAT_NR(name) __NR_##name
#endif
#elif defined(__x86_64__)
#include <asm/unistd_32_ia32.h>
#define KSU_COMPAT_NR(name) __NR_ia32_##name
#endif

const struct ksu_compat_syscall_numbers ksu_compat_syscalls = {
    .read = KSU_COMPAT_NR(read),
    .fstat64 = KSU_COMPAT_NR(fstat64),
    .setresuid = KSU_COMPAT_NR(setresuid32),
    .execve = KSU_COMPAT_NR(execve),
    .execveat = KSU_COMPAT_NR(execveat),
    .fstatat64 = KSU_COMPAT_NR(fstatat64),
    .faccessat = KSU_COMPAT_NR(faccessat),
};
#endif
