#ifndef __KSU_H_ADB_ROOT
#define __KSU_H_ADB_ROOT
#include <asm/ptrace.h>
#include "runtime/ksud.h"

#ifdef CONFIG_KSU_TRACEPOINT_HOOK
long ksu_adb_root_handle_execve_tracepoint(struct pt_regs *regs);
#else
long ksu_adb_root_handle_execve_manual(const char *filename, struct user_arg_ptr *envp_p);
#endif

#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 9, 0)
/*
 * Hide adb-root related SELinux contexts from user-space SELinux probes.
 * This is used by selinux_hide to prevent tools such as DirtySepolicy
 * from detecting adb_root through context validation and access checks.
 */
bool ksu_adb_root_should_hide_context(const char *context);
#endif

void ksu_adb_root_init(void);

void ksu_adb_root_exit(void);

#endif
