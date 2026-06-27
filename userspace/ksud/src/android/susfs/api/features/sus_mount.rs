use anyhow::Result;

use crate::android::susfs::api::{
    magic::{CMD_SUSFS_HIDE_SUS_MNTS_FOR_NON_SU_PROCS, ERR_CMD_NOT_SUPPORTED},
    susfsctl::{communicate, parse_err},
};

#[repr(C)]
struct SusfsHideSusMntsForNonSuProcs {
    enabled: bool,
    err: i32,
}

pub fn hide_sus_mnts_for_non_su_procs(enabled: bool) -> Result<()> {
    let mut info = SusfsHideSusMntsForNonSuProcs {
        enabled,
        err: ERR_CMD_NOT_SUPPORTED,
    };

    communicate(CMD_SUSFS_HIDE_SUS_MNTS_FOR_NON_SU_PROCS, &mut info);
    parse_err(CMD_SUSFS_HIDE_SUS_MNTS_FOR_NON_SU_PROCS, info.err)?;
    Ok(())
}
