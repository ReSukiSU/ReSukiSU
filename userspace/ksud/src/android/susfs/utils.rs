use std::{fs, os::unix::fs::MetadataExt};

use anyhow::Result;
use libc::{SYS_reboot, syscall};

use crate::android::susfs::{
    api::SusfsSusKstat,
    magic::{ERR_CMD_NOT_SUPPORTED, KSU_INSTALL_MAGIC1, SUSFS_MAGIC},
};

pub(super) fn susfs_ctl<T>(info: &mut T, cmd: u64) {
    unsafe {
        syscall(
            SYS_reboot,
            KSU_INSTALL_MAGIC1,
            SUSFS_MAGIC,
            cmd,
            std::ptr::from_mut::<T>(info),
        );
    }
}

pub(super) fn str_to_c_array<const N: usize>(s: &str, array: &mut [u8; N]) {
    let bytes = s.as_bytes();
    let len = bytes.len().min(N - 1);
    array[..len].copy_from_slice(&bytes[..len]);
    array[len] = 0;
}

pub(super) fn fetch_metadata(path: &str) -> Result<fs::Metadata> {
    fs::metadata(path).map_err(|e| {
        anyhow::format_err!("[-] Failed to get metadata from path: '{path}', error: {e}")
    })
}

pub(super) fn copy_metadata_to_sus_kstat(info: &mut SusfsSusKstat, md: &fs::Metadata) {
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

pub(super) fn handle_result(err: i32, cmd: u64) -> Result<()> {
    if err == ERR_CMD_NOT_SUPPORTED {
        return Err(anyhow::format_err!(
            "unsupported susfs operation, cmd: 0x{cmd:x}"
        ));
    }
    if err != 0 && err != ERR_CMD_NOT_SUPPORTED {
        return Err(anyhow::format_err!("{err}"));
    }

    Ok(())
}

pub(super) fn parse_or_default<T: std::str::FromStr>(val: &str, default: T) -> Result<T> {
    if val == "default" {
        Ok(default)
    } else {
        val.parse::<T>()
            .map_err(|_| anyhow::format_err!("Invalid number format: {val}"))
    }
}
