use android_logger::Config;
use anyhow::{Context, Ok, Result};
use clap::Parser;
use log::LevelFilter;

#[cfg(all(target_arch = "aarch64", target_os = "android"))]
use crate::android::susfs;
use crate::{
    android::{
        debug, dynamic_manager, feature, init_event, ksucalls,
        module::{self, module_config},
        profile, sepolicy, su, umount_config, utils,
    },
    apk_sign, assets,
    boot_patch::{BootPatchArgs, BootRestoreArgs},
    defs,
};

/// KernelSU userspace cli
#[derive(Parser, Debug)]
#[command(author, version = defs::VERSION_NAME, about, long_about = None)]
struct Args {
    #[command(subcommand)]
    command: Commands,
}

#[derive(clap::Subcommand, Debug)]
enum Commands {
    /// Manage KernelSU modules
    Module {
        #[command(subcommand)]
        command: Module,
    },

    /// Trigger `post-fs-data` event
    PostFsData,

    /// Trigger `service` event
    Services,

    /// Trigger `boot-complete` event
    BootCompleted,

    #[cfg(all(target_arch = "aarch64", target_os = "android"))]
    /// Manage susfs component
    Susfs {
        #[command(subcommand)]
        command: Susfs,
    },

    /// Manage auto apply user custom umount configs
    UmountConfig {
        #[command(subcommand)]
        command: UmountConfigOp,
    },

    /// Install KernelSU userspace component to system
    Install,

    /// Uninstall KernelSU modules and itself(LKM Only)
    Uninstall,

    /// SELinux policy Patch tool
    Sepolicy {
        #[command(subcommand)]
        command: Sepolicy,
    },

    /// Manage App Profiles
    Profile {
        #[command(subcommand)]
        command: Profile,
    },

    /// Manage kernel features
    Feature {
        #[command(subcommand)]
        command: Feature,
    },

    /// Patch boot or init_boot images to apply KernelSU
    BootPatch(BootPatchArgs),

    /// Restore boot or init_boot images patched by KernelSU
    BootRestore(BootRestoreArgs),

    /// Show boot information
    BootInfo {
        #[command(subcommand)]
        command: BootInfo,
    },

    /// KPM module manager
    #[cfg(all(target_arch = "aarch64", target_os = "android"))]
    Kpm {
        #[command(subcommand)]
        command: kpm_cmd::Kpm,
    },

    /// For developers
    Debug {
        #[command(subcommand)]
        command: Debug,
    },
    /// Kernel interface
    Kernel {
        #[command(subcommand)]
        command: Kernel,
    },
}

#[derive(clap::Subcommand, Debug)]
enum UmountConfigOp {
    /// Add an new umount config to configuration file
    Add {
        /// mount point path
        mnt: String,
        /// umount flags (default: 0, MNT_DETACH: 2)
        #[arg(short, long, default_value = "0")]
        flags: u32,
    },
    /// Delete an umount config from configuration file
    Del {
        /// mount point path
        mnt: String,
    },
    /// Clear all auto apply umount config from configuration file
    Clear,
    /// List all configured auto apply umount configuration
    List,
}

#[derive(clap::Subcommand, Debug)]
enum BootInfo {
    /// show current kmi version
    CurrentKmi,

    /// show supported kmi versions
    SupportedKmis,

    /// check if device is A/B capable
    IsAbDevice,

    /// show auto-selected boot partition name
    DefaultPartition,

    /// list available partitions for current or OTA toggled slot
    AvailablePartitions,

    /// show slot suffix for current or OTA toggled slot
    SlotSuffix {
        /// toggle to another slot
        #[arg(short = 'u', long, default_value = "false")]
        ota: bool,
    },
}

#[derive(clap::Subcommand, Debug)]
enum Debug {
    /// Set the manager app, kernel CONFIG_KSU_DEBUG should be enabled.
    SetManager {
        /// manager package name
        #[arg(default_value_t = String::from("com.resukisu.resukisu"))]
        apk: String,
    },

    /// Get apk size and hash
    GetSign {
        /// apk path
        apk: String,
    },

    /// Root Shell
    Su {
        /// switch to gloabl mount namespace
        #[arg(short, long, default_value = "false")]
        global_mnt: bool,
    },

    /// Get kernel version
    Version,

    /// For testing
    Test,

    /// Process mark management
    Mark {
        #[command(subcommand)]
        command: MarkCommand,
    },
}

