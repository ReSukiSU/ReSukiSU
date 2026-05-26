mod api;
pub mod cli;
mod config;
mod magic;
mod slot_info;
mod utils;

use anyhow::Result;
use log::warn;

pub fn on_boot_completed() -> Result<()> {
    let Some(config) = config::read_config() else {
        return Ok(());
    };

    for sus_path in config.sus_path.sus_path {
        if let Err(e) = api::add_sus_path(&api::SusPathType::Normal, &sus_path) {
            warn!("failed to add sus_path '{}': {}", sus_path, e);
        }
    }
    for sus_path_loop in config.sus_path.sus_path_loop {
        if let Err(e) = api::add_sus_path(&api::SusPathType::Loop, &sus_path_loop) {
            warn!("failed to add sus_path_loop '{}': {}", sus_path_loop, e);
        }
    }
    for sus_map in config.sus_map {
        if let Err(e) = api::add_sus_map(&sus_map) {
            warn!("failed to add sus_map '{}': {}", sus_map, e);
        }
    }

    Ok(())
}

pub fn on_post_fs_data() -> Result<()> {
    let Some(config) = config::read_config() else {
        return Ok(());
    };

    if let Err(e) = api::set_uname(&config.common.release, &config.common.version) {
        warn!("failed to set uname: {}", e);
    }

    if let Err(e) = api::enable_avc_log_spoofing(config.common.avc_spoofing.into()) {
        warn!("failed to enable avc log spoofing: {}", e);
    }

    if let Err(e) = api::enable_log(config.common.enable_susfs_log.into()) {
        warn!("failed to enable susfs log: {}", e);
    }

    if let Err(e) = api::hide_sus_mnts_for_non_su_procs(config.common.hide_sus_mnts_for_non_su_procs.into()) {
        warn!("failed to hide sus mnts for non su procs: {}", e);
    }

    for sus_kstat in config.kstat.sus_kstat {
        if let Err(e) = api::add_sus_kstat(&sus_kstat) {
            warn!("failed to add sus_kstat '{}': {}", sus_kstat, e);
        }
    }
    for update_kstat in config.kstat.update_kstat {
        if let Err(e) = api::update_sus_kstat(&update_kstat) {
            warn!("failed to update sus_kstat '{}': {}", update_kstat, e);
        }
    }
    for full_clone in config.kstat.full_clone {
        if let Err(e) = api::update_sus_kstat_full_clone(&full_clone) {
            warn!("failed to update sus_kstat_full_clone '{}': {}", full_clone, e);
        }
    }
    for statically in config.kstat.statically {
        if let Err(e) = api::add_sus_kstat_statically(
            &statically.path,
            &statically.ino,
            &statically.dev,
            &statically.nlink,
            &statically.size,
            &statically.atime,
            &statically.atime_nsec,
            &statically.mtime,
            &statically.mtime_nsec,
            &statically.ctime,
            &statically.ctime_nsec,
            &statically.blocks,
            &statically.blksize,
        ) {
            warn!("failed to add sus_kstat_statically '{}': {}", statically.path, e);
        }
    }

    Ok(())
}
