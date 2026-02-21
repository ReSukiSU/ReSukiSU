use std::{
    fs,
    os::unix::fs::MetadataExt,
    process::exit,
};

use clap::Subcommand;
use libc::{SYS_reboot, c_char, c_int, c_long, c_uint, c_ulong, syscall};

const KSU_INSTALL_MAGIC1: c_ulong = 0xDEADBEEF;
const SUSFS_MAGIC: c_ulong = 0xFAFAFAFA;

const CMD_SUSFS_ADD_SUS_PATH: c_ulong = 0x55550;
const CMD_SUSFS_SET_ANDROID_DATA_ROOT_PATH: c_ulong = 0x55551;
const CMD_SUSFS_SET_SDCARD_ROOT_PATH: c_ulong = 0x55552;
const CMD_SUSFS_ADD_SUS_PATH_LOOP: c_ulong = 0x55553;
const CMD_SUSFS_HIDE_SUS_MNTS_FOR_NON_SU_PROCS: c_ulong = 0x55561;
const CMD_SUSFS_ADD_SUS_KSTAT: c_ulong = 0x55570;
const CMD_SUSFS_UPDATE_SUS_KSTAT: c_ulong = 0x55571;
const CMD_SUSFS_ADD_SUS_KSTAT_STATICALLY: c_ulong = 0x55572;
const CMD_SUSFS_SET_UNAME: c_ulong = 0x55590;
const CMD_SUSFS_ENABLE_LOG: c_ulong = 0x555a0;
const CMD_SUSFS_SET_CMDLINE_OR_BOOTCONFIG: c_ulong = 0x555b0;
const CMD_SUSFS_ADD_OPEN_REDIRECT: c_ulong = 0x555c0;
const CMD_SUSFS_SHOW_VERSION: c_ulong = 0x555e1;
const CMD_SUSFS_SHOW_ENABLED_FEATURES: c_ulong = 0x555e2;
const CMD_SUSFS_SHOW_VARIANT: c_ulong = 0x555e3;
const CMD_SUSFS_ENABLE_AVC_LOG_SPOOFING: c_ulong = 0x60010;
const CMD_SUSFS_ADD_SUS_MAP: c_ulong = 0x60020;

const SUSFS_MAX_LEN_PATHNAME: usize = 256;
const SUSFS_FAKE_CMDLINE_OR_BOOTCONFIG_SIZE: usize = 8192;
const SUSFS_ENABLED_FEATURES_SIZE: usize = 8192;
const SUSFS_MAX_VERSION_BUFSIZE: usize = 16;
const SUSFS_MAX_VARIANT_BUFSIZE: usize = 16;
const NEW_UTS_LEN: usize = 64;
const ERR_CMD_NOT_SUPPORTED: c_int = 126;

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
    AddSusKstatStatically {
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
    },
}

#[derive(Subcommand, Debug)]
pub enum ShowType {
    Version,
    EnabledFeatures,
    Variant,
}

#[repr(C)]
struct SusfsSusPath {
    target_ino: c_ulong,
    target_pathname: [c_char; SUSFS_MAX_LEN_PATHNAME],
    i_uid: c_uint,
    err: c_int,
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
    target_pathname: [c_char; SUSFS_MAX_LEN_PATHNAME],
    is_inited: bool,
    cmd: c_int,
    err: c_int,
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
    err: c_int,
}

#[repr(C)]
struct SusfsSusKstat {
    is_statically: bool,
    target_ino: c_ulong,
    target_pathname: [c_char; SUSFS_MAX_LEN_PATHNAME],
    spoofed_ino: c_ulong,
    spoofed_dev: c_ulong,
    spoofed_nlink: c_uint,
    spoofed_size: libc::c_longlong,
    spoofed_atime_tv_sec: c_long,
    spoofed_mtime_tv_sec: c_long,
    spoofed_ctime_tv_sec: c_long,
    spoofed_atime_tv_nsec: c_long,
    spoofed_mtime_tv_nsec: c_long,
    spoofed_ctime_tv_nsec: c_long,
    spoofed_blksize: c_ulong,
    spoofed_blocks: libc::c_ulonglong,
    err: c_int,
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
    release: [c_char; NEW_UTS_LEN + 1],
    version: [c_char; NEW_UTS_LEN + 1],
    err: c_int,
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
    err: c_int,
}

