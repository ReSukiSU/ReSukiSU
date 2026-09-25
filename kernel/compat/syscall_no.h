#ifndef KSU_SYSCALL_NO_H
#define KSU_SYSCALL_NO_H

#include <asm/syscall.h>

// clang-format off
#ifdef CONFIG_COMPAT
    #if defined(__aarch64__)
        #if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 11, 0) || defined(KSU_COMPAT_HAS_NR_COMPAT32_SYSCALLS)
            #include <asm/unistd_compat_32.h>
            #define ksu_get_syscall_no(name) (is_compat_task() ? __NR_compat32_##name : __NR_##name)
            #define ksu_get_compat_syscall_no(name) __NR_compat32_##name
        #else
            // gen compat syscall
            // e.g.
            // _NR_execve -> __COMPAT__NR_execve
            #define __SYSCALL(nr, sym) __COMPAT##nr = (nr),

            enum ksu_compat_syscall_nr {
                #include <asm/unistd32.h>
            };

            #undef __SYSCALL
            #include <asm/unistd.h>

            #define ksu_get_syscall_no(name) (is_compat_task() ? __COMPAT__NR_##name : __NR_##name)
            #define ksu_get_compat_syscall_no(name) __COMPAT__NR_##name
        #endif // 6.11-
    #elif defined(__x86_64__)
        #include <asm/unistd_32_ia32.h>
        #define ksu_get_syscall_no(name) (in_compat_syscall() ? __NR_ia32_##name : __NR_##name)
        #define ksu_get_compat_syscall_no(name) __NR_ia32_##name
    #endif // !__aarch64__ && !__x86_64__
#else
    #define ksu_get_syscall_no(name) __NR_##name
    #define ksu_get_compat_syscall_no(name) CALL ksu_get_compat_syscall_no when CONFIG_COMPAT is NOT enabled is MISTAKE
#endif // !CONFIG_COMPAT

#endif // #ifndef KSU_SYSCALL_NO_H
