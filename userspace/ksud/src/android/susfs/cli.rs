#![allow(clippy::similar_names)]

use std::{fs, os::unix::fs::MetadataExt};

use anyhow::Result;
use clap::{Args, Subcommand};

use crate::android::susfs::{
    api::{
        self, SusfsEnabledFeatures, SusfsOpenRedirect, SusfsSpoofCmdline, SusfsSusKstat,
        SusfsSusMap, SusfsUname, SusfsVariant, SusfsVersion,
    },
    magic::{
        CMD_SUSFS_ADD_OPEN_REDIRECT, CMD_SUSFS_ADD_SUS_KSTAT_STATICALLY, CMD_SUSFS_ADD_SUS_MAP,
        CMD_SUSFS_SET_CMDLINE_OR_BOOTCONFIG, CMD_SUSFS_SET_UNAME, CMD_SUSFS_SHOW_ENABLED_FEATURES,
        CMD_SUSFS_SHOW_VARIANT, CMD_SUSFS_SHOW_VERSION, ERR_CMD_NOT_SUPPORTED,
        SUSFS_ENABLED_FEATURES_SIZE, SUSFS_FAKE_CMDLINE_OR_BOOTCONFIG_SIZE,
        SUSFS_MAX_VARIANT_BUFSIZE, SUSFS_MAX_VERSION_BUFSIZE,
    },
    utils::{fetch_metadata, handle_result, parse_or_default, str_to_c_array, susfs_ctl},
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

pub fn susfs_cli(sub_commmand: SuSFSSubCommands) -> Result<()> {
    match sub_commmand {
        SuSFSSubCommands::AddSusPath { path } => {
            api::add_sus_path(&api::SusPathType::Normal, &path)?;
        }
        SuSFSSubCommands::AddSusPathLoop { path } => {
            api::add_sus_path(&api::SusPathType::Loop, &path)?;
        }
        SuSFSSubCommands::SetAndroidDataRootPath { path } => {
            api::set_external_dir(&api::ExternalDirType::AndroidData, &path)?;
        }
        SuSFSSubCommands::SetSdcardRootPath { path } => {
            api::set_external_dir(&api::ExternalDirType::Sdcard, &path)?;
        }
        SuSFSSubCommands::HideSusMntsForNonSuProcs { enabled } => {
            api::bool_enable(&api::BoolEnable::HideSusMntsForNonSuProcs, enabled)?;
        }
        SuSFSSubCommands::AddSusKstat { path } => {
            api::set_kstat(&api::SusKstatType::Add, &path)?;
        }
        SuSFSSubCommands::UpdateSusKstat { path } => {
            api::set_kstat(&api::SusKstatType::Update, &path)?;
        }
        SuSFSSubCommands::UpdateSusKstatFullClone { path } => {
            api::set_kstat(&api::SusKstatType::FullClone, &path)?;
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
            api::bool_enable(&api::BoolEnable::Log, enabled)?;
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
            api::bool_enable(&api::BoolEnable::AvcLogSpoofing, enabled)?;
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
