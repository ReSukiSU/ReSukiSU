#![allow(clippy::similar_names)]

use std::{fs, os::unix::fs::MetadataExt};

use anyhow::Result;
use clap::{Args, Subcommand};
use libc::{SYS_reboot, syscall};

use crate::android::susfs::magic::{
    CMD_SUSFS_ADD_OPEN_REDIRECT, CMD_SUSFS_ADD_SUS_KSTAT, CMD_SUSFS_ADD_SUS_KSTAT_STATICALLY,
    CMD_SUSFS_ADD_SUS_MAP, CMD_SUSFS_ADD_SUS_PATH, CMD_SUSFS_ADD_SUS_PATH_LOOP,
    CMD_SUSFS_ENABLE_AVC_LOG_SPOOFING, CMD_SUSFS_ENABLE_LOG,
    CMD_SUSFS_HIDE_SUS_MNTS_FOR_NON_SU_PROCS, CMD_SUSFS_SET_ANDROID_DATA_ROOT_PATH,
    CMD_SUSFS_SET_CMDLINE_OR_BOOTCONFIG, CMD_SUSFS_SET_SDCARD_ROOT_PATH, CMD_SUSFS_SET_UNAME,
    CMD_SUSFS_SHOW_ENABLED_FEATURES, CMD_SUSFS_SHOW_VARIANT, CMD_SUSFS_SHOW_VERSION,
    CMD_SUSFS_UPDATE_SUS_KSTAT, ERR_CMD_NOT_SUPPORTED, KSU_INSTALL_MAGIC1, NEW_UTS_LEN,
    SUSFS_ENABLED_FEATURES_SIZE, SUSFS_FAKE_CMDLINE_OR_BOOTCONFIG_SIZE, SUSFS_MAGIC,
    SUSFS_MAX_LEN_PATHNAME, SUSFS_MAX_VARIANT_BUFSIZE, SUSFS_MAX_VERSION_BUFSIZE,
};

#[derive(Subcommand, Debug)]
pub enum SuSFSSubCommands {
    /// Added path and all its sub-paths will be hidden from several syscalls
    AddSusPath {
        #[arg(help = "Path of file or directory")]
        path: String,
    },
    /// Similar to add_sus_path but flagged as SUS_PATH per zygote spawned process (not for sdcard)
    AddSusPathLoop {
        #[arg(help = "Path not inside sdcard")]
        path: String,
    },
    /// Fix leak of app path after /sdcard/Android/data
    SetAndroidDataRootPath {
        #[arg(help = "Root dir of /sdcard/Android/data")]
        path: String,
    },
    /// Hide paths after /sdcard/
    SetSdcardRootPath {
        #[arg(help = "Root dir of /sdcard")]
        path: String,
    },
    /// Hide sus mounts for non-su processes
    HideSusMntsForNonSuProcs {
        #[arg(help = "0: DO NOT hide, 1: Hide")]
        enabled: u8,
    },
    /// Add path to store original stat info in kernel memory (before bind mount/overlay)
    AddSusKstat { path: String },
    /// Update the target ino for a path added via add_sus_kstat
    UpdateSusKstat { path: String },
    /// Update target ino only, other stat members remain same as original
    UpdateSusKstatFullClone { path: String },
    /// Spoof uname release and version
    SetUname { release: String, version: String },
    /// Enable/Disable susfs log in kernel
    EnableLog {
        #[arg(help = "0: disable, 1: enable")]
        enabled: u8,
    },
    /// Spoof /proc/cmdline or /proc/bootconfig
    SetCmdlineOrBootconfig { path: String },
    /// Redirect target path to be opened with user defined path
    AddOpenRedirect {
        target_path: String,
        redirected_path: String,
    },
    /// Hidden from /proc/self/maps etc.
    AddSusMap { path: String },
    /// Enable/Disable spoofing sus 'su' context in avc log
    EnableAvcLogSpoofing {
        #[arg(help = "0: disable, 1: enable")]
        enabled: u8,
    },
    /// Show version, enabled_features, or variant
    Show {
        #[command(subcommand)]
        info_type: ShowType,
    },
    /// (Advanced) Add sus kstat statically with manual or default values
    AddSusKstatStatically(Box<AddSusKstatStaticallyArgs>),
}

