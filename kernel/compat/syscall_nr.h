#ifndef __KSU_H_COMPAT_SYSCALL_NR
#define __KSU_H_COMPAT_SYSCALL_NR

#ifdef CONFIG_COMPAT
struct ksu_compat_syscall_numbers {
    int read;
    int fstat64;
    int setresuid;
    int execve;
    int execveat;
    int fstatat64;
    int faccessat;
};

extern const struct ksu_compat_syscall_numbers ksu_compat_syscalls;
#endif

#endif
