use anyhow::Result;

use crate::android::susfs::api::{
    communicate::{communicate, parse_err},
    magic::{CMD_SUSFS_ENABLE_AVC_LOG_SPOOFING, ERR_CMD_NOT_SUPPORTED},
};

#[repr(C)]
struct AvcLogSpoofing {
    enabled: bool,
    err: i32,
}

pub fn avc_spoofing(enabled: bool) -> Result<()> {
    let mut arg = AvcLogSpoofing {
        enabled,
        err: ERR_CMD_NOT_SUPPORTED,
    };

    communicate(CMD_SUSFS_ENABLE_AVC_LOG_SPOOFING, &mut arg);
    parse_err(CMD_SUSFS_ENABLE_AVC_LOG_SPOOFING, arg.err)?;
    Ok(())
}
