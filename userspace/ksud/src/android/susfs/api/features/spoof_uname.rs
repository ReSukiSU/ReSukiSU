use anyhow::{Result, anyhow};

use crate::android::susfs::{
    api::{
        magic::{CMD_SUSFS_SET_UNAME, ERR_CMD_NOT_SUPPORTED, NEW_UTS_LEN},
        susfsctl::{communicate, parse_err},
    },
    utils::{is_valid_uname_release, is_valid_uname_version, str_to_c_array},
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

pub fn set_uname(version: &str, release: &str) -> Result<()> {
    let mut info = SusfsUname::default();
    let version_str = version.trim();
    let release_str = release.trim();

    if !is_valid_uname_version(version_str) || !is_valid_uname_release(release_str) {
        return Err(anyhow!(
            "Uname version ({}) or release ({}) invalid!",
            version_str,
            release_str
        ));
    }

    // ksud stores spoof_version as the visible uname/release value and spoof_release
    // as the kernel build-time string.
    // The SuSFS ABI struct keeps the kernel field order (release, then version).
    str_to_c_array(version_str, &mut info.release);
    str_to_c_array(release_str, &mut info.version);
    info.err = ERR_CMD_NOT_SUPPORTED;

    communicate(CMD_SUSFS_SET_UNAME, &mut info);
    parse_err(CMD_SUSFS_SET_UNAME, info.err)
}
