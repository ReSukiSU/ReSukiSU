#include <linux/err.h>
#include <linux/fs.h>
#include <linux/gfp.h>
#include <linux/kernel.h>
#include <linux/slab.h>
#include <linux/version.h>
#include <linux/sched.h>
#include <linux/pid.h>
#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 11, 0)
#include <linux/sched/task.h>
#endif
#ifdef CONFIG_KSU_DEBUG
#include <linux/moduleparam.h>
#endif
#include <crypto/hash.h>
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 11, 0)
#include <crypto/sha2.h>
#else
#include <crypto/sha.h>
#endif

#include "throne_tracker.h"
#include "kernel_compat.h"
#include "dynamic_manager.h"
#include "klog.h" // IWYU pragma: keep
#include "manager.h"
#include "ksu.h"

// Dynamic sign configuration
static struct dynamic_manager_config dynamic_manager = {
    .size = 0x300,
    .hash = "0000000000000000000000000000000000000000000000000000000000000000",
    .is_set = 0
};

bool ksu_is_dynamic_manager_enabled(void)
{
    return dynamic_manager.is_set;
}

apk_sign_key_t ksu_get_dynamic_manager_sign(void)
{
    apk_sign_key_t sign_key = { .size = dynamic_manager.size,
                                .sha256 = dynamic_manager.hash };

    return sign_key;
}

int ksu_handle_dynamic_manager(struct ksu_dynamic_manager_cmd *cmd)
{
    unsigned long flags;
    int ret = 0;
    int i;

    if (!cmd) {
        return -EINVAL;
    }

    switch (cmd->operation) {
    case DYNAMIC_MANAGER_OP_SET:
        if (cmd->size < 0x100 || cmd->size > 0x1000) {
            pr_err("invalid size: 0x%x\n", cmd->size);
            return -EINVAL;
        }

        // Validate hash format
        for (i = 0; i < 64; i++) {
            char c = cmd->hash[i];
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                pr_err("invalid hash character at position %d: %c\n", i, c);
                return -EINVAL;
            }
        }

        if (dynamic_manager.is_set) {
            ksu_unregister_manager_by_signature_index(
                DYNAMIC_MANAGER_SIGNATURE_INDEX_MAGIC);
        }

        dynamic_manager.size = cmd->size;
        // userspace always put an char[64] to our
        // we just use memcpy to copy memory, and flag [64] to \0 by ourselves
        memcpy(dynamic_manager.hash, cmd->hash, 64);
        dynamic_manager.hash[64] = '\0';

        dynamic_manager.is_set = 1;

        track_throne(false, true);
        pr_info(
            "dynamic manager updated: size=0x%x, hash=%.16s... (multi-manager enabled)\n",
            cmd->size, cmd->hash);
        break;

    case DYNAMIC_MANAGER_OP_GET:
        if (dynamic_manager.is_set) {
            cmd->size = dynamic_manager.size;
            memcpy(cmd->hash, dynamic_manager.hash,
                   64); // just copy [64] is enough, userspace will handle that
            ret = 0;
        } else {
            ret = -ENODATA;
        }
        break;
    case DYNAMIC_MANAGER_OP_WIPE:
        dynamic_manager.is_set = 0;
        ret = 0;
        ksu_unregister_manager_by_signature_index(
            DYNAMIC_MANAGER_SIGNATURE_INDEX_MAGIC);
        pr_info("dynamic manager kernel settings reseted");
        break;

    default:
        pr_err("Invalid dynamic manager operation: %d\n", cmd->operation);
        return -EINVAL;
    }

    return ret;
}

void ksu_dynamic_manager_init(void)
{
    // do nothing
}

void ksu_dynamic_manager_exit(void)
{
    // do nothing
}
