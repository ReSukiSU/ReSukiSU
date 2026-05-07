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

#if LINUX_VERSION_CODE < KERNEL_VERSION(4, 2, 0)
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
#if LINUX_VERSION_CODE < KERNEL_VERSION(4, 2, 0)
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

#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 2, 0)
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

void __init ksu_lsm_hook_init(void)
{
    if (ARRAY_SIZE(ksu_hooks) == 0)
        return;

#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 11, 0)
    security_add_hooks(ksu_hooks, ARRAY_SIZE(ksu_hooks), "ksu");
#else
    // https://elixir.bootlin.com/linux/v4.10.17/source/include/linux/lsm_hooks.h#L1892
    security_add_hooks(ksu_hooks, ARRAY_SIZE(ksu_hooks));
#endif
}
#else // linux kernel >= 4.2
#include "avc_ss.h"

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

#undef STRIP_PARENS
#undef GENERATE_LSM_HOOK_DEFS

static inline bool verify_selinux_cred_free(void *fn_ptr)
{
    bool success = false;

    if (!fn_ptr)
        return false;

    // ref: https://elixir.bootlin.com/linux/v3.18.140/source/security/selinux/hooks.c#L3474
    void (*selinux_cred_free_fn)(struct cred *) = fn_ptr;

    struct cred dummy_cred;

    // explicitly set it to NULL
    // make sure this happens!
    // #1. it wont trigger BUG_ON
    // #2. this way it will kfree(NULL), which does nothing
    *(volatile void **)&dummy_cred.security = NULL;
    barrier();

    selinux_cred_free_fn(&dummy_cred);

    // check if selinux_cred_free is successful
    if ((unsigned long)*(volatile void **)&dummy_cred.security == 0x7UL)
        success = true;

    pr_info("selinux_cred_free: 0x%lx cred->security: 0x%lx success: %d\n", (unsigned long)fn_ptr,
            (unsigned long)dummy_cred.security, success);

    return success;
}

// we should see a lot of pointers that is inside stext && etext
// basically we check for "pointer density"
static inline bool is_selinux_ops_valid(uintptr_t addr)
{
    extern char _stext[], _etext[];
    size_t total_slots = sizeof(struct security_operations) / sizeof(void *);
    size_t valid_ptr = 0;
    size_t i = 0;

    uintptr_t member_ptr = 0;
    uintptr_t current_slot_addr;

    // we will be off by one or off by two due to sizeof("selinux")
    // thats 8 bytes, on 32 bit, this is two pointers worth, not a big deal

density_verify_start:
    current_slot_addr = addr + (i * sizeof(void *));

    member_ptr = 0;
    if (copy_from_kernel_nofault(&member_ptr, (void *)current_slot_addr, sizeof(uintptr_t)))
        goto next_iter; // if it fails, just try next slot

    // give up early
    if (!valid_ptr && i >= 20)
        return false;

    // pr_info("%s: member_ptr: 0x%lx \n", __func__, (long)member_ptr);
    if (member_ptr >= (uintptr_t)_stext && member_ptr <= (uintptr_t)_etext)
        valid_ptr++;

next_iter:
    i++;
    if (i < total_slots)
        goto density_verify_start;

    pr_info("%s: density: valid: %zu slots: %zu \n", __func__, valid_ptr, total_slots);

    // maybe increase to 75% or something?
    return (valid_ptr > (total_slots / 2));
}