#[derive(clap::Subcommand, Debug)]
enum MarkCommand {
    /// Get mark status for a process (or all)
    Get {
        /// target pid (0 for total count)
        #[arg(default_value = "0")]
        pid: i32,
    },

    /// Mark a process
    Mark {
        /// target pid (0 for all processes)
        #[arg(default_value = "0")]
        pid: i32,
    },

    /// Unmark a process
    Unmark {
        /// target pid (0 for all processes)
        #[arg(default_value = "0")]
        pid: i32,
    },

    /// Refresh mark for all running processes
    Refresh,
}

#[derive(clap::Subcommand, Debug)]
enum Sepolicy {
    /// Patch sepolicy
    Patch {
        /// sepolicy statements
        sepolicy: String,
    },

    /// Apply sepolicy from file
    Apply {
        /// sepolicy file path
        file: String,
    },

    /// Check if sepolicy statement is supported/valid
    Check {
        /// sepolicy statements
        sepolicy: String,
    },
}

#[derive(clap::Subcommand, Debug)]
enum Module {
    /// Install module <ZIP>
    Install {
        /// module zip file path
        zip: String,
    },

    /// Undo module uninstall mark <id>
    UndoUninstall {
        /// module id
        id: String,
    },

    /// Uninstall module <id>
    Uninstall {
        /// module id
        id: String,
    },

    /// enable module <id>
    Enable {
        /// module id
        id: String,
    },

    /// disable module <id>
    Disable {
        // module id
        id: String,
    },

    /// run action for module <id>
    Action {
        // module id
        id: String,
    },

    /// module lua runner
    #[cfg(all(target_os = "android", target_arch = "aarch64"))]
    Lua {
        // module id
        id: String,
        // lua function
        function: String,
    },

    /// list all modules
    List,

    /// manage module configuration
    Config {
        #[command(subcommand)]
        command: ModuleConfigCmd,
    },
}

#[derive(clap::Subcommand, Debug)]
enum ModuleConfigCmd {
    /// Get a config value
    Get {
        /// config key
        key: String,
    },

    /// Set a config value
    Set {
        /// config key
        key: String,
        /// config value (omit to read from stdin)
        value: Option<String>,
        /// read value from stdin (default if value not provided)
        #[arg(long)]
        stdin: bool,
        /// use temporary config (cleared on reboot)
        #[arg(short, long)]
        temp: bool,
    },

    /// List all config entries
    List,

    /// Delete a config entry
    Delete {
        /// config key
        key: String,
        /// delete from temporary config
        #[arg(short, long)]
        temp: bool,
    },

    /// Clear all config entries
    Clear {
        /// clear temporary config
        #[arg(short, long)]
        temp: bool,
    },
}

#[derive(clap::Subcommand, Debug)]
enum Profile {
    /// get root profile's selinux policy of <package-name>
    GetSepolicy {
        /// package name
        package: String,
    },

    /// set root profile's selinux policy of <package-name> to <profile>
    SetSepolicy {
        /// package name
        package: String,
        /// policy statements
        policy: String,
    },

    /// get template of <id>
    GetTemplate {
        /// template id
        id: String,
    },

    /// set template of <id> to <template string>
    SetTemplate {
        /// template id
        id: String,
        /// template string
        template: String,
    },

    /// delete template of <id>
    DeleteTemplate {
        /// template id
        id: String,
    },

    /// list all templates
    ListTemplates,
}

#[derive(clap::Subcommand, Debug)]
enum Feature {
    /// Get feature value and support status
    Get {
        /// Feature ID or name (su_compat, kernel_umount)
        id: String,
    },

    /// Set feature value
    Set {
        /// Feature ID or name
        id: String,
        /// Feature value (0=disable, 1=enable)
        value: u64,
    },

    /// List all available features
    List,

    /// Check feature status (supported/unsupported/managed)
    Check {
        /// Feature ID or name (su_compat, kernel_umount)
        id: String,
    },

    /// Load configuration from file and apply to kernel
    Load,

    /// Save current kernel feature states to file
    Save,
}

