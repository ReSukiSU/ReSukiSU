#![allow(clippy::similar_names)]

use std::{fs, os::unix::fs::MetadataExt, path::Path};

use anyhow::Result;

use crate::android::susfs::{
    magic::{
        CMD_SUSFS_ADD_SUS_KSTAT, CMD_SUSFS_ADD_SUS_KSTAT_STATICALLY, CMD_SUSFS_UPDATE_SUS_KSTAT,
        ERR_CMD_NOT_SUPPORTED, SUSFS_MAX_LEN_PATHNAME,
    },
    utils::{handle_result, parse_or_default, str_to_c_array, susfs_ctl},
};

#[repr(C)]
struct SusfsSusKstat {
    is_statically: bool,
    target_ino: u64,
    target_pathname: [u8; SUSFS_MAX_LEN_PATHNAME],
    spoofed_ino: u64,
    spoofed_dev: u64,
    spoofed_nlink: u32,
    spoofed_size: i64,
    spoofed_atime_tv_sec: i64,
    spoofed_mtime_tv_sec: i64,
    spoofed_ctime_tv_sec: i64,
    spoofed_atime_tv_nsec: i64,
    spoofed_mtime_tv_nsec: i64,
    spoofed_ctime_tv_nsec: i64,
    spoofed_blksize: u64,
    spoofed_blocks: u64,
    err: i32,
}

impl Default for SusfsSusKstat {
    fn default() -> Self {
        Self {
            is_statically: false,
            target_ino: 0,
            target_pathname: [0; SUSFS_MAX_LEN_PATHNAME],
            spoofed_ino: 0,
            spoofed_dev: 0,
            spoofed_nlink: 0,
            spoofed_size: 0,
            spoofed_atime_tv_sec: 0,
            spoofed_mtime_tv_sec: 0,
            spoofed_ctime_tv_sec: 0,
            spoofed_atime_tv_nsec: 0,
            spoofed_mtime_tv_nsec: 0,
            spoofed_ctime_tv_nsec: 0,
            spoofed_blksize: 0,
            spoofed_blocks: 0,
            err: 0,
        }
    }
}

fn copy_metadata_to_sus_kstat(info: &mut SusfsSusKstat, md: &fs::Metadata) {
    info.spoofed_ino = md.ino();
    info.spoofed_dev = md.dev();
    info.spoofed_nlink = md.nlink() as u32;
    info.spoofed_size = md.size() as i64;
    info.spoofed_atime_tv_sec = md.atime();
    info.spoofed_mtime_tv_sec = md.mtime();
    info.spoofed_ctime_tv_sec = md.ctime();
    info.spoofed_atime_tv_nsec = md.atime_nsec();
    info.spoofed_mtime_tv_nsec = md.mtime_nsec();
    info.spoofed_ctime_tv_nsec = md.ctime_nsec();
    info.spoofed_blksize = md.blksize();
    info.spoofed_blocks = md.blocks();
}

pub fn update_sus_kstat<P>(path: P) -> Result<()>
where
    P: AsRef<Path>,
{
    let md = fs::metadata(path.as_ref())?;
    let mut info = SusfsSusKstat::default();

    str_to_c_array(
        path.as_ref().to_str().unwrap_or_default(),
        &mut info.target_pathname,
    );

    info.is_statically = false;
    info.target_ino = md.ino();
    info.spoofed_size = md.size() as i64;
    info.spoofed_blocks = md.blocks();
    info.err = ERR_CMD_NOT_SUPPORTED;

    susfs_ctl(&mut info, CMD_SUSFS_UPDATE_SUS_KSTAT);
    handle_result(info.err, CMD_SUSFS_UPDATE_SUS_KSTAT)?;
    Ok(())
}

pub fn add_sus_kstat<P>(path: P) -> Result<()>
where
    P: AsRef<Path>,
{
    let md = fs::metadata(path.as_ref())?;
    let mut info = SusfsSusKstat::default();

    str_to_c_array(
        path.as_ref().to_str().unwrap_or_default(),
        &mut info.target_pathname,
    );
    copy_metadata_to_sus_kstat(&mut info, &md);

    info.is_statically = false;
    info.target_ino = md.ino();
    info.err = ERR_CMD_NOT_SUPPORTED;

    susfs_ctl(&mut info, CMD_SUSFS_ADD_SUS_KSTAT);
    handle_result(info.err, CMD_SUSFS_ADD_SUS_KSTAT)?;
    Ok(())
}
pub fn update_sus_kstat_full_clone<P>(path: P) -> Result<()>
where
    P: AsRef<Path>,
{
    let md = fs::metadata(path.as_ref())?;
    let mut info = SusfsSusKstat::default();

    str_to_c_array(
        path.as_ref().to_str().unwrap_or_default(),
        &mut info.target_pathname,
    );
    copy_metadata_to_sus_kstat(&mut info, &md);

    info.is_statically = false;
    info.target_ino = md.ino();
    info.err = ERR_CMD_NOT_SUPPORTED;

    susfs_ctl(&mut info, CMD_SUSFS_UPDATE_SUS_KSTAT);
    handle_result(info.err, CMD_SUSFS_UPDATE_SUS_KSTAT)?;
    Ok(())
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
) -> Result<()> {
    let md = fs::metadata(path)?;

    let mut info = SusfsSusKstat {
        target_ino: md.ino(),
        is_statically: true,
        ..Default::default()
    };

    let s_ino = parse_or_default(ino, md.ino())?;
    let s_dev = parse_or_default(dev, md.dev())?;
    let s_nlink = parse_or_default(nlink, md.nlink())?;
    let s_size = parse_or_default(size, md.size())?;
    let s_atime = parse_or_default(atime, md.atime())?;
    let s_atime_nsec = parse_or_default(atime_nsec, md.atime_nsec())?;
    let s_mtime = parse_or_default(mtime, md.mtime())?;
    let s_mtime_nsec = parse_or_default(mtime_nsec, md.mtime_nsec())?;
    let s_ctime = parse_or_default(ctime, md.ctime())?;
    let s_ctime_nsec = parse_or_default(ctime_nsec, md.ctime_nsec())?;
    let s_blocks = parse_or_default(blocks, md.blocks())?;
    let s_blksize = parse_or_default(blksize, md.blksize())?;

    str_to_c_array(path, &mut info.target_pathname);

    info.spoofed_ino = s_ino as u64;
    info.spoofed_dev = s_dev as u64;
    info.spoofed_nlink = s_nlink as u32;
    info.spoofed_size = s_size as i64;
    info.spoofed_atime_tv_sec = s_atime as i64;
    info.spoofed_mtime_tv_sec = s_mtime as i64;
    info.spoofed_ctime_tv_sec = s_ctime as i64;
    info.spoofed_atime_tv_nsec = s_atime_nsec as i64;
    info.spoofed_mtime_tv_nsec = s_mtime_nsec as i64;
    info.spoofed_ctime_tv_nsec = s_ctime_nsec as i64;
    info.spoofed_blksize = s_blksize as u64;
    info.spoofed_blocks = s_blocks as u64;

    info.err = ERR_CMD_NOT_SUPPORTED;

    susfs_ctl(&mut info, CMD_SUSFS_ADD_SUS_KSTAT_STATICALLY);
    handle_result(info.err, CMD_SUSFS_ADD_SUS_KSTAT_STATICALLY)?;
    Ok(())
}
