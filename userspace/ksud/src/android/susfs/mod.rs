mod api;
pub mod cli;
mod config;
mod magic;
mod utils;

use anyhow::Result;

pub fn on_boot_completed() -> Result<()> {
    let Some(config) = config::read_config() else {
        return Ok(());
    };

    for sus_path in config.sus_path.sus_path {
        api::add_sus_path(&api::SusPathType::Normal, &sus_path)?;
    }
    for sus_path_loop in config.sus_path.sus_path_loop {
        api::add_sus_path(&api::SusPathType::Loop, &sus_path_loop)?;
    }
    for sus_map in config.sus_map {
        api::add_sus_map(&sus_map)?;
    }

    Ok(())
}

pub fn on_post_fs_data() -> Result<()> {
    let Some(config) = config::read_config() else {
        return Ok(());
    };

    api::set_uname(&config.common.release, &config.common.version)?;
    api::enable_avc_log_spoofing(config.common.avc_spoofing.into())?;
    for sus_kstat in config.kstat.sus_kstat {
        api::add_sus_kstat(&sus_kstat)?;
    }
    for update_kstat in config.kstat.update_kstat {
        api::update_sus_kstat(&update_kstat)?;
    }
    for full_clone in config.kstat.full_clone {
        api::update_sus_kstat_full_clone(&full_clone)?;
    }
    for statically in config.kstat.statically {
        api::add_sus_kstat_statically(
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
        )?;
    }

    Ok(())
}
