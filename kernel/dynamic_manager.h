#ifndef __KSU_H_DYNAMIC_MANAGER
#define __KSU_H_DYNAMIC_MANAGER

#include <linux/types.h>
#include "ksu.h"
#include "supercalls.h"
#include "manager_sign.h"

#define DYNAMIC_MANAGER_FILE_MAGIC 0x7f445347 // 'DSG', u32
#define DYNAMIC_MANAGER_FILE_VERSION 1 // u32
#define KERNEL_SU_DYNAMIC_MANAGER "/data/adb/ksu/.dynamic_manager"
#define DYNAMIC_MANAGER_SIGNATURE_INDEX_MAGIC 255

struct dynamic_sign_key {
    unsigned size;
    const char *hash;
};

#define DYNAMIC_SIGN_DEFAULT_CONFIG                                            \
    {                                                                          \
        .size = 0x300,                                                         \
        .hash =                                                                \
            "0000000000000000000000000000000000000000000000000000000000000000" \
    }

struct dynamic_manager_config {
    unsigned size;
    char hash[65];
    int is_set;
};

struct manager_info {
    uid_t uid;
    int signature_index;
    bool is_active;
};

// Dynamic sign operations
void ksu_dynamic_manager_init(void);
void ksu_dynamic_manager_exit(void);
int ksu_handle_dynamic_manager(struct ksu_dynamic_manager_cmd *cmd);
bool ksu_load_dynamic_manager(void);
bool ksu_is_dynamic_manager_enabled(void);
apk_sign_key_t ksu_get_dynamic_manager_sign(void);

// Configuration access for signature verification
bool ksu_get_dynamic_manager_config(unsigned int *size, const char **hash);

#endif