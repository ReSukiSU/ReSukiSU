//! ## Spoof Uname
//!
//! Spoof uname for all processes, set string to `default` to imply the function to use original string.
//!
//! NOTE: only `release` and `version` are spoofed as others are no longer needed.
//!
//! e.g.: `set_uname("4.9.337-g3291538446b7", "#1 SMP PREEMPT Mon Oct 6 16:50:48 UTC 2025")`

use anyhow::{anyhow, Result};
use rustix::path::Arg;
use crate::android::susfs::{
    magic::{CMD_SUSFS_SET_UNAME, ERR_CMD_NOT_SUPPORTED, NEW_UTS_LEN},
    utils::str_to_c_array,
    communicate::{communicate, parse_err}
};

#[repr(C)]
struct SusfsUname {
    release: [u8; NEW_UTS_LEN + 1],
    version: [u8; NEW_UTS_LEN + 1],
    err: i32,
}

impl Default for SusfsUname {
    fn default() -> Self {
        Self {
            release: [0; NEW_UTS_LEN + 1],
            version: [0; NEW_UTS_LEN + 1],
            err: 0,
        }
    }
}

/// Spoof uname for all processes, set string to `default` to imply the function to use original string.
///
/// NOTE: only `release` and `version` are spoofed as others are no longer needed.
///
/// e.g.: `set_uname("4.9.337-g3291538446b7", "#1 SMP PREEMPT Mon Oct 6 16:50:48 UTC 2025")`
pub fn set_uname<S>(version: &S, release: &S) -> Result<()>
where
    S: ToString,
{
    let mut info = SusfsUname::default();
    let version_str = version.to_string().trim().to_string();
    let release_str = release.to_string().trim().to_string();

    if version_str.len() == 0 || release_str.len() == 0 {
        return Err(anyhow!("Neither version nor release can be empty."));
    }

    // ksud CLI stores version as the visible uname/release value and release as the kernel
    // build-time string;
    // The SuSFS ABI struct keeps the kernel field order (release, then version).
    str_to_c_array(version_str.as_str(), &mut info.release);
    str_to_c_array(release_str.as_str(), &mut info.version);
    info.err = ERR_CMD_NOT_SUPPORTED;

    communicate(CMD_SUSFS_SET_UNAME, &mut info);
    parse_err(CMD_SUSFS_SET_UNAME, info.err)?;

    Ok(())
}
