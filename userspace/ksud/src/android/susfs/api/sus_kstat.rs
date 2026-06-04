#![allow(clippy::similar_names)]

use std::{fs, os::unix::fs::MetadataExt, path::Path, ffi::{c_ulong, c_long}};

use bitflags::bitflags;
use anyhow::Result;

use crate::android::susfs::{
    magic::{
        CMD_SUSFS_ADD_SUS_KSTAT, CMD_SUSFS_ADD_SUS_KSTAT_STATICALLY, CMD_SUSFS_UPDATE_SUS_KSTAT,
        ERR_CMD_NOT_SUPPORTED, SUSFS_MAX_LEN_PATHNAME,
    },
    utils::str_to_c_array,
    communicate::{communicate, parse_err}
};

bitflags! {
    #[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
    pub struct KstatSpoofFlags: i32 {
        const SPOOF_INO = 1 << 0;
        const SPOOF_DEV = 1 << 1;
        const SPOOF_NLINK = 1 << 2;
        const SPOOF_SIZE = 1 << 3;
        const SPOOF_ATIME_TV_SEC = 1 << 4;
        const SPOOF_ATIME_TV_NSEC = 1 << 5;
        const SPOOF_MTIME_TV_SEC = 1 << 6;
        const SPOOF_MTIME_TV_NSEC = 1 << 7;
        const SPOOF_CTIME_TV_SEC = 1 << 8;
        const SPOOF_CTIME_TV_NSEC = 1 << 9;
        const SPOOF_BLOCKS = 1 << 10;
        const SPOOF_BLKSIZE = 1 << 11;

        const AUTO_SPOOF = (
            Self::SPOOF_INO.bits() | Self::SPOOF_DEV.bits() |
            Self::SPOOF_ATIME_TV_SEC.bits() | Self::SPOOF_ATIME_TV_NSEC.bits() |
            Self::SPOOF_MTIME_TV_SEC.bits() | Self::SPOOF_MTIME_TV_NSEC.bits() |
            Self::SPOOF_CTIME_TV_SEC.bits() | Self::SPOOF_CTIME_TV_NSEC.bits() |
            Self::SPOOF_BLKSIZE.bits() | Self::SPOOF_BLOCKS.bits()
        );

        const AUTO_SPOOF_FULL_CLONE = (
            Self::AUTO_SPOOF.bits() | Self::SPOOF_NLINK.bits() | Self::SPOOF_SIZE.bits()
        );
    }
}

#[repr(C)]
struct SusfsSusKstat {
    is_statically: bool,
    target_ino: c_ulong,
    target_pathname: [u8; SUSFS_MAX_LEN_PATHNAME],
    spoofed_ino: c_ulong,
    spoofed_dev: c_ulong,
    spoofed_nlink: u32,
    spoofed_size: i64,
    spoofed_atime_tv_sec: c_long,
    spoofed_atime_tv_nsec: c_ulong,
    spoofed_mtime_tv_sec: c_long,
    spoofed_mtime_tv_nsec: c_ulong,
    spoofed_ctime_tv_sec: c_long,
    spoofed_ctime_tv_nsec: c_ulong,
    spoofed_blocks: i64,
    spoofed_blksize: c_long,
    flags: i32,
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
            flags: 0,
            err: 0,
        }
    }
}

