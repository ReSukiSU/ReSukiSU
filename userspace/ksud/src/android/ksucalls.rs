#![allow(clippy::unreadable_literal)]
use std::{fs, os::fd::RawFd, sync::OnceLock};

use libc::{_IO, _IOR, _IOW, _IOWR};

use crate::defs::MountInfo;

// Event constants
const EVENT_POST_FS_DATA: u32 = 1;
const EVENT_BOOT_COMPLETED: u32 = 2;
const EVENT_MODULE_MOUNTED: u32 = 3;

const K: u32 = b'K' as u32;
const KSU_IOCTL_GRANT_ROOT: i32 = _IO(K, 1);
const KSU_IOCTL_GET_INFO: i32 = _IOR::<()>(K, 2);
const KSU_IOCTL_REPORT_EVENT: i32 = _IOW::<()>(K, 3);
const KSU_IOCTL_SET_SEPOLICY: i32 = _IOWR::<()>(K, 4);
const KSU_IOCTL_CHECK_SAFEMODE: i32 = _IOR::<()>(K, 5);
const KSU_IOCTL_GET_FEATURE: i32 = _IOWR::<()>(K, 13);
const KSU_IOCTL_SET_FEATURE: i32 = _IOW::<()>(K, 14);
const KSU_IOCTL_GET_WRAPPER_FD: i32 = _IOW::<()>(K, 15);
const KSU_IOCTL_MANAGE_MARK: i32 = _IOWR::<()>(K, 16);
const KSU_IOCTL_NUKE_EXT4_SYSFS: i32 = _IOW::<()>(K, 17);
const KSU_IOCTL_MANAGE_TRY_UMOUNT: i32 = _IOW::<()>(K, 18);

const SUKISU_IOCTL_DYNAMIC_MANAGER: i32 = _IOWR::<()>(K, 103);

#[repr(C)]
#[derive(Clone, Copy, Default)]
struct GetInfoCmd {
    version: u32,
    flags: u32,
}

#[repr(C)]
struct ReportEventCmd {
    event: u32,
}

#[repr(C)]
#[derive(Clone, Copy, Default)]
pub struct SetSepolicyCmd {
    pub cmd: u64,
    pub arg: u64,
}

#[repr(C)]
#[derive(Clone, Copy, Default)]
struct CheckSafemodeCmd {
    in_safe_mode: u8,
}

#[repr(C)]
#[derive(Clone, Copy, Default)]
struct GetFeatureCmd {
    feature_id: u32,
    value: u64,
    supported: u8,
}

#[repr(C)]
#[derive(Clone, Copy, Default)]
struct SetFeatureCmd {
    feature_id: u32,
    value: u64,
}

#[repr(C)]
#[derive(Clone, Copy, Default)]
struct GetWrapperFdCmd {
    fd: i32,
    flags: u32,
}

#[repr(C)]
#[derive(Clone, Copy, Default)]
struct ManageMarkCmd {
    operation: u32,
    pid: i32,
    result: u32,
}

#[repr(C)]
#[derive(Clone, Copy, Default)]
pub struct NukeExt4SysfsCmd {
    pub arg: u64,
}

#[repr(C)]
#[derive(Clone, Copy, Default)]
struct ManageTryUmountCmd {
    arg: u64,   // char ptr, this is the mountpoint
    flags: u32, // this is the flag we use for it
    mode: u8,   // denotes what to do with it 0:wipe_list 1:add_to_list 2:delete_entry
}

#[repr(C)]
#[derive(Clone, Copy)]
struct DynamicManage {
    operation: u32,
    size: u32,
    hash: [u8; 64],
}

// Mark operation constants
const KSU_MARK_GET: u32 = 1;
const KSU_MARK_MARK: u32 = 2;
const KSU_MARK_UNMARK: u32 = 3;
const KSU_MARK_REFRESH: u32 = 4;

// Umount operation constants
const KSU_UMOUNT_WIPE: u8 = 0;
const KSU_UMOUNT_ADD: u8 = 1;
const KSU_UMOUNT_DEL: u8 = 2;
const KSU_UMOUNT_GETSIZE_NEW: u8 = 200;
const KSU_UMOUNT_GETLIST_NEW: u8 = 201;

// Dynamic Manager operation constants
const SUKISU_DYNAMIC_MANAGER_SET: u32 = 0;
const SUKISU_DYNAMIC_MANAGER_GET: u32 = 1;
const SUKISU_DYNAMIC_MANAGER_CLEAR: u32 = 2;

