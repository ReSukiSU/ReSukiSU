use anyhow::Result;

use crate::android::susfs::{
    magic::{CMD_SUSFS_SET_UNAME, ERR_CMD_NOT_SUPPORTED, NEW_UTS_LEN},
    utils::{handle_result, str_to_c_array, susfs_ctl},
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

pub fn set_uname<S>(release: &S, version: &S) -> Result<()>
where
    S: ToString,
{
    let mut info = SusfsUname::default();
    str_to_c_array(release.to_string().as_str(), &mut info.release);
    str_to_c_array(version.to_string().as_str(), &mut info.version);
    info.err = ERR_CMD_NOT_SUPPORTED;

    susfs_ctl(&mut info, CMD_SUSFS_SET_UNAME);
    handle_result(info.err, CMD_SUSFS_SET_UNAME)?;

    Ok(())
}
