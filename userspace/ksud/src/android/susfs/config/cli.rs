use std::path::PathBuf;

use anyhow::{Context, Result, bail};
use clap::{Subcommand, ValueEnum};
use num_enum::TryFromPrimitive;
use serde::Serialize;

use crate::android::susfs::{config::model::Config, enums::UidScheme};

#[derive(Debug, Subcommand)]
pub enum ConfigCommand {
    /// Enable applying the persisted SUSFS configuration during boot.
    Enable,
    /// Disable applying the persisted SUSFS configuration during boot.
    Disable,
    /// Print the complete persisted configuration as JSON.
    #[command(name = "list_all")]
    ListAll,
    /// Print the complete persisted configuration as backup JSON.
    Backup,
    /// Restore a backup, or restore defaults when no path is supplied.
    Restore {
        /// Path to a JSON backup. Omit to restore the default configuration.
        path: Option<PathBuf>,
    },
    #[command(name = "cmdline_or_bootconfig")]
    CmdlineOrBootconfig {
        #[command(subcommand)]
        command: StringConfigCommand,
    },
    #[command(name = "avc_log_spoofing")]
    AvcLogSpoofing {
        #[command(subcommand)]
        command: ToggleConfigCommand,
    },
    Logging {
        #[command(subcommand)]
        command: ToggleConfigCommand,
    },
    #[command(name = "hide_sus_mnts_for_non_su_procs")]
    HideSusMntsForNonSuProcs {
        #[command(subcommand)]
        command: ToggleConfigCommand,
    },
    Uname {
        #[command(subcommand)]
        command: UnameConfigCommand,
    },
    #[command(name = "sus_path")]
    SusPath {
        #[command(subcommand)]
        command: SusPathConfigCommand,
    },
    #[command(name = "sus_kstat")]
    SusKstat {
        #[command(subcommand)]
        command: SusKstatConfigCommand,
    },
    #[command(name = "open_redirect")]
    OpenRedirect {
        #[command(subcommand)]
        command: OpenRedirectConfigCommand,
    },
    #[command(name = "sus_map")]
    SusMap {
        #[command(subcommand)]
        command: PathConfigCommand,
    },
}

#[derive(Debug, Subcommand)]
pub enum ToggleConfigCommand {
    Add,
    Remove,
    List,
}

#[derive(Debug, Subcommand)]
pub enum StringConfigCommand {
    Add { value: String },
    Remove,
    List,
}

#[derive(Debug, Subcommand)]
pub enum UnameConfigCommand {
    Add { release: String, version: String },
    Remove,
    List,
}

#[derive(Debug, Subcommand)]
pub enum SusPathConfigCommand {
    Add {
        path: String,
        /// Reapply the SUS path for each newly spawned unmounted app process.
        #[arg(long = "loop")]
        is_loop: bool,
    },
    Remove {
        path: String,
    },
    List,
}

#[derive(Copy, Clone, Debug, ValueEnum)]
pub enum SusKstatConfigType {
    #[value(name = "normal")]
    Normal,
    #[value(name = "full_clone")]
    FullClone,
    #[value(name = "statically")]
    Statically,
}

#[derive(Debug, Subcommand)]
pub enum SusKstatConfigCommand {
    Add {
        path: String,
        spoof_type: SusKstatConfigType,
        /// Static values in ino/dev/nlink/size/atime/atime_nsec/mtime/mtime_nsec/ctime/ctime_nsec/blocks/blksize order.
        /// Use "default" or omit trailing values to preserve the original field.
        #[arg(allow_hyphen_values = true)]
        values: Vec<String>,
    },
    Remove {
        path: String,
    },
    List,
}

#[derive(Debug, Subcommand)]
pub enum OpenRedirectConfigCommand {
    Add {
        target_path: String,
        redirected_path: String,
        uid_scheme: i32,
    },
    Remove {
        target_path: String,
    },
    List,
}

#[derive(Debug, Subcommand)]
pub enum PathConfigCommand {
    Add { path: String },
    Remove { path: String },
    List,
}

enum BooleanField {
    AvcLogSpoofing,
    Logging,
    HideSusMntsForNonSuProcs,
}

pub fn run(command: ConfigCommand) -> Result<()> {
    match command {
        ConfigCommand::Enable => update_config(|config| {
            config.set_enabled(true);
            Ok(())
        }),
        ConfigCommand::Disable => update_config(|config| {
            config.set_enabled(false);
            Ok(())
        }),
        ConfigCommand::ListAll | ConfigCommand::Backup => print_json(&Config::read_or_default()),
        ConfigCommand::Restore { path } => {
            let config = match path {
                Some(path) => Config::read_from(path)?,
                None => Config::default(),
            };
            config.save()
        }
        ConfigCommand::CmdlineOrBootconfig { command } => run_string(command),
        ConfigCommand::AvcLogSpoofing { command } => {
            run_boolean(BooleanField::AvcLogSpoofing, command)
        }
        ConfigCommand::Logging { command } => run_boolean(BooleanField::Logging, command),
        ConfigCommand::HideSusMntsForNonSuProcs { command } => {
            run_boolean(BooleanField::HideSusMntsForNonSuProcs, command)
        }
        ConfigCommand::Uname { command } => run_uname(command),
        ConfigCommand::SusPath { command } => run_sus_path(command),
        ConfigCommand::SusKstat { command } => run_sus_kstat(command),
        ConfigCommand::OpenRedirect { command } => run_open_redirect(command),
        ConfigCommand::SusMap { command } => run_path(command),
    }
}

fn update_config(action: impl FnOnce(&mut Config) -> Result<()>) -> Result<()> {
    let mut config = Config::read_or_default();
    action(&mut config)?;
    config.save()
}

