#ifndef __KSU_UAPI_SUPERCALL_H
#define __KSU_UAPI_SUPERCALL_H

#include <linux/ioctl.h>
#include <linux/types.h>

#include "uapi/app_profile.h"
#include "uapi/feature.h"
#include "uapi/selinux.h"

#define DEFINE_KSU_UAPI_CONST(type, name, val) \
    enum { name = (val) }; \
    static const type name##_RUST = (val);

#define KSU_FULL_VERSION_STRING 255

/* Magic numbers for reboot hook to install fd */
DEFINE_KSU_UAPI_CONST(__u32, KSU_INSTALL_MAGIC1, 0xDEADBEEF)
DEFINE_KSU_UAPI_CONST(__u32, KSU_INSTALL_MAGIC2, 0xCAFEBABE)

struct ksu_become_daemon_cmd {
    __u8 token[65]; /* Input: daemon token (null-terminated) */
};

DEFINE_KSU_UAPI_CONST(__u32, EVENT_POST_FS_DATA, 1)
DEFINE_KSU_UAPI_CONST(__u32, EVENT_BOOT_COMPLETED, 2)
DEFINE_KSU_UAPI_CONST(__u32, EVENT_MODULE_MOUNTED, 3)

DEFINE_KSU_UAPI_CONST(__u32, KSU_GET_INFO_FLAG_LKM, (1U << 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_GET_INFO_FLAG_MANAGER, (1U << 1))
DEFINE_KSU_UAPI_CONST(__u32, KSU_GET_INFO_FLAG_LATE_LOAD, (1U << 2))
DEFINE_KSU_UAPI_CONST(__u32, KSU_GET_INFO_FLAG_PR_BUILD, (1U << 3))

struct ksu_get_info_cmd {
    __u32 version; /* Output: KERNEL_SU_VERSION */
    __u32 flags; /* Output: KSU_GET_INFO_FLAG_* bits */
    __u32 features; /* Output: max feature ID supported */
};

struct ksu_report_event_cmd {
    __u32 event; /* Input: EVENT_POST_FS_DATA, EVENT_BOOT_COMPLETED, etc. */
};

struct ksu_set_sepolicy_cmd {
    __u64 data_len; /* Input: bytes of serialized command payload */
    __aligned_u64 data; /* Input: pointer to serialized payload */
};

struct ksu_sepolicy_cmd_hdr {
    __u32 cmd; /* Input: command type, CMD_* */
    __u32 subcmd; /* Input: command subtype */
};
/*
 * After each ksu_sepolicy_cmd_hdr, command arguments are encoded sequentially as:
 * [u32 len][len bytes][\0], where len excludes the trailing '\0'.
 * len == 0 represents ALL.
 * Argument count is derived from cmd:
 * KSU_SEPOLICY_CMD_NORMAL_PERM=4, KSU_SEPOLICY_CMD_XPERM=5,
 * KSU_SEPOLICY_CMD_TYPE_STATE=1, KSU_SEPOLICY_CMD_TYPE=2,
 * KSU_SEPOLICY_CMD_TYPE_ATTR=2, KSU_SEPOLICY_CMD_ATTR=1,
 * KSU_SEPOLICY_CMD_TYPE_TRANSITION=5, KSU_SEPOLICY_CMD_TYPE_CHANGE=4,
 * KSU_SEPOLICY_CMD_GENFSCON=3.
 */

struct ksu_check_safemode_cmd {
    __u8 in_safe_mode; /* Output: true if in safe mode, false otherwise */
};

/* deprecated */
struct ksu_get_allow_list_cmd {
    __u32 uids[128]; /* Output: array of allowed/denied UIDs */
    __u32 count; /* Output: number of UIDs in array */
    __u8 allow; /* Input: true for allow list, false for deny list */
};

struct ksu_new_get_allow_list_cmd {
    __u16 count; /* Input / Output: number of UIDs in array */
    __u16 total_count; /* Output: total number of UIDs in requested list */
    __u32 uids[0]; /* Output: array of allowed/denied UIDs */
};

struct ksu_uid_granted_root_cmd {
    __u32 uid; /* Input: target UID to check */
    __u8 granted; /* Output: true if granted, false otherwise */
};

struct ksu_uid_should_umount_cmd {
    __u32 uid; /* Input: target UID to check */
    __u8 should_umount; /* Output: true if should umount, false otherwise */
};

struct ksu_get_manager_appid_cmd {
    __u32 appid; /* Output: manager app id */
};

struct ksu_get_app_profile_cmd {
    struct app_profile profile; /* Input/Output: app profile structure */
};

struct ksu_set_app_profile_cmd {
    struct app_profile profile; /* Input: app profile structure */
};

struct ksu_get_feature_cmd {
    __u32 feature_id; /* Input: feature ID (enum ksu_feature_id) */
    __u64 value; /* Output: feature value/state */
    __u8 supported; /* Output: true if feature is supported, false otherwise */
};

struct ksu_set_feature_cmd {
    __u32 feature_id; /* Input: feature ID (enum ksu_feature_id) */
    __u64 value; /* Input: feature value/state to set */
};

struct ksu_get_wrapper_fd_cmd {
    __u32 fd; /* Input: userspace fd */
    __u32 flags; /* Input: flags of userspace fd */
};

struct ksu_manage_mark_cmd {
    __u32 operation; /* Input: KSU_MARK_* */
    __s32 pid; /* Input: target pid (0 for all processes) */
    __u32 result; /* Output: for get operation - mark status or reg_count */
};

DEFINE_KSU_UAPI_CONST(__u32, KSU_MARK_GET, 1)
DEFINE_KSU_UAPI_CONST(__u32, KSU_MARK_MARK, 2)
DEFINE_KSU_UAPI_CONST(__u32, KSU_MARK_UNMARK, 3)
DEFINE_KSU_UAPI_CONST(__u32, KSU_MARK_REFRESH, 4)

struct ksu_nuke_ext4_sysfs_cmd {
    __aligned_u64 arg; /* Input: mnt pointer */
};

struct ksu_get_sulog_fd_cmd {
    __u32 flags; /* Input: reserved for future use, must be 0 */
};

struct ksu_manage_try_umount_cmd {
    __aligned_u64 arg; /* char ptr, this is the mountpoint */
    __u32 flags; /* this is the flag we use for it */
    // downstream: 107,200 = getsize old/new; 108,201 = getlist old/new
    __u8 mode; /* denotes what to do with it 0:wipe_list 1:add_to_list 2:delete_entry */
};

DEFINE_KSU_UAPI_CONST(__u8, KSU_UMOUNT_WIPE, 0) /* ignore everything and wipe list */
DEFINE_KSU_UAPI_CONST(__u8, KSU_UMOUNT_ADD, 1) /* add entry (path + flags) */
DEFINE_KSU_UAPI_CONST(__u8, KSU_UMOUNT_DEL, 2) /* delete entry, strcmp */

// Downstream add mode
DEFINE_KSU_UAPI_CONST(__u8, KSU_UMOUNT_GETSIZE_LEGACY, 107) // get list size (legacy)
DEFINE_KSU_UAPI_CONST(__u8, KSU_UMOUNT_GETLIST_LEGACY, 108) // get list (legacy)
DEFINE_KSU_UAPI_CONST(__u8, KSU_UMOUNT_GETSIZE_NEW, 200) // get list size (new (with flags))
DEFINE_KSU_UAPI_CONST(__u8, KSU_UMOUNT_GETLIST_NEW, 201) // get list (new (with flags))

// Downstream supercall struct
struct ksu_get_full_version_cmd {
    char version_full[KSU_FULL_VERSION_STRING]; // Output: full version string
};

struct ksu_hook_type_cmd {
    char hook_type[32]; // Output: hook type string
};

struct ksu_enable_kpm_cmd {
    __u8 enabled; // Output: true if KPM is enabled
};

DEFINE_KSU_UAPI_CONST(__u8, DYNAMIC_MANAGER_OP_SET, 0)
DEFINE_KSU_UAPI_CONST(__u8, DYNAMIC_MANAGER_OP_GET, 1)
DEFINE_KSU_UAPI_CONST(__u8, DYNAMIC_MANAGER_OP_WIPE, 2)

struct ksu_dynamic_manager_cmd {
    __u8 operation;
    unsigned int size;
    __u8 hash[64];
};

struct ksu_manager_entry {
    __u32 uid;
    __u8 signature_index;
} __attribute__((packed));

struct ksu_get_managers_cmd {
    __u16 count; // Input / Output: number of managers in array
    __u16 total_count; // Output: total number of managers in requested list
    struct ksu_manager_entry managers[]; // Output: Array of active manager
} __attribute__((packed));

DEFINE_KSU_UAPI_CONST(__u8, KSU_KPM_LOAD, 1)
DEFINE_KSU_UAPI_CONST(__u8, KSU_KPM_UNLOAD, 2)
DEFINE_KSU_UAPI_CONST(__u8, KSU_KPM_NUM, 3)
DEFINE_KSU_UAPI_CONST(__u8, KSU_KPM_LIST, 4)
DEFINE_KSU_UAPI_CONST(__u8, KSU_KPM_INFO, 5)
DEFINE_KSU_UAPI_CONST(__u8, KSU_KPM_CONTROL, 6)
DEFINE_KSU_UAPI_CONST(__u8, KSU_KPM_VERSION, 7)

struct ksu_kpm_cmd {
    __u8 __user control_code;
    __aligned_u64 __user arg1;
    __aligned_u64 __user arg2;
    __aligned_u64 __user result_code;
} __attribute__((packed));

DEFINE_KSU_UAPI_CONST(__u8, KERNEL_PATCH_NOT_FOUND, 0)
DEFINE_KSU_UAPI_CONST(__u8, KERNEL_PATCH_ORIGINAL, 1)
DEFINE_KSU_UAPI_CONST(__u8, KERNEL_PATCH_KPN, 2)
DEFINE_KSU_UAPI_CONST(__u8, KERNEL_PATCH_SUKISU, 3)

struct ksu_get_kernel_patch_implement {
    __u8 type; // Output: Current Kernel Patch Implement
};

/* IOCTL command definitions */
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_GRANT_ROOT, _IOC(_IOC_NONE, 'K', 1, 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_GET_INFO, _IOC(_IOC_READ, 'K', 2, 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_REPORT_EVENT, _IOC(_IOC_WRITE, 'K', 3, 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_SET_SEPOLICY, _IOC(_IOC_READ | _IOC_WRITE, 'K', 4, 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_CHECK_SAFEMODE, _IOC(_IOC_READ, 'K', 5, 0))
// deprecated
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_GET_ALLOW_LIST, _IOC(_IOC_READ | _IOC_WRITE, 'K', 6, 0))
// deprecated
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_GET_DENY_LIST, _IOC(_IOC_READ | _IOC_WRITE, 'K', 7, 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_NEW_GET_ALLOW_LIST, _IOWR('K', 6, struct ksu_new_get_allow_list_cmd))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_NEW_GET_DENY_LIST, _IOWR('K', 7, struct ksu_new_get_allow_list_cmd))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_UID_GRANTED_ROOT, _IOC(_IOC_READ | _IOC_WRITE, 'K', 8, 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_UID_SHOULD_UMOUNT, _IOC(_IOC_READ | _IOC_WRITE, 'K', 9, 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_GET_MANAGER_APPID, _IOC(_IOC_READ, 'K', 10, 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_GET_APP_PROFILE, _IOC(_IOC_READ | _IOC_WRITE, 'K', 11, 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_SET_APP_PROFILE, _IOC(_IOC_WRITE, 'K', 12, 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_GET_FEATURE, _IOC(_IOC_READ | _IOC_WRITE, 'K', 13, 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_SET_FEATURE, _IOC(_IOC_WRITE, 'K', 14, 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_GET_WRAPPER_FD, _IOC(_IOC_WRITE, 'K', 15, 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_MANAGE_MARK, _IOC(_IOC_READ | _IOC_WRITE, 'K', 16, 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_NUKE_EXT4_SYSFS, _IOC(_IOC_WRITE, 'K', 17, 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_MANAGE_TRY_UMOUNT, _IOC(_IOC_WRITE, 'K', 18, 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_SET_INIT_PGRP, _IO('K', 19))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_GET_SULOG_FD, _IOW('K', 20, struct ksu_get_sulog_fd_cmd))

// Downstream add IOCTL command definitions
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_GET_FULL_VERSION, _IOC(_IOC_READ, 'K', 100, 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_HOOK_TYPE, _IOC(_IOC_READ, 'K', 101, 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_ENABLE_KPM, _IOC(_IOC_READ, 'K', 102, 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_DYNAMIC_MANAGER, _IOC(_IOC_READ | _IOC_WRITE, 'K', 103, 0))
// 104 = old get_managers, deprecated
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_GET_MANAGERS, _IOC(_IOC_READ | _IOC_WRITE, 'K', 105, 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_GET_KERNEL_PATCH_IMPLEMENT, _IOC(_IOC_READ, 'K', 106, 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_KPM, _IOC(_IOC_READ | _IOC_WRITE, 'K', 200, 0))

#undef DEFINE_KSU_UAPI_CONST
#endif
