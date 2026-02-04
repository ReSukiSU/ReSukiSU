#include <linux/err.h>
#include <linux/fs.h>
#include <linux/gfp.h>
#include <linux/kernel.h>
#include <linux/slab.h>
#include <linux/version.h>
#include <linux/workqueue.h>
#include <linux/task_work.h>
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

// Task work structure for persistent storage
struct save_dynamic_manager_tw {
    struct callback_head cb;
    struct dynamic_manager_config config;
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

static void do_save_dynamic_manager_and_track_throne(struct callback_head *_cb)
{
    struct save_dynamic_manager_tw *tw =
        container_of(_cb, struct save_dynamic_manager_tw, cb);
    u32 magic = DYNAMIC_MANAGER_FILE_MAGIC;
    u32 version = DYNAMIC_MANAGER_FILE_VERSION;
    loff_t off = 0;
    struct file *fp;
    const struct cred *saved = override_creds(ksu_cred);

    if (!tw->config.is_set) {
        pr_info("Dynamic sign config not set, skipping save\n");
        goto revert;
    }

    fp = filp_open(KERNEL_SU_DYNAMIC_MANAGER, O_WRONLY | O_CREAT | O_TRUNC,
                   0644);
    if (IS_ERR(fp)) {
        pr_err("save_dynamic_manager create file failed: %ld\n", PTR_ERR(fp));
        goto revert;
    }

    if (ksu_kernel_write_compat(fp, &magic, sizeof(magic), &off) !=
        sizeof(magic)) {
        pr_err("save_dynamic_manager write magic failed.\n");
        goto close_file;
    }

    if (ksu_kernel_write_compat(fp, &version, sizeof(version), &off) !=
        sizeof(version)) {
        pr_err("save_dynamic_manager write version failed.\n");
        goto close_file;
    }

    if (ksu_kernel_write_compat(fp, &tw->config, sizeof(tw->config), &off) !=
        sizeof(tw->config)) {
        pr_err("save_dynamic_manager write config failed.\n");
        goto close_file;
    }

    pr_info("Dynamic sign config saved successfully\n");

    track_throne(false);

close_file:
    filp_close(fp, 0);
revert:
    revert_creds(saved);
    kfree(tw);
}

static void do_load_dynamic_manager(struct callback_head *_cb)
{
    loff_t off = 0;
    ssize_t ret = 0;
    struct file *fp = NULL;
    u32 magic;
    u32 version;
    struct dynamic_manager_config loaded_config;
    unsigned long flags;
    int i;
    const struct cred *saved = override_creds(ksu_cred);

    fp = filp_open(KERNEL_SU_DYNAMIC_MANAGER, O_RDONLY, 0);
    if (IS_ERR(fp)) {
        if (PTR_ERR(fp) == -ENOENT) {
            pr_info("No saved dynamic manager config found\n");
        } else {
            pr_err("load_dynamic_manager open file failed: %ld\n", PTR_ERR(fp));
        }
        goto revert;
    }

    if (ksu_kernel_read_compat(fp, &magic, sizeof(magic), &off) !=
            sizeof(magic) ||
        magic != DYNAMIC_MANAGER_FILE_MAGIC) {
        pr_err("dynamic manager file invalid magic: %x!\n", magic);
        goto close_file;
    }

    if (ksu_kernel_read_compat(fp, &version, sizeof(version), &off) !=
        sizeof(version)) {
        pr_err("dynamic manager read version failed\n");
        goto close_file;
    }

    pr_info("dynamic manager file version: %d\n", version);

    ret =
        ksu_kernel_read_compat(fp, &loaded_config, sizeof(loaded_config), &off);
    if (ret <= 0) {
        pr_info("load_dynamic_manager read err: %zd\n", ret);
        goto close_file;
    }

    if (ret != sizeof(loaded_config)) {
        pr_err("load_dynamic_manager read incomplete config: %zd/%zu\n", ret,
               sizeof(loaded_config));
        goto close_file;
    }

    if (loaded_config.size < 0x100 || loaded_config.size > 0x1000) {
        pr_err("Invalid saved config size: 0x%x\n", loaded_config.size);
        goto close_file;
    }

    if (strlen(loaded_config.hash) != 64) {
        pr_err("Invalid saved config hash length: %zu\n",
               strlen(loaded_config.hash));
        goto close_file;
    }

    // Validate hash format
    for (i = 0; i < 64; i++) {
        char c = loaded_config.hash[i];
        if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
            pr_err("Invalid saved config hash character at position %d: %c\n",
                   i, c);
            goto close_file;
        }
    }

