use anyhow::Result;

use crate::android::susfs::{
    api::{
        magic::{CMD_SUSFS_ADD_SUS_MAP, ERR_CMD_NOT_SUPPORTED, SUSFS_MAX_LEN_PATHNAME},
        susfsctl::{communicate, parse_err},
    },
    macros::ensure_path_exists,
    utils::str_to_c_array,
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

pub fn add_sus_map(path: &str) -> Result<()> {
    ensure_path_exists!(path);
    let mut info = SusfsSusMap::default();
    str_to_c_array(path, &mut info.target_pathname);
    info.err = ERR_CMD_NOT_SUPPORTED;

    communicate(CMD_SUSFS_ADD_SUS_MAP, &mut info);
    parse_err(CMD_SUSFS_ADD_SUS_MAP, info.err)?;

    Ok(())
}
