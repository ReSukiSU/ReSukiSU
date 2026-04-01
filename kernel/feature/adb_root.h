#ifndef __KSU_H_ADB_ROOT
#define __KSU_H_ADB_ROOT
#include <asm/ptrace.h>
#include "runtime/ksud.h"

#ifdef KSU_TP_HOOK
long ksu_adb_root_handle_execve_tracepoint(struct pt_regs *regs);
#else
long ksu_adb_root_handle_execve_manual(const char *filename, struct user_arg_ptr *envp_p);
#endif

void ksu_adb_root_init(void);

void ksu_adb_root_exit(void);

#endif