fn print_json(value: &(impl Serialize + ?Sized)) -> Result<()> {
    println!("{}", serde_json::to_string_pretty(value)?);
    Ok(())
}

fn run_boolean(field: BooleanField, command: ToggleConfigCommand) -> Result<()> {
    if matches!(command, ToggleConfigCommand::List) {
        let config = Config::read_or_default();
        let value = match field {
            BooleanField::AvcLogSpoofing => config.avc_log_spoofing,
            BooleanField::Logging => config.logging,
            BooleanField::HideSusMntsForNonSuProcs => config.hide_sus_mnts_for_non_su_procs,
        };
        return print_json(&value);
    }

    let enabled = matches!(command, ToggleConfigCommand::Add);
    update_config(|config| {
        match field {
            BooleanField::AvcLogSpoofing => {
                config.set_avc_log_spoofing(enabled);
            }
            BooleanField::Logging => {
                config.set_logging(enabled);
            }
            BooleanField::HideSusMntsForNonSuProcs => {
                config.set_hide_sus_mnts_for_non_su_procs(enabled);
            }
        }
        Ok(())
    })
}

fn run_string(command: StringConfigCommand) -> Result<()> {
    match command {
        StringConfigCommand::Add { value } => {
            update_config(|config| config.set_cmdline_or_bootconfig(&value).map(|_| ()))
        }
        StringConfigCommand::Remove => {
            update_config(|config| config.set_cmdline_or_bootconfig("").map(|_| ()))
        }
        StringConfigCommand::List => print_json(&Config::read_or_default().cmdline_or_bootconfig),
    }
}

fn run_uname(command: UnameConfigCommand) -> Result<()> {
    match command {
        UnameConfigCommand::Add { release, version } => {
            update_config(|config| config.set_uname(&release, &version).map(|_| ()))
        }
        UnameConfigCommand::Remove => {
            update_config(|config| config.set_uname("default", "default").map(|_| ()))
        }
        UnameConfigCommand::List => print_json(&Config::read_or_default().uname),
    }
}

fn run_sus_path(command: SusPathConfigCommand) -> Result<()> {
    match command {
        SusPathConfigCommand::Add { path, is_loop } => {
            update_config(|config| config.add_sus_path(&path, is_loop).map(|_| ()))
        }
        SusPathConfigCommand::Remove { path } => update_config(|config| {
            config.remove_sus_path(&path);
            Ok(())
        }),
        SusPathConfigCommand::List => print_json(&Config::read_or_default().sus_path),
    }
}

fn run_sus_kstat(command: SusKstatConfigCommand) -> Result<()> {
    match command {
        SusKstatConfigCommand::Add {
            path,
            spoof_type,
            values,
        } => update_config(|config| match spoof_type {
            SusKstatConfigType::Normal => {
                ensure_no_static_values(&values, spoof_type)?;
                config.add_sus_kstat(&path, false).map(|_| ())
            }
            SusKstatConfigType::FullClone => {
                ensure_no_static_values(&values, spoof_type)?;
                config.add_sus_kstat(&path, true).map(|_| ())
            }
            SusKstatConfigType::Statically => {
                if values.len() > 12 {
                    bail!("sus_kstat statically accepts at most 12 field values");
                }

                let mut parsed = values
                    .iter()
                    .map(|value| parse_static_value(value))
                    .collect::<Result<Vec<_>>>()?;
                parsed.resize(12, None);

                config
                    .add_sus_kstat_statically(
                        &path, parsed[0], parsed[1], parsed[2], parsed[3], parsed[4], parsed[5],
                        parsed[6], parsed[7], parsed[8], parsed[9], parsed[10], parsed[11],
                    )
                    .map(|_| ())
            }
        }),
        SusKstatConfigCommand::Remove { path } => update_config(|config| {
            config.remove_sus_kstat(&path);
            Ok(())
        }),
        SusKstatConfigCommand::List => print_json(&Config::read_or_default().sus_kstat),
    }
}

fn ensure_no_static_values(values: &[String], spoof_type: SusKstatConfigType) -> Result<()> {
    if values.is_empty() {
        Ok(())
    } else {
        bail!("{spoof_type:?} sus_kstat entries do not accept static field values")
    }
}

fn parse_static_value(value: &str) -> Result<Option<i64>> {
    if value == "default" {
        Ok(None)
    } else {
        value
            .parse::<i64>()
            .map(Some)
            .with_context(|| format!("invalid sus_kstat static value: {value}"))
    }
}

fn run_open_redirect(command: OpenRedirectConfigCommand) -> Result<()> {
    match command {
        OpenRedirectConfigCommand::Add {
            target_path,
            redirected_path,
            uid_scheme,
        } => {
            let uid_scheme = UidScheme::try_from_primitive(uid_scheme)?;
            update_config(|config| {
                config
                    .add_open_redirect(&target_path, &redirected_path, &uid_scheme)
                    .map(|_| ())
            })
        }
        OpenRedirectConfigCommand::Remove { target_path } => update_config(|config| {
            config.remove_open_redirect(&target_path);
            Ok(())
        }),
        OpenRedirectConfigCommand::List => print_json(&Config::read_or_default().open_redirect),
    }
}

fn run_path(command: PathConfigCommand) -> Result<()> {
    match command {
        PathConfigCommand::Add { path } => {
            update_config(|config| config.add_sus_map(&path).map(|_| ()))
        }
        PathConfigCommand::Remove { path } => update_config(|config| {
            config.remove_sus_map(&path);
            Ok(())
        }),
        PathConfigCommand::List => print_json(&Config::read_or_default().sus_map),
    }
}