#[derive(clap::Subcommand, Debug)]
enum Kernel {
    /// Nuke ext4 sysfs
    NukeExt4Sysfs {
        /// mount point
        mnt: String,
    },
    /// Manage umount list
    Umount {
        #[command(subcommand)]
        command: UmountOp,
    },
    /// Manage dynamic manager
    DynamicManager {
        #[command(subcommand)]
        command: DynamicManagerOp,
    },
    /// Notify that module is mounted
    NotifyModuleMounted,
}
#[derive(clap::Subcommand, Debug)]
enum DynamicManagerOp {
    /// Get the signature of the current dynamic manager (size+hash)
    Get,
    /// Set the signature of the dynamic manager
    Set {
        /// the signature size
        size: u32,
        /// the signature hash
        #[arg(value_parser = dynamic_manager::parse_hash)]
        hash: [u8; 64],
    },
    /// Set the signature of the dynamic manager for apk
    SetApk {
        /// the apk path
        apk: String,
    },
    /// Clear the dynamic manager
    Clear,
}

#[derive(clap::Subcommand, Debug)]
enum UmountOp {
    /// Add mount point to umount list
    Add {
        /// mount point path
        mnt: String,
        /// umount flags (default: 0, MNT_DETACH: 2)
        #[arg(short, long, default_value = "0")]
        flags: u32,
    },
    /// Delete mount point from umount list
    Del {
        /// mount point path
        mnt: String,
    },
    /// Wipe all entries from umount list
    Wipe,
    /// List all entries from umount list
    List,
}

#[cfg(all(target_arch = "aarch64", target_os = "android"))]
mod kpm_cmd {
    use std::path::PathBuf;

    use clap::Subcommand;

    #[derive(Subcommand, Debug)]
    pub enum Kpm {
        /// Load a KPM module: load <path> [args]
        Load { path: PathBuf, args: Option<String> },
        /// Unload a KPM module: unload <name>
        Unload { name: String },
        /// Get number of loaded modules
        Num,
        /// List loaded KPM modules
        List,
        /// Get info of a KPM module: info <name>
        Info { name: String },
        /// Send control command to a KPM module: control <name> <args>
        Control { name: String, args: String },
        /// Print KPM Loader version
        Version,
    }
}

#[cfg(all(target_arch = "aarch64", target_os = "android"))]
#[derive(clap::Subcommand, Debug)]
enum Susfs {
    /// Get SUSFS Status
    Status,
    /// Get SUSFS Version
    Version,
    /// Get SUSFS enable Features
    Features,
}

