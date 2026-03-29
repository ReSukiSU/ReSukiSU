#![allow(clippy::unreadable_literal)]
use crate::defs::MountInfo;
use crate::ksu_uapi;
use std::fs;
use std::os::fd::RawFd;
use std::sync::OnceLock;

// Global driver fd cache
static DRIVER_FD: OnceLock<RawFd> = OnceLock::new();
static INFO_CACHE: OnceLock<ksu_uapi::ksu_get_info_cmd> = OnceLock::new();

fn scan_driver_fd() -> Option<RawFd> {
    let fd_dir = fs::read_dir("/proc/self/fd").ok()?;

    for entry in fd_dir.flatten() {
        if let Ok(fd_num) = entry.file_name().to_string_lossy().parse::<i32>() {
            let link_path = format!("/proc/self/fd/{fd_num}");
            if let Ok(target) = fs::read_link(&link_path) {
                let target_str = target.to_string_lossy();
                if target_str.contains("[ksu_driver]") {
                    return Some(fd_num);
                }
            }
        }
    }

    None
}

// Get cached driver fd
fn init_driver_fd() -> Option<RawFd> {
    let fd = scan_driver_fd();
    if fd.is_none() {
        let mut fd = -1;
        unsafe {
            libc::syscall(
                libc::SYS_reboot,
                ksu_uapi::KSU_INSTALL_MAGIC1_RUST,
                ksu_uapi::KSU_INSTALL_MAGIC2_RUST,
                0,
                &mut fd,
            );
        };
        if fd >= 0 { Some(fd) } else { None }
    } else {
        fd
    }
}

// ioctl wrapper using libc
pub fn ksuctl<T>(request: u32, arg: *mut T) -> std::io::Result<i32> {
    use std::io;

    let fd = *DRIVER_FD.get_or_init(|| init_driver_fd().unwrap_or(-1));
    unsafe {
        let ret = libc::ioctl(fd as libc::c_int, request as i32, arg);
        if ret < 0 {
            Err(io::Error::last_os_error())
        } else {
            Ok(ret)
        }
    }
}

// API implementations
fn get_info() -> ksu_uapi::ksu_get_info_cmd {
    *INFO_CACHE.get_or_init(|| {
        let mut cmd = ksu_uapi::ksu_get_info_cmd {
            version: 0,
            flags: 0,
            features: 0,
        };
        let _ = ksuctl(ksu_uapi::KSU_IOCTL_GET_INFO_RUST, &raw mut cmd);
        cmd
    })
}

pub fn get_version() -> i32 {
    get_info().version as i32
}

pub fn is_late_load() -> bool {
    get_info().flags & ksu_uapi::KSU_GET_INFO_FLAG_LATE_LOAD_RUST != 0
}

pub fn grant_root() -> std::io::Result<()> {
    ksuctl(
        ksu_uapi::KSU_IOCTL_GRANT_ROOT_RUST,
        std::ptr::null_mut::<u8>(),
    )?;
    Ok(())
}

fn report_event(event: u32) {
    let mut cmd = ksu_uapi::ksu_report_event_cmd { event };
    let _ = ksuctl(ksu_uapi::KSU_IOCTL_REPORT_EVENT_RUST, &raw mut cmd);
}

pub fn report_post_fs_data() {
    report_event(ksu_uapi::EVENT_POST_FS_DATA_RUST);
}

pub fn report_boot_complete() {
    report_event(ksu_uapi::EVENT_BOOT_COMPLETED_RUST);
}

pub fn report_module_mounted() {
    report_event(ksu_uapi::EVENT_MODULE_MOUNTED_RUST);
}

pub fn check_kernel_safemode() -> bool {
    let mut cmd = ksu_uapi::ksu_check_safemode_cmd { in_safe_mode: 0 };
    let _ = ksuctl(ksu_uapi::KSU_IOCTL_CHECK_SAFEMODE_RUST, &raw mut cmd);
    cmd.in_safe_mode != 0
}

pub fn set_sepolicy(payload: *const u8, payload_len: u64) -> std::io::Result<i32> {
    let mut ioctl_cmd = crate::ksu_uapi::ksu_set_sepolicy_cmd {
        data_len: payload_len,
        data: payload as u64,
    };

    ksuctl(ksu_uapi::KSU_IOCTL_SET_SEPOLICY_RUST, &raw mut ioctl_cmd)
}

