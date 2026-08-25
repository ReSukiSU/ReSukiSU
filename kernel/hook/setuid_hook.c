#include <linux/compiler.h>
#include <linux/version.h>
#include <linux/slab.h>
#include <linux/thread_info.h>
#include <linux/seccomp.h>
#include <linux/printk.h>
#include <linux/sched.h>
#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 10, 0)
#include <linux/sched/signal.h>
#endif
#include <linux/string.h>
#include <linux/types.h>
#include <linux/uaccess.h>
#include <linux/uidgid.h>
#include <linux/namei.h>

#include "policy/app_profile.h"
#include "policy/allowlist.h"
#include "hook/setuid_hook.h"
#include "klog.h" // IWYU pragma: keep
#include "manager/manager_identity.h"
#include "infra/seccomp_cache.h"
#include "supercall/supercall.h"
#ifdef CONFIG_KSU_TRACEPOINT_HOOK
#include "hook/tp_marker.h"
#endif
#include "compat/kernel_compat.h"
#include "feature/kernel_umount.h"
#include "feature/sucompat.h"
#ifdef CONFIG_KSU_SUSFS
#include <linux/susfs_def.h>
#include <linux/workqueue.h>
#endif

static inline void ksu_set_file_immutable(const char *path_name, bool immutable)
{
    struct path path;
    struct inode *inode;
    int error;

    error = kern_path(path_name, LOOKUP_FOLLOW, &path);
    if (error) {
        return;
    }

#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 0, 0) || defined(KSU_HAS_D_INODE)
    inode = d_inode(path.dentry);
#else
    inode = path.dentry->d_inode;
#endif

    error = mnt_want_write(path.mnt);
    if (error) {
        path_put(&path);
        return;
    }

    inode_lock(inode);
    if (immutable) {
        inode->i_flags |= S_IMMUTABLE;
    } else {
        inode->i_flags &= ~S_IMMUTABLE;
    }
    inode_unlock(inode);

    mnt_drop_write(path.mnt);
    path_put(&path);
}

static inline void ksu_set_ksud_status(uid_t new_uid)
{
    u16 appid = new_uid % PER_USER_RANGE;
    int signature_index = ksu_get_manager_signature_index_by_appid(appid);
    if (signature_index != 255) {
        ksu_set_file_immutable("/data/adb/ksud", false);
        pr_info("Mark /data/adb/ksud read write");
    } else {
        ksu_set_file_immutable("/data/adb/ksud", true);
        pr_info("Mark /data/adb/ksud read only");
    }
}

#ifdef CONFIG_KSU_SUSFS
extern struct work_struct susfs_extra_works;

static int handle_zygote_next_setresuid(uid_t new_uid)
{
    // Check if spawned process is isolated service first, and force to do umount if so
    if (is_isolated_process(new_uid)) {
        susfs_set_current_proc_no_su();
        susfs_set_current_proc_umounted();
        susfs_set_current_proc_umounted_for_zygote_next();
        goto do_susfs_work;
    }

    // manager NEVER use zygote next!

    // we should not umount for webview zygote
    if (unlikely(new_uid == WEBVIEW_ZYGOTE_UID)) {
        if (ksu_webview_zygote_umount_enabled) {
            susfs_set_current_proc_no_su();
            susfs_set_current_proc_umounted();
            susfs_set_current_proc_umounted_for_zygote_next();
            goto do_susfs_work;
        }
        susfs_set_current_proc_no_su();
        return 0;
    }

    // Check if spawned process is normal user app and needs to be umounted
    if (likely(is_appuid(new_uid) && ksu_uid_should_umount(new_uid))) {
        susfs_set_current_proc_no_su();
        susfs_set_current_proc_umounted();
        susfs_set_current_proc_umounted_for_zygote_next();
        goto do_susfs_work;
    }

    // - Disable seccomp restriction for root allowed apps since running with "su" will disable seccomp anyway
    if (ksu_is_allow_uid_for_current(new_uid)) {
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 10, 0)
        if (current->seccomp.mode == SECCOMP_MODE_FILTER && current->seccomp.filter) {
            spin_lock_irq(&current->sighand->siglock);
            ksu_seccomp_allow_cache(current->seccomp.filter, __NR_reboot);
            spin_unlock_irq(&current->sighand->siglock);
        }
#else
        disable_seccomp();
#endif
        return 0;
    }

    susfs_set_current_proc_no_su();
    return 0;

do_susfs_work: {
    // Do not umount here as we are in init namespace now

    // Handle extra susfs work
    if (!work_pending(&susfs_extra_works))
        schedule_work(&susfs_extra_works);
}

    return 0;
}
#endif

int ksu_handle_setuid(uid_t new_uid, uid_t old_uid)
{
#ifdef CONFIG_KSU_SUSFS
    // Only susfs will process zygote_next
    // We don't care it in Tracepoint / Manual hook

    if (is_zygote_next(current_cred())) {
        handle_zygote_next_setresuid(new_uid);
    }
#endif
    // We are only interested in processes spawned by zygote.
    if (!is_zygote(current_cred())) {
        return 0;
    }

    if (old_uid != new_uid) {
        pr_info("handle_setresuid from %d to %d\n", old_uid, new_uid);
    }

    if (ksu_is_allow_uid_for_current(new_uid)) {
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 10, 0)
        if (current->seccomp.mode == SECCOMP_MODE_FILTER && current->seccomp.filter) {
            spin_lock_irq(&current->sighand->siglock);
            ksu_seccomp_allow_cache(current->seccomp.filter, __NR_reboot);
            spin_unlock_irq(&current->sighand->siglock);
        }
#else
        disable_seccomp();
#endif
        ksu_clear_current_proc_unprivillege();
        if (ksu_is_manager_uid(new_uid)) {
            pr_info("install fd for ksu manager(uid=%d)\n", new_uid);
            ksu_mark_manager(new_uid);
            ksu_set_ksud_status(new_uid);
            ksu_install_fd();
        }
        return 0;
    }

    ksu_set_current_proc_unprivillege();

    // Handle kernel umount
    ksu_handle_umount(old_uid, new_uid);

    return 0;
}

int ksu_handle_setresuid(uid_t ruid, uid_t euid, uid_t suid)
{
#ifdef CONFIG_KSU_MANUAL_HOOK_AUTO_SETUID_HOOK
    return 0; // dummy hook here
#else
    // we rely on the fact that zygote always call setresuid(3) with same uids
    return ksu_handle_setuid(ruid, ksu_get_uid_t(current_uid()));
#endif
}

void __init ksu_setuid_hook_init(void)
{
    ksu_kernel_umount_init();
}

void __exit ksu_setuid_hook_exit(void)
{
    pr_info("ksu_setuid_hook_exit\n");
    ksu_kernel_umount_exit();
}
