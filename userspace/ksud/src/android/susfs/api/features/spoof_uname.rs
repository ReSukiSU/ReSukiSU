use anyhow::Result;

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

pub fn set_uname(release: &str, version: &str) -> Result<()> {
    let mut info = SusfsUname::default();
    let mut release = release.to_string();
    let mut version = version.to_string();

    if !(is_valid_uname_release(&release) && is_valid_uname_version(&version)) {
        log::warn!(
            "Uname release ({}) or version ({}) invalid! Falling back to default...",
            release,
            version
        );
        release = "default".to_string();
        version = "default".to_string();
    }

    // ksud stores spoof_version as the visible uname/release value and spoof_release
    // as the kernel build-time string.
    // The SuSFS ABI struct keeps the kernel field order (release, then version).
    str_to_c_array(&release, &mut info.release);
    str_to_c_array(&version, &mut info.version);
    info.err = ERR_CMD_NOT_SUPPORTED;

    communicate(CMD_SUSFS_SET_UNAME, &mut info);
    parse_err(CMD_SUSFS_SET_UNAME, info.err)
}
