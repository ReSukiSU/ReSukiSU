//! ## AVC Log Spoofing
//!
//! Spoofing the sus tcontext `su` with `u:r:priv_app:s0:c512,c768` shown in AVC log in kernel.
//!
//! **Important Notes**:
//! - It is set to `0` by default in kernel.
//! - Enabling this may sometimes make developers hard to identify the cause when they are debugging
//! with some permission or SELinux issues, so users are advised to disable this when doing so.

use anyhow::Result;

use crate::android::susfs::{
    magic::{CMD_SUSFS_ENABLE_AVC_LOG_SPOOFING, ERR_CMD_NOT_SUPPORTED},
    communicate::{communicate, parse_err},
};

#[repr(C)]
struct AvcLogSpoofing {
    enabled: bool,
    err: i32,
}

/// Spoofing the sus tcontext `su` with `u:r:priv_app:s0:c512,c768` shown in AVC log in kernel.
///
/// **Important Notes**:
/// - It is set to `0` by default in kernel.
/// - Enabling this may sometimes make developers hard to identify the cause when they are debugging
/// with some permission or SELinux issues, so users are advised to disable this when doing so.
pub fn enable_avc_log_spoofing(enabled: u8) -> Result<()> {
    if enabled > 1 {
        return Err(anyhow::format_err!("Invalid value for enabled (0 or 1)"));
    }

    let mut arg = AvcLogSpoofing {
        enabled: enabled == 1,
        err: ERR_CMD_NOT_SUPPORTED,
    };

    communicate(CMD_SUSFS_ENABLE_AVC_LOG_SPOOFING, &mut arg);
    parse_err(CMD_SUSFS_ENABLE_AVC_LOG_SPOOFING, arg.err)?;
    Ok(())
}
