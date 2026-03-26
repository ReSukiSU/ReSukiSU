#ifndef __KSU_H_KSU
#define __KSU_H_KSU

#include <linux/types.h>
#include <linux/cred.h>
#include <linux/workqueue.h>

#define KERNEL_SU_VERSION KSU_VERSION
#define KERNEL_SU_OPTION 0xDEADBEEF

extern struct cred *ksu_cred;
extern bool ksu_late_loaded;
extern bool allow_shell;

// kernel su version full strings
#ifndef KSU_VERSION_FULL
#define KSU_VERSION_FULL "v3.x-00000000@unknown"
#endif
#define KSU_FULL_VERSION_STRING 255

void ksu_lsm_hook_init(void);

#endif
