#ifndef __KSU_H_KSUD
#define __KSU_H_KSUD

#include <linux/compat.h>
#include <asm/syscall.h>

#define KSUD_PATH "/data/adb/ksud"

void ksu_ksud_init(void);
void ksu_ksud_exit(void);

#define MAX_ARG_STRINGS 0x7FFFFFFF
struct user_arg_ptr {
#ifdef CONFIG_COMPAT
    bool is_compat;
#endif
    union {
        const char __user *const __user *native;
#ifdef CONFIG_COMPAT
        const compat_uptr_t __user *compat;
#endif
    } ptr;
};

void ksu_handle_execveat_ksud(const char *filename, struct user_arg_ptr *argv, struct user_arg_ptr *envp, int *flags);
void ksu_execve_hook_ksud(const struct pt_regs *regs);
void ksu_stop_ksud_execve_hook(void);
void ksu_stop_input_hook_runtime(void);

#endif
