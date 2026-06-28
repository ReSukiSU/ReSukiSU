use anyhow::Result;

use crate::android::susfs::{
    api::{
        magic::{
            CMD_SUSFS_ADD_SUS_PATH, CMD_SUSFS_ADD_SUS_PATH_LOOP, ERR_CMD_NOT_SUPPORTED,
            SUSFS_MAX_LEN_PATHNAME,
        },
        susfsctl::{communicate, parse_err},
    },
    macros::ensure_path_exists,
    utils::str_to_c_array,
};

#[repr(C)]
struct SusfsSusPath {
    target_pathname: [u8; SUSFS_MAX_LEN_PATHNAME],
    err: i32,
}

impl Default for SusfsSusPath {
    fn default() -> Self {
        Self {
            target_pathname: [0; SUSFS_MAX_LEN_PATHNAME],
            err: 0,
        }
    }
}

pub fn add_sus_path(path: &str, is_loop: bool) -> Result<()> {
    ensure_path_exists!(path);

    let mut info = SusfsSusPath::default();
    let magic = match is_loop {
        true => CMD_SUSFS_ADD_SUS_PATH_LOOP,
        false => CMD_SUSFS_ADD_SUS_PATH,
    };
    str_to_c_array(path, &mut info.target_pathname);
    info.err = ERR_CMD_NOT_SUPPORTED;

    communicate(magic, &mut info);
    parse_err(magic, info.err)?;
    Ok(())
}