/// Get feature value and support status from kernel
/// Returns (value, supported)
pub fn get_feature(feature_id: u32) -> std::io::Result<(u64, bool)> {
    let mut cmd = ksu_uapi::ksu_get_feature_cmd {
        feature_id,
        value: 0,
        supported: 0,
    };
    ksuctl(ksu_uapi::KSU_IOCTL_GET_FEATURE_RUST, &raw mut cmd)?;
    Ok((cmd.value, cmd.supported != 0))
}

/// Set feature value in kernel
pub fn set_feature(feature_id: u32, value: u64) -> std::io::Result<()> {
    let mut cmd = ksu_uapi::ksu_set_feature_cmd { feature_id, value };
    ksuctl(ksu_uapi::KSU_IOCTL_SET_FEATURE_RUST, &raw mut cmd)?;
    Ok(())
}

pub fn get_wrapped_fd(fd: RawFd) -> std::io::Result<RawFd> {
    let mut cmd = ksu_uapi::ksu_get_wrapper_fd_cmd {
        fd: fd as u32,
        flags: 0,
    };
    let result = ksuctl(ksu_uapi::KSU_IOCTL_GET_WRAPPER_FD_RUST, &raw mut cmd)?;
    Ok(result)
}

pub fn get_sulog_fd() -> std::io::Result<RawFd> {
    let mut cmd = ksu_uapi::ksu_get_sulog_fd_cmd { flags: 0 };
    let result = ksuctl(ksu_uapi::KSU_IOCTL_GET_SULOG_FD, &raw mut cmd)?;
    Ok(result)
}

/// Get mark status for a process (pid=0 returns total marked count)
pub fn mark_get(pid: i32) -> std::io::Result<u32> {
    let mut cmd = ksu_uapi::ksu_manage_mark_cmd {
        operation: ksu_uapi::KSU_MARK_GET_RUST,
        pid,
        result: 0,
    };
    ksuctl(ksu_uapi::KSU_IOCTL_MANAGE_MARK_RUST, &raw mut cmd)?;
    Ok(cmd.result)
}

/// Mark a process (pid=0 marks all processes)
pub fn mark_set(pid: i32) -> std::io::Result<()> {
    let mut cmd = ksu_uapi::ksu_manage_mark_cmd {
        operation: ksu_uapi::KSU_MARK_MARK_RUST,
        pid,
        result: 0,
    };
    ksuctl(ksu_uapi::KSU_IOCTL_MANAGE_MARK_RUST, &raw mut cmd)?;
    Ok(())
}

/// Unmark a process (pid=0 unmarks all processes)
pub fn mark_unset(pid: i32) -> std::io::Result<()> {
    let mut cmd = ksu_uapi::ksu_manage_mark_cmd {
        operation: ksu_uapi::KSU_MARK_UNMARK_RUST,
        pid,
        result: 0,
    };
    ksuctl(ksu_uapi::KSU_IOCTL_MANAGE_MARK_RUST, &raw mut cmd)?;
    Ok(())
}

/// Refresh mark for all running processes
pub fn mark_refresh() -> std::io::Result<()> {
    let mut cmd = ksu_uapi::ksu_manage_mark_cmd {
        operation: ksu_uapi::KSU_MARK_REFRESH_RUST,
        pid: 0,
        result: 0,
    };
    ksuctl(ksu_uapi::KSU_IOCTL_MANAGE_MARK_RUST, &raw mut cmd)?;
    Ok(())
}

pub fn nuke_ext4_sysfs(mnt: &str) -> anyhow::Result<()> {
    let c_mnt = std::ffi::CString::new(mnt)?;
    let mut ioctl_cmd = ksu_uapi::ksu_nuke_ext4_sysfs_cmd {
        arg: c_mnt.as_ptr() as u64,
    };
    ksuctl(ksu_uapi::KSU_IOCTL_NUKE_EXT4_SYSFS_RUST, &raw mut ioctl_cmd)?;
    Ok(())
}

/// Wipe all entries from umount list
pub fn umount_list_wipe() -> std::io::Result<()> {
    let mut cmd = ksu_uapi::ksu_manage_try_umount_cmd {
        arg: 0,
        flags: 0,
        mode: ksu_uapi::KSU_UMOUNT_WIPE_RUST,
    };
    ksuctl(ksu_uapi::KSU_IOCTL_MANAGE_TRY_UMOUNT_RUST, &raw mut cmd)?;
    Ok(())
}

/// Add mount point to umount list
pub fn umount_list_add(path: &str, flags: u32) -> anyhow::Result<()> {
    let c_path = std::ffi::CString::new(path)?;
    let mut cmd = ksu_uapi::ksu_manage_try_umount_cmd {
        arg: c_path.as_ptr() as u64,
        flags,
        mode: ksu_uapi::KSU_UMOUNT_ADD_RUST,
    };
    ksuctl(ksu_uapi::KSU_IOCTL_MANAGE_TRY_UMOUNT_RUST, &raw mut cmd)?;
    Ok(())
}

