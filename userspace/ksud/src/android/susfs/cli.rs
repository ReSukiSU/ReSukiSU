// TODO: Add get_x commands

use std::{fmt::Display, str::FromStr};

use anyhow::Result;
use clap::{ArgAction, Args, Parser, Subcommand, error::ErrorKind};

use crate::android::susfs::{api::prelude as api, config::model::Config, slot_info};

#[derive(Debug, Args)]
pub struct SusfsArgs {
    #[command(subcommand)]
    pub command: SuSFSSubCommands,

    #[command(flatten)]
    pub behaviour: Behaviours,
}

#[derive(Args, Debug)]
#[group(multiple = false)]
pub struct Behaviours {
    /// Apply until next boot.
    #[arg(long)]
    pub api_only: bool,

    /// Apply after next boot.
    #[arg(long)]
    pub config_only: bool,
}

#[derive(Debug, Subcommand)]
#[allow(clippy::large_enum_variant)]
pub enum SuSFSSubCommands {
    /// Added path and all its sub-paths will be hidden for umounted app process from several syscalls.
    ///
    /// Please be reminded that if the target path has upper mounts then make sure the proper layer is added,
    /// otherwise it may not be effective for the target process.
    ///
    /// * Important Notes *
    /// - Only effective for umounted process with uid >= 10000.
    #[command(name = "add_sus_path")]
    AddSusPath {
        /// Path of file or directory
        path: String,
    },

    /// Added path and all its sub-paths will be hidden for umounted app process from several syscalls.
    ///
    /// Please be reminded that if the target path has upper mounts then make sure the proper layer is added,
    /// otherwise it may not be effective for the target process.
    ///
    /// The only difference to add_sus_path is that the added sus_path via this cli will be flagged as SUS_PATH
    /// again for the app process when it is being spawned by zygote and marked umounted.
    ///
    /// * Important Notes *
    /// - Only effective for umounted process with uid >= 10000.
    #[command(name = "add_sus_path_loop")]
    AddSusPathLoop {
        /// Path of file or directory
        path: String,
    },

    /// Remove sus_path or sus_path_loop previously added from auto-startup.
    #[command(name = "del_sus_path")]
    DelSusPath {
        // Path of file or directory
        path: String,
    },

    /// Hide SUS mounts for non-SU processes.
    ///
    /// * Important Notes *
    /// - It is set to 0 in kernel by default.
    /// - For ReZygisk without TreatWheel module, it is recommended to set to 1 in post-fs-data.sh to prevent
    ///   zygote from caching the sus mounts in memory, and revert to 0 in boot-completed.sh stage, or keep it enabled
    ///   if you want to keep them hidden from /proc/self/[mounts|mountinfo|mountstat] for non-su processes.
    #[command(name = "hide_sus_mnts_for_non_su_procs")]
    HideSusMntsForNonSuProcs {
        /// 0: DO NOT hide sus mounts for non-su processes
        /// 1: hide all sus mounts for non-su processes
        #[arg(action = ArgAction::Set, value_parser = treat_int_as_boolean)]
        enabled: bool,
    },

    /// Add the desired path BEFORE it gets bind mounted or overlayed, this is used for storing original stat info in kernel
    /// memory.
    ///
    /// This command must be completed with <update_sus_kstat> later after the added path is bind mounted or overlayed.
    ///
    /// * Important Notes *
    /// - Only effective for umounted process with uid >= 10000.
    #[command(name = "add_sus_kstat")]
    AddSusKstat {
        /// Path of file or directory
        path: String,
    },

    /// Add the desired path you have added before via <add_sus_kstat> to complete the kstat spoofing procedure.
    ///
    /// This updates the target ino, but size and blocks are remained the same as current stat.
    ///
    /// * Important Notes *
    /// - Only effective for umounted process with uid >= 10000.
    #[command(name = "update_sus_kstat")]
    UpdateSusKstat {
        /// Path of file or directory
        path: String,
    },