pub fn run() -> Result<()> {
    android_logger::init_once(
        Config::default()
            .with_max_level(crate::debug_select!(LevelFilter::Trace, LevelFilter::Info))
            .with_tag("KernelSU"),
    );

    // the kernel executes su with argv[0] = "su" and replace it with us
    let arg0 = std::env::args().next().unwrap_or_default();
    if arg0 == "su" || arg0 == "/system/bin/su" {
        return su::root_shell();
    }

    let cli = Args::parse();

    log::info!("command: {:?}", cli.command);

    let result = match cli.command {
        Commands::PostFsData => init_event::on_post_data_fs(),
        Commands::BootCompleted => {
            init_event::on_boot_completed();
            Ok(())
        }
        #[cfg(all(target_arch = "aarch64", target_os = "android"))]
        Commands::Susfs { command } => {
            match command {
                Susfs::Version => println!("{}", susfs::get_susfs_version()),

                Susfs::Status => println!("{}", susfs::get_susfs_status()),

                Susfs::Features => println!("{}", susfs::get_susfs_features()),
            }
            Ok(())
        }
        Commands::UmountConfig { command } => match command {
            UmountConfigOp::Add { mnt, flags } => umount_config::add_umount(&mnt, flags),
            UmountConfigOp::Del { mnt } => umount_config::del_umount(&mnt),
            UmountConfigOp::Clear => umount_config::wipe_umount(),
            UmountConfigOp::List => umount_config::list_umount(),
        },
        Commands::Module { command } => {
            utils::switch_mnt_ns(1)?;
            match command {
                Module::Install { zip } => module::install_module(&zip),
                Module::UndoUninstall { id } => module::undo_uninstall_module(&id),
                Module::Uninstall { id } => module::uninstall_module(&id),
                Module::Enable { id } => module::enable_module(&id),
                Module::Disable { id } => module::disable_module(&id),
                Module::Action { id } => module::run_action(&id),
                #[cfg(all(target_os = "android", target_arch = "aarch64"))]
                Module::Lua { id, function } => {
                    module::run_lua(&id, &function, false, true).map_err(|e| anyhow::anyhow!("{e}"))
                }
                Module::List => module::list_modules(),
                Module::Config { command } => {
                    // Get module ID from environment variable
                    let module_id = std::env::var("KSU_MODULE").map_err(|_| {
                        anyhow::anyhow!("This command must be run in the context of a module")
                    })?;

                    match command {
                        ModuleConfigCmd::Get { key } => {
                            // Use merge_configs to respect priority (temp overrides persist)
                            let config = module_config::merge_configs(&module_id)?;
                            match config.get(&key) {
                                Some(value) => {
                                    println!("{value}");
                                    Ok(())
                                }
                                None => anyhow::bail!("Key '{key}' not found"),
                            }
                        }
                        ModuleConfigCmd::Set {
                            key,
                            value,
                            stdin,
                            temp,
                        } => {
                            // Validate key at CLI layer for better user experience
                            module_config::validate_config_key(&key)?;

                            // Read value from stdin or argument
                            let value_str = match value {
                                Some(v) if !stdin => v,
                                _ => {
                                    // Read from stdin
                                    use std::io::Read;
                                    let mut buffer = String::new();
                                    std::io::stdin()
                                        .read_to_string(&mut buffer)
                                        .context("Failed to read from stdin")?;
                                    buffer
                                }
                            };

                            // Validate value
                            module_config::validate_config_value(&value_str)?;

                            let config_type = if temp {
                                module_config::ConfigType::Temp
                            } else {
                                module_config::ConfigType::Persist
                            };
                            module_config::set_config_value(
                                &module_id,
                                &key,
                                &value_str,
                                config_type,
                            )
                        }
                        ModuleConfigCmd::List => {
                            let config = module_config::merge_configs(&module_id)?;
                            if config.is_empty() {
                                println!("No config entries found");
                            } else {
                                for (key, value) in config {
                                    println!("{key}={value}");
                                }
                            }
                            Ok(())
                        }
                        ModuleConfigCmd::Delete { key, temp } => {
                            let config_type = if temp {
                                module_config::ConfigType::Temp
                            } else {
                                module_config::ConfigType::Persist
                            };
                            module_config::delete_config_value(&module_id, &key, config_type)
                        }
                        ModuleConfigCmd::Clear { temp } => {
                            let config_type = if temp {
                                module_config::ConfigType::Temp
                            } else {
                                module_config::ConfigType::Persist
                            };
                            module_config::clear_config(&module_id, config_type)
                        }
                    }
                }
            }
        }
        Commands::Install => utils::install(),
        Commands::Uninstall => utils::uninstall(),
        Commands::Sepolicy { command } => match command {
            Sepolicy::Patch { sepolicy } => sepolicy::live_patch(&sepolicy),
            Sepolicy::Apply { file } => sepolicy::apply_file(file),
            Sepolicy::Check { sepolicy } => sepolicy::check_rule(&sepolicy),
        },
        Commands::Services => {
            init_event::on_services();
            Ok(())
        }
        Commands::Profile { command } => match command {
            Profile::GetSepolicy { package } => profile::get_sepolicy(package),
            Profile::SetSepolicy { package, policy } => profile::set_sepolicy(package, policy),
            Profile::GetTemplate { id } => profile::get_template(id),
            Profile::SetTemplate { id, template } => profile::set_template(id, template),
            Profile::DeleteTemplate { id } => profile::delete_template(id),
            Profile::ListTemplates => profile::list_templates(),
        },

        Commands::Feature { command } => match command {
            Feature::Get { id } => feature::get_feature(&id),
            Feature::Set { id, value } => feature::set_feature(&id, value),
            Feature::List => {
                feature::list_features();
                Ok(())
            }
            Feature::Check { id } => feature::check_feature(&id),
            Feature::Load => feature::load_config_and_apply(),
            Feature::Save => feature::save_config(),
        },

        Commands::Debug { command } => match command {
            Debug::SetManager { apk } => debug::set_manager(&apk),
            Debug::GetSign { apk } => {
                let sign = apk_sign::get_apk_signature(&apk)?;
                println!("size: {:#x}, hash: {}", sign.0, sign.1);
                Ok(())
            }
            Debug::Version => {
                println!("Kernel Version: {}", ksucalls::get_version());
                Ok(())
            }
            Debug::Su { global_mnt } => su::grant_root(global_mnt),
            Debug::Test => assets::ensure_binaries(false),
            Debug::Mark { command } => match command {
                MarkCommand::Get { pid } => debug::mark_get(pid),
                MarkCommand::Mark { pid } => debug::mark_set(pid),
                MarkCommand::Unmark { pid } => debug::mark_unset(pid),
                MarkCommand::Refresh => debug::mark_refresh(),
            },
        },

        Commands::BootPatch(boot_patch) => crate::boot_patch::patch(boot_patch),

        Commands::BootInfo { command } => match command {
            BootInfo::CurrentKmi => {
                let kmi = crate::boot_patch::get_current_kmi()?;
                println!("{kmi}");
                // return here to avoid printing the error message
                return Ok(());
            }
            BootInfo::SupportedKmis => {
                let kmi = crate::assets::list_supported_kmi();
                for kmi in &kmi {
                    println!("{kmi}");
                }
                return Ok(());
            }
            BootInfo::IsAbDevice => {
                let val =
                    utils::getprop("ro.build.ab_update").unwrap_or_else(|| String::from("false"));
                let is_ab = val.trim().to_lowercase() == "true";
                println!("{}", if is_ab { "true" } else { "false" });
                return Ok(());
            }
            BootInfo::DefaultPartition => {
                let kmi = crate::boot_patch::get_current_kmi().unwrap_or_else(|_| String::new());
                let name = crate::boot_patch::choose_boot_partition(&kmi, false, &None);
                println!("{name}");
                return Ok(());
            }
            BootInfo::SlotSuffix { ota } => {
                let suffix = crate::boot_patch::get_slot_suffix(ota);
                println!("{suffix}");
                return Ok(());
            }
            BootInfo::AvailablePartitions => {
                let parts = crate::boot_patch::list_available_partitions();
                for p in &parts {
                    println!("{p}");
                }
                return Ok(());
            }
        },
        Commands::BootRestore(boot_restore) => crate::boot_patch::restore(boot_restore),
        Commands::Kernel { command } => match command {
            Kernel::NukeExt4Sysfs { mnt } => ksucalls::nuke_ext4_sysfs(&mnt),
            Kernel::Umount { command } => match command {
                UmountOp::Add { mnt, flags } => ksucalls::umount_list_add(&mnt, flags),
                UmountOp::Del { mnt } => ksucalls::umount_list_del(&mnt),
                UmountOp::Wipe => ksucalls::umount_list_wipe().map_err(Into::into),
                UmountOp::List => {
                    let list = ksucalls::umount_list_list()?;
                    println!("{}", serde_json::to_string(&list)?);
                    Ok(())
                }
            },
            Kernel::DynamicManager { command } => match command {
                DynamicManagerOp::Set { size, hash } => dynamic_manager::set(size, hash),
                DynamicManagerOp::Get => {
                    let (size, hash) = ksucalls::dynamic_manager_get()?;
                    println!("size: {}, hash: {}", size, String::from_utf8_lossy(&hash));
                    Ok(())
                }
                DynamicManagerOp::SetApk { apk } => {
                    let sign = apk_sign::get_apk_signature(&apk)?;

                    let bytes = sign.1.as_bytes();

                    let mut hash = [0u8; 64];
                    hash.copy_from_slice(bytes);

                    dynamic_manager::set(sign.0, hash)
                }
                DynamicManagerOp::Clear => dynamic_manager::clear(),
            },
            Kernel::NotifyModuleMounted => {
                ksucalls::report_module_mounted();
                Ok(())
            }
        },
        #[cfg(all(target_arch = "aarch64", target_os = "android"))]
        Commands::Kpm { command } => {
            use kpm_cmd::Kpm;

            use crate::android::kpm;
            match command {
                Kpm::Load { path, args } => {
                    kpm::load_module(path.to_str().unwrap(), args.as_deref())
                }
                Kpm::Unload { name } => kpm::unload_module(name),
                Kpm::Num => kpm::num().map(|_| ()),
                Kpm::List => kpm::list(),
                Kpm::Info { name } => kpm::info(name),
                Kpm::Control { name, args } => {
                    let ret = kpm::control(name, args)?;
                    println!("{ret}");
                    Ok(())
                }
                Kpm::Version => kpm::version(),
            }
        }
    };

    if let Err(e) = &result {
        log::error!("Error: {e:?}");
    }
    result
}
