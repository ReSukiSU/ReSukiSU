#include <linux/version.h>
#include <linux/security.h>
#include <linux/init.h>
#include <linux/sched.h>
#include <linux/cred.h>
#include <linux/key.h>
#include <linux/string.h>
#include <linux/kernel.h>
#include <linux/uidgid.h>

#include "manager/throne_tracker.h"
#include "compat/kernel_compat.h"
#include "ksu.h"
#include "klog.h"

#ifdef CONFIG_KSU_MANUAL_HOOK_AUTO_SETUID_HOOK
#include "setuid_hook.h"

static int ksu_task_fix_setuid(struct cred *new, const struct cred *old, int flags)
{
    uid_t new_uid = ksu_get_uid_t(new->uid);
    uid_t old_uid = ksu_get_uid_t(old->uid);

    return ksu_handle_setuid(new_uid, old_uid);
}
#endif

#ifdef CONFIG_KSU_MANUAL_HOOK_AUTO_INITRC_HOOK
#ifdef KSU_COMPAT_USE_STATIC_KEY
extern struct static_key_true ksu_init_rc_hook;
#else
extern bool ksu_init_rc_hook __read_mostly;
#endif

#if LINUX_VERSION_CODE < KERNEL_VERSION(4, 2, 0) && !defined(KSU_COMPAT_HAS_LIST_OF_LSM_HOOKS)
#include <linux/stop_machine.h>

static int ksu_unregister_file_permission(void *data);
#endif

static int ksu_file_permission(struct file *file, int mask)
{
#ifdef KSU_COMPAT_USE_STATIC_KEY
    if (static_branch_unlikely(&ksu_init_rc_hook))
        ksu_handle_initrc(file);
#else
    if (unlikely(ksu_init_rc_hook))
        ksu_handle_initrc(file);
    else {
#if LINUX_VERSION_CODE < KERNEL_VERSION(4, 2, 0) && !defined(KSU_COMPAT_HAS_LIST_OF_LSM_HOOKS)
        // 4.2- always don't have static key
        // static key since 4.3
        // there really unregister file_permission
        stop_machine(ksu_unregister_file_permission, NULL, NULL);
#endif
    }
#endif

    return 0;
}
#endif

static int ksu_inode_rename(struct inode *old_inode, struct dentry *old_dentry, struct inode *new_inode,
                            struct dentry *new_dentry)
{
    ksu_handle_rename(old_dentry, new_dentry);

    return 0;
}

#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 2, 0) || defined(KSU_COMPAT_HAS_LIST_OF_LSM_HOOKS)
#include <linux/lsm_hooks.h>

static struct security_hook_list ksu_hooks[] = {
    LSM_HOOK_INIT(inode_rename, ksu_inode_rename),
#ifdef CONFIG_KSU_MANUAL_HOOK_AUTO_SETUID_HOOK
    LSM_HOOK_INIT(task_fix_setuid, ksu_task_fix_setuid),
#endif

#ifdef CONFIG_KSU_MANUAL_HOOK_AUTO_INITRC_HOOK
    LSM_HOOK_INIT(file_permission, ksu_file_permission),
#endif
};

void __init ksu_lsm_hook_built_in_init(void)
{
    if (ARRAY_SIZE(ksu_hooks) == 0)
        return;

        // https://github.com/torvalds/linux/commit/d69dece5f5b6bc7a5e39d2b6136ddc69469331fe
#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 11, 0) || defined(KSU_COMPAT_REQUIRE_PROVIDE_LSM_NAME)
    security_add_hooks(ksu_hooks, ARRAY_SIZE(ksu_hooks), "ksu");
#else
    // https://elixir.bootlin.com/linux/v4.10.17/source/include/linux/lsm_hooks.h#L1892
    security_add_hooks(ksu_hooks, ARRAY_SIZE(ksu_hooks));
#endif
}
#else // linux kernel >= 4.2 || KSU_COMPAT_HAS_LIST_OF_LSM_HOOKS
#include "avc_ss.h"
#include "feature/selinux_hide.h"
#include "infra/symbol_resolver.h"

#ifdef CONFIG_KSU_MANUAL_HOOK_AUTO_SETUID_HOOK
#define IF_CONFIG_KSU_MANUAL_HOOK_AUTO_SETUID_HOOK(x) x
#else
#define IF_CONFIG_KSU_MANUAL_HOOK_AUTO_SETUID_HOOK(x)
#endif

#ifdef CONFIG_KSU_MANUAL_HOOK_AUTO_INITRC_HOOK
#define IF_CONFIG_KSU_MANUAL_HOOK_AUTO_INITRC_HOOK(x) x
#else
#define IF_CONFIG_KSU_MANUAL_HOOK_AUTO_INITRC_HOOK(x)
#endif