    /// Add the desired path you have added before via <add_sus_kstat> to complete the kstat spoofing procedure.
    ///
    /// This updates the target ino only, other stat members are remained the same as the original stat.
    ///
    /// * Important Notes *
    /// - Only effective for umounted process with uid >= 10000.
    #[command(name = "update_sus_kstat_full_clone")]
    UpdateSusKstatFullClone {
        /// Path of file or directory
        path: String,
    },

    /// Spoof the kstat of a file or directory by static fields.
    ///
    /// * Important Notes *
    /// - Only effective for umounted process with uid >= 10000.
    #[command(name = "add_sus_kstat_statically")]
    AddSusKstatStatically {
        /// Path of file or directory
        path: String,
        #[arg(default_value = "default", value_parser = treat_default_as_none::<i64>)]
        ino: std::option::Option<i64>, // do not change `std::option::Option` to `Option`, it's in long form because need to bypass clap magic.
        #[arg(default_value = "default", value_parser = treat_default_as_none::<i64>)]
        dev: std::option::Option<i64>,
        #[arg(default_value = "default", value_parser = treat_default_as_none::<i64>)]
        nlink: std::option::Option<i64>,
        #[arg(default_value = "default", value_parser = treat_default_as_none::<i64>)]
        size: std::option::Option<i64>,
        #[arg(default_value = "default", value_parser = treat_default_as_none::<i64>)]
        atime: std::option::Option<i64>,
        #[arg(default_value = "default", value_parser = treat_default_as_none::<i64>)]
        atime_nsec: std::option::Option<i64>,
        #[arg(default_value = "default", value_parser = treat_default_as_none::<i64>)]
        mtime: std::option::Option<i64>,
        #[arg(default_value = "default", value_parser = treat_default_as_none::<i64>)]
        mtime_nsec: std::option::Option<i64>,
        #[arg(default_value = "default", value_parser = treat_default_as_none::<i64>)]
        ctime: std::option::Option<i64>,
        #[arg(default_value = "default", value_parser = treat_default_as_none::<i64>)]
        ctime_nsec: std::option::Option<i64>,
        #[arg(default_value = "default", value_parser = treat_default_as_none::<i64>)]
        blocks: std::option::Option<i64>,
        #[arg(default_value = "default", value_parser = treat_default_as_none::<i64>)]
        blksize: std::option::Option<i64>,
    },

    /// Remove sus_kstat or sus_kstat_statically previously added to be auto-applied when boot-up.
    #[command(name = "del_sus_kstat")]
    DelSusKstat {
        /// Path of file or directory
        path: String,
    },

    /// Spoof uname for all processes.
    #[command(name = "set_uname")]
    SetUname { version: String, release: String },

    /// SUSFS log in kernel.
    #[command(name = "enable_log")]
    EnableLog {
        /// 0: Disable
        /// 1: Enable
        #[arg(action = ArgAction::Set, value_parser = treat_int_as_boolean)]
        enabled: bool,
    },

    /// Spoof the output of /proc/cmdline (non-gki) or /proc/bootconfig (gki) from a text file.
    #[command(name = "set_cmdline_or_bootconfig")]
    SetCmdlineOrBootconfig {
        /// Path to fake cmdline file or fake bootconfig file
        path: String,
    },

    /// Redirect the target path to be opened with user defined path and pre-defined uid scheme.
    ///
    /// * Important Notes *
    /// - Both target_pathname and redirected_pathname must be existed before they can be added to open_redirect.
    /// - Users have to take care of the selinux permission of both target_pathname and redirected_pathname by themselves.
    /// - Only effective for current process that matches the pre-defined uid scheme.
    #[command(name = "add_open_redirect")]
    AddOpenRedirect {
        target_path: String,
        redirected_path: String,
        /// 0: Effective for non-app processes (uid < 10000)
        /// 1: Effective for non-su processes of which uid is 0 (All root process but not with su domain)
        /// 2: Effective for non-su processes (Use it carefully!)
        /// 3: Effective for processes that are marked umounted with uid >= 10000 (Use it carefully!)
        /// 4: Effective for processes that are marked umounted (include most of the init spawned process, use it carefully!)
        uid_scheme: i32,
    },

