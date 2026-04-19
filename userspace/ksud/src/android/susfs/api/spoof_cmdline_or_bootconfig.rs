use std::{fs, path::Path};

use anyhow::Result;

use crate::android::susfs::{
    magic::{
        CMD_SUSFS_SET_CMDLINE_OR_BOOTCONFIG, ERR_CMD_NOT_SUPPORTED,
        SUSFS_FAKE_CMDLINE_OR_BOOTCONFIG_SIZE,
    },
    utils::{handle_result, susfs_ctl},
};

#[repr(C)]
struct SusfsSpoofCmdline {
    fake_cmdline_or_bootconfig: [u8; SUSFS_FAKE_CMDLINE_OR_BOOTCONFIG_SIZE],
    err: i32,
}

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

    susfs_ctl(&mut *info, CMD_SUSFS_SET_CMDLINE_OR_BOOTCONFIG);
    handle_result(info.err, CMD_SUSFS_SET_CMDLINE_OR_BOOTCONFIG)?;
    Ok(())
}