fn parse_or_default<T>(
    val: &str,
    flag: &mut i32,
    flag_add: KstatSpoofFlags,
) -> Result<Option<T>>
where
    T: std::str::FromStr,
{
    if val.trim() == "default" {
        Ok(None)
    } else {
        match val.parse::<T>() {
            Ok(val) => {
                *flag |= flag_add.bits();
                Ok(Some(val))
            },
            Err(_) => Err(anyhow::anyhow!("failed to parse \"{val}\""))
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
    info.spoofed_atime_tv_nsec = md.atime_nsec() as c_ulong;
    info.spoofed_mtime_tv_nsec = md.mtime_nsec() as c_ulong;
    info.spoofed_ctime_tv_nsec = md.ctime_nsec() as c_ulong;
    info.spoofed_blksize = md.blksize() as c_long;
    info.spoofed_blocks = md.blocks() as i64;
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
    copy_metadata_to_sus_kstat(&mut info, &md);
    info.flags |= KstatSpoofFlags::AUTO_SPOOF.bits();
    info.err = ERR_CMD_NOT_SUPPORTED;

    communicate(CMD_SUSFS_UPDATE_SUS_KSTAT, &mut info);
    parse_err(CMD_SUSFS_UPDATE_SUS_KSTAT, info.err)?;
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
    info.flags |= KstatSpoofFlags::AUTO_SPOOF.bits();
    info.err = ERR_CMD_NOT_SUPPORTED;

    communicate(CMD_SUSFS_ADD_SUS_KSTAT, &mut info);
    parse_err(CMD_SUSFS_ADD_SUS_KSTAT, info.err)?;
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
    info.flags |= KstatSpoofFlags::AUTO_SPOOF_FULL_CLONE.bits();
    info.err = ERR_CMD_NOT_SUPPORTED;

    communicate(CMD_SUSFS_UPDATE_SUS_KSTAT, &mut info);
    parse_err(CMD_SUSFS_UPDATE_SUS_KSTAT, info.err)?;
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
    let mut flag: i32 = 0;

    let s_ino = parse_or_default(ino, &mut flag, KstatSpoofFlags::SPOOF_INO)?;
    let s_dev = parse_or_default(dev, &mut flag, KstatSpoofFlags::SPOOF_DEV)?;
    let s_nlink = parse_or_default(nlink, &mut flag, KstatSpoofFlags::SPOOF_NLINK)?;
    let s_size = parse_or_default(size, &mut flag, KstatSpoofFlags::SPOOF_SIZE)?;
    let s_atime = parse_or_default(
        atime,
        &mut flag,
        KstatSpoofFlags::SPOOF_ATIME_TV_SEC,
    )?;
    let s_atime_nsec = parse_or_default(
        atime_nsec,
        &mut flag,
        KstatSpoofFlags::SPOOF_ATIME_TV_NSEC,
    )?;
    let s_mtime = parse_or_default(
        mtime,
        &mut flag,
        KstatSpoofFlags::SPOOF_MTIME_TV_SEC,
    )?;
    let s_mtime_nsec = parse_or_default(
        mtime_nsec,
        &mut flag,
        KstatSpoofFlags::SPOOF_MTIME_TV_NSEC,
    )?;
    let s_ctime = parse_or_default(
        ctime,
        &mut flag,
        KstatSpoofFlags::SPOOF_CTIME_TV_SEC,
    )?;
    let s_ctime_nsec = parse_or_default(
        ctime_nsec,
        &mut flag,
        KstatSpoofFlags::SPOOF_CTIME_TV_NSEC,
    )?;
    let s_blocks = parse_or_default(
        blocks,
        &mut flag,
        KstatSpoofFlags::SPOOF_BLOCKS,
    )?;
    let s_blksize = parse_or_default(
        blksize,
        &mut flag,
        KstatSpoofFlags::SPOOF_BLKSIZE,
    )?;

    str_to_c_array(path, &mut info.target_pathname);
    info.flags = flag;
    if let Some(s_ino) = s_ino{
        info.spoofed_ino = s_ino;
    }
    if let Some(s_dev) = s_dev{
        info.spoofed_dev = s_dev;
    }
    if let Some(s_nlink) = s_nlink{
        info.spoofed_nlink = s_nlink;
    }
    if let Some(s_size) = s_size{
        info.spoofed_size = s_size;
    }
    if let Some(s_atime) = s_atime{
        info.spoofed_atime_tv_sec = s_atime;
    }
    if let Some(s_atime_nsec) = s_atime_nsec{
        info.spoofed_atime_tv_nsec = s_atime_nsec;
    }
    if let Some(s_mtime) = s_mtime{
        info.spoofed_mtime_tv_sec = s_mtime;
    }
    if let Some(s_mtime_nsec) = s_mtime_nsec{
        info.spoofed_mtime_tv_nsec = s_mtime_nsec;
    }
    if let Some(s_ctime) = s_ctime{
        info.spoofed_ctime_tv_sec = s_ctime;
    }
    if let Some(s_ctime_nsec) = s_ctime_nsec{
        info.spoofed_ctime_tv_nsec = s_ctime_nsec;
    }
    if let Some(s_blocks) = s_blocks{
        info.spoofed_blocks = s_blocks;
    }
    if let Some(s_blksize) = s_blksize{
        info.spoofed_blksize = s_blksize;
    }

    info.err = ERR_CMD_NOT_SUPPORTED;

    communicate(CMD_SUSFS_ADD_SUS_KSTAT_STATICALLY, &mut info);
    parse_err(CMD_SUSFS_ADD_SUS_KSTAT_STATICALLY, info.err)?;
    Ok(())
}