#[repr(C)]
struct SusfsSpoofCmdline {
    fake_cmdline_or_bootconfig: [c_char; SUSFS_FAKE_CMDLINE_OR_BOOTCONFIG_SIZE],
    err: c_int,
}

#[repr(C)]
struct SusfsOpenRedirect {
    target_ino: c_ulong,
    target_pathname: [c_char; SUSFS_MAX_LEN_PATHNAME],
    redirected_pathname: [c_char; SUSFS_MAX_LEN_PATHNAME],
    err: c_int,
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
    target_pathname: [c_char; SUSFS_MAX_LEN_PATHNAME],
    err: c_int,
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
    err: c_int,
}

#[repr(C)]
struct SusfsEnabledFeatures {
    enabled_features: [c_char; SUSFS_ENABLED_FEATURES_SIZE],
    err: c_int,
}

#[repr(C)]
struct SusfsVariant {
    susfs_variant: [c_char; SUSFS_MAX_VARIANT_BUFSIZE],
    err: c_int,
}

#[repr(C)]
struct SusfsVersion {
    susfs_version: [c_char; SUSFS_MAX_VERSION_BUFSIZE],
    err: c_int,
}

fn susfs_ctl<T>(info: &mut T, cmd: c_ulong) {
    unsafe {
        syscall(
            SYS_reboot,
            KSU_INSTALL_MAGIC1,
            SUSFS_MAGIC,
            cmd,
            info as *mut T,
        );
    }
}

fn str_to_c_array<const N: usize>(s: &str, array: &mut [c_char; N]) {
    let bytes = s.as_bytes();
    let len = bytes.len().min(N - 1);
    for i in 0..len {
        array[i] = bytes[i] as c_char;
    }
    array[len] = 0;
}

fn fetch_metadata(path: &str) -> fs::Metadata {
    fs::metadata(path).unwrap_or_else(|e| {
        eprintln!(
            "[-] Failed to get metadata from path: '{}', error: {}",
            path, e
        );
        exit(e.raw_os_error().unwrap_or(1));
    })
}

fn copy_metadata_to_sus_kstat(info: &mut SusfsSusKstat, md: &fs::Metadata) {
    info.spoofed_ino = md.ino() as c_ulong;
    info.spoofed_dev = md.dev() as c_ulong;
    info.spoofed_nlink = md.nlink() as c_uint;
    info.spoofed_size = md.size() as libc::c_longlong;
    info.spoofed_atime_tv_sec = md.atime() as c_long;
    info.spoofed_mtime_tv_sec = md.mtime() as c_long;
    info.spoofed_ctime_tv_sec = md.ctime() as c_long;
    info.spoofed_atime_tv_nsec = md.atime_nsec() as c_long;
    info.spoofed_mtime_tv_nsec = md.mtime_nsec() as c_long;
    info.spoofed_ctime_tv_nsec = md.ctime_nsec() as c_long;
    info.spoofed_blksize = md.blksize() as c_ulong;
    info.spoofed_blocks = md.blocks() as libc::c_ulonglong;
}

fn handle_result(err: c_int, cmd: c_ulong) {
    if err == ERR_CMD_NOT_SUPPORTED {
        println!(
            "[-] CMD: '0x{:x}', SUSFS operation not supported, please enable it in kernel",
            cmd
        );
    }
    if err != 0 && err != ERR_CMD_NOT_SUPPORTED {
        exit(err);
    }
}

fn parse_or_default<T: std::str::FromStr>(val: &str, default: T) -> T {
    if val == "default" {
        default
    } else {
        val.parse::<T>().unwrap_or_else(|_| {
            eprintln!("Invalid number format: {}", val);
            exit(libc::EINVAL);
        })
    }
}

