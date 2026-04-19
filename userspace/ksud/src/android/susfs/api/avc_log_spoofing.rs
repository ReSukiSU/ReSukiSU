use anyhow::Result;

use crate::android::susfs::{
    magic::{CMD_SUSFS_ENABLE_AVC_LOG_SPOOFING, ERR_CMD_NOT_SUPPORTED},
    utils::{handle_result, susfs_ctl},
};

#[repr(C)]
struct AvcLogSpoofing {
    enabled: bool,
    err: i32,
}

pub fn enable_avc_log_spoofing(enabled: u8) -> Result<()> {
    if enabled > 1 {
        return Err(anyhow::format_err!("Invalid value for enabled (0 or 1)"));
    }

    let mut info = AvcLogSpoofing {
        enabled: enabled == 1,
        err: ERR_CMD_NOT_SUPPORTED,
    };

    susfs_ctl(&mut info, CMD_SUSFS_ENABLE_AVC_LOG_SPOOFING);
    handle_result(info.err, CMD_SUSFS_ENABLE_AVC_LOG_SPOOFING)?;
    Ok(())
}
