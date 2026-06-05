use crate::android::susfs::api;
use crate::android::susfs::config;
use crate::android::susfs::config::data::Data;
use log::warn;

pub fn on_boot_completed() {
    let config = config::read_config();

    apply_sus_paths(&config);
    apply_sus_maps(&config);
}

pub fn on_services() {
    // let config = config::read_config();

    // apply_sus_paths(&config);
    // apply_sus_maps(&config);
}

fn apply_sus_paths(config: &Data) {
    for sus_path in &config.sus_path.sus_path {
        if sus_path.trim().is_empty() {
            continue;
        }
        apply_sus_path_entry(&api::SusPathType::Normal, "sus_path", sus_path);
    }
    for sus_path_loop in &config.sus_path.sus_path_loop {
        if sus_path_loop.trim().is_empty() {
            continue;
        }
        apply_sus_path_entry(&api::SusPathType::Loop, "sus_path_loop", sus_path_loop);
    }
}

fn apply_sus_path_entry(path_type: &api::SusPathType, label: &str, path: &str) {
    if let Err(e) = api::add_sus_path(path_type, &path) {
        warn!("failed to add {label} '{path}': {e}");
    }
}

fn apply_sus_maps(config: &Data) {
    for sus_map in &config.sus_map {
        if sus_map.trim().is_empty() {
            continue;
        }
        if let Err(e) = api::add_sus_map(sus_map.as_str()) {
            warn!("failed to add sus_map '{sus_map}': {e}");
        }
    }
}

pub fn on_post_fs_data() {
    let config = config::read_config();

    if let Err(e) = api::set_uname(&config.common.version, &config.common.release) {
        warn!("failed to set uname: {e}");
    }

    if let Err(e) = api::enable_avc_log_spoofing(config.common.avc_spoofing.into()) {
        warn!("failed to enable avc log spoofing: {e}");
    }

    if let Err(e) = api::enable_log(config.common.enable_susfs_log.into()) {
        warn!("failed to enable susfs log: {e}");
    }

    if let Err(e) =
        api::hide_sus_mnts_for_non_su_procs(config.common.hide_sus_mnts_for_non_su_procs.into())
    {
        warn!("failed to hide sus mnts for non su procs: {e}");
    }

    // apply_sus_paths(&config);

    for sus_kstat in &config.kstat.sus_kstat {
        if sus_kstat.trim().is_empty() {
            continue;
        }
        if let Err(e) = api::add_sus_kstat(sus_kstat.as_str()) {
            warn!("failed to add sus_kstat '{sus_kstat}': {e}");
        }
    }
    for statically in &config.kstat.statically {
        if statically.path.trim().is_empty() {
            continue;
        }
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
            warn!(
                "failed to add sus_kstat_statically '{}': {}",
                statically.path, e
            );
        }
    }
}

pub fn on_post_mount() {
    let config = config::read_config();

    // apply_sus_paths(&config);
    // apply_sus_maps(&config);

    for update_kstat in &config.kstat.update_kstat {
        if update_kstat.trim().is_empty() {
            continue;
        }
        if let Err(e) = api::update_sus_kstat(update_kstat.as_str()) {
            warn!("failed to update sus_kstat '{update_kstat}': {e}");
        }
    }
    for full_clone in &config.kstat.full_clone {
        if full_clone.trim().is_empty() {
            continue;
        }
        if let Err(e) = api::update_sus_kstat_full_clone(full_clone.as_str()) {
            warn!("failed to update sus_kstat_full_clone '{full_clone}': {e}");
        }
    }
}