    dynamic_manager = loaded_config;

    pr_info("Dynamic sign config loaded: size=0x%x, hash=%.16s...\n",
            loaded_config.size, loaded_config.hash);

close_file:
    filp_close(fp, 0);
revert:
    revert_creds(saved);
    kfree(_cb);
}

static bool save_dynamic_manager_and_track_throne(void)
{
    struct task_struct *tsk;
    struct save_dynamic_manager_tw *tw;
    unsigned long flags;

    tsk = get_pid_task(find_vpid(1), PIDTYPE_PID);
    if (!tsk) {
        pr_err("save_dynamic_manager_and_track_throne find init task err\n");
        return false;
    }

    tw = kzalloc(sizeof(*tw), GFP_KERNEL);
    if (!tw) {
        pr_err("save_dynamic_manager_and_track_throne alloc cb err\n");
        goto put_task;
    }

    tw->config = dynamic_manager;

    tw->cb.func = do_save_dynamic_manager_and_track_throne;
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 10, 0)
    task_work_add(tsk, &tw->cb, TWA_RESUME);
#else
    task_work_add(tsk, &tw->cb, true);
#endif

put_task:
    put_task_struct(tsk);
    return true;
}

static void do_clear_dynamic_manager(struct callback_head *_cb)
{
    loff_t off = 0;
    struct file *fp;
    char zero_buffer[512];
    const struct cred *saved = override_creds(ksu_cred);

    memset(zero_buffer, 0, sizeof(zero_buffer));

    fp = filp_open(KERNEL_SU_DYNAMIC_MANAGER, O_WRONLY | O_CREAT | O_TRUNC,
                   0644);
    if (IS_ERR(fp)) {
        pr_err("clear_dynamic_manager create file failed: %ld\n", PTR_ERR(fp));
        goto revert;
    }

    // Write null bytes to overwrite the file content
    if (ksu_kernel_write_compat(fp, zero_buffer, sizeof(zero_buffer), &off) !=
        sizeof(zero_buffer)) {
        pr_err("clear_dynamic_manager write null bytes failed.\n");
    } else {
        pr_info("Dynamic sign config file cleared successfully\n");
    }

    filp_close(fp, 0);
revert:
    revert_creds(saved);
    kfree(_cb);
}

static bool clear_dynamic_manager_file(void)
{
    struct task_struct *tsk;
    struct callback_head *cb;

    tsk = get_pid_task(find_vpid(1), PIDTYPE_PID);
    if (!tsk) {
        pr_err("clear_dynamic_manager_file find init task err\n");
        return false;
    }

    cb = kzalloc(sizeof(*cb), GFP_KERNEL);
    if (!cb) {
        pr_err("clear_dynamic_manager_file alloc cb err\n");
        goto put_task;
    }
    cb->func = do_clear_dynamic_manager;
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 10, 0)
    task_work_add(tsk, cb, TWA_RESUME);
#else
    task_work_add(tsk, cb, true);
#endif