// Global driver fd cache
static DRIVER_FD: OnceLock<RawFd> = OnceLock::new();
static INFO_CACHE: OnceLock<GetInfoCmd> = OnceLock::new();

const KSU_INSTALL_MAGIC1: u32 = 0xDEADBEEF;
const KSU_INSTALL_MAGIC2: u32 = 0xCAFEBABE;

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
                KSU_INSTALL_MAGIC1,
                KSU_INSTALL_MAGIC2,
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
pub fn ksuctl<T>(request: i32, arg: *mut T) -> std::io::Result<i32> {
    use std::io;

    let fd = *DRIVER_FD.get_or_init(|| init_driver_fd().unwrap_or(-1));
    unsafe {
        let ret = libc::ioctl(fd as libc::c_int, request, arg);
        if ret < 0 {
            Err(io::Error::last_os_error())
        } else {
            Ok(ret)
        }
    }
}

// API implementations
fn get_info() -> GetInfoCmd {
    *INFO_CACHE.get_or_init(|| {
        let mut cmd = GetInfoCmd {
            version: 0,
            flags: 0,
        };
        let _ = ksuctl(KSU_IOCTL_GET_INFO, &raw mut cmd);
        cmd
    })
}

pub fn get_version() -> i32 {
    get_info().version as i32
}

pub fn grant_root() -> std::io::Result<()> {
    ksuctl(KSU_IOCTL_GRANT_ROOT, std::ptr::null_mut::<u8>())?;
    Ok(())
}

fn report_event(event: u32) {
    let mut cmd = ReportEventCmd { event };
    let _ = ksuctl(KSU_IOCTL_REPORT_EVENT, &raw mut cmd);
}

pub fn report_post_fs_data() {
    report_event(EVENT_POST_FS_DATA);
}

pub fn report_boot_complete() {
    report_event(EVENT_BOOT_COMPLETED);
}

pub fn report_module_mounted() {
    report_event(EVENT_MODULE_MOUNTED);
}

pub fn check_kernel_safemode() -> bool {
    let mut cmd = CheckSafemodeCmd { in_safe_mode: 0 };
    let _ = ksuctl(KSU_IOCTL_CHECK_SAFEMODE, &raw mut cmd);
    cmd.in_safe_mode != 0
}

pub fn set_sepolicy(cmd: &SetSepolicyCmd) -> std::io::Result<()> {
    let mut ioctl_cmd = *cmd;
    ksuctl(KSU_IOCTL_SET_SEPOLICY, &raw mut ioctl_cmd)?;
    Ok(())
}

/// Get feature value and support status from kernel
/// Returns (value, supported)
pub fn get_feature(feature_id: u32) -> std::io::Result<(u64, bool)> {
    let mut cmd = GetFeatureCmd {
        feature_id,
        value: 0,
        supported: 0,
    };
    ksuctl(KSU_IOCTL_GET_FEATURE, &raw mut cmd)?;
    Ok((cmd.value, cmd.supported != 0))
}

/// Set feature value in kernel
pub fn set_feature(feature_id: u32, value: u64) -> std::io::Result<()> {
    let mut cmd = SetFeatureCmd { feature_id, value };
    ksuctl(KSU_IOCTL_SET_FEATURE, &raw mut cmd)?;
    Ok(())
}

pub fn get_wrapped_fd(fd: RawFd) -> std::io::Result<RawFd> {
    let mut cmd = GetWrapperFdCmd { fd, flags: 0 };
    let result = ksuctl(KSU_IOCTL_GET_WRAPPER_FD, &raw mut cmd)?;
    Ok(result)
}

/// Get mark status for a process (pid=0 returns total marked count)
pub fn mark_get(pid: i32) -> std::io::Result<u32> {
    let mut cmd = ManageMarkCmd {
        operation: KSU_MARK_GET,
        pid,
        result: 0,
    };
    ksuctl(KSU_IOCTL_MANAGE_MARK, &raw mut cmd)?;
    Ok(cmd.result)
}

/// Mark a process (pid=0 marks all processes)
pub fn mark_set(pid: i32) -> std::io::Result<()> {
    let mut cmd = ManageMarkCmd {
        operation: KSU_MARK_MARK,
        pid,
        result: 0,
    };
    ksuctl(KSU_IOCTL_MANAGE_MARK, &raw mut cmd)?;
    Ok(())
}

/// Unmark a process (pid=0 unmarks all processes)
pub fn mark_unset(pid: i32) -> std::io::Result<()> {
    let mut cmd = ManageMarkCmd {
        operation: KSU_MARK_UNMARK,
        pid,
        result: 0,
    };
    ksuctl(KSU_IOCTL_MANAGE_MARK, &raw mut cmd)?;
    Ok(())
}

