#![allow(clippy::similar_names)]

use std::{
    ffi::{c_long, c_ulong},
    fs,
    os::unix::fs::MetadataExt,
};

use anyhow::Result;
use bitflags::bitflags;

use crate::android::susfs::{
    api::{
        magic::{
            CMD_SUSFS_ADD_SUS_KSTAT, CMD_SUSFS_ADD_SUS_KSTAT_STATICALLY,
            CMD_SUSFS_UPDATE_SUS_KSTAT, ERR_CMD_NOT_SUPPORTED, SUSFS_MAX_LEN_PATHNAME,
        },
        susfsctl::{communicate, parse_err},
    },
    utils::str_to_c_array,
};

bitflags! {
    #[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
    pub struct SusKstatSpoofFlags: i32 {
        const INO = 1 << 0;
        const DEV = 1 << 1;
        const NLINK = 1 << 2;
        const SIZE = 1 << 3;
        const ATIME_TV_SEC = 1 << 4;
        const ATIME_TV_NSEC = 1 << 5;
        const MTIME_TV_SEC = 1 << 6;
        const MTIME_TV_NSEC = 1 << 7;
        const CTIME_TV_SEC = 1 << 8;
        const CTIME_TV_NSEC = 1 << 9;
        const BLOCKS = 1 << 10;
        const BLKSIZE = 1 << 11;

        const AUTO = (
            Self::INO.bits() | Self::DEV.bits() |
            Self::ATIME_TV_SEC.bits() | Self::ATIME_TV_NSEC.bits() |
            Self::MTIME_TV_SEC.bits() | Self::MTIME_TV_NSEC.bits() |
            Self::CTIME_TV_SEC.bits() | Self::CTIME_TV_NSEC.bits() |
            Self::BLKSIZE.bits() | Self::BLOCKS.bits()
        );

        const AUTO_FULL_CLONE = (
            Self::AUTO.bits() | Self::NLINK.bits() | Self::SIZE.bits()
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

fn copy_metadata_to_sus_kstat(info: &mut SusfsSusKstat, md: &fs::Metadata) {
    info.spoofed_ino = md.ino() as c_ulong;
    info.spoofed_dev = md.dev() as c_ulong;
    info.spoofed_nlink = md.nlink() as u32;
    info.spoofed_size = md.size() as i64;
    info.spoofed_atime_tv_sec = md.atime() as c_long;
    info.spoofed_mtime_tv_sec = md.mtime() as c_long;
    info.spoofed_ctime_tv_sec = md.ctime() as c_long;
    info.spoofed_atime_tv_nsec = md.atime_nsec() as c_ulong;
    info.spoofed_mtime_tv_nsec = md.mtime_nsec() as c_ulong;
    info.spoofed_ctime_tv_nsec = md.ctime_nsec() as c_ulong;
    info.spoofed_blksize = md.blksize() as c_long;
    info.spoofed_blocks = md.blocks() as i64;
}

pub fn update_sus_kstat(path: &str, full_clone: bool) -> Result<()> {
    let md = fs::metadata(path)?;
    let mut info = SusfsSusKstat::default();
    let spoof_flag = match full_clone {
        true => SusKstatSpoofFlags::AUTO_FULL_CLONE,
        false => SusKstatSpoofFlags::AUTO,
    };

    str_to_c_array(path, &mut info.target_pathname);

    info.is_statically = false;
    info.target_ino = md.ino() as c_ulong;
    copy_metadata_to_sus_kstat(&mut info, &md);
    info.flags |= spoof_flag.bits();
    info.err = ERR_CMD_NOT_SUPPORTED;

    communicate(CMD_SUSFS_UPDATE_SUS_KSTAT, &mut info);
    parse_err(CMD_SUSFS_UPDATE_SUS_KSTAT, info.err)?;
    Ok(())
}

pub fn add_sus_kstat(path: &str) -> Result<()> {
    let md = fs::metadata(path)?;
    let mut info = SusfsSusKstat::default();

    str_to_c_array(path, &mut info.target_pathname);
    copy_metadata_to_sus_kstat(&mut info, &md);

    info.is_statically = false;
    info.target_ino = md.ino() as c_ulong;
    info.flags |= SusKstatSpoofFlags::AUTO.bits();
    info.err = ERR_CMD_NOT_SUPPORTED;

    communicate(CMD_SUSFS_ADD_SUS_KSTAT, &mut info);
    parse_err(CMD_SUSFS_ADD_SUS_KSTAT, info.err)?;
    Ok(())
}

#[allow(clippy::too_many_arguments)]
pub fn add_sus_kstat_statically(
    path: &str,
    ino: Option<i64>,
    dev: Option<i64>,
    nlink: Option<i64>,
    size: Option<i64>,
    atime: Option<i64>,
    atime_nsec: Option<i64>,
    mtime: Option<i64>,
    mtime_nsec: Option<i64>,
    ctime: Option<i64>,
    ctime_nsec: Option<i64>,
    blocks: Option<i64>,
    blksize: Option<i64>,
) -> Result<()> {
    let md = fs::metadata(path)?;

    let mut info = SusfsSusKstat {
        target_ino: md.ino() as c_ulong,
        is_statically: true,
        ..Default::default()
    };

    str_to_c_array(path, &mut info.target_pathname);

    macro_rules! set_spoof_field {
        ($field:ident, $opt_val:expr, $ty:ty, $flag:ident) => {
            if let Some(val) = $opt_val {
                info.$field = val as $ty;
                info.flags |= SusKstatSpoofFlags::$flag.bits();
            }
        };
    }

    set_spoof_field!(spoofed_ino, ino, c_ulong, INO);
    set_spoof_field!(spoofed_dev, dev, c_ulong, DEV);
    set_spoof_field!(spoofed_nlink, nlink, u32, NLINK);
    set_spoof_field!(spoofed_size, size, i64, SIZE);
    set_spoof_field!(spoofed_atime_tv_sec, atime, c_long, ATIME_TV_SEC);
    set_spoof_field!(spoofed_atime_tv_nsec, atime_nsec, c_ulong, ATIME_TV_NSEC);
    set_spoof_field!(spoofed_mtime_tv_sec, mtime, c_long, MTIME_TV_SEC);
    set_spoof_field!(spoofed_mtime_tv_nsec, mtime_nsec, c_ulong, MTIME_TV_NSEC);
    set_spoof_field!(spoofed_ctime_tv_sec, ctime, c_long, CTIME_TV_SEC);
    set_spoof_field!(spoofed_ctime_tv_nsec, ctime_nsec, c_ulong, CTIME_TV_NSEC);
    set_spoof_field!(spoofed_blocks, blocks, i64, BLOCKS);
    set_spoof_field!(spoofed_blksize, blksize, c_long, BLKSIZE);

    info.err = ERR_CMD_NOT_SUPPORTED;

    communicate(CMD_SUSFS_ADD_SUS_KSTAT_STATICALLY, &mut info);
    parse_err(CMD_SUSFS_ADD_SUS_KSTAT_STATICALLY, info.err)?;
    Ok(())
}
