use anyhow::Result;

use crate::android::susfs::api::{
    magic::{CMD_SUSFS_ENABLE_AVC_LOG_SPOOFING, ERR_CMD_NOT_SUPPORTED},
    susfsctl::{communicate, parse_err},
};

#[repr(C)]
struct SusfsAvcLogSpoofing {
    enabled: bool,
    err: i32,
}

pub fn enable_avc_log_spoofing(enabled: bool) -> Result<()> {
    let mut arg = SusfsAvcLogSpoofing {
        enabled,
        err: ERR_CMD_NOT_SUPPORTED,
    };

    communicate(CMD_SUSFS_ENABLE_AVC_LOG_SPOOFING, &mut arg);
    parse_err(CMD_SUSFS_ENABLE_AVC_LOG_SPOOFING, arg.err)
}
