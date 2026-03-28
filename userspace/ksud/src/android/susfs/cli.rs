use anyhow::Result;
use clap::{Args, Parser, Subcommand};

use crate::android::susfs::api::{self};

#[derive(Debug, Args)]
pub struct SusfsArgs {
    #[command(subcommand)]
    pub command: SuSFSSubCommands,
}

#[derive(Debug, Subcommand)]
pub enum SuSFSSubCommands {
    /// Added path and all its sub-paths will be hidden from several syscalls
    #[command(name = "add_sus_path")]
    AddSusPath {
        #[arg(help = "Path of file or directory")]
        path: String,
    },
    /// Similar to add_sus_path but flagged as SUS_PATH per zygote spawned process (not for sdcard)
    #[command(name = "add_sus_path_loop")]
    AddSusPathLoop {
        #[arg(help = "Path not inside sdcard")]
        path: String,
    },
    /// Add path to store original stat info in kernel memory (before bind mount/overlay)
    #[command(name = "add_sus_kstat")]
    AddSusKstat { path: String },
    /// Update the target ino for a path added via add_sus_kstat
    #[command(name = "update_sus_kstat")]
    UpdateSusKstat { path: String },
    /// Update target ino only, other stat members remain same as original
    #[command(name = "update_sus_kstat_full_clone")]
    UpdateSusKstatFullClone { path: String },
    /// Spoof uname release and version
    #[command(name = "set_uname")]
    SetUname { release: String, version: String },
    /// Enable/Disable susfs log in kernel
    #[command(name = "enable_log")]
    EnableLog {
        #[arg(help = "0: disable, 1: enable")]
        enabled: u8,
    },
    /// Spoof /proc/cmdline or /proc/bootconfig
    #[command(name = "set_cmdline_or_bootconfig")]
    SetCmdlineOrBootconfig { path: String },
    /// Redirect target path to be opened with user defined path
    #[command(name = "add_open_redirect")]
    AddOpenRedirect {
        target_path: String,
        redirected_path: String,
        ///0: Effective for non-app processes (uid < 10000
        ///1: Effective for non-su processes of which uid is 0 (All root process but not with su domain)
        ///2: Effective for non-su processes (Use it carefully!)
        ///3: Effective for processes that are marked umounted with uid >= 10000 (Use it carefully!)
        ///4: Effective for processes that are marked umounted (include most of the init spawned process, use it carefully!)
        uid_scheme: u64,
    },
    /// Hidden from /proc/self/maps etc.
    #[command(name = "add_sus_map")]
    AddSusMap { path: String },
    /// Enable/Disable spoofing sus 'su' context in avc log
    #[command(name = "enable_avc_log_spoofing")]
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
    #[command(name = "add_sus_kstat_statically")]
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

#[derive(Debug, Parser)]
struct SusfsParser {
    #[command(flatten)]
    arg: SusfsArgs,
}

pub fn run_from_args(args: &[String]) -> Result<()> {
    let parser = SusfsParser::try_parse_from(args)?;
    run_main(parser.arg.command)
}

pub fn run_main(command: SuSFSSubCommands) -> Result<()> {
    //let command = SuSFSSubCommands::try_parse_from(args)?;
    match command {
        SuSFSSubCommands::AddSusPath { path } => {
            api::add_sus_path(&api::SusPathType::Normal, &path)?;
        }
        SuSFSSubCommands::AddSusPathLoop { path } => {
            api::add_sus_path(&api::SusPathType::Loop, &path)?;
        }
        SuSFSSubCommands::AddSusKstat { path } => {
            api::add_sus_kstat(path)?;
        }
        SuSFSSubCommands::UpdateSusKstat { path } => {
            api::update_sus_kstat(path)?;
        }
        SuSFSSubCommands::UpdateSusKstatFullClone { path } => {
            api::update_sus_kstat_full_clone(path)?;
        }
        SuSFSSubCommands::SetUname { release, version } => {
            api::set_uname(&release, &version)?;
        }
        SuSFSSubCommands::EnableLog { enabled } => {
            api::enable_log(enabled)?;
        }
        SuSFSSubCommands::SetCmdlineOrBootconfig { path } => {
            api::set_cmdline_or_bootconfig(path)?;
        }
        SuSFSSubCommands::AddOpenRedirect {
            target_path,
            redirected_path,
            uid_scheme,
        } => {
            api::add_open_redirect(target_path, redirected_path, uid_scheme)?;
        }
        SuSFSSubCommands::AddSusMap { path } => {
            api::add_sus_map(path)?;
        }
        SuSFSSubCommands::EnableAvcLogSpoofing { enabled } => {
            api::enable_avc_log_spoofing(enabled)?;
        }
        SuSFSSubCommands::Show { info_type } => match info_type {
            ShowType::Version => {
                api::show_version()?;
            }
            ShowType::EnabledFeatures => {
                api::show_features()?;
            }
            ShowType::Variant => {
                api::show_variant()?;
            }
        },
        SuSFSSubCommands::AddSusKstatStatically(args) => {
            api::add_sus_kstat_statically(
                &args.path,
                &args.ino,
                &args.dev,
                &args.nlink,
                &args.size,
                &args.atime,
                &args.atime_nsec,
                &args.mtime,
                &args.mtime_nsec,
                &args.ctime,
                &args.ctime_nsec,
                &args.blocks,
                &args.blksize,
            )?;
        }
    }

    Ok(())
}