/// Refresh mark for all running processes
pub fn mark_refresh() -> std::io::Result<()> {
    let mut cmd = ManageMarkCmd {
        operation: KSU_MARK_REFRESH,
        pid: 0,
        result: 0,
    };
    ksuctl(KSU_IOCTL_MANAGE_MARK, &raw mut cmd)?;
    Ok(())
}

pub fn nuke_ext4_sysfs(mnt: &str) -> anyhow::Result<()> {
    let c_mnt = std::ffi::CString::new(mnt)?;
    let mut ioctl_cmd = NukeExt4SysfsCmd {
        arg: c_mnt.as_ptr() as u64,
    };
    ksuctl(KSU_IOCTL_NUKE_EXT4_SYSFS, &raw mut ioctl_cmd)?;
    Ok(())
}

/// Wipe all entries from umount list
pub fn umount_list_wipe() -> std::io::Result<()> {
    let mut cmd = ManageTryUmountCmd {
        arg: 0,
        flags: 0,
        mode: KSU_UMOUNT_WIPE,
    };
    ksuctl(KSU_IOCTL_MANAGE_TRY_UMOUNT, &raw mut cmd)?;
    Ok(())
}

/// Add mount point to umount list
pub fn umount_list_add(path: &str, flags: u32) -> anyhow::Result<()> {
    let c_path = std::ffi::CString::new(path)?;
    let mut cmd = ManageTryUmountCmd {
        arg: c_path.as_ptr() as u64,
        flags,
        mode: KSU_UMOUNT_ADD,
    };
    ksuctl(KSU_IOCTL_MANAGE_TRY_UMOUNT, &raw mut cmd)?;
    Ok(())
}

/// Delete mount point from umount list
pub fn umount_list_del(path: &str) -> anyhow::Result<()> {
    let c_path = std::ffi::CString::new(path)?;
    let mut cmd = ManageTryUmountCmd {
        arg: c_path.as_ptr() as u64,
        flags: 0,
        mode: KSU_UMOUNT_DEL,
    };
    ksuctl(KSU_IOCTL_MANAGE_TRY_UMOUNT, &raw mut cmd)?;
    Ok(())
}

pub fn dynamic_manager_set(size: u32, hash: [u8; 64]) -> anyhow::Result<()> {
    let mut cmd = DynamicManage {
        operation: SUKISU_DYNAMIC_MANAGER_SET,
        size,
        hash,
    };
    ksuctl(SUKISU_IOCTL_DYNAMIC_MANAGER, &raw mut cmd)?;
    Ok(())
}

pub fn dynamic_manager_get() -> anyhow::Result<(u32, [u8; 64])> {
    let mut cmd = DynamicManage {
        operation: SUKISU_DYNAMIC_MANAGER_GET,
        size: 0,
        hash: [0u8; 64],
    };
    ksuctl(SUKISU_IOCTL_DYNAMIC_MANAGER, &raw mut cmd)?;
    Ok((cmd.size, cmd.hash))
}

pub fn dynamic_manager_clear() -> anyhow::Result<()> {
    let mut cmd = DynamicManage {
        operation: SUKISU_DYNAMIC_MANAGER_CLEAR,
        size: 0,
        hash: [0u8; 64],
    };
    ksuctl(SUKISU_IOCTL_DYNAMIC_MANAGER, &raw mut cmd)?;
    Ok(())
}

/// List all mount points in umount list
pub fn umount_list_list() -> anyhow::Result<Vec<MountInfo>> {
    const FLAGS_SIZE: usize = std::mem::size_of::<u32>();
    let mut total_size: usize = 0;
    let mut size_cmd = ManageTryUmountCmd {
        arg: &raw mut total_size as u64,
        flags: 0,
        mode: KSU_UMOUNT_GETSIZE_NEW,
    };
    ksuctl(KSU_IOCTL_MANAGE_TRY_UMOUNT, &raw mut size_cmd)?;

    if total_size == 0 {
        return Ok(vec![]);
    }

    let mut buffer = vec![0u8; total_size];
    let mut list_cmd = ManageTryUmountCmd {
        arg: buffer.as_mut_ptr() as u64,
        flags: 0,
        mode: KSU_UMOUNT_GETLIST_NEW,
    };
    ksuctl(KSU_IOCTL_MANAGE_TRY_UMOUNT, &raw mut list_cmd)?;

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
