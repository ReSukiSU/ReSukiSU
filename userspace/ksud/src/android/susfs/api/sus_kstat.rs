#![allow(clippy::similar_names)]

use std::{fs, os::unix::fs::MetadataExt, path::Path};

use anyhow::Result;
use bitflags::bitflags;

use crate::android::susfs::{
    magic::{
        CMD_SUSFS_ADD_SUS_KSTAT, CMD_SUSFS_ADD_SUS_KSTAT_STATICALLY, CMD_SUSFS_UPDATE_SUS_KSTAT,
        ERR_CMD_NOT_SUPPORTED, SUSFS_MAX_LEN_PATHNAME,
    },
    utils::{handle_result, str_to_c_array, susfs_ctl},
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
    default: T,
    info: &mut SusfsSusKstat,
    flags: KstatSpoofFlags,
) -> Result<T>
where
    T: std::str::FromStr,
{
    if val == "default" {
        Ok(default)
    } else {
        info.flags |= flags.bits();
        val.parse::<T>()
            .map_err(|_| anyhow::format_err!("Invalid number format: {val}"))
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
    info.flags |= KstatSpoofFlags::AUTO_SPOOF.bits();
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
    info.flags |= KstatSpoofFlags::AUTO_SPOOF.bits();
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
    info.flags |= KstatSpoofFlags::AUTO_SPOOF_FULL_CLONE.bits();
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

    let s_ino = parse_or_default(ino, md.ino(), &mut info, KstatSpoofFlags::SPOOF_INO)?;
    let s_dev = parse_or_default(dev, md.dev(), &mut info, KstatSpoofFlags::SPOOF_DEV)?;
    let s_nlink = parse_or_default(nlink, md.nlink(), &mut info, KstatSpoofFlags::SPOOF_NLINK)?;
    let s_size = parse_or_default(size, md.size(), &mut info, KstatSpoofFlags::SPOOF_SIZE)?;
    let s_atime = parse_or_default(
        atime,
        md.atime(),
        &mut info,
        KstatSpoofFlags::SPOOF_ATIME_TV_SEC,
    )?;
    let s_atime_nsec = parse_or_default(
        atime_nsec,
        md.atime_nsec(),
        &mut info,
        KstatSpoofFlags::SPOOF_ATIME_TV_NSEC,
    )?;
    let s_mtime = parse_or_default(
        mtime,
        md.mtime(),
        &mut info,
        KstatSpoofFlags::SPOOF_MTIME_TV_SEC,
    )?;
    let s_mtime_nsec = parse_or_default(
        mtime_nsec,
        md.mtime_nsec(),
        &mut info,
        KstatSpoofFlags::SPOOF_MTIME_TV_NSEC,
    )?;
    let s_ctime = parse_or_default(
        ctime,
        md.ctime(),
        &mut info,
        KstatSpoofFlags::SPOOF_CTIME_TV_SEC,
    )?;
    let s_ctime_nsec = parse_or_default(
        ctime_nsec,
        md.ctime_nsec(),
        &mut info,
        KstatSpoofFlags::SPOOF_CTIME_TV_NSEC,
    )?;
    let s_blocks = parse_or_default(
        blocks,
        md.blocks(),
        &mut info,
        KstatSpoofFlags::SPOOF_BLOCKS,
    )?;
    let s_blksize = parse_or_default(
        blksize,
        md.blksize(),
        &mut info,
        KstatSpoofFlags::SPOOF_BLKSIZE,
    )?;

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
