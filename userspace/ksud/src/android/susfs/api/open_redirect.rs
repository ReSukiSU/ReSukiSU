use std::{fs, path::Path};

use anyhow::Result;
use num_enum::{IntoPrimitive, TryFromPrimitive};

use crate::android::susfs::{
    communicate::{communicate, parse_err},
    magic::{CMD_SUSFS_ADD_OPEN_REDIRECT, ERR_CMD_NOT_SUPPORTED, SUSFS_MAX_LEN_PATHNAME},
    utils::str_to_c_array,
};

#[repr(C)]
struct SusfsOpenRedirect {
    target_pathname: [u8; SUSFS_MAX_LEN_PATHNAME],
    redirected_pathname: [u8; SUSFS_MAX_LEN_PATHNAME],
    uid_scheme: i32,
    err: i32,
}

#[derive(Debug, PartialEq, TryFromPrimitive, IntoPrimitive)]
#[repr(i32)]
pub enum UidScheme {
    NonApp = 0,
    RootExceptSu = 1,
    NonSu = 2,
    UnmountedApp = 3,
    Unmounted = 4,
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

/// Redirect the target path to be opened with user defined path and pre-defined uid scheme
///
/// `<uid_scheme>`
/// - `0`: Effective for non-app processes (uid < 10000)
/// - `1`: Effective for non-su processes of which uid is 0 (All root process but not with su domain)
/// - `2`: Effective for non-su processes (Use it carefully!)
/// - `3`: Effective for processes that are marked unmounted with uid >= 10000 (Use it carefully!)
/// - `4`: Effective for processes that are marked unmounted (include most of the init spawned process,
///   use it carefully!)
///
/// Important Notes:
/// - Both target_pathname and redirected_pathname must be existed before they can be added to open_redirect
/// - Users have to take care of the SELinux permission of both target_pathname and redirected_pathname
///   by themselves
/// - Only effective for current process that matches the pre-defined uid scheme
pub fn add_open_redirect<P>(target_path: P, redirected_path: P, uid_scheme: i32) -> Result<()>
where
    P: AsRef<Path>,
{
    if UidScheme::try_from(uid_scheme).is_err() {
        return Err(anyhow::anyhow!("uid_scheme is invalid!"));
    }

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

    communicate(CMD_SUSFS_ADD_OPEN_REDIRECT, &mut info);
    parse_err(CMD_SUSFS_ADD_OPEN_REDIRECT, info.err)?;
    Ok(())
}
