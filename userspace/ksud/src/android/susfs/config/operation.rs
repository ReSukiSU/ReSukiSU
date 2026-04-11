use std::path::Path;

use crate::android::susfs::config::{
    data::{ConfigType, SusKstatStatically},
    read_config, save_config,
};

pub fn sus_path<P>(path: P, types: &ConfigType)
where
    P: AsRef<Path>,
{
    let Some(mut config) = read_config() else {
        return;
    };
    match types {
        ConfigType::Add => config
            .sus_path
            .sus_path
            .insert(path.as_ref().to_str().unwrap().to_string()),
        ConfigType::Remove => config
            .sus_path
            .sus_path
            .remove(path.as_ref().to_str().unwrap()),
    };

    save_config(&config);
}

pub fn enable_avc_spoofing(enabled: u8) {
    let Some(mut config) = read_config() else {
        return;
    };
    config.common.avc_spoofing = enabled == 1;

    save_config(&config);
}

pub fn set_uname<S>(release: &S, version: &S, types: &ConfigType)
where
    S: ToString,
{
    let Some(mut config) = read_config() else {
        return;
    };
    match types {
        ConfigType::Add => {
            config.common.version = version.to_string();
            config.common.release = release.to_string();
        }
        ConfigType::Remove => {
            config.common.version = "default".to_string();
            config.common.release = "default".to_string();
        }
    }
    save_config(&config);
}

pub fn sus_path_loop<P>(path: P, types: &ConfigType)
where
    P: AsRef<Path>,
{
    let Some(mut config) = read_config() else {
        return;
    };
    match types {
        ConfigType::Add => config
            .sus_path
            .sus_path_loop
            .insert(path.as_ref().to_str().unwrap().to_string()),
        ConfigType::Remove => config
            .sus_path
            .sus_path_loop
            .remove(path.as_ref().to_str().unwrap()),
    };

    save_config(&config);
}

pub fn sus_map<P>(path: P, types: &ConfigType)
where
    P: AsRef<Path>,
{
    let Some(mut config) = read_config() else {
        return;
    };
    match types {
        ConfigType::Add => config
            .sus_map
            .insert(path.as_ref().to_str().unwrap().to_string()),
        ConfigType::Remove => config.sus_map.remove(path.as_ref().to_str().unwrap()),
    };

    save_config(&config);
}

pub fn sus_kstat<P>(path: P, types: &ConfigType)
where
    P: AsRef<Path>,
{
    let Some(mut config) = read_config() else {
        return;
    };
    match types {
        ConfigType::Add => config
            .kstat
            .sus_kstat
            .insert(path.as_ref().to_str().unwrap().to_string()),
        ConfigType::Remove => config
            .kstat
            .sus_kstat
            .remove(path.as_ref().to_str().unwrap()),
    };

    save_config(&config);
}

pub fn sus_kstat_update<P>(path: P, types: &ConfigType)
where
    P: AsRef<Path>,
{
    let Some(mut config) = read_config() else {
        return;
    };
    match types {
        ConfigType::Add => config
            .kstat
            .update_kstat
            .insert(path.as_ref().to_str().unwrap().to_string()),
        ConfigType::Remove => config
            .kstat
            .update_kstat
            .remove(path.as_ref().to_str().unwrap()),
    };

    save_config(&config);
}

pub fn sus_kstat_full_clone<P>(path: P, types: &ConfigType)
where
    P: AsRef<Path>,
{
    let Some(mut config) = read_config() else {
        return;
    };
    match types {
        ConfigType::Add => config
            .kstat
            .full_clone
            .insert(path.as_ref().to_str().unwrap().to_string()),
        ConfigType::Remove => config
            .kstat
            .full_clone
            .remove(path.as_ref().to_str().unwrap()),
    };

    save_config(&config);
}

#[allow(clippy::too_many_arguments)]
pub fn add_sus_kstat_statically(
    path: &str,
    ino: &str,
    dev: &str,
    nlink: &str,
    size: &str,
    atime: &str,
    atime_nsec: &str,
    mtime: &str,
    mtime_nsec: &str,
    ctime: &str,
    ctime_nsec: &str,
    blocks: &str,
    blksize: &str,
    types: &ConfigType,
) {
    let Some(mut config) = read_config() else {
        return;
    };

    match types {
        ConfigType::Add => config.kstat.statically.insert(SusKstatStatically {
            path: path.to_string(),
            ino: ino.to_string(),
            dev: dev.to_string(),
            nlink: nlink.to_string(),
            size: size.to_string(),
            atime: atime.to_string(),
            atime_nsec: atime_nsec.to_string(),
            mtime: mtime.to_string(),
            mtime_nsec: mtime_nsec.to_string(),
            ctime: ctime.to_string(),
            ctime_nsec: ctime_nsec.to_string(),
            blocks: blocks.to_string(),
            blksize: blksize.to_string(),
        }),
        ConfigType::Remove => config.kstat.statically.remove(&SusKstatStatically {
            path: path.to_string(),
            ino: ino.to_string(),
            dev: dev.to_string(),
            nlink: nlink.to_string(),
            size: size.to_string(),
            atime: atime.to_string(),
            atime_nsec: atime_nsec.to_string(),
            mtime: mtime.to_string(),
            mtime_nsec: mtime_nsec.to_string(),
            ctime: ctime.to_string(),
            ctime_nsec: ctime_nsec.to_string(),
            blocks: blocks.to_string(),
            blksize: blksize.to_string(),
        }),
    };
}
