use anyhow::Result;

use crate::android::susfs::api::{
    communicate::{communicate, parse_err},
    magic::{CMD_SUSFS_HIDE_SUS_MNTS_FOR_NON_SU_PROCS, ERR_CMD_NOT_SUPPORTED},
};

#[repr(C)]
struct SusfsSusMount {
    enabled: bool,
    err: i32,
}

pub fn ignore_umount(enabled: bool) -> Result<()> {
    let mut info = SusfsSusMount {
        enabled,
        err: ERR_CMD_NOT_SUPPORTED,
    };

    communicate(CMD_SUSFS_HIDE_SUS_MNTS_FOR_NON_SU_PROCS, &mut info);
    parse_err(CMD_SUSFS_HIDE_SUS_MNTS_FOR_NON_SU_PROCS, info.err)?;
    Ok(())
}
