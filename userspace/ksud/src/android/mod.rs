pub mod cli;
mod debug;
mod dynamic_manager;
mod feature;
mod init_event;
#[cfg(all(target_arch = "aarch64", target_os = "android"))]
mod kpm;
mod ksucalls;
mod late_load;
mod magica;
mod module;
mod profile;
mod resetprop;
mod restorecon;
mod sepolicy;
mod su;
#[cfg(all(target_arch = "aarch64", target_os = "android"))]
mod susfs;
mod umount_config;
mod unload;
pub mod utils;
