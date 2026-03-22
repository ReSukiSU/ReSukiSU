use std::{fs, path::Path};

use anyhow::Result;

use crate::android::susfs::{
    magic::{CMD_SUSFS_ADD_OPEN_REDIRECT, ERR_CMD_NOT_SUPPORTED, SUSFS_MAX_LEN_PATHNAME},
    utils::{handle_result, str_to_c_array, susfs_ctl},
};

#[repr(C)]
struct SusfsOpenRedirect {
    uid_scheme: u64,
    target_pathname: [u8; SUSFS_MAX_LEN_PATHNAME],
    redirected_pathname: [u8; SUSFS_MAX_LEN_PATHNAME],
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

pub fn add_open_redirect<P>(target_path: P, redirected_path: P, uid_scheme: u64) -> Result<()>
where
    P: AsRef<Path>,
{
    let abs_target = fs::canonicalize(&target_path)?;
    let abs_redirect = fs::canonicalize(&redirected_path)?;

    let mut info = SusfsOpenRedirect::default();
    str_to_c_array(abs_target.to_str().unwrap(), &mut info.target_pathname);
    str_to_c_array(
        abs_redirect.to_str().unwrap(),
        &mut info.redirected_pathname,
    );

    info.uid_scheme = uid_scheme;
    info.err = ERR_CMD_NOT_SUPPORTED;

    susfs_ctl(&mut info, CMD_SUSFS_ADD_OPEN_REDIRECT);
    handle_result(info.err, CMD_SUSFS_ADD_OPEN_REDIRECT)?;
    Ok(())
}
