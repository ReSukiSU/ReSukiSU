use std::path::Path;

use crate::android::susfs::config::{data::SusKstatStatically, read_config, save_config};

pub fn add_sus_path<P>(path: P)
where
    P: AsRef<Path>,
{
    let Some(mut config) = read_config() else {
        return;
    };
    config
        .sus_path
        .sus_path
        .insert(path.as_ref().to_str().unwrap().to_string());

    save_config(&config);
}

pub fn enable_avc_spoofing(enabled: u8) {
    let Some(mut config) = read_config() else {
        return;
    };
    config.common.avc_spoofing = enabled == 1;

    save_config(&config);
}

pub fn set_uname<S>(release: &S, version: &S)
where
    S: ToString,
{
    let Some(mut config) = read_config() else {
        return;
    };

    config.common.version = version.to_string();
    config.common.release = release.to_string();

    save_config(&config);
}

pub fn add_sus_path_loop<P>(path: P)
where
    P: AsRef<Path>,
{
    let Some(mut config) = read_config() else {
        return;
    };
    config
        .sus_path
        .sus_path_loop
        .insert(path.as_ref().to_str().unwrap().to_string());

    save_config(&config);
}

pub fn add_sus_map<P>(path: P)
where
    P: AsRef<Path>,
{
    let Some(mut config) = read_config() else {
        return;
    };
    config
        .sus_map
        .insert(path.as_ref().to_str().unwrap().to_string());

    save_config(&config);
}

pub fn add_sus_kstat<P>(path: P)
where
    P: AsRef<Path>,
{
    let Some(mut config) = read_config() else {
        return;
    };
    config
        .kstat
        .sus_kstat
        .insert(path.as_ref().to_str().unwrap().to_string());

    save_config(&config);
}

pub fn add_sus_kstat_update<P>(path: P)
where
    P: AsRef<Path>,
{
    let Some(mut config) = read_config() else {
        return;
    };
    config
        .kstat
        .update_kstat
        .insert(path.as_ref().to_str().unwrap().to_string());

    save_config(&config);
}

pub fn add_sus_kstat_full_clone<P>(path: P)
where
    P: AsRef<Path>,
{
    let Some(mut config) = read_config() else {
        return;
    };
    config
        .kstat
        .full_clone
        .insert(path.as_ref().to_str().unwrap().to_string());

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
) {
    let Some(mut config) = read_config() else {
        return;
    };

    config.kstat.statically.insert(SusKstatStatically {
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
    });

    save_config(&config);
}

pub fn del_sus_path<P>(path: P)
where
    P: AsRef<Path>,
{
    let Some(mut config) = read_config() else {
        return;
    };
    config
        .sus_path
        .sus_path
        .remove(path.as_ref().to_str().unwrap());

    save_config(&config);
}

pub fn del_uname() {
    let Some(mut config) = read_config() else {
        return;
    };

    config.common.version = "default".to_string();
    config.common.release = "default".to_string();

    save_config(&config);
}

pub fn del_sus_path_loop<P>(path: P)
where
    P: AsRef<Path>,
{
    let Some(mut config) = read_config() else {
        return;
    };
    config
        .sus_path
        .sus_path_loop
        .remove(path.as_ref().to_str().unwrap());

    save_config(&config);
}

pub fn del_sus_map<P>(path: P)
where
    P: AsRef<Path>,
{
    let Some(mut config) = read_config() else {
        return;
    };
    config.sus_map.remove(path.as_ref().to_str().unwrap());

    save_config(&config);
}

pub fn del_sus_kstat<P>(path: P)
where
    P: AsRef<Path>,
{
    let Some(mut config) = read_config() else {
        return;
    };
    config
        .kstat
        .sus_kstat
        .remove(path.as_ref().to_str().unwrap());

    save_config(&config);
}

pub fn del_sus_kstat_update<P>(path: P)
where
    P: AsRef<Path>,
{
    let Some(mut config) = read_config() else {
        return;
    };
    config
        .kstat
        .update_kstat
        .remove(path.as_ref().to_str().unwrap());

    save_config(&config);
}

pub fn del_sus_kstat_full_clone<P>(path: P)
where
    P: AsRef<Path>,
{
    let Some(mut config) = read_config() else {
        return;
    };
    config
        .kstat
        .full_clone
        .remove(path.as_ref().to_str().unwrap());

    save_config(&config);
}

#[allow(clippy::too_many_arguments)]
pub fn del_sus_kstat_statically(
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
) {
    let Some(mut config) = read_config() else {
        return;
    };

    config.kstat.statically.remove(&SusKstatStatically {
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
    });

    save_config(&config);
}