put_task:
    put_task_struct(tsk);
    return true;
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

        if (strlen(cmd->hash) != 64) {
            pr_err("invalid hash length: %zu\n", strlen(cmd->hash));
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
#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 13, 0)
        strscpy(dynamic_manager.hash, cmd->hash, sizeof(dynamic_manager.hash));
#else
        strlcpy(dynamic_manager.hash, cmd->hash, sizeof(dynamic_manager.hash));
#endif
        dynamic_manager.is_set = 1;

        save_dynamic_manager_and_track_throne();
        pr_info(
            "dynamic manager updated: size=0x%x, hash=%.16s... (multi-manager enabled)\n",
            cmd->size, cmd->hash);
        break;

    case DYNAMIC_MANAGER_OP_GET:
        if (dynamic_manager.is_set) {
            cmd->size = dynamic_manager.size;
#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 13, 0)
            strscpy(cmd->hash, dynamic_manager.hash, sizeof(cmd->hash));
#else
            strlcpy(cmd->hash, dynamic_manager.hash, sizeof(cmd->hash));
#endif
            ret = 0;
        } else {
            ret = -ENODATA;
        }
        break;

    case DYNAMIC_MANAGER_OP_CLEAR:
        if (!dynamic_manager.is_set)
            break;
        ksu_unregister_manager_by_signature_index(
            DYNAMIC_MANAGER_SIGNATURE_INDEX_MAGIC);

        dynamic_manager.size = 0x300;
        strcpy(
            dynamic_manager.hash,
            "0000000000000000000000000000000000000000000000000000000000000000");
        dynamic_manager.is_set = 0;

        // Clear file using the same method as save
        clear_dynamic_manager_file();

        pr_info("Dynamic sign config cleared (multi-manager disabled)\n");
        break;

    default:
        pr_err("Invalid dynamic manager operation: %d\n", cmd->operation);
        return -EINVAL;
    }

    return ret;
}

bool ksu_load_dynamic_manager(void)
{
    struct task_struct *tsk;
    struct callback_head *cb;

    tsk = get_pid_task(find_vpid(1), PIDTYPE_PID);
    if (!tsk) {
        pr_err("ksu_load_dynamic_manager find init task err\n");
        return false;
    }

    cb = kzalloc(sizeof(*cb), GFP_KERNEL);
    if (!cb) {
        pr_err("ksu_load_dynamic_manager alloc cb err\n");
        goto put_task;
    }
    cb->func = do_load_dynamic_manager;
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 10, 0)
    task_work_add(tsk, cb, TWA_RESUME);
#else
    task_work_add(tsk, cb, true);
#endif

put_task:
    put_task_struct(tsk);
    return true;
}

void ksu_dynamic_manager_init(void)
{
    int i;

    ksu_load_dynamic_manager();

    pr_info(
        "Dynamic sign initialized with conditional multi-manager support\n");
}

void ksu_dynamic_manager_exit(void)
{
    struct task_struct *tsk;
    struct save_dynamic_manager_tw *tw;
    unsigned long flags;

    // Save current config before exit
    tsk = get_pid_task(find_vpid(1), PIDTYPE_PID);
    if (!tsk) {
        pr_err("ksu_dynamic_manager_exit find init task err\n");
        return;
    }

    tw = kzalloc(sizeof(*tw), GFP_KERNEL);
    if (!tw) {
        pr_err("ksu_dynamic_manager_exit alloc cb err\n");
        goto put_task;
    }

    tw->config = dynamic_manager;

    tw->cb.func = do_save_dynamic_manager_and_track_throne;
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 10, 0)
    task_work_add(tsk, &tw->cb, TWA_RESUME);
#else
    task_work_add(tsk, &tw->cb, true);
#endif

put_task:
    put_task_struct(tsk);
    pr_info("Dynamic sign exited with persistent storage\n");
}

// Get dynamic manager configuration for signature verification
bool ksu_get_dynamic_manager_config(unsigned int *size, const char **hash)
{
    unsigned long flags;
    bool valid = false;

    if (dynamic_manager.is_set) {
        if (size)
            *size = dynamic_manager.size;
        if (hash)
            *hash = dynamic_manager.hash;
        valid = true;
    }

    return valid;
}