/// Delete mount point from umount list
pub fn umount_list_del(path: &str) -> anyhow::Result<()> {
    let c_path = std::ffi::CString::new(path)?;
    let mut cmd = ksu_uapi::ksu_manage_try_umount_cmd {
        arg: c_path.as_ptr() as u64,
        flags: 0,
        mode: ksu_uapi::KSU_UMOUNT_DEL_RUST,
    };
    ksuctl(ksu_uapi::KSU_IOCTL_MANAGE_TRY_UMOUNT_RUST, &raw mut cmd)?;
    Ok(())
}

/// Set current process's process group to init_group (pgid = 0)
pub fn set_init_pgrp() -> std::io::Result<()> {
    ksuctl(
        ksu_uapi::KSU_IOCTL_SET_INIT_PGRP_RUST,
        std::ptr::null_mut::<u8>(),
    )?;
    Ok(())
}

// downstream begin

pub fn dynamic_manager_set(size: u32, hash: [u8; 64]) -> anyhow::Result<()> {
    let mut cmd = ksu_uapi::ksu_dynamic_manager_cmd {
        operation: ksu_uapi::DYNAMIC_MANAGER_OP_SET_RUST,
        size,
        hash,
    };
    ksuctl(ksu_uapi::KSU_IOCTL_DYNAMIC_MANAGER_RUST, &raw mut cmd)?;
    Ok(())
}

pub fn dynamic_manager_get() -> anyhow::Result<(u32, [u8; 64])> {
    let mut cmd = ksu_uapi::ksu_dynamic_manager_cmd {
        operation: ksu_uapi::DYNAMIC_MANAGER_OP_GET_RUST,
        size: 0,
        hash: [0u8; 64],
    };
    ksuctl(ksu_uapi::KSU_IOCTL_DYNAMIC_MANAGER_RUST, &raw mut cmd)?;
    Ok((cmd.size, cmd.hash))
}

pub fn dynamic_manager_clear() -> anyhow::Result<()> {
    let mut cmd = ksu_uapi::ksu_dynamic_manager_cmd {
        operation: ksu_uapi::DYNAMIC_MANAGER_OP_WIPE_RUST,
        size: 0,
        hash: [0u8; 64],
    };
    ksuctl(ksu_uapi::KSU_IOCTL_DYNAMIC_MANAGER_RUST, &raw mut cmd)?;
    Ok(())
}

/// List all mount points in umount list
pub fn umount_list_list() -> anyhow::Result<Vec<MountInfo>> {
    const FLAGS_SIZE: usize = std::mem::size_of::<u32>();
    let mut total_size: usize = 0;
    let mut size_cmd = ksu_uapi::ksu_manage_try_umount_cmd {
        arg: &raw mut total_size as u64,
        flags: 0,
        mode: ksu_uapi::KSU_UMOUNT_GETSIZE_NEW_RUST,
    };
    ksuctl(
        ksu_uapi::KSU_IOCTL_MANAGE_TRY_UMOUNT_RUST,
        &raw mut size_cmd,
    )?;

    if total_size == 0 {
        return Ok(vec![]);
    }

    let mut buffer = vec![0u8; total_size];
    let mut list_cmd = ksu_uapi::ksu_manage_try_umount_cmd {
        arg: buffer.as_mut_ptr() as u64,
        flags: 0,
        mode: ksu_uapi::KSU_UMOUNT_GETLIST_NEW_RUST,
    };
    ksuctl(
        ksu_uapi::KSU_IOCTL_MANAGE_TRY_UMOUNT_RUST,
        &raw mut list_cmd,
    )?;

    let mut list = Vec::new();
    let mut cursor = 0;
    let len = buffer.len();

    while cursor < len {
        use anyhow::Context;
        let null_pos = buffer[cursor..]
            .iter()
            .position(|&b| b == 0)
            .context("Malformed buffer: missing null terminator")?;

        let end_str = cursor + null_pos;

        let path = String::from_utf8_lossy(&buffer[cursor..end_str]).into_owned();

        cursor = end_str + 1;

        if cursor + 4 > len {
            break;
        }

        let flags_bytes: [u8; FLAGS_SIZE] = buffer[cursor..cursor + FLAGS_SIZE]
            .try_into()
            .expect("Slice length matches");

        let flags = u32::from_ne_bytes(flags_bytes);

        cursor += FLAGS_SIZE;

        list.push(MountInfo { path, flags });
    }

    Ok(list)
}
