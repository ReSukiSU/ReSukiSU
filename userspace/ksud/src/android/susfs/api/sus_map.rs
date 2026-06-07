use std::path::Path;

use anyhow::Result;

use crate::android::susfs::{
    communicate::{communicate, parse_err},
    magic::{CMD_SUSFS_ADD_SUS_MAP, ERR_CMD_NOT_SUPPORTED, SUSFS_MAX_LEN_PATHNAME},
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

/// Added real file path which gets mmapped will be hidden from
/// `/proc/self/(maps|smaps|smaps_rollup|map_files|mem|pagemap)`
///
/// e.g. `add_sus_map("/data/adb/modules/my_module/zygisk/arm64-v8a.so")`
///
/// **Important Notes**:
/// - It does NOT support hiding for anon memory.
/// - It does NOT hide any inline hooks or plt hooks cause by the injected library itself.
/// - It may not be able to evade detections by apps that implement a good injection detection.
/// - Only effective for umounted process with uid >= 10000
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

    communicate(CMD_SUSFS_ADD_SUS_MAP, &mut info);
    parse_err(CMD_SUSFS_ADD_SUS_MAP, info.err)?;

    Ok(())
}
