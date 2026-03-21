use std::{fs, os::unix::fs::MetadataExt};

use anyhow::Result;

use crate::android::susfs::{
    magic::{
        CMD_SUSFS_ADD_SUS_KSTAT, CMD_SUSFS_ADD_SUS_PATH, CMD_SUSFS_ADD_SUS_PATH_LOOP,
        CMD_SUSFS_ENABLE_AVC_LOG_SPOOFING, CMD_SUSFS_ENABLE_LOG,
        CMD_SUSFS_HIDE_SUS_MNTS_FOR_NON_SU_PROCS, CMD_SUSFS_SET_ANDROID_DATA_ROOT_PATH,
        CMD_SUSFS_SET_SDCARD_ROOT_PATH, CMD_SUSFS_UPDATE_SUS_KSTAT, ERR_CMD_NOT_SUPPORTED,
        NEW_UTS_LEN, SUSFS_ENABLED_FEATURES_SIZE, SUSFS_FAKE_CMDLINE_OR_BOOTCONFIG_SIZE,
        SUSFS_MAX_LEN_PATHNAME, SUSFS_MAX_VARIANT_BUFSIZE, SUSFS_MAX_VERSION_BUFSIZE,
    },
    utils::{copy_metadata_to_sus_kstat, handle_result, str_to_c_array, susfs_ctl},
};

#[repr(C)]
pub(super) struct SusfsSusPath {
    pub(super) target_ino: u64,
    pub(super) target_pathname: [u8; SUSFS_MAX_LEN_PATHNAME],
    pub(super) i_uid: u32,
    pub(super) err: i32,
}

impl Default for SusfsSusPath {
    fn default() -> Self {
        Self {
            target_ino: 0,
            target_pathname: [0; SUSFS_MAX_LEN_PATHNAME],
            i_uid: 0,
            err: 0,
        }
    }
}

#[repr(C)]
pub(super) struct ExternalDir {
    pub(super) target_pathname: [u8; SUSFS_MAX_LEN_PATHNAME],
    pub(super) is_inited: bool,
    pub(super) cmd: i32,
    pub(super) err: i32,
}

impl Default for ExternalDir {
    fn default() -> Self {
        Self {
            target_pathname: [0; SUSFS_MAX_LEN_PATHNAME],
            is_inited: false,
            cmd: 0,
            err: 0,
        }
    }
}

#[repr(C)]
pub(super) struct SusfsSusKstat {
    pub(super) is_statically: bool,
    pub(super) target_ino: u64,
    pub(super) target_pathname: [u8; SUSFS_MAX_LEN_PATHNAME],
    pub(super) spoofed_ino: u64,
    pub(super) spoofed_dev: u64,
    pub(super) spoofed_nlink: u32,
    pub(super) spoofed_size: i64,
    pub(super) spoofed_atime_tv_sec: i64,
    pub(super) spoofed_mtime_tv_sec: i64,
    pub(super) spoofed_ctime_tv_sec: i64,
    pub(super) spoofed_atime_tv_nsec: i64,
    pub(super) spoofed_mtime_tv_nsec: i64,
    pub(super) spoofed_ctime_tv_nsec: i64,
    pub(super) spoofed_blksize: u64,
    pub(super) spoofed_blocks: u64,
    pub(super) err: i32,
}

impl Default for SusfsSusKstat {
    fn default() -> Self {
        Self {
            is_statically: false,
            target_ino: 0,
            target_pathname: [0; SUSFS_MAX_LEN_PATHNAME],
            spoofed_ino: 0,
            spoofed_dev: 0,
            spoofed_nlink: 0,
            spoofed_size: 0,
            spoofed_atime_tv_sec: 0,
            spoofed_mtime_tv_sec: 0,
            spoofed_ctime_tv_sec: 0,
            spoofed_atime_tv_nsec: 0,
            spoofed_mtime_tv_nsec: 0,
            spoofed_ctime_tv_nsec: 0,
            spoofed_blksize: 0,
            spoofed_blocks: 0,
            err: 0,
        }
    }
}

#[repr(C)]
pub(super) struct SusfsUname {
    pub(super) release: [u8; NEW_UTS_LEN + 1],
    pub(super) version: [u8; NEW_UTS_LEN + 1],
    pub(super) err: i32,
}

impl Default for SusfsUname {
    fn default() -> Self {
        Self {
            release: [0; NEW_UTS_LEN + 1],
            version: [0; NEW_UTS_LEN + 1],
            err: 0,
        }
    }
}

#[repr(C)]
#[derive(Default)]
pub(super) struct SusfsBool {
    pub(super) enabled: bool,
    pub(super) err: i32,
}

#[repr(C)]
pub(super) struct SusfsSpoofCmdline {
    pub(super) fake_cmdline_or_bootconfig: [u8; SUSFS_FAKE_CMDLINE_OR_BOOTCONFIG_SIZE],
    pub(super) err: i32,
}

#[repr(C)]
pub(super) struct SusfsOpenRedirect {
    pub(super) target_ino: u64,
    pub(super) target_pathname: [u8; SUSFS_MAX_LEN_PATHNAME],
    pub(super) redirected_pathname: [u8; SUSFS_MAX_LEN_PATHNAME],
    pub(super) err: i32,
}

