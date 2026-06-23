use std::path::Path;

use crate::android::susfs::config::{data::SusKstatStatically, read_config};

pub fn add_sus_path<P>(path: P)
where
    P: AsRef<Path>,
{
    let mut config = read_config();
    config
        .sus_path
        .sus_path
        .insert(path.as_ref().to_str().unwrap().to_string());
}

pub fn enable_avc_spoofing(enabled: u8) {
    let mut config = read_config();
    config.common.avc_spoofing = enabled == 1;
}

pub fn enable_susfs_log(enabled: u8) {
    let mut config = read_config();
    config.common.enable_susfs_log = enabled == 1;
}

pub fn set_hide_sus_mnts_for_non_su_procs(enabled: u8) {
    let mut config = read_config();
    config.common.hide_sus_mnts_for_non_su_procs = enabled == 1;
}

pub fn set_uname<S>(version: &S, release: &S)
where
    S: ToString,
{
    let mut config = read_config();

    config.common.spoof_version = version.to_string();
    config.common.spoof_release = release.to_string();
}

pub fn add_sus_path_loop<P>(path: P)
where
    P: AsRef<Path>,
{
    let mut config = read_config();
    config
        .sus_path
        .sus_path_loop
        .insert(path.as_ref().to_str().unwrap().to_string());
}

pub fn add_sus_map<P>(path: P)
where
    P: AsRef<Path>,
{
    let mut config = read_config();
    config
        .sus_map
        .insert(path.as_ref().to_str().unwrap().to_string());
}

pub fn add_sus_kstat<P>(path: P)
where
    P: AsRef<Path>,
{
    let mut config = read_config();
    config
        .kstat
        .sus_kstat
        .insert(path.as_ref().to_str().unwrap().to_string());
}

pub fn add_sus_kstat_update<P>(path: P)
where
    P: AsRef<Path>,
{
    let mut config = read_config();
    config
        .kstat
        .update_kstat
        .insert(path.as_ref().to_str().unwrap().to_string());
}

pub fn add_sus_kstat_full_clone<P>(path: P)
where
    P: AsRef<Path>,
{
    let mut config = read_config();
    config
        .kstat
        .full_clone
        .insert(path.as_ref().to_str().unwrap().to_string());
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
    let mut config = read_config();

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
}

pub fn del_sus_path<P>(path: P)
where
    P: AsRef<Path>,
{
    let mut config = read_config();
    config
        .sus_path
        .sus_path
        .remove(path.as_ref().to_str().unwrap());
}

use anyhow::Result;

pub fn del_uname_selective(target: &str) -> Result<()> {
    let mut config = read_config();

    match target {
        "version" => {
            // Reset only uname information
            config.common.spoof_version = "default".to_string();
        }
        "release" => {
            // Reset only build time information
            config.common.spoof_release = "default".to_string();
        }
        "all" => {
            // Reset both
            config.common.spoof_version = "default".to_string();
            config.common.spoof_release = "default".to_string();
        }
        _ => {
            return Err(anyhow::anyhow!(
                "invalid target '{}': expected 'version', 'release', or 'all'",
                target
            ));
        }
    }

    Ok(())
}

pub fn del_sus_path_loop<P>(path: P)
where
    P: AsRef<Path>,
{
    let mut config = read_config();
    config
        .sus_path
        .sus_path_loop
        .remove(path.as_ref().to_str().unwrap());
}

pub fn del_sus_map<P>(path: P)
where
    P: AsRef<Path>,
{
    let mut config = read_config();
    config.sus_map.remove(path.as_ref().to_str().unwrap());
}

pub fn del_sus_kstat<P>(path: P)
where
    P: AsRef<Path>,
{
    let mut config = read_config();
    config
        .kstat
        .sus_kstat
        .remove(path.as_ref().to_str().unwrap());
}

pub fn del_sus_kstat_update<P>(path: P)
where
    P: AsRef<Path>,
{
    let mut config = read_config();
    config
        .kstat
        .update_kstat
        .remove(path.as_ref().to_str().unwrap());
}

pub fn del_sus_kstat_full_clone<P>(path: P)
where
    P: AsRef<Path>,
{
    let mut config = read_config();
    config
        .kstat
        .full_clone
        .remove(path.as_ref().to_str().unwrap());
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
    let mut config = read_config();

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
}
