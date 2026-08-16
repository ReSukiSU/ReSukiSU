use std::fs;

use anyhow::Result;

use crate::android::susfs::api::{
    magic::{
        CMD_SUSFS_SET_CMDLINE_OR_BOOTCONFIG, ERR_CMD_NOT_SUPPORTED,
        SUSFS_FAKE_CMDLINE_OR_BOOTCONFIG_SIZE,
    },
    susfsctl::{parse_err, susfsctl},
};

#[repr(C)]
struct SusfsSpoofCmdlineOrBootconfig {
    fake_cmdline_or_bootconfig: [u8; SUSFS_FAKE_CMDLINE_OR_BOOTCONFIG_SIZE],
    err: i32,
}

pub fn set_cmdline_or_bootconfig(path: &str) -> Result<()> {
    if path.is_empty() {
        return Ok(());
    }

    let abs_path = fs::canonicalize(path)?;
    let content = fs::read(&abs_path)?;
    if content.len() >= SUSFS_FAKE_CMDLINE_OR_BOOTCONFIG_SIZE {
        return Err(anyhow::format_err!("file_size too long"));
    }

    let mut info = Box::new(SusfsSpoofCmdlineOrBootconfig {
        fake_cmdline_or_bootconfig: [0; SUSFS_FAKE_CMDLINE_OR_BOOTCONFIG_SIZE],
        err: ERR_CMD_NOT_SUPPORTED,
    });

    for (i, &b) in content.iter().enumerate() {
        info.fake_cmdline_or_bootconfig[i] = b;
    }

    susfsctl(CMD_SUSFS_SET_CMDLINE_OR_BOOTCONFIG, &mut *info);
    parse_err(CMD_SUSFS_SET_CMDLINE_OR_BOOTCONFIG, info.err)
}
