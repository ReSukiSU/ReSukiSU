use anyhow::Result;

use crate::android::susfs::api::{
    communicate::{communicate, parse_err},
    magic::{CMD_SUSFS_ENABLE_LOG, ERR_CMD_NOT_SUPPORTED},
};

#[repr(C)]
struct SusfsLog {
    enabled: bool,
    err: i32,
}

pub fn log(enabled: bool) -> Result<()> {
    let mut info = SusfsLog {
        enabled,
        err: ERR_CMD_NOT_SUPPORTED,
    };

    communicate(CMD_SUSFS_ENABLE_LOG, &mut info);
    parse_err(CMD_SUSFS_ENABLE_LOG, info.err)?;
    Ok(())
}
