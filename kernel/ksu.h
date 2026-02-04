#ifndef __KSU_H_KSU
#define __KSU_H_KSU

#include <linux/types.h>
#include <linux/cred.h>

#define KERNEL_SU_VERSION KSU_VERSION
#define KERNEL_SU_OPTION 0xDEADBEEF

#define EVENT_POST_FS_DATA 1
#define EVENT_BOOT_COMPLETED 2
#define EVENT_MODULE_MOUNTED 3

// kernel su version full strings
#ifndef KSU_VERSION_FULL
#define KSU_VERSION_FULL "v3.x-00000000@unknown"
#endif
#define KSU_FULL_VERSION_STRING 255

struct manager_list_info {
    int count;
    struct {
        uid_t uid;
        int signature_index;
    } managers[2];
};

void ksu_lsm_hook_init(void);

extern struct cred *ksu_cred;

#endif
