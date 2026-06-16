use anyhow::{Result, anyhow};

use crate::android::susfs::{
    api::{
        communicate::{communicate, parse_err},
        magic::{CMD_SUSFS_SET_UNAME, ERR_CMD_NOT_SUPPORTED, NEW_UTS_LEN},
    },
    utils::str_to_c_array,
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

pub fn set_uname<S>(version: &S, release: &S) -> Result<()>
where
    S: ToString,
{
    let mut info = SusfsUname::default();
    let version_str = version.to_string().trim().to_string();
    let release_str = release.to_string().trim().to_string();

    if version_str.is_empty() || release_str.is_empty() {
        return Err(anyhow!("Neither version nor release can be empty."));
    }

    // ksud stores spoof_version as the visible uname/release value and spoof_release
    // as the kernel build-time string.
    // The SuSFS ABI struct keeps the kernel field order (release, then version).
    str_to_c_array(version_str.as_str(), &mut info.release);
    str_to_c_array(release_str.as_str(), &mut info.version);
    info.err = ERR_CMD_NOT_SUPPORTED;

    communicate(CMD_SUSFS_SET_UNAME, &mut info);
    parse_err(CMD_SUSFS_SET_UNAME, info.err)?;

    Ok(())
}
