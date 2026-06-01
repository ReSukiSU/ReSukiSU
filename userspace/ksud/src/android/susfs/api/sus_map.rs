use std::path::Path;

use anyhow::Result;

use crate::android::susfs::{
    magic::{CMD_SUSFS_ADD_SUS_MAP, ERR_CMD_NOT_SUPPORTED, SUSFS_MAX_LEN_PATHNAME},
    utils::{handle_result, str_to_c_array, susfs_ctl},
};

#[repr(C)]
struct SusfsSusMap {
    target_pathname: [u8; SUSFS_MAX_LEN_PATHNAME],
    err: i32,
}

impl Default for SusfsSusMap {
    fn default() -> Self {
        Self {
            target_pathname: [0; SUSFS_MAX_LEN_PATHNAME],
            err: 0,
        }
    }
}

pub fn add_sus_map<P>(path: P) -> Result<()>
where
    P: AsRef<Path>,
{
    let mut info = SusfsSusMap::default();
    str_to_c_array(
        path.as_ref().to_str().unwrap_or_default(),
        &mut info.target_pathname,
    );
    info.err = ERR_CMD_NOT_SUPPORTED;

    susfs_ctl(&mut info, CMD_SUSFS_ADD_SUS_MAP);
    handle_result(info.err, CMD_SUSFS_ADD_SUS_MAP)?;

    Ok(())
}
