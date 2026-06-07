use anyhow::Result;

use crate::android::susfs::{
    communicate::{communicate, parse_err},
    magic::{
        CMD_SUSFS_ADD_SUS_PATH, CMD_SUSFS_ADD_SUS_PATH_LOOP, ERR_CMD_NOT_SUPPORTED,
        SUSFS_MAX_LEN_PATHNAME,
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

pub enum SusPathType {
    Normal,
    Loop,
}

/// Add normal or loop sus path.
///
/// ## Variants
///
/// ### Normal
///
/// Added path and all its sub-paths will be hidden for umounted app process from several syscalls.
///
/// Please be reminded that if the target path has upper mounts then make sure the proper layer is
/// added, otherwise it may not be effective for the target process.
///
/// ### Loop
///
/// The only difference to normal one is that the added sus path via this will be flagged as
/// sus path again for the app process when it is being spawned by zygote and marked umounted.
///
/// Also, it does not check if the path is existed or not, instead it checks for empty string only,
/// so be careful what to add.
///
/// ## Important Notice
///
/// - Only effective for umounted process with uid >= 10000
pub fn add_sus_path<S>(types: &SusPathType, path: &S) -> Result<()>
where
    S: ToString,
{
    let mut info = SusfsSusPath::default();
    let magic = match types {
        SusPathType::Normal => CMD_SUSFS_ADD_SUS_PATH,
        SusPathType::Loop => CMD_SUSFS_ADD_SUS_PATH_LOOP,
    };
    str_to_c_array(path.to_string().as_str(), &mut info.target_pathname);
    info.err = ERR_CMD_NOT_SUPPORTED;

    communicate(magic, &mut info);
    parse_err(magic, info.err)?;
    Ok(())
}