pub fn susfs_cli(sub_commmand: SuSFSSubCommands) {
    match sub_commmand {
        SuSFSSubCommands::AddSusPath { path } => {
            let md = fetch_metadata(&path);
            let mut info = SusfsSusPath::default();
            str_to_c_array(&path, &mut info.target_pathname);
            info.target_ino = md.ino() as c_ulong;
            info.i_uid = md.uid() as c_uint;
            info.err = ERR_CMD_NOT_SUPPORTED;

            susfs_ctl(&mut info, CMD_SUSFS_ADD_SUS_PATH);
            handle_result(info.err, CMD_SUSFS_ADD_SUS_PATH);
        }
        SuSFSSubCommands::AddSusPathLoop { path } => {
            let md = fetch_metadata(&path);
            let mut info = SusfsSusPath::default();
            str_to_c_array(&path, &mut info.target_pathname);
            info.target_ino = md.ino() as c_ulong;
            info.i_uid = md.uid() as c_uint;
            info.err = ERR_CMD_NOT_SUPPORTED;

            susfs_ctl(&mut info, CMD_SUSFS_ADD_SUS_PATH_LOOP);
            handle_result(info.err, CMD_SUSFS_ADD_SUS_PATH_LOOP);
        }
        SuSFSSubCommands::SetAndroidDataRootPath { path } => {
            let mut info = ExternalDir::default();
            str_to_c_array(&path, &mut info.target_pathname);
            info.cmd = CMD_SUSFS_SET_ANDROID_DATA_ROOT_PATH as c_int;
            info.err = ERR_CMD_NOT_SUPPORTED;

            susfs_ctl(&mut info, CMD_SUSFS_SET_ANDROID_DATA_ROOT_PATH);
            handle_result(info.err, CMD_SUSFS_SET_ANDROID_DATA_ROOT_PATH);
        }
        SuSFSSubCommands::SetSdcardRootPath { path } => {
            let mut info = ExternalDir::default();
            str_to_c_array(&path, &mut info.target_pathname);
            info.cmd = CMD_SUSFS_SET_SDCARD_ROOT_PATH as c_int;
            info.err = ERR_CMD_NOT_SUPPORTED;

            susfs_ctl(&mut info, CMD_SUSFS_SET_SDCARD_ROOT_PATH);
            handle_result(info.err, CMD_SUSFS_SET_SDCARD_ROOT_PATH);
        }
        SuSFSSubCommands::HideSusMntsForNonSuProcs { enabled } => {
            if enabled > 1 {
                eprintln!("Invalid value for enabled (0 or 1)");
                exit(1);
            }
            let mut info = SusfsHideSusMnts::default();
            info.enabled = enabled == 1;
            info.err = ERR_CMD_NOT_SUPPORTED;

            susfs_ctl(&mut info, CMD_SUSFS_HIDE_SUS_MNTS_FOR_NON_SU_PROCS);
            handle_result(info.err, CMD_SUSFS_HIDE_SUS_MNTS_FOR_NON_SU_PROCS);
        }
        SuSFSSubCommands::AddSusKstat { path } => {
            let md = fetch_metadata(&path);
            let mut info = SusfsSusKstat::default();
            str_to_c_array(&path, &mut info.target_pathname);
            info.is_statically = false;
            info.target_ino = md.ino() as c_ulong;
            copy_metadata_to_sus_kstat(&mut info, &md);
            info.err = ERR_CMD_NOT_SUPPORTED;

            susfs_ctl(&mut info, CMD_SUSFS_ADD_SUS_KSTAT);
            handle_result(info.err, CMD_SUSFS_ADD_SUS_KSTAT);
        }
        SuSFSSubCommands::UpdateSusKstat { path } => {
            let md = fetch_metadata(&path);
            let mut info = SusfsSusKstat::default();
            str_to_c_array(&path, &mut info.target_pathname);
            info.is_statically = false;
            info.target_ino = md.ino() as c_ulong;
            info.spoofed_size = md.size() as libc::c_longlong;
            info.spoofed_blocks = md.blocks() as libc::c_ulonglong;
            info.err = ERR_CMD_NOT_SUPPORTED;

            susfs_ctl(&mut info, CMD_SUSFS_UPDATE_SUS_KSTAT);
            handle_result(info.err, CMD_SUSFS_UPDATE_SUS_KSTAT);
        }
        SuSFSSubCommands::UpdateSusKstatFullClone { path } => {
            let md = fetch_metadata(&path);
            let mut info = SusfsSusKstat::default();
            str_to_c_array(&path, &mut info.target_pathname);
            info.is_statically = false;
            info.target_ino = md.ino() as c_ulong;
            info.err = ERR_CMD_NOT_SUPPORTED;

            susfs_ctl(&mut info, CMD_SUSFS_UPDATE_SUS_KSTAT);
            handle_result(info.err, CMD_SUSFS_UPDATE_SUS_KSTAT);
        }
        SuSFSSubCommands::SetUname { release, version } => {
            let mut info = SusfsUname::default();
            str_to_c_array(&release, &mut info.release);
            str_to_c_array(&version, &mut info.version);
            info.err = ERR_CMD_NOT_SUPPORTED;

            susfs_ctl(&mut info, CMD_SUSFS_SET_UNAME);
            handle_result(info.err, CMD_SUSFS_SET_UNAME);
        }
        SuSFSSubCommands::EnableLog { enabled } => {
            if enabled > 1 {
                eprintln!("Invalid value for enabled (0 or 1)");
                exit(1);
            }
            let mut info = SusfsLog::default();
            info.enabled = enabled == 1;
            info.err = ERR_CMD_NOT_SUPPORTED;

            susfs_ctl(&mut info, CMD_SUSFS_ENABLE_LOG);
            handle_result(info.err, CMD_SUSFS_ENABLE_LOG);
        }
        SuSFSSubCommands::SetCmdlineOrBootconfig { path } => {
            let abs_path = fs::canonicalize(&path).unwrap_or_else(|e| {
                eprintln!("realpath failed: {}", e);
                exit(e.raw_os_error().unwrap_or(1));
            });
            let content = fs::read(&abs_path).unwrap_or_else(|e| {
                eprintln!("Error opening file: {}", e);
                exit(e.raw_os_error().unwrap_or(1));
            });
            if content.len() >= SUSFS_FAKE_CMDLINE_OR_BOOTCONFIG_SIZE {
                eprintln!("file_size too long");
                exit(libc::EINVAL);
            }

            let mut info = Box::new(SusfsSpoofCmdline {
                fake_cmdline_or_bootconfig: [0; SUSFS_FAKE_CMDLINE_OR_BOOTCONFIG_SIZE],
                err: ERR_CMD_NOT_SUPPORTED,
            });

            for (i, &b) in content.iter().enumerate() {
                info.fake_cmdline_or_bootconfig[i] = b as c_char;
            }

            susfs_ctl(&mut info, CMD_SUSFS_SET_CMDLINE_OR_BOOTCONFIG);
            handle_result(info.err, CMD_SUSFS_SET_CMDLINE_OR_BOOTCONFIG);
        }
        SuSFSSubCommands::AddOpenRedirect {
            target_path,
            redirected_path,
        } => {
            let abs_target = fs::canonicalize(&target_path).expect("realpath target failed");
            let abs_redirect =
                fs::canonicalize(&redirected_path).expect("realpath redirect failed");

            let md = fetch_metadata(abs_target.to_str().unwrap());

            let mut info = SusfsOpenRedirect::default();
            str_to_c_array(abs_target.to_str().unwrap(), &mut info.target_pathname);
            str_to_c_array(
                abs_redirect.to_str().unwrap(),
                &mut info.redirected_pathname,
            );
            info.target_ino = md.ino() as c_ulong;
            info.err = ERR_CMD_NOT_SUPPORTED;

            susfs_ctl(&mut info, CMD_SUSFS_ADD_OPEN_REDIRECT);
            handle_result(info.err, CMD_SUSFS_ADD_OPEN_REDIRECT);
        }
        SuSFSSubCommands::AddSusMap { path } => {
            let mut info = SusfsSusMap::default();
            str_to_c_array(&path, &mut info.target_pathname);
            info.err = ERR_CMD_NOT_SUPPORTED;

            susfs_ctl(&mut info, CMD_SUSFS_ADD_SUS_MAP);
            handle_result(info.err, CMD_SUSFS_ADD_SUS_MAP);
        }
        SuSFSSubCommands::EnableAvcLogSpoofing { enabled } => {
            if enabled > 1 {
                exit(1);
            }
            let mut info = SusfsAvcLogSpoofing::default();
            info.enabled = enabled == 1;
            info.err = ERR_CMD_NOT_SUPPORTED;
            susfs_ctl(&mut info, CMD_SUSFS_ENABLE_AVC_LOG_SPOOFING);
            handle_result(info.err, CMD_SUSFS_ENABLE_AVC_LOG_SPOOFING);
        }
        SuSFSSubCommands::Show { info_type } => match info_type {
            ShowType::Version => {
                let mut info = SusfsVersion {
                    susfs_version: [0; SUSFS_MAX_VERSION_BUFSIZE],
                    err: ERR_CMD_NOT_SUPPORTED,
                };
                susfs_ctl(&mut info, CMD_SUSFS_SHOW_VERSION);
                handle_result(info.err, CMD_SUSFS_SHOW_VERSION);

                if info.err == 0 {
                    let len = info
                        .susfs_version
                        .iter()
                        .position(|&b| b == 0)
                        .unwrap_or(SUSFS_MAX_VERSION_BUFSIZE);
                    let bytes: Vec<u8> =
                        info.susfs_version[..len].iter().map(|&c| c as u8).collect();
                    let ver = String::from_utf8(bytes).unwrap_or_else(|_| "<invalid>".to_string());

                    if ver.starts_with('v') {
                        println!("{}", ver);
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
                handle_result(info.err, CMD_SUSFS_SHOW_ENABLED_FEATURES);

                if info.err == 0 {
                    let len = info
                        .enabled_features
                        .iter()
                        .position(|&b| b == 0)
                        .unwrap_or(SUSFS_ENABLED_FEATURES_SIZE);
                    let bytes: Vec<u8> = info.enabled_features[..len]
                        .iter()
                        .map(|&c| c as u8)
                        .collect();
                    let features =
                        String::from_utf8(bytes).unwrap_or_else(|_| "<invalid>".to_string());
                    print!("{}", features);
                }
            }
            ShowType::Variant => {
                let mut info = SusfsVariant {
                    susfs_variant: [0; SUSFS_MAX_VARIANT_BUFSIZE],
                    err: ERR_CMD_NOT_SUPPORTED,
                };
                susfs_ctl(&mut info, CMD_SUSFS_SHOW_VARIANT);
                handle_result(info.err, CMD_SUSFS_SHOW_VARIANT);

                if info.err == 0 {
                    let len = info
                        .susfs_variant
                        .iter()
                        .position(|&b| b == 0)
                        .unwrap_or(SUSFS_MAX_VARIANT_BUFSIZE);
                    let bytes: Vec<u8> =
                        info.susfs_variant[..len].iter().map(|&c| c as u8).collect();
                    let variant =
                        String::from_utf8(bytes).unwrap_or_else(|_| "<invalid>".to_string());
                    println!("{}", variant);
                }
            }
        },
        SuSFSSubCommands::AddSusKstatStatically {
            path,
            ino,
            dev,
            nlink,
            size,
            atime,
            atime_nsec,
            mtime,
            mtime_nsec,
            ctime,
            ctime_nsec,
            blocks,
            blksize,
        } => {
            let md = fetch_metadata(&path);
            let mut info = SusfsSusKstat::default();

            info.target_ino = md.ino() as c_ulong;
            info.is_statically = true;

            let s_ino = parse_or_default(&ino, md.ino());
            let s_dev = parse_or_default(&dev, md.dev());
            let s_nlink = parse_or_default(&nlink, md.nlink() as u64);
            let s_size = parse_or_default(&size, md.size());
            let s_atime = parse_or_default(&atime, md.atime());
            let s_atime_nsec = parse_or_default(&atime_nsec, md.atime_nsec());
            let s_mtime = parse_or_default(&mtime, md.mtime());
            let s_mtime_nsec = parse_or_default(&mtime_nsec, md.mtime_nsec());
            let s_ctime = parse_or_default(&ctime, md.ctime());
            let s_ctime_nsec = parse_or_default(&ctime_nsec, md.ctime_nsec());
            let s_blocks = parse_or_default(&blocks, md.blocks());
            let s_blksize = parse_or_default(&blksize, md.blksize());

            str_to_c_array(&path, &mut info.target_pathname);

            info.spoofed_ino = s_ino as c_ulong;
            info.spoofed_dev = s_dev as c_ulong;
            info.spoofed_nlink = s_nlink as c_uint;
            info.spoofed_size = s_size as libc::c_longlong;
            info.spoofed_atime_tv_sec = s_atime as c_long;
            info.spoofed_mtime_tv_sec = s_mtime as c_long;
            info.spoofed_ctime_tv_sec = s_ctime as c_long;
            info.spoofed_atime_tv_nsec = s_atime_nsec as c_long;
            info.spoofed_mtime_tv_nsec = s_mtime_nsec as c_long;
            info.spoofed_ctime_tv_nsec = s_ctime_nsec as c_long;
            info.spoofed_blksize = s_blksize as c_ulong;
            info.spoofed_blocks = s_blocks as libc::c_ulonglong;

            info.err = ERR_CMD_NOT_SUPPORTED;

            susfs_ctl(&mut info, CMD_SUSFS_ADD_SUS_KSTAT_STATICALLY);
            handle_result(info.err, CMD_SUSFS_ADD_SUS_KSTAT_STATICALLY);
        }
    }
}