#define LSM_HOOK_LIST(HOOK_ITEM)                                                                                       \
    HOOK_ITEM(inode_rename, ksu_inode_rename,                                                                          \
              (struct inode * old_inode, struct dentry * old_dentry, struct inode * new_inode,                         \
               struct dentry * new_dentry),                                                                            \
              (old_inode, old_dentry, new_inode, new_dentry))                                                          \
    IF_CONFIG_KSU_MANUAL_HOOK_AUTO_SETUID_HOOK(HOOK_ITEM(task_fix_setuid, ksu_task_fix_setuid,                         \
                                                         (struct cred * new, const struct cred *old, int flags),       \
                                                         (new, old, flags)))                                           \
    IF_CONFIG_KSU_MANUAL_HOOK_AUTO_INITRC_HOOK(                                                                        \
        HOOK_ITEM(file_permission, ksu_file_permission, (struct file * file, int mask), (file, mask)))

#define STRIP_PARENS(...) __VA_ARGS__

#define GENERATE_LSM_HOOK_DEFS(TARGET, HANDLER, ARGS_DECL, ARGS_CALL)                                                  \
    static int(*orig_##TARGET) STRIP_PARENS(ARGS_DECL) = NULL;                                                         \
                                                                                                                       \
    static int hook_##TARGET STRIP_PARENS(ARGS_DECL)                                                                   \
    {                                                                                                                  \
        HANDLER STRIP_PARENS(ARGS_CALL);                                                                               \
        if (orig_##TARGET)                                                                                             \
            return orig_##TARGET STRIP_PARENS(ARGS_CALL);                                                              \
        return 0;                                                                                                      \
    }

LSM_HOOK_LIST(GENERATE_LSM_HOOK_DEFS)

setprocattr_fn ksu_orig_setprocattr;

#undef STRIP_PARENS
#undef GENERATE_LSM_HOOK_DEFS

static uintptr_t selinux_ops_addr = 0;

static inline void set_selinux_ops()
{
    struct security_operations *ops = NULL;

#ifdef CONFIG_KALLSYMS_ALL
    ops = (struct security_operations *)find_kernel_symbol_exact("selinux_ops");
#else
    extern struct security_operations selinux_ops;

    ops = (struct security_operations *)&selinux_ops;
#endif

    if (!ops)
        return;

    selinux_ops_addr = (uintptr_t)ops;
}

#define ASSIGN_ORIG_AND_HOOK(TARGET, HANDLER, ARGS_DECL, ARGS_CALL)                                                    \
    orig_##TARGET = ops->TARGET;                                                                                       \
    ops->TARGET = hook_##TARGET;

static int ksu_register_lsm_hook(void *data)
{
    struct security_operations *ops = (struct security_operations *)selinux_ops_addr;

    LSM_HOOK_LIST(ASSIGN_ORIG_AND_HOOK)
    return 0;
}
#undef ASSIGN_ORIG_AND_HOOK

void ksu_unregister_setprocattr_lsm_hook()
{
    struct security_operations *ops = (struct security_operations *)selinux_ops_addr;

    if (ksu_orig_setprocattr) {
        ops->setprocattr = ksu_orig_setprocattr;
    }
}

void ksu_register_setprocattr_lsm_hook()
{
    struct security_operations *ops = (struct security_operations *)selinux_ops_addr;

    ksu_orig_setprocattr = ops->setprocattr;
    ops->setprocattr = ksu_handle_selinux_setprocattr;
}

#ifdef CONFIG_KSU_MANUAL_HOOK_AUTO_INITRC_HOOK
static int ksu_unregister_file_permission(void *data)
{
    struct security_operations *ops = (struct security_operations *)selinux_ops_addr;

    if (orig_file_permission) {
        pr_info("%s: restoring file_permission 0x%lx -> 0x%lx\n", __func__, (long)ops->file_permission,
                (long)orig_file_permission);
        ops->file_permission = orig_file_permission;
    }

    return 0;
}
#endif

void __init ksu_lsm_hook_built_in_init(void)
{
    set_selinux_ops();

    struct security_operations *ops = (struct security_operations *)selinux_ops_addr;
    if (!ops)
        goto show_not_found_warning;

    if (strcmp((char *)ops, "selinux"))
        goto show_not_found_warning;

    pr_info("%s: selinux_ops: 0x%lx .name = %s\n", __func__, (long)ops, (const char *)ops);

    stop_machine(ksu_register_lsm_hook, NULL, NULL);
    return;
show_not_found_warning:
    pr_alert("*************************************************************");
    pr_alert("**     NOTICE NOTICE NOTICE NOTICE NOTICE NOTICE NOTICE    **");
    pr_alert("**                                                         **");
    pr_alert("**                 selinux_ops NOT FOUND                   **");
    pr_alert("**     ReSukiSU won't working due lost necessary hooks     **");
    pr_alert("**                                                         **");
    pr_alert("**     NOTICE NOTICE NOTICE NOTICE NOTICE NOTICE NOTICE    **");
    pr_alert("*************************************************************");
}
#endif // linux kernel < 4.2
