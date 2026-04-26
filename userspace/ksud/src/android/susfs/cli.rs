use anyhow::Result;
use clap::{Args, Parser, Subcommand, error::ErrorKind};

use crate::android::susfs::{api, config};

#[derive(Debug, Args)]
pub struct SusfsArgs {
    #[command(subcommand)]
    pub command: SuSFSSubCommands,
}

#[derive(Debug, Subcommand)]
#[allow(clippy::large_enum_variant)]
pub enum SuSFSSubCommands {
    ///  Added path and all its sub-paths will be hidden for umounted app process from several syscalls
    /// Please be reminded that if the target path has upper mounts then make sure the proper layer is added, otherwise it may not be effective for the target process.
    #[command(name = "add_sus_path")]
    AddSusPath {
        #[arg(help = "Path of file or directory")]
        path: String,
    },
    ///  The only difference to add_sus_path is that the added sus_path via this cli will be flagged as SUS_PATH again for the app process when it is being spawned by zygote and marked umounted
    /// Also it does not check if the path is existed or not, instead it checks for empty string only, so be careful what to add.
    #[command(name = "add_sus_path_loop")]
    AddSusPathLoop {
        #[arg(help = "Path not inside sdcard")]
        path: String,
    },

    /// Deleted path and all its sub-paths will be hidden for umounted app process from several syscalls
    /// Please be reminded that if the target path has upper mounts then make sure the proper layer is added, otherwise it may not be effective for the target process.
    #[command(name = "del_sus_path")]
    DelSusPath {
        #[arg(help = "Path of file or directory")]
        path: String,
    },
    ///  The only difference to add_sus_path is that the added sus_path via this cli will be flagged as SUS_PATH again for the app process when it is being spawned by zygote and marked umounted
    /// Also it does not check if the path is existed or not, instead it checks for empty string only, so be careful what to add.
    #[command(name = "del_sus_path_loop")]
    DelSusPathLoop {
        #[arg(help = "Path not inside sdcard")]
        path: String,
    },
    /// Add path to store original stat info in kernel memory (before bind mount/overlay)
    #[command(name = "add_sus_kstat")]
    AddSusKstat {
        /// Add the desired path BEFORE it gets bind mounted or overlayed, this is used for storing original stat info in kernel memory
        /// This command must be completed with <update_sus_kstat> later after the added path is bind mounted or overlayed
        path: String,
    },
    /// Update the target ino for a path added via add_sus_kstat
    #[command(name = "update_sus_kstat")]
    UpdateSusKstat {
        /// Add the desired path you have added before via <add_sus_kstat> to complete the kstat spoofing procedure\n");
        /// This updates the target ino, but size and blocks are remained the same as current stat
        path: String,
    },
    /// Update target ino only, other stat members remain same as original
    #[command(name = "update_sus_kstat_full_clone")]
    UpdateSusKstatFullClone {
        /// Add the desired path you have added before via <add_sus_kstat> to complete the kstat spoofing procedure
        /// This updates the target ino only, other stat members are remained the same as the original stat
        path: String,
    },
    /// Deleted path to store original stat info in kernel memory (before bind mount/overlay)
    #[command(name = "del_sus_kstat")]
    DelSusKstat {
        /// Deleted the desired path BEFORE it gets bind mounted or overlayed, this is used for storing original stat info in kernel memory
        /// This command must be completed with <update_sus_kstat> later after the added path is bind mounted or overlayed
        path: String,
    },
    /// Deleted the target ino for a path added via add_sus_kstat
    #[command(name = "del_update_sus_kstat")]
    DelUpdateSusKstat {
        /// Deleted the desired path you have added before via <add_sus_kstat> to complete the kstat spoofing procedure\n");
        /// This updates the target ino, but size and blocks are remained the same as current stat
        path: String,
    },
    /// Deleted target ino only, other stat members remain same as original
    #[command(name = "del_sus_kstat_full_clone")]
    DelSusKstatFullClone {
        /// Deleted the desired path you have added before via <add_sus_kstat> to complete the kstat spoofing procedure
        /// This updates the target ino only, other stat members are remained the same as the original stat
        path: String,
    },
    /// Spoof uname for all processes, set string to 'default' to imply the function to use original string
    #[command(name = "set_uname")]
    SetUname { release: String, version: String },
    /// Deleted Spoof uname for all processes, set string to 'default' to imply the function to use original string
    #[command(name = "del_uname")]
    DelUname,
    /// Enable/Disable susfs log in kernel
    #[command(name = "enable_log")]
    EnableLog {
        /// 0: disable susfs log in kernel
        /// 1: enable susfs log in kernel
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
    /// Added real file path which gets mmapped will be hidden from /proc/self/[maps|smaps|smaps_rollup|map_files|mem|pagemap]
    #[command(name = "add_sus_map")]
    AddSusMap { path: String },
    /// Deleted real file path which gets mmapped will be hidden from /proc/self/[maps|smaps|smaps_rollup|map_files|mem|pagemap]
    #[command(name = "del_sus_map")]
    DelSusMap { path: String },
    /// Enable/Disable spoofing sus 'su' context in avc log
    #[command(name = "enable_avc_log_spoofing")]
    EnableAvcLogSpoofing {
        /// 0: disable spoofing the sus tcontext 'su' shown in avc log in kernel
        /// 1: enable spoofing the sus tcontext 'su' with 'u:r:priv_app:s0:c512,c768' shown in avc log in kernel
        enabled: u8,
    },
    /// Show version, enabled_features, or variant
    Show {
        /// version: show the current susfs version implemented in kernel
        /// enabled_features: show the current implemented susfs features in kernel
        /// variant: show the current variant: GKI or NON-GKI
        #[command(subcommand)]
        info_type: ShowType,
    },
    /// (Advanced) Add sus kstat statically with manual or default values
    #[command(name = "add_sus_kstat_statically")]
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
    /// (Advanced) Deleted sus kstat statically with manual or default values
    #[command(name = "del_sus_kstat_statically")]
    DelSusKstatStatically {
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

#[derive(Debug, Parser)]
struct SusfsParser {
    #[command(flatten)]
    arg: SusfsArgs,
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
    run_main(parser.arg.command)
}

pub fn run_main(command: SuSFSSubCommands) -> Result<()> {
    match command {
        SuSFSSubCommands::AddSusPath { path } => {
            api::add_sus_path(&api::SusPathType::Normal, &path)?;
            config::operation::add_sus_path(&path);
        }
        SuSFSSubCommands::AddSusPathLoop { path } => {
            api::add_sus_path(&api::SusPathType::Loop, &path)?;
            config::operation::add_sus_path_loop(&path);
        }
        SuSFSSubCommands::AddSusKstat { path } => {
            api::add_sus_kstat(&path)?;
            config::operation::add_sus_kstat(&path);
        }
        SuSFSSubCommands::UpdateSusKstat { path } => {
            api::update_sus_kstat(&path)?;
            config::operation::add_sus_kstat_update(&path);
        }
        SuSFSSubCommands::UpdateSusKstatFullClone { path } => {
            config::operation::add_sus_kstat_full_clone(&path);
            api::update_sus_kstat_full_clone(&path)?;
        }
        SuSFSSubCommands::SetUname { release, version } => {
            config::operation::set_uname(&release, &version);
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
            api::add_sus_map(&path)?;
            config::operation::add_sus_map(&path);
        }
        SuSFSSubCommands::EnableAvcLogSpoofing { enabled } => {
            api::enable_avc_log_spoofing(enabled)?;
            config::operation::enable_avc_spoofing(enabled);
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
            api::add_sus_kstat_statically(
                &path,
                &ino,
                &dev,
                &nlink,
                &size,
                &atime,
                &atime_nsec,
                &mtime,
                &mtime_nsec,
                &ctime,
                &ctime_nsec,
                &blocks,
                &blksize,
            )?;
            config::operation::add_sus_kstat_statically(
                &path,
                &ino,
                &dev,
                &nlink,
                &size,
                &atime,
                &atime_nsec,
                &mtime,
                &mtime_nsec,
                &ctime,
                &ctime_nsec,
                &blocks,
                &blksize,
            );
        }
        // Del
        SuSFSSubCommands::DelSusPath { path } => {
            config::operation::del_sus_path(&path);
        }
        SuSFSSubCommands::DelSusPathLoop { path } => {
            config::operation::del_sus_path_loop(&path);
        }
        SuSFSSubCommands::DelSusKstat { path } => {
            config::operation::del_sus_kstat(&path);
        }
        SuSFSSubCommands::DelUpdateSusKstat { path } => {
            config::operation::del_sus_kstat_update(&path);
        }
        SuSFSSubCommands::DelSusKstatFullClone { path } => {
            config::operation::del_sus_kstat_full_clone(&path);
        }
        SuSFSSubCommands::DelUname => {
            config::operation::del_uname();
        }
        SuSFSSubCommands::DelSusMap { path } => {
            config::operation::del_sus_map(&path);
        }
        SuSFSSubCommands::DelSusKstatStatically {
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
            config::operation::del_sus_kstat_statically(
                &path,
                &ino,
                &dev,
                &nlink,
                &size,
                &atime,
                &atime_nsec,
                &mtime,
                &mtime_nsec,
                &ctime,
                &ctime_nsec,
                &blocks,
                &blksize,
            );
        }
    }

    Ok(())
}