impl Default for SusfsOpenRedirect {
    fn default() -> Self {
        Self {
            target_ino: 0,
            target_pathname: [0; SUSFS_MAX_LEN_PATHNAME],
            redirected_pathname: [0; SUSFS_MAX_LEN_PATHNAME],
            err: 0,
        }
    }
}

#[repr(C)]
pub(super) struct SusfsSusMap {
    pub(super) target_pathname: [u8; SUSFS_MAX_LEN_PATHNAME],
    pub(super) err: i32,
}

impl Default for SusfsSusMap {
    fn default() -> Self {
        Self {
            target_pathname: [0; SUSFS_MAX_LEN_PATHNAME],
            err: 0,
        }
    }
}

#[repr(C)]
pub(super) struct SusfsEnabledFeatures {
    pub(super) enabled_features: [u8; SUSFS_ENABLED_FEATURES_SIZE],
    pub(super) err: i32,
}

#[repr(C)]
pub(super) struct SusfsVariant {
    pub(super) susfs_variant: [u8; SUSFS_MAX_VARIANT_BUFSIZE],
    pub(super) err: i32,
}

#[repr(C)]
pub(super) struct SusfsVersion {
    pub(super) susfs_version: [u8; SUSFS_MAX_VERSION_BUFSIZE],
    pub(super) err: i32,
}

pub(super) enum SusPathType {
    Normal,
    Loop,
}

pub(super) enum ExternalDirType {
    Sdcard,
    AndroidData,
}

#[derive(PartialEq)]
pub(super) enum SusKstatType {
    Update,
    FullClone,
    Add,
}

pub(super) enum BoolEnable {
    AvcLogSpoofing,
    Log,
    HideSusMntsForNonSuProcs,
}

pub(super) fn bool_enable(types: &BoolEnable, enabled: u8) -> Result<()> {
    if enabled > 1 {
        return Err(anyhow::format_err!("Invalid value for enabled (0 or 1)"));
    }
    let mut info = SusfsBool {
        enabled: enabled == 1,
        err: ERR_CMD_NOT_SUPPORTED,
    };
    let magic = match types {
        BoolEnable::HideSusMntsForNonSuProcs => CMD_SUSFS_HIDE_SUS_MNTS_FOR_NON_SU_PROCS,
        BoolEnable::Log => CMD_SUSFS_ENABLE_LOG,
        BoolEnable::AvcLogSpoofing => CMD_SUSFS_ENABLE_AVC_LOG_SPOOFING,
    };
    susfs_ctl(&mut info, magic);
    handle_result(info.err, magic)?;
    Ok(())
}

pub fn set_kstat<S>(types: &SusKstatType, path: &S) -> Result<()>
where
    S: ToString,
{
    let md = fs::metadata(path.to_string())?;
    let magic = if *types == SusKstatType::Add {
        CMD_SUSFS_ADD_SUS_KSTAT
    } else {
        CMD_SUSFS_UPDATE_SUS_KSTAT
    };

    let mut info = SusfsSusKstat::default();
    str_to_c_array(path.to_string().as_str(), &mut info.target_pathname);
    info.is_statically = false;
    info.target_ino = md.ino() as u64;
    match types {
        SusKstatType::Update => {
            info.spoofed_size = md.size() as i64;
            info.spoofed_blocks = md.blocks() as u64;
        }
        SusKstatType::Add => {
            copy_metadata_to_sus_kstat(&mut info, &md);
        }
        SusKstatType::FullClone => {}
    }
    info.err = ERR_CMD_NOT_SUPPORTED;

    susfs_ctl(&mut info, magic);
    handle_result(info.err, magic)?;
    Ok(())
}

pub fn add_sus_path<S>(types: &SusPathType, path: &S) -> Result<()>
where
    S: ToString,
{
    let md = fs::metadata(path.to_string())?;
    let mut info = SusfsSusPath::default();
    let magic = match types {
        SusPathType::Normal => CMD_SUSFS_ADD_SUS_PATH,
        SusPathType::Loop => CMD_SUSFS_ADD_SUS_PATH_LOOP,
    };
    str_to_c_array(path.to_string().as_str(), &mut info.target_pathname);
    info.target_ino = md.ino() as u64;
    info.i_uid = md.uid() as u32;
    info.err = ERR_CMD_NOT_SUPPORTED;

    susfs_ctl(&mut info, magic);
    handle_result(info.err, magic)?;
    Ok(())
}

pub(super) fn set_external_dir<S>(types: &ExternalDirType, path: &S) -> Result<()>
where
    S: ToString,
{
    let mut info = ExternalDir::default();
    let magic = match types {
        ExternalDirType::Sdcard => CMD_SUSFS_SET_SDCARD_ROOT_PATH,
        ExternalDirType::AndroidData => CMD_SUSFS_SET_ANDROID_DATA_ROOT_PATH,
    };
    str_to_c_array(path.to_string().as_str(), &mut info.target_pathname);
    info.cmd = magic as i32;
    info.err = ERR_CMD_NOT_SUPPORTED;

    susfs_ctl(&mut info, magic);
    handle_result(info.err, magic)?;
    Ok(())
}