    /// Remove a previously added open redirect entry.
    #[command(name = "del_open_redirect")]
    DelOpenRedirect { target_path: String },

    /// Added real file path which gets mmapped will be hidden from /proc/self/[maps|smaps|smaps_rollup|map_files|mem|pagemap].
    ///
    /// * Important Notes *
    /// - It does NOT support hiding for anon memory.
    /// - It does NOT hide any inline hooks or plt hooks cause by the injected library itself.
    /// - It may not be able to evade detections by apps that implement a good injection detection.
    /// - Only effective for umounted process with uid >= 10000.
    #[command(name = "add_sus_map")]
    AddSusMap {
        /// Path to actual library
        path: String,
    },

    /// Remove a previously added sus_map entry.
    #[command(name = "del_sus_map")]
    DelSusMap {
        /// Path to actual library
        path: String,
    },

    /// Spoofing the sus tcontext 'su' shown in avc log in kernel.
    ///
    /// * Important Notes *
    /// - It is set to '0' by default in kernel.
    /// - Enabling this may sometimes make developers hard to identify the cause when they are debugging with
    ///   some permission or selinux issues, so users are advised to disable this when doing so.
    #[command(name = "enable_avc_log_spoofing")]
    EnableAvcLogSpoofing {
        /// 0: Disable
        /// 1: Enable
        #[arg(action = ArgAction::Set, value_parser = treat_int_as_boolean)]
        enabled: bool,
    },

    /// Show version, enabled_features, or variant info.
    Show {
        #[command(subcommand)]
        info_type: ShowType,
    },

    /// Read boot slot kernel uname and build-time
    #[command(name = "slot_info")]
    SlotInfo {
        /// Path to boot image (optional)
        /// Defaults to /dev/block/by-name/[boot_a|boot_b|boot]
        boot_image: Option<String>,

        /// Enable verbose debug logging
        #[arg(short, long)]
        verbose: bool,
    },
}

#[derive(Subcommand, Debug)]
pub enum ShowType {
    Version,
    #[command(name = "enabled_features")]
    EnabledFeatures,
    Variant,
}

#[derive(Debug, Parser)]
struct SusfsParser {
    #[command(flatten)]
    arg: SusfsArgs,
}

fn treat_default_as_none<T>(value: &str) -> Result<Option<T>, String>
where
    T: FromStr,
    T::Err: Display,
{
    if value == "default" {
        Ok(None)
    } else {
        value.parse().map(Some).map_err(|e| format!("{e}"))
    }
}

fn treat_int_as_boolean(s: &str) -> Result<bool, String> {
    match s {
        "0" => Ok(false),
        "1" => Ok(true),
        _ => Err(format!("{} is not a valid boolean value", s)),
    }
}

pub fn run_from_args(args: &[String]) -> Result<()> {
    let parser = match SusfsParser::try_parse_from(args) {
        Ok(cli) => cli,
        Err(e) => {
            if matches!(
                e.kind(),
                ErrorKind::DisplayHelpOnMissingArgumentOrSubcommand
                    | ErrorKind::DisplayVersion
                    | ErrorKind::DisplayHelp
            ) {
                e.print()?;
                return Ok(());
            }
            return Err(anyhow::anyhow!("{e}"));
        }
    };
    run_main(parser.arg)
}

