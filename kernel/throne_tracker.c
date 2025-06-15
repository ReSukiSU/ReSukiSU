#include <linux/err.h>
#include <linux/fs.h>
#include <linux/list.h>
#include <linux/slab.h>
#include <linux/bitmap.h>
#include <linux/string.h>
#include <linux/types.h>
#include <linux/version.h>
#include <linux/stat.h>
#include <linux/namei.h>

#include "allowlist.h"
#include "apk_sign.h"
#include "klog.h" // IWYU pragma: keep
#include "manager.h"
#include "throne_tracker.h"
#include "apk_sign.h"
#include "kernel_compat.h"
#include "dynamic_manager.h"

#define SYSTEM_PACKAGES_LIST_PATH "/data/system/packages.list"
#define MAX_APP_ID 10000 // FIRST_APPLICATION_UID - LAST_APPLICATION_UID = 19999

struct uid_data {
    struct list_head list;
    u32 uid;
    char package[KSU_MAX_PACKAGE_NAME];
};

static unsigned long *last_app_id_map = NULL;
static DEFINE_MUTEX(app_list_lock);

static void crown_manager(const char *apk, struct list_head *uid_data,
                          u8 signature_index)
{
    char pkg[KSU_MAX_PACKAGE_NAME];
    struct uid_data *np;
    if (get_pkg_from_apk_path(pkg, apk) < 0) {
        pr_err("Failed to get package name from apk path: %s\n", apk);
        return;
    }

    pr_info("manager pkg: %s\n", pkg);

    list_for_each_entry (np, uid_data, list) {
        if (strncmp(np->package, pkg, KSU_MAX_PACKAGE_NAME) == 0) {
            pr_info("Crowning manager: %s uid=%d, signature_index=%d\n", pkg,
                    np->uid, signature_index);

            ksu_register_manager(np->uid, signature_index);
            break;
        }
    }
}

#define DATA_PATH_LEN 384 // 384 is enough for /data/app/<package>/base.apk

struct data_path {
    char dirpath[DATA_PATH_LEN];
    int depth;
    struct list_head list;
};

struct apk_path_hash {
    unsigned int hash;
    bool exists;
    struct list_head list;
};

static struct list_head apk_path_hash_list = LIST_HEAD_INIT(apk_path_hash_list);

struct my_dir_context {
    struct dir_context ctx;
    struct list_head *data_path_list;
    char *parent_dir;
    void *private_data;
    int depth;
};
// https://docs.kernel.org/filesystems/porting.html
// filldir_t (readdir callbacks) calling conventions have changed. Instead of returning 0 or -E... it returns bool now. false means "no more" (as -E... used to) and true - "keep going" (as 0 in old calling conventions). Rationale: callers never looked at specific -E... values anyway. -> iterate_shared() instances require no changes at all, all filldir_t ones in the tree converted.
#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 1, 0)
#define FILLDIR_RETURN_TYPE bool
#define FILLDIR_ACTOR_CONTINUE true
#define FILLDIR_ACTOR_STOP false
#else
#define FILLDIR_RETURN_TYPE int
#define FILLDIR_ACTOR_CONTINUE 0
#define FILLDIR_ACTOR_STOP -EINVAL
#endif

#if LINUX_VERSION_CODE >= KERNEL_VERSION(3, 19, 0)
#define MY_ACTOR_CTX_ARG struct dir_context *ctx
#else
#define MY_ACTOR_CTX_ARG void *ctx_void
#endif

