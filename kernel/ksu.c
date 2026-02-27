#include <linux/export.h>
#include <linux/fs.h>
#include <linux/kobject.h>
#include <linux/module.h>
#include <generated/utsrelease.h>
#include <generated/compile.h>
#include <linux/version.h> /* LINUX_VERSION_CODE, KERNEL_VERSION macros */

#ifdef CONFIG_KSU_SUSFS
#include <linux/susfs.h>
#endif
#include <linux/sched.h>

#include "allowlist.h"
#include "ksu.h"
#include "app_profile.h"
#include "feature.h"
#include "klog.h" // IWYU pragma: keep
#include "manager.h"
#include "throne_tracker.h"
#include "ksud.h"
#include "supercalls.h"
#include "ksu.h"
#include "file_wrapper.h"
#include "selinux/selinux.h"

// workaround for A12-5.10 kernel
// Some third-party kernel (e.g. LineageOS) uses wrong toolchain, which supports
// CC_HAVE_STACKPROTECTOR_SYSREG while gki's toolchain doesn't.
// Therefore, ksu lkm, which uses gki toolchain, requires this __stack_chk_guard,
// while those third-party kernel can't provide.
// Thus, we manually provide it instead of using kernel's
#if defined(CONFIG_STACKPROTECTOR) &&                                          \
    (defined(CONFIG_ARM64) && defined(MODULE) &&                               \
     !defined(CONFIG_STACKPROTECTOR_PER_TASK))
#include <linux/stackprotector.h>
#include <linux/random.h>
unsigned long __stack_chk_guard __ro_after_init
    __attribute__((visibility("hidden")));

__attribute__((no_stack_protector)) void ksu_setup_stack_chk_guard()
{
    unsigned long canary;

    /* Try to get a semi random initial value. */
    get_random_bytes(&canary, sizeof(canary));
    canary ^= LINUX_VERSION_CODE;
    canary &= CANARY_MASK;
    __stack_chk_guard = canary;
}

__attribute__((naked)) int __init kernelsu_init_early(void)
{
    asm("mov x19, x30;\n"
        "bl ksu_setup_stack_chk_guard;\n"
        "mov x30, x19;\n"
        "b kernelsu_init;\n");
}
#define NEED_OWN_STACKPROTECTOR 1
#else
#define NEED_OWN_STACKPROTECTOR 0
#endif

struct cred *ksu_cred;
bool ksu_late_loaded;

#include "sulog.h"
#include "dynamic_manager.h"
#include "sucompat.h"
#include "setuid_hook.h"

// dispatcher of ksu hooks
#if defined(KSU_TP_HOOK)
#include "syscall_hook_manager.h"
#define ksu_hook_init() ksu_syscall_hook_manager_init()
#define ksu_hook_exit() ksu_syscall_hook_manager_exit()
#elif defined(CONFIG_KSU_MANUAL_HOOK)
// only lsm hook need call init
#if LINUX_VERSION_CODE < KERNEL_VERSION(6, 8, 0) &&                            \
    LINUX_VERSION_CODE >= KERNEL_VERSION(4, 2, 0)
#define ksu_hook_init() ksu_lsm_hook_init()
#else
#define ksu_hook_init()                                                        \
    do {                                                                       \
    } while (0)
#endif
#define ksu_hook_exit()                                                        \
    do {                                                                       \
    } while (0)
#elif defined(CONFIG_KSU_SUSFS)
// susfs doesn't have exit
#define ksu_hook_init() susfs_init()
#define ksu_hook_exit()                                                        \
    do {                                                                       \
    } while (0)
#else
#error Unsupport hook type
#endif

int __init kernelsu_init(void)
{
    pr_info("Initialized on: %s (%s) with driver version: %u\n", UTS_RELEASE,
            UTS_MACHINE, KSU_VERSION);
#ifdef MODULE
    ksu_late_loaded = (current->pid != 1);
#else
    ksu_late_loaded = false;
#endif

#ifdef CONFIG_KSU_DEBUG
    pr_alert("*************************************************************");
    pr_alert("**	 NOTICE NOTICE NOTICE NOTICE NOTICE NOTICE NOTICE	**");
    pr_alert("**														 **");
    pr_alert("**		 You are running KernelSU in DEBUG mode		  **");
    pr_alert("**														 **");
    pr_alert("**	 NOTICE NOTICE NOTICE NOTICE NOTICE NOTICE NOTICE	**");
    pr_alert("*************************************************************");
#endif

    ksu_cred = prepare_creds();
    if (!ksu_cred) {
        pr_err("prepare cred failed!\n");
    }

    ksu_feature_init();

    ksu_supercalls_init();

    ksu_setuid_hook_init();
    ksu_sucompat_init();
    if (ksu_late_loaded) {
        pr_info("late load mode, skipping kprobe hooks\n");

        apply_kernelsu_rules();
        cache_sid();
        setup_ksu_cred();

        // Grant current process (ksud late-load) root
        // with KSU SELinux domain before enforcing SELinux, so it
        // can continue to access /data/app etc. after enforcement.
        escape_to_root_for_init();

        ksu_allowlist_init();
        ksu_load_allow_list();

        ksu_hook_init();

        ksu_throne_tracker_init();
        ksu_observer_init();
        ksu_file_wrapper_init();

        ksu_sulog_init();
        ksu_dynamic_manager_init();

        ksu_boot_completed = true;
        track_throne(false, true);

        if (!getenforce()) {
            pr_info("Permissive SELinux, enforcing\n");
            setenforce(true);
        }
    } else {
        ksu_hook_init();

        ksu_allowlist_init();

        ksu_throne_tracker_init();

        ksu_ksud_init();

        ksu_file_wrapper_init();
    }

#ifdef MODULE
#ifndef CONFIG_KSU_DEBUG
    kobject_del(&THIS_MODULE->mkobj.kobj);
#endif
#endif
    return 0;
}

extern void ksu_observer_exit(void);
void kernelsu_exit(void)
{
    ksu_allowlist_exit();

    ksu_observer_exit();

    ksu_throne_tracker_exit();
    if (!ksu_late_loaded)
        ksu_ksud_exit();

    ksu_hook_exit();
    ksu_sucompat_exit();
    ksu_setuid_hook_exit();

    ksu_dynamic_manager_exit();
    ksu_sulog_exit();

    ksu_supercalls_exit();

    ksu_feature_exit();

    if (ksu_cred) {
        put_cred(ksu_cred);
    }
}

#if NEED_OWN_STACKPROTECTOR
module_init(kernelsu_init_early);
#else
module_init(kernelsu_init);
#endif
module_exit(kernelsu_exit);

MODULE_LICENSE("GPL");
MODULE_AUTHOR("weishu");
MODULE_DESCRIPTION("Android KernelSU");

#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 0, 0)
#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 13, 0)
MODULE_IMPORT_NS("VFS_internal_I_am_really_a_filesystem_and_am_NOT_a_driver");
#else
MODULE_IMPORT_NS(VFS_internal_I_am_really_a_filesystem_and_am_NOT_a_driver);
#endif
#endif
