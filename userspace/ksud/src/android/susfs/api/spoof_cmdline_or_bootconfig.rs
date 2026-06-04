//! ## Spoof Cmdline or Bootconfig
//!
//! Spoof the output of /proc/cmdline (non-gki) or /proc/bootconfig (gki) from a text file

use std::{fs, path::Path};

use anyhow::Result;

use crate::android::susfs::{
    magic::{
        CMD_SUSFS_SET_CMDLINE_OR_BOOTCONFIG, ERR_CMD_NOT_SUPPORTED,
        SUSFS_FAKE_CMDLINE_OR_BOOTCONFIG_SIZE,
    },
    communicate::{communicate, parse_err},
};

#[repr(C)]
struct SusfsSpoofCmdline {
    fake_cmdline_or_bootconfig: [u8; SUSFS_FAKE_CMDLINE_OR_BOOTCONFIG_SIZE],
    err: i32,
}

/// Spoof the output of /proc/cmdline (non-gki) or /proc/bootconfig (gki) from a text file
pub fn set_cmdline_or_bootconfig<P>(path: P) -> Result<()>
where
    P: AsRef<Path>,
{
    let abs_path = fs::canonicalize(&path)?;
    let content = fs::read(&abs_path)?;
    if content.len() >= SUSFS_FAKE_CMDLINE_OR_BOOTCONFIG_SIZE {
        return Err(anyhow::format_err!("file_size too long"));
    }

    let mut info = Box::new(SusfsSpoofCmdline {
        fake_cmdline_or_bootconfig: [0; SUSFS_FAKE_CMDLINE_OR_BOOTCONFIG_SIZE],
        err: ERR_CMD_NOT_SUPPORTED,
    });

    for (i, &b) in content.iter().enumerate() {
        info.fake_cmdline_or_bootconfig[i] = b;
    }

    communicate(CMD_SUSFS_SET_CMDLINE_OR_BOOTCONFIG, &mut *info);
    parse_err(CMD_SUSFS_SET_CMDLINE_OR_BOOTCONFIG, info.err)?;
    Ok(())
}
