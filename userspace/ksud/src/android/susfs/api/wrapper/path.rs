use anyhow::Result;

use crate::android::susfs::{
    api::{
        communicate::{communicate, parse_err},
        magic::{
            CMD_SUSFS_ADD_SUS_PATH, CMD_SUSFS_ADD_SUS_PATH_LOOP, ERR_CMD_NOT_SUPPORTED,
            SUSFS_MAX_LEN_PATHNAME,
        },
    },
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

pub fn add_path<S>(path: &S, is_loop: bool) -> Result<()>
where
    S: ToString,
{
    let mut info = SusfsSusPath::default();
    let magic = match is_loop {
        true => CMD_SUSFS_ADD_SUS_PATH_LOOP,
        false => CMD_SUSFS_ADD_SUS_PATH,
    };
    str_to_c_array(path.to_string().as_str(), &mut info.target_pathname);
    info.err = ERR_CMD_NOT_SUPPORTED;

    communicate(magic, &mut info);
    parse_err(magic, info.err)?;
    Ok(())
}