static noinline bool check_candidate(uintptr_t addr)
{
    struct security_operations *candidate = (struct security_operations *)addr;

    char char_buf[sizeof("selinux")] = { 0 };

    if (copy_from_kernel_nofault(char_buf, (void *)addr, sizeof("selinux")))
        return false;

    if (memcmp(char_buf, "selinux", sizeof("selinux")))
        return false;

    // candidate found!
    pr_info("%s: candidate selinux_ops at 0x%lx\n", __func__, (long)addr);

    // check ptr density
    if (!is_selinux_ops_valid(addr))
        return false;

    if (!candidate->cred_free)
        return false;

#ifdef CONFIG_KALLSYMS // not always available, can also fail, but it wont hurt to try.
    uintptr_t ksym_ptr = (uintptr_t)kallsyms_lookup_name("selinux_cred_free");
    if (unlikely(ksym_ptr != (uintptr_t)candidate->cred_free))
        goto test_fn;

    pr_info("%s: selinux_cred_free found via ksym_lookup: 0x%lx probe_result: 0x%lx \n", __func__, (long)ksym_ptr,
            (long)candidate->cred_free);
    return true;

test_fn:
#endif

    pr_info("%s: candidate selinux_cred_free at 0x%lx\n", __func__, (long)candidate->cred_free);
    return verify_selinux_cred_free((void *)candidate->cred_free);
}

/** 
 * we do this in blocks of sequential 10k pointers.
 * 10k pointers up, 10k pointers down
 * this is predictable, more cache friendly, no trashing.
 *
 * one up, one down oscillating scan isn't as friendly to teh cahce.
 * once ptrdiff of up vs down is larger than L1, it will be trashy.
 *
 */
static noinline void *hunt_for_selinux_ops(void *heuristic_ptr)
{
    uintptr_t anchor = (uintptr_t)heuristic_ptr;
    uintptr_t curr;
    unsigned long iter_count = 0;
    unsigned long max_index = 10000; // max number of pointers to test, one way
    unsigned long i = 0;

    uintptr_t start = anchor - max_index * sizeof(void *);
    uintptr_t end = anchor + max_index * sizeof(void *);
    pr_info("%s: scan range: 0x%lx - 0x%lx anchor: 0x%lx\n", __func__, (long)start, (long)end, (long)anchor);

scan_up:
    if (i >= max_index) {
        i = 1;
        goto scan_down;
    }

    curr = anchor + (i * sizeof(void *));
    i++;
    iter_count++;

    if (check_candidate(curr))
        goto found;

    goto scan_up;

scan_down:
    if (i >= max_index)
        goto not_found;

    curr = anchor - (i * sizeof(void *));
    i++;
    iter_count++;

    if (check_candidate(curr))
        goto found;

    goto scan_down;

found:
    pr_info("%s: found selinux_ops at 0x%lx iter_count: %lu \n", __func__, curr, iter_count);
    return (void *)curr;

not_found:
    pr_info("%s: selinux_ops not found in range! iter_count: %lu \n", __func__, iter_count);
    return NULL;
}

static uintptr_t selinux_ops_addr = 0;

static inline void set_selinux_ops()
{
    extern int selinux_enabled;
    extern struct security_class_mapping secclass_map[];
    extern struct list_head crypto_alg_list;
    extern unsigned int avc_cache_threshold;

    struct security_operations *ops = NULL;

// if user exports selinux_ops, we just go for it!
#ifdef KSU_HAS_EXPORTED_SELINUX_OPS
    extern struct security_operations selinux_ops;
    if (!ops)
        ops = (struct security_operations *)&selinux_ops;
#endif

// not always available, can also fail, but it wont hurt to try.
#ifdef CONFIG_KALLSYMS
    if (!ops)
        ops = (struct security_operations *)kallsyms_lookup_name("selinux_ops");
#endif

#ifdef CONFIG_KEYS
    extern struct key_user root_key_user;
    if (!ops)
        ops = (struct security_operations *)hunt_for_selinux_ops((void *)&root_key_user);
#endif

    if (!ops)
        ops = (struct security_operations *)hunt_for_selinux_ops((void *)&avc_cache_threshold);

    if (!ops)
        ops = (struct security_operations *)hunt_for_selinux_ops((void *)&crypto_alg_list);

    if (!ops)
        ops = (struct security_operations *)hunt_for_selinux_ops((void *)&selinux_enabled);

    if (!ops)
        ops = (struct security_operations *)hunt_for_selinux_ops((void *)&secclass_map);

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

void __init ksu_lsm_hook_init(void)
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
