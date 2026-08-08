#ifndef __KSU_H_MODULE_LOAD_FILTER
#define __KSU_H_MODULE_LOAD_FILTER

#include <linux/types.h>

#define KSU_BLOCKED_PRESET_MODULES_MAX 256
#define KSU_MODULE_LOAD_CONTINUE 1

extern char ksu_blocked_preset_modules[KSU_BLOCKED_PRESET_MODULES_MAX];

// Return KSU_MODULE_LOAD_CONTINUE to execute the original syscall. Any other
// value is returned directly to userspace. Manual hooks can use the same
// contract as the tracepoint-hook adapter.
int ksu_handle_init_module(const void __user *umod, unsigned long umod_len);
int ksu_handle_finit_module(int fd, int flags);

#endif