pub fn run_main(args: SusfsArgs) -> Result<()> {
    let mut config = Config::read_or_default();
    let do_config = !args.behaviour.api_only;
    let do_api = !args.behaviour.config_only;

    match args.command {
        SuSFSSubCommands::AddSusPath { path } => {
            if do_api {
                api::add_sus_path(&path, false)?;
            }
            if do_config {
                config.add_sus_path(&path, false)?;
            }
        }
        SuSFSSubCommands::AddSusPathLoop { path } => {
            if do_api {
                api::add_sus_path(&path, true)?;
            }
            if do_config {
                config.add_sus_path(&path, true)?;
            }
        }
        SuSFSSubCommands::DelSusPath { path } => {
            if do_config {
                config.del_sus_path(&path);
            }
        }
        SuSFSSubCommands::AddSusKstat { path } => {
            if do_api {
                api::add_sus_kstat(&path)?;
            }
        }
        SuSFSSubCommands::UpdateSusKstat { path } => {
            if do_api {
                api::update_sus_kstat(&path, false)?;
            }
            if do_config {
                config.add_sus_kstat(&path, false)?;
            }
        }
        SuSFSSubCommands::UpdateSusKstatFullClone { path } => {
            if do_api {
                api::update_sus_kstat(&path, true)?;
            }
            if do_config {
                config.add_sus_kstat(&path, true)?;
            }
        }
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
            if do_api {
                api::add_sus_kstat_statically(
                    &path, ino, dev, nlink, size, atime, atime_nsec, mtime, mtime_nsec, ctime,
                    ctime_nsec, blocks, blksize,
                )?;
            }
            if do_config {
                config.add_sus_kstat_statically(
                    &path, ino, dev, nlink, size, atime, atime_nsec, mtime, mtime_nsec, ctime,
                    ctime_nsec, blocks, blksize,
                )?;
            }
        }
        SuSFSSubCommands::DelSusKstat { path } => {
            if do_config {
                config.del_sus_kstat(&path);
            }
        }
        SuSFSSubCommands::SetUname { version, release } => {
            if do_api {
                api::set_uname(&version, &release)?;
            }
            if do_config {
                config.set_uname(&version, &release)?;
            }
        }
        SuSFSSubCommands::HideSusMntsForNonSuProcs { enabled } => {
            if do_api {
                api::hide_sus_mnts_for_non_su_procs(enabled)?;
            }
            if do_config {
                config.set_hide_sus_mnts_for_non_su_procs(enabled);
            }
        }
        SuSFSSubCommands::EnableLog { enabled } => {
            if do_api {
                api::enable_log(enabled)?;
            }
            if do_config {
                config.set_logging(enabled);
            }
        }
        SuSFSSubCommands::SetCmdlineOrBootconfig { path } => {
            if do_api {
                api::set_cmdline_or_bootconfig(&path)?;
            }
            if do_config {
                config.set_cmdline_or_bootconfig(&path)?;
            }
        }
        SuSFSSubCommands::AddOpenRedirect {
            target_path,
            redirected_path,
            uid_scheme,
        } => {
            if do_api {
                api::add_open_redirect(&target_path, &redirected_path, uid_scheme)?;
            }
            if do_config {
                config.add_open_redirect(&target_path, &redirected_path, uid_scheme)?;
            }
        }
        SuSFSSubCommands::DelOpenRedirect { target_path } => {
            if do_config {
                config.del_open_redirect(&target_path);
            }
        }
        SuSFSSubCommands::AddSusMap { path } => {
            if do_api {
                api::add_sus_map(&path)?;
            }
            if do_config {
                config.add_sus_map(&path)?;
            }
        }
        SuSFSSubCommands::DelSusMap { path } => {
            if do_config {
                config.del_sus_map(&path);
            }
        }
        SuSFSSubCommands::EnableAvcLogSpoofing { enabled } => {
            if do_api {
                api::enable_avc_log_spoofing(enabled)?;
            }
            if do_config {
                config.set_avc_log_spoofing(enabled);
            }
        }
        SuSFSSubCommands::Show { info_type } => match info_type {
            ShowType::Version => {
                let version = api::version()?;
                println!("{version}");
            }
            ShowType::EnabledFeatures => {
                let features = api::enabled_features()?;
                println!("{features}");
            }
            ShowType::Variant => {
                let variant = api::variant()?;
                println!("{variant}");
            }
        },
        SuSFSSubCommands::SlotInfo {
            boot_image,
            verbose,
        } => {
            if let Some(path) = boot_image {
                slot_info::analyze_boot_image(&path, verbose)?;
            } else {
                slot_info::show_slot_info_json(verbose)?;
            }
        }
    }

    Ok(())
}
