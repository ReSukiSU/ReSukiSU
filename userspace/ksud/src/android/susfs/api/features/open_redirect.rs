use std::fs;

use anyhow::{Result, anyhow};

use crate::android::susfs::{
    api::{
        magic::{CMD_SUSFS_ADD_OPEN_REDIRECT, ERR_CMD_NOT_SUPPORTED, SUSFS_MAX_LEN_PATHNAME},
        susfsctl::{communicate, parse_err},
    },
    enums::UidScheme,
    utils::str_to_c_array,
};

#[repr(C)]
struct SusfsOpenRedirect {
    target_pathname: [u8; SUSFS_MAX_LEN_PATHNAME],
    redirected_pathname: [u8; SUSFS_MAX_LEN_PATHNAME],
    uid_scheme: i32,
    err: i32,
}

impl Default for SusfsOpenRedirect {
    fn default() -> Self {
        Self {
            uid_scheme: 0,
            target_pathname: [0; SUSFS_MAX_LEN_PATHNAME],
            redirected_pathname: [0; SUSFS_MAX_LEN_PATHNAME],
            err: 0,
        }
    }
}

pub fn add_open_redirect(target_path: &str, redirected_path: &str, uid_scheme: i32) -> Result<()> {
    if UidScheme::try_from(uid_scheme).is_err() {
        return Err(anyhow::anyhow!("uid_scheme is invalid!"));
    }

    let abs_target = fs::canonicalize(target_path)?;
    let abs_redirect = fs::canonicalize(redirected_path)?;

    let mut info = SusfsOpenRedirect::default();
    str_to_c_array(
        abs_target.to_str().ok_or(anyhow!("Invalid target path!"))?,
        &mut info.target_pathname,
    );
    str_to_c_array(
        abs_redirect
            .to_str()
            .ok_or(anyhow!("Invalid redirect path!"))?,
        &mut info.redirected_pathname,
    );

    info.uid_scheme = uid_scheme;
    info.err = ERR_CMD_NOT_SUPPORTED;

    communicate(CMD_SUSFS_ADD_OPEN_REDIRECT, &mut info);
    parse_err(CMD_SUSFS_ADD_OPEN_REDIRECT, info.err)?;
    Ok(())
}
