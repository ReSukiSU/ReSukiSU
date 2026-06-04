use anyhow::Result;

use crate::android::susfs::{
    magic::{CMD_SUSFS_HIDE_SUS_MNTS_FOR_NON_SU_PROCS, ERR_CMD_NOT_SUPPORTED},
    communicate::{communicate, parse_err},
};

#[repr(C)]
struct SusfsSusMount {
    enabled: bool,
    err: i32,
}

pub fn hide_sus_mnts_for_non_su_procs(enabled: u8) -> Result<()> {
    if enabled > 1 {
        return Err(anyhow::format_err!("Invalid value for enabled (0 or 1)"));
    }

    let mut info = SusfsSusMount {
        enabled: enabled == 1,
        err: ERR_CMD_NOT_SUPPORTED,
    };

    communicate(CMD_SUSFS_HIDE_SUS_MNTS_FOR_NON_SU_PROCS, &mut info);
    parse_err(CMD_SUSFS_HIDE_SUS_MNTS_FOR_NON_SU_PROCS, info.err)?;
    Ok(())
}
