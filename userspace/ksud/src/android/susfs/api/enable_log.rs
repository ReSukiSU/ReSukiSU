//! ## Enable Log
//!
//! Enable SuSFS log in kernel.

use anyhow::Result;

use crate::android::susfs::{
    magic::{CMD_SUSFS_ENABLE_LOG, ERR_CMD_NOT_SUPPORTED},
    communicate::{communicate, parse_err},
};

#[repr(C)]
struct SusfsLog {
    enabled: bool,
    err: i32,
}

/// Enable SuSFS log in kernel.
pub fn enable_log(enabled: u8) -> Result<()> {
    if enabled > 1 {
        return Err(anyhow::format_err!("Invalid value for enabled (0 or 1)"));
    }

    let mut info = SusfsLog {
        enabled: enabled == 1,
        err: ERR_CMD_NOT_SUPPORTED,
    };

    communicate(CMD_SUSFS_ENABLE_LOG, &mut info);
    parse_err(CMD_SUSFS_ENABLE_LOG, info.err)?;
    Ok(())
}
