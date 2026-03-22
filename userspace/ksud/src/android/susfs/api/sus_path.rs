use anyhow::Result;

use std::{fs, os::unix::fs::MetadataExt};

use crate::android::susfs::{
    magic::{
        CMD_SUSFS_ADD_SUS_PATH, CMD_SUSFS_ADD_SUS_PATH_LOOP, ERR_CMD_NOT_SUPPORTED,
        SUSFS_MAX_LEN_PATHNAME,
    },
    utils::{handle_result, str_to_c_array, susfs_ctl},
};

#[repr(C)]
struct SusfsSusPath {
    target_ino: u64,
    target_pathname: [u8; SUSFS_MAX_LEN_PATHNAME],
    i_uid: u32,
    err: i32,
}

impl Default for SusfsSusPath {
    fn default() -> Self {
        Self {
            target_ino: 0,
            target_pathname: [0; SUSFS_MAX_LEN_PATHNAME],
            i_uid: 0,
            err: 0,
        }
    }
}

pub enum SusPathType {
    Normal,
    Loop,
}

pub fn add_sus_path<S>(types: &SusPathType, path: &S) -> Result<()>
where
    S: ToString,
{
    let md = fs::metadata(path.to_string())?;
    let mut info = SusfsSusPath::default();
    let magic = match types {
        SusPathType::Normal => CMD_SUSFS_ADD_SUS_PATH,
        SusPathType::Loop => CMD_SUSFS_ADD_SUS_PATH_LOOP,
    };
    str_to_c_array(path.to_string().as_str(), &mut info.target_pathname);
    info.target_ino = md.ino();
    info.i_uid = md.uid();
    info.err = ERR_CMD_NOT_SUPPORTED;

    susfs_ctl(&mut info, magic);
    handle_result(info.err, magic)?;
    Ok(())
}