FILLDIR_RETURN_TYPE my_actor(MY_ACTOR_CTX_ARG, const char *name, int namelen,
                             loff_t off, u64 ino, unsigned int d_type)
{
#if LINUX_VERSION_CODE < KERNEL_VERSION(3, 19, 0)
    // then pull it out of the void
    struct dir_context *ctx = (struct dir_context *)ctx_void;
#endif
    struct my_dir_context *my_ctx =
        container_of(ctx, struct my_dir_context, ctx);
    char dirpath[DATA_PATH_LEN];

    if (!my_ctx) {
        pr_err("Invalid context\n");
        return FILLDIR_ACTOR_STOP;
    }

    if (!strncmp(name, "..", namelen) || !strncmp(name, ".", namelen))
        return FILLDIR_ACTOR_CONTINUE; // Skip "." and ".."

    if (d_type == DT_DIR && namelen >= 8 && !strncmp(name, "vmdl", 4) &&
        !strncmp(name + namelen - 4, ".tmp", 4)) {
        pr_info("Skipping directory: %.*s\n", namelen, name);
        return FILLDIR_ACTOR_CONTINUE; // Skip staging package
    }

    if (snprintf(dirpath, DATA_PATH_LEN, "%s/%.*s", my_ctx->parent_dir, namelen,
                 name) >= DATA_PATH_LEN) {
        pr_err("Path too long: %s/%.*s\n", my_ctx->parent_dir, namelen, name);
        return FILLDIR_ACTOR_CONTINUE;
    }

    if (d_type == DT_DIR && my_ctx->depth > 0) {
        struct data_path *data = kzalloc(sizeof(struct data_path), GFP_ATOMIC);

        if (!data) {
            pr_err("Failed to allocate memory for %s\n", dirpath);
            return FILLDIR_ACTOR_CONTINUE;
        }

        strscpy(data->dirpath, dirpath, DATA_PATH_LEN);
        data->depth = my_ctx->depth - 1;
        list_add_tail(&data->list, my_ctx->data_path_list);
    } else {
        if ((namelen == 8) && (strncmp(name, "base.apk", namelen) == 0)) {
            struct apk_path_hash *pos, *n;
#if LINUX_VERSION_CODE < KERNEL_VERSION(4, 8, 0)
            unsigned int hash = full_name_hash(dirpath, strlen(dirpath));
#else
            unsigned int hash = full_name_hash(NULL, dirpath, strlen(dirpath));
#endif
            u8 signature_index = 0;
            struct apk_path_hash *apk_data = NULL;
            bool manager = is_manager_apk(dirpath, &signature_index);

            list_for_each_entry (pos, &apk_path_hash_list, list) {
                if (hash == pos->hash) {
                    pos->exists = true;
                    return FILLDIR_ACTOR_CONTINUE;
                }
            }

            pr_info("Found new base.apk at path: %s\n", dirpath);

            apk_data = kzalloc(sizeof(*apk_data), GFP_ATOMIC);
            if (apk_data) {
                apk_data->hash = hash;
                apk_data->exists = true;
                list_add_tail(&apk_data->list, &apk_path_hash_list);
            }

            if (manager) {
                crown_manager(dirpath, my_ctx->private_data, signature_index);

                // Manager found, clear APK cache list
                list_for_each_entry_safe (pos, n, &apk_path_hash_list, list) {
                    list_del(&pos->list);
                    kfree(pos);
                }
            }
        }
    }

    return FILLDIR_ACTOR_CONTINUE;
}

void search_manager(const char *path, int depth, struct list_head *uid_data)
{
    int i;
    unsigned long data_app_magic = 0;
    struct apk_path_hash *pos, *n;
    struct list_head data_path_list;
    struct data_path data;

    INIT_LIST_HEAD(&data_path_list);

    // Initialize APK cache list
    list_for_each_entry (pos, &apk_path_hash_list, list) {
        pos->exists = false;
    }

    // First depth
    strscpy(data.dirpath, path, DATA_PATH_LEN);
    data.depth = depth;
    list_add_tail(&data.list, &data_path_list);

    for (i = depth; i >= 0; i--) {
        struct data_path *pos, *n;

        list_for_each_entry_safe (pos, n, &data_path_list, list) {
            struct my_dir_context ctx = {
                .ctx.actor = my_actor,
                .data_path_list = &data_path_list,
                .parent_dir = pos->dirpath,
                .private_data = uid_data,
                .depth = pos->depth,
            };
            struct file *file;

            file = ksu_filp_open_compat(pos->dirpath, O_RDONLY | O_NOFOLLOW, 0);
            if (IS_ERR(file)) {
                pr_err("Failed to open directory: %s, err: %ld\n", pos->dirpath,
                       PTR_ERR(file));
                goto skip_iterate;
            }

            // grab magic on first folder, which is /data/app
            if (!data_app_magic) {
                if (file->f_inode->i_sb->s_magic) {
                    data_app_magic = file->f_inode->i_sb->s_magic;
                    pr_info("%s: dir: %s got magic! 0x%lx\n", __func__,
                            pos->dirpath, data_app_magic);
                } else {
                    filp_close(file, NULL);
                    goto skip_iterate;
                }
            }

            if (file->f_inode->i_sb->s_magic != data_app_magic) {
                pr_info("%s: skip: %s magic: 0x%lx expected: 0x%lx\n", __func__,
                        pos->dirpath, file->f_inode->i_sb->s_magic,
                        data_app_magic);
                filp_close(file, NULL);
                goto skip_iterate;
            }

            iterate_dir(file, &ctx.ctx);
            filp_close(file, NULL);
        skip_iterate:
            list_del(&pos->list);
            if (pos != &data)
                kfree(pos);
        }
    }

    // Remove stale cached APK entries
    list_for_each_entry_safe (pos, n, &apk_path_hash_list, list) {
        if (!pos->exists) {
            list_del(&pos->list);
            kfree(pos);
        }
    }
}

static bool is_uid_exist(uid_t uid, char *package, void *data)
{
    struct list_head *list = (struct list_head *)data;
    struct uid_data *np;

    bool exist = false;
    list_for_each_entry (np, list, list) {
        if (np->uid == uid % PER_USER_RANGE &&
            strncmp(np->package, package, KSU_MAX_PACKAGE_NAME) == 0) {
            exist = true;
            break;
        }
    }
    return exist;
}