#[derive(Subcommand, Debug)]
pub enum ShowType {
    Version,
    EnabledFeatures,
    Variant,
}

#[derive(Debug, Args)]
pub struct AddSusKstatStaticallyArgs {
    path: String,
    #[arg(default_value = "default")]
    ino: String,
    #[arg(default_value = "default")]
    dev: String,
    #[arg(default_value = "default")]
    nlink: String,
    #[arg(default_value = "default")]
    size: String,
    #[arg(default_value = "default")]
    atime: String,
    #[arg(default_value = "default")]
    atime_nsec: String,
    #[arg(default_value = "default")]
    mtime: String,
    #[arg(default_value = "default")]
    mtime_nsec: String,
    #[arg(default_value = "default")]
    ctime: String,
    #[arg(default_value = "default")]
    ctime_nsec: String,
    #[arg(default_value = "default")]
    blocks: String,
    #[arg(default_value = "default")]
    blksize: String,
}

#[repr(C)]
struct SusfsSusPath {
    target_ino: u64,
    target_pathname: [u8; SUSFS_MAX_LEN_PATHNAME],
    i_uid: u32,
    err: i32,
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
struct ExternalDir {
    target_pathname: [u8; SUSFS_MAX_LEN_PATHNAME],
    is_inited: bool,
    cmd: i32,
    err: i32,
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
#[derive(Default)]
struct SusfsHideSusMnts {
    enabled: bool,
    err: i32,
}

#[repr(C)]
struct SusfsSusKstat {
    is_statically: bool,
    target_ino: u64,
    target_pathname: [u8; SUSFS_MAX_LEN_PATHNAME],
    spoofed_ino: u64,
    spoofed_dev: u64,
    spoofed_nlink: u32,
    spoofed_size: i64,
    spoofed_atime_tv_sec: i64,
    spoofed_mtime_tv_sec: i64,
    spoofed_ctime_tv_sec: i64,
    spoofed_atime_tv_nsec: i64,
    spoofed_mtime_tv_nsec: i64,
    spoofed_ctime_tv_nsec: i64,
    spoofed_blksize: u64,
    spoofed_blocks: u64,
    err: i32,
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
struct SusfsUname {
    release: [u8; NEW_UTS_LEN + 1],
    version: [u8; NEW_UTS_LEN + 1],
    err: i32,
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
struct SusfsLog {
    enabled: bool,
    err: i32,
}

#[repr(C)]
struct SusfsSpoofCmdline {
    fake_cmdline_or_bootconfig: [u8; SUSFS_FAKE_CMDLINE_OR_BOOTCONFIG_SIZE],
    err: i32,
}

#[repr(C)]
struct SusfsOpenRedirect {
    target_ino: u64,
    target_pathname: [u8; SUSFS_MAX_LEN_PATHNAME],
    redirected_pathname: [u8; SUSFS_MAX_LEN_PATHNAME],
    err: i32,
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
struct SusfsSusMap {
    target_pathname: [u8; SUSFS_MAX_LEN_PATHNAME],
    err: i32,
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
#[derive(Default)]
struct SusfsAvcLogSpoofing {
    enabled: bool,
    err: i32,
}

#[repr(C)]
struct SusfsEnabledFeatures {
    enabled_features: [u8; SUSFS_ENABLED_FEATURES_SIZE],
    err: i32,
}

#[repr(C)]
struct SusfsVariant {
    susfs_variant: [u8; SUSFS_MAX_VARIANT_BUFSIZE],
    err: i32,
}

#[repr(C)]
struct SusfsVersion {
    susfs_version: [u8; SUSFS_MAX_VERSION_BUFSIZE],
    err: i32,
}

fn susfs_ctl<T>(info: &mut T, cmd: u64) {
    unsafe {
        syscall(
            SYS_reboot,
            KSU_INSTALL_MAGIC1,
            SUSFS_MAGIC,
            cmd,
            std::ptr::from_mut::<T>(info),
        );
    }
}

fn str_to_c_array<const N: usize>(s: &str, array: &mut [u8; N]) {
    let bytes = s.as_bytes();
    let len = bytes.len().min(N - 1);
    array[..len].copy_from_slice(&bytes[..len]);
    array[len] = 0;
}

fn fetch_metadata(path: &str) -> Result<fs::Metadata> {
    fs::metadata(path).map_err(|e| {
        anyhow::format_err!("[-] Failed to get metadata from path: '{path}', error: {e}",)
    })
}

fn copy_metadata_to_sus_kstat(info: &mut SusfsSusKstat, md: &fs::Metadata) {
    info.spoofed_ino = md.ino();
    info.spoofed_dev = md.dev();
    info.spoofed_nlink = md.nlink() as u32;
    info.spoofed_size = md.size() as i64;
    info.spoofed_atime_tv_sec = md.atime();
    info.spoofed_mtime_tv_sec = md.mtime();
    info.spoofed_ctime_tv_sec = md.ctime();
    info.spoofed_atime_tv_nsec = md.atime_nsec();
    info.spoofed_mtime_tv_nsec = md.mtime_nsec();
    info.spoofed_ctime_tv_nsec = md.ctime_nsec();
    info.spoofed_blksize = md.blksize();
    info.spoofed_blocks = md.blocks();
}

fn handle_result(err: i32, cmd: u64) -> Result<()> {
    if err == ERR_CMD_NOT_SUPPORTED {
        return Err(anyhow::format_err!(
            "unsupported susfs operation, cmd: 0x{cmd:x}"
        ));
    }
    if err != 0 && err != ERR_CMD_NOT_SUPPORTED {
        return Err(anyhow::format_err!("{err}"));
    }

    Ok(())
}

fn parse_or_default<T: std::str::FromStr>(val: &str, default: T) -> Result<T> {
    if val == "default" {
        Ok(default)
    } else {
        val.parse::<T>()
            .map_err(|_| anyhow::format_err!("Invalid number format: {val}"))
    }
}

pub fn susfs_cli(sub_commmand: SuSFSSubCommands) -> Result<()> {
    match sub_commmand {
        SuSFSSubCommands::AddSusPath { path } => {
            let md = fetch_metadata(&path)?;
            let mut info = SusfsSusPath::default();
            str_to_c_array(&path, &mut info.target_pathname);
            info.target_ino = md.ino() as u64;
            info.i_uid = md.uid() as u32;
            info.err = ERR_CMD_NOT_SUPPORTED;

            susfs_ctl(&mut info, CMD_SUSFS_ADD_SUS_PATH);
            handle_result(info.err, CMD_SUSFS_ADD_SUS_PATH)?;
        }
        SuSFSSubCommands::AddSusPathLoop { path } => {
            let md = fetch_metadata(&path)?;
            let mut info = SusfsSusPath::default();
            str_to_c_array(&path, &mut info.target_pathname);
            info.target_ino = md.ino() as u64;
            info.i_uid = md.uid() as u32;
            info.err = ERR_CMD_NOT_SUPPORTED;

            susfs_ctl(&mut info, CMD_SUSFS_ADD_SUS_PATH_LOOP);
            handle_result(info.err, CMD_SUSFS_ADD_SUS_PATH_LOOP)?;
        }
        SuSFSSubCommands::SetAndroidDataRootPath { path } => {
            let mut info = ExternalDir::default();
            str_to_c_array(&path, &mut info.target_pathname);
            info.cmd = CMD_SUSFS_SET_ANDROID_DATA_ROOT_PATH as i32;
            info.err = ERR_CMD_NOT_SUPPORTED;

            susfs_ctl(&mut info, CMD_SUSFS_SET_ANDROID_DATA_ROOT_PATH);
            handle_result(info.err, CMD_SUSFS_SET_ANDROID_DATA_ROOT_PATH)?;
        }
        SuSFSSubCommands::SetSdcardRootPath { path } => {
            let mut info = ExternalDir::default();
            str_to_c_array(&path, &mut info.target_pathname);
            info.cmd = CMD_SUSFS_SET_SDCARD_ROOT_PATH as i32;
            info.err = ERR_CMD_NOT_SUPPORTED;

            susfs_ctl(&mut info, CMD_SUSFS_SET_SDCARD_ROOT_PATH);
            handle_result(info.err, CMD_SUSFS_SET_SDCARD_ROOT_PATH)?;
        }
        SuSFSSubCommands::HideSusMntsForNonSuProcs { enabled } => {
            if enabled > 1 {
                return Err(anyhow::format_err!("Invalid value for enabled (0 or 1)"));
            }
            let mut info = SusfsHideSusMnts {
                enabled: enabled == 1,
                err: ERR_CMD_NOT_SUPPORTED,
            };
            susfs_ctl(&mut info, CMD_SUSFS_HIDE_SUS_MNTS_FOR_NON_SU_PROCS);
            handle_result(info.err, CMD_SUSFS_HIDE_SUS_MNTS_FOR_NON_SU_PROCS)?;
        }
        SuSFSSubCommands::AddSusKstat { path } => {
            let md = fetch_metadata(&path)?;
            let mut info = SusfsSusKstat::default();
            str_to_c_array(&path, &mut info.target_pathname);
            info.is_statically = false;
            info.target_ino = md.ino() as u64;
            copy_metadata_to_sus_kstat(&mut info, &md);
            info.err = ERR_CMD_NOT_SUPPORTED;

            susfs_ctl(&mut info, CMD_SUSFS_ADD_SUS_KSTAT);
            handle_result(info.err, CMD_SUSFS_ADD_SUS_KSTAT)?;
        }
        SuSFSSubCommands::UpdateSusKstat { path } => {
            let md = fetch_metadata(&path)?;
            let mut info = SusfsSusKstat::default();
            str_to_c_array(&path, &mut info.target_pathname);
            info.is_statically = false;
            info.target_ino = md.ino() as u64;
            info.spoofed_size = md.size() as i64;
            info.spoofed_blocks = md.blocks() as u64;
            info.err = ERR_CMD_NOT_SUPPORTED;

            susfs_ctl(&mut info, CMD_SUSFS_UPDATE_SUS_KSTAT);
            handle_result(info.err, CMD_SUSFS_UPDATE_SUS_KSTAT)?;
        }
        SuSFSSubCommands::UpdateSusKstatFullClone { path } => {
            let md = fetch_metadata(&path)?;
            let mut info = SusfsSusKstat::default();
            str_to_c_array(&path, &mut info.target_pathname);
            info.is_statically = false;
            info.target_ino = md.ino() as u64;
            info.err = ERR_CMD_NOT_SUPPORTED;

            susfs_ctl(&mut info, CMD_SUSFS_UPDATE_SUS_KSTAT);
            handle_result(info.err, CMD_SUSFS_UPDATE_SUS_KSTAT)?;
        }
        SuSFSSubCommands::SetUname { release, version } => {
            let mut info = SusfsUname::default();
            str_to_c_array(&release, &mut info.release);
            str_to_c_array(&version, &mut info.version);
            info.err = ERR_CMD_NOT_SUPPORTED;

            susfs_ctl(&mut info, CMD_SUSFS_SET_UNAME);
            handle_result(info.err, CMD_SUSFS_SET_UNAME)?;
        }
        SuSFSSubCommands::EnableLog { enabled } => {
            if enabled > 1 {
                return Err(anyhow::format_err!("Invalid value for enabled (0 or 1)"));
            }
            let mut info = SusfsLog {
                enabled: enabled == 1,
                err: ERR_CMD_NOT_SUPPORTED,
            };

            susfs_ctl(&mut info, CMD_SUSFS_ENABLE_LOG);
            handle_result(info.err, CMD_SUSFS_ENABLE_LOG)?;
        }
        SuSFSSubCommands::SetCmdlineOrBootconfig { path } => {
            let abs_path = fs::canonicalize(&path)?;
            let content = fs::read(&abs_path)?;
            if content.len() >= SUSFS_FAKE_CMDLINE_OR_BOOTCONFIG_SIZE {
                return Err(anyhow::format_err!("file_size too long"));
            }

            let mut info = Box::new(SusfsSpoofCmdline {
                fake_cmdline_or_bootconfig: [0; SUSFS_FAKE_CMDLINE_OR_BOOTCONFIG_SIZE],
                err: ERR_CMD_NOT_SUPPORTED,
            });

            for (i, &b) in content.iter().enumerate() {
                info.fake_cmdline_or_bootconfig[i] = b;
            }

            susfs_ctl(&mut info, CMD_SUSFS_SET_CMDLINE_OR_BOOTCONFIG);
            handle_result(info.err, CMD_SUSFS_SET_CMDLINE_OR_BOOTCONFIG)?;
        }
        SuSFSSubCommands::AddOpenRedirect {
            target_path,
            redirected_path,
        } => {
            let abs_target = fs::canonicalize(&target_path)?;
            let abs_redirect = fs::canonicalize(&redirected_path)?;

            let md = fetch_metadata(abs_target.to_str().unwrap())?;

            let mut info = SusfsOpenRedirect::default();
            str_to_c_array(abs_target.to_str().unwrap(), &mut info.target_pathname);
            str_to_c_array(
                abs_redirect.to_str().unwrap(),
                &mut info.redirected_pathname,
            );
            info.target_ino = md.ino() as u64;
            info.err = ERR_CMD_NOT_SUPPORTED;

            susfs_ctl(&mut info, CMD_SUSFS_ADD_OPEN_REDIRECT);
            handle_result(info.err, CMD_SUSFS_ADD_OPEN_REDIRECT)?;
        }
        SuSFSSubCommands::AddSusMap { path } => {
            let mut info = SusfsSusMap::default();
            str_to_c_array(&path, &mut info.target_pathname);
            info.err = ERR_CMD_NOT_SUPPORTED;

            susfs_ctl(&mut info, CMD_SUSFS_ADD_SUS_MAP);
            handle_result(info.err, CMD_SUSFS_ADD_SUS_MAP)?;
        }
        SuSFSSubCommands::EnableAvcLogSpoofing { enabled } => {
            if enabled > 1 {
                return Err(anyhow::format_err!("Invalid status number"));
            }
            let mut info = SusfsAvcLogSpoofing {
                enabled: enabled == 1,
                err: ERR_CMD_NOT_SUPPORTED,
            };
            susfs_ctl(&mut info, CMD_SUSFS_ENABLE_AVC_LOG_SPOOFING);
            handle_result(info.err, CMD_SUSFS_ENABLE_AVC_LOG_SPOOFING)?;
        }
        SuSFSSubCommands::Show { info_type } => match info_type {
            ShowType::Version => {
                let mut info = SusfsVersion {
                    susfs_version: [0; SUSFS_MAX_VERSION_BUFSIZE],
                    err: ERR_CMD_NOT_SUPPORTED,
                };
                susfs_ctl(&mut info, CMD_SUSFS_SHOW_VERSION);
                handle_result(info.err, CMD_SUSFS_SHOW_VERSION)?;

                if info.err == 0 {
                    let len = info
                        .susfs_version
                        .iter()
                        .position(|&b| b == 0)
                        .unwrap_or(SUSFS_MAX_VERSION_BUFSIZE);
                    let bytes: Vec<u8> = info.susfs_version[..len].to_vec();
                    let ver = String::from_utf8(bytes).unwrap_or_else(|_| "<invalid>".to_string());

                    if ver.starts_with('v') {
                        println!("{ver}");
                    } else {
                        println!("unsupport");
                    }
                }
            }
            ShowType::EnabledFeatures => {
                let mut info = Box::new(SusfsEnabledFeatures {
                    enabled_features: [0; SUSFS_ENABLED_FEATURES_SIZE],
                    err: ERR_CMD_NOT_SUPPORTED,
                });
                susfs_ctl(&mut *info, CMD_SUSFS_SHOW_ENABLED_FEATURES);
                handle_result(info.err, CMD_SUSFS_SHOW_ENABLED_FEATURES)?;

                if info.err == 0 {
                    let len = info
                        .enabled_features
                        .iter()
                        .position(|&b| b == 0)
                        .unwrap_or(SUSFS_ENABLED_FEATURES_SIZE);
                    let bytes: Vec<u8> = info.enabled_features[..len].to_vec();
                    let features =
                        String::from_utf8(bytes).unwrap_or_else(|_| "<invalid>".to_string());
                    print!("{features}");
                }
            }
            ShowType::Variant => {
                let mut info = SusfsVariant {
                    susfs_variant: [0; SUSFS_MAX_VARIANT_BUFSIZE],
                    err: ERR_CMD_NOT_SUPPORTED,
                };
                susfs_ctl(&mut info, CMD_SUSFS_SHOW_VARIANT);
                handle_result(info.err, CMD_SUSFS_SHOW_VARIANT)?;

                if info.err == 0 {
                    let len = info
                        .susfs_variant
                        .iter()
                        .position(|&b| b == 0)
                        .unwrap_or(SUSFS_MAX_VARIANT_BUFSIZE);
                    let bytes: Vec<u8> = info.susfs_variant[..len].to_vec();
                    let variant =
                        String::from_utf8(bytes).unwrap_or_else(|_| "<invalid>".to_string());
                    println!("{variant}");
                }
            }
        },
        SuSFSSubCommands::AddSusKstatStatically(args) => {
            let md = fetch_metadata(&args.path)?;
            let mut info = SusfsSusKstat {
                target_ino: md.ino() as u64,
                is_statically: true,
                ..Default::default()
            };

            let s_ino = parse_or_default(&args.ino, md.ino())?;
            let s_dev = parse_or_default(&args.dev, md.dev())?;
            let s_nlink = parse_or_default(&args.nlink, md.nlink() as u64)?;
            let s_size = parse_or_default(&args.size, md.size())?;
            let s_atime = parse_or_default(&args.atime, md.atime())?;
            let s_atime_nsec = parse_or_default(&args.atime_nsec, md.atime_nsec())?;
            let s_mtime = parse_or_default(&args.mtime, md.mtime())?;
            let s_mtime_nsec = parse_or_default(&args.mtime_nsec, md.mtime_nsec())?;
            let s_ctime = parse_or_default(&args.ctime, md.ctime())?;
            let s_ctime_nsec = parse_or_default(&args.ctime_nsec, md.ctime_nsec())?;
            let s_blocks = parse_or_default(&args.blocks, md.blocks())?;
            let s_blksize = parse_or_default(&args.blksize, md.blksize())?;

            str_to_c_array(&args.path, &mut info.target_pathname);

            info.spoofed_ino = s_ino as u64;
            info.spoofed_dev = s_dev as u64;
            info.spoofed_nlink = s_nlink as u32;
            info.spoofed_size = s_size as i64;
            info.spoofed_atime_tv_sec = s_atime as i64;
            info.spoofed_mtime_tv_sec = s_mtime as i64;
            info.spoofed_ctime_tv_sec = s_ctime as i64;
            info.spoofed_atime_tv_nsec = s_atime_nsec as i64;
            info.spoofed_mtime_tv_nsec = s_mtime_nsec as i64;
            info.spoofed_ctime_tv_nsec = s_ctime_nsec as i64;
            info.spoofed_blksize = s_blksize as u64;
            info.spoofed_blocks = s_blocks as u64;

            info.err = ERR_CMD_NOT_SUPPORTED;

            susfs_ctl(&mut info, CMD_SUSFS_ADD_SUS_KSTAT_STATICALLY);
            handle_result(info.err, CMD_SUSFS_ADD_SUS_KSTAT_STATICALLY)?;
        }
    }

    Ok(())
}
