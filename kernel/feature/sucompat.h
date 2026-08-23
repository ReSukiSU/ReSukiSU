#ifndef __KSU_H_SUCOMPAT
#define __KSU_H_SUCOMPAT
#include <asm/ptrace.h>
#include <linux/types.h>
#include "compat/kernel_compat.h"

#ifdef KSU_COMPAT_USE_STATIC_KEY
extern struct static_key_true ksu_su_compat_enabled;
#else
extern bool ksu_su_compat_enabled;
#endif

void ksu_sucompat_init(void);
void ksu_sucompat_exit(void);

// Handler functions exported for hook_manager
#ifdef CONFIG_KSU_SUSFS
int ksu_handle_faccessat(int *dfd, struct filename **filename, int *mode, int *__unused_flags);
int ksu_handle_stat(int *dfd, struct filename **filename, int *flags);
#else
int ksu_handle_faccessat(int *dfd, const char __user **filename_user, int *mode, int *__unused_flags);
int ksu_handle_stat(int *dfd, const char __user **filename_user, int *flags);
#endif // #ifdef CONFIG_KSU_SUSFS

#ifdef CONFIG_KSU_TRACEPOINT_HOOK
#include <asm/current.h>
#include "hook/tp_marker.h"

// WARNING! THERE HAVE TRYING TO CALL SYSCALL INTERNALLY
// ENSURE CALL IT ONLY IN TRACEPOINT SYSCALL REDIRECT
long ksu_handle_faccessat_sucompat_internal(int orig_nr, struct pt_regs *regs);
long ksu_handle_stat_sucompa_internal(int orig_nr, struct pt_regs *regs);
long ksu_handle_execve_sucompat_internal(const char __user **filename_user, int orig_nr, struct pt_regs *regs);
long ksu_handle_execveat_sucompat_internal(const char __user **filename_user, int orig_nr, struct pt_regs *regs);

// false for ksu_is_current_proc_unprivillege
// when the check of this flag executed in tracepoint, then mean we MUST be marked, or the code won't be executed
#define ksu_is_current_proc_unprivillege() false
#define ksu_set_current_proc_unprivillege() ksu_clear_task_tracepoint_flag_if_needed(current)
#define ksu_clear_current_proc_unprivillege() ksu_set_task_tracepoint_flag(current)

#elif defined(CONFIG_KSU_SUSFS) // susfs
#include <linux/susfs_def.h>

#define ksu_is_current_proc_unprivillege susfs_is_current_proc_no_su
#define ksu_set_current_proc_unprivillege susfs_set_current_proc_no_su
#define ksu_clear_current_proc_unprivillege susfs_clear_current_proc_no_su
#else // manual hook

// 63 already used as TIF_KSU_DISABLE_ESCAPE_WITH_ROOT (64bit)
// 31 already used as TIF_KSU_DISABLE_ESCAPE_WITH_ROOT (32bit)
#ifdef CONFIG_64BIT
#define TIF_PROC_NON_PRIVILEGE 62
#else
#define TIF_PROC_NON_PRIVILEGE 30
#endif

static inline bool ksu_is_current_proc_unprivillege(void)
{
    return (likely(test_thread_flag(TIF_PROC_NON_PRIVILEGE)));
}

static inline void ksu_set_current_proc_unprivillege(void)
{
    set_thread_flag(TIF_PROC_NON_PRIVILEGE);
}

static inline void ksu_clear_current_proc_unprivillege(void)
{
    clear_thread_flag(TIF_PROC_NON_PRIVILEGE);
}
#endif

#endif