void track_throne(bool prune_only, bool force_search_manager)
{
    struct list_head uid_list;
    struct uid_data *np, *n;
    struct file *fp;
    char chr = 0;
    loff_t pos = 0;
    loff_t line_start = 0;
    char buf[KSU_MAX_PACKAGE_NAME];
    bool need_search = force_search_manager;

    // init uid list head, bitmap
    unsigned long *curr_app_id_map = NULL;
    unsigned long *diff_map = NULL;

    mutex_lock(&app_list_lock);
    if (unlikely(!last_app_id_map)) {
        last_app_id_map = ksu_bitmap_zalloc(MAX_APP_ID, GFP_KERNEL);
    }
    mutex_unlock(&app_list_lock);

    curr_app_id_map = ksu_bitmap_zalloc(MAX_APP_ID, GFP_KERNEL);
    if (!curr_app_id_map) {
        pr_err("track_throne: failed to allocate curr_app_id_map\n");
        return;
    }

    diff_map = ksu_bitmap_zalloc(MAX_APP_ID, GFP_KERNEL);
    if (!diff_map) {
        pr_err("track_throne: failed to allocate diff_map\n");
        ksu_bitmap_free(curr_app_id_map); // Free allocated memory when failed
        return;
    }
    INIT_LIST_HEAD(&uid_list);

    fp = ksu_filp_open_compat(SYSTEM_PACKAGES_LIST_PATH, O_RDONLY, 0);
    if (IS_ERR(fp)) {
        pr_err("%s: open " SYSTEM_PACKAGES_LIST_PATH " failed: %ld\n", __func__,
               PTR_ERR(fp));
        goto out;
    }

    for (;;) {
        struct uid_data *data = NULL;
        ssize_t count = ksu_kernel_read_compat(fp, &chr, sizeof(chr), &pos);
        const char *delim = " ";
        char *package = NULL;
        char *tmp = NULL;
        char *uid = NULL;
        u32 res;

        if (count != sizeof(chr))
            break;
        if (chr != '\n')
            continue;

        count = ksu_kernel_read_compat(fp, buf, sizeof(buf), &line_start);
        data = kzalloc(sizeof(struct uid_data), GFP_ATOMIC);
        if (!data) {
            filp_close(fp, 0);
            goto out;
        }

        tmp = buf;

        package = strsep(&tmp, delim);
        uid = strsep(&tmp, delim);
        if (!uid || !package) {
            pr_err("update_uid: package or uid is NULL!\n");
            break;
        }

        if (kstrtou32(uid, 10, &res)) {
            pr_err("update_uid: uid parse err\n");
            break;
        }
        data->uid = res;
        strncpy(data->package, package, KSU_MAX_PACKAGE_NAME);
        list_add_tail(&data->list, &uid_list);

        u16 appid = res % PER_USER_RANGE;

        if (appid >= FIRST_APPLICATION_UID &&
            appid < (FIRST_APPLICATION_UID + MAX_APP_ID)) {
            set_bit(appid - FIRST_APPLICATION_UID, curr_app_id_map);
        }
        // reset line start
        line_start = pos;
    }

    filp_close(fp, 0);

    if (prune_only)
        goto prune;

    // check uninstalled is manager, and
    // run search_manager when new application installed
    mutex_lock(&app_list_lock);

    if (bitmap_andnot(diff_map, last_app_id_map, curr_app_id_map, MAX_APP_ID)) {
        int bit = -1;
        while ((bit = find_next_bit(diff_map, MAX_APP_ID, bit + 1)) <
               MAX_APP_ID) {
            u16 appid = bit + FIRST_APPLICATION_UID;
            // we check the uninstalled app is manager or not
            // if it is manager, unregister its appid,
            // because it is invalid for now,
            // if keep them alive, we may grant unknown app manager privillage
            if (ksu_is_manager_appid(appid)) {
                pr_info("Manager APK removed, invalidate previous App ID: %d\n",
                        appid);
                ksu_unregister_manager(appid);
            }
        }
    }

    if (bitmap_andnot(diff_map, curr_app_id_map, last_app_id_map, MAX_APP_ID)) {
        if (!bitmap_empty(diff_map, MAX_APP_ID)) {
            // because we maybe have more than 1 manager alive in same time,
            // always search manager when user install new apps
            need_search = true;
        }
    }

    bitmap_copy(last_app_id_map, curr_app_id_map, MAX_APP_ID);
    mutex_unlock(&app_list_lock);

    if (need_search) {
        pr_info("Searching for manager(s)...\n");
        search_manager("/data/app", 2, &uid_list);
        pr_info("Manager search finished\n");
    }

prune:
    // then prune the allowlist
    ksu_prune_allowlist(is_uid_exist, &uid_list);
out:
    // free uid_list
    list_for_each_entry_safe (np, n, &uid_list, list) {
        list_del(&np->list);
        kfree(np);
    }

    if (curr_app_id_map)
        ksu_bitmap_free(curr_app_id_map);
    if (diff_map)
        ksu_bitmap_free(diff_map);
}

void ksu_throne_tracker_init(void)
{
    // nothing to do
}

void ksu_throne_tracker_exit(void)
{
    // nothing to do
}
