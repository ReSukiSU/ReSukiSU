use anyhow::Result;

use crate::android::susfs::{
    communicate::{communicate, parse_err},
    magic::{CMD_SUSFS_HIDE_SUS_MNTS_FOR_NON_SU_PROCS, ERR_CMD_NOT_SUPPORTED},
};

#[repr(C)]
struct SusfsSusMount {
    enabled: bool,
    err: i32,
}

/// Hide sus mounts for non-SU processes.
///
/// **Important Notes**:
/// - It is set to `0` in kernel by default.
/// - For ReZygisk without TreatWheel module, it is recommended to set to `1` in `post-fs-data.sh`
///   to prevent zygote from caching the sus mounts in memory, and revert to `0` in `boot-completed.sh`
///   stage, or keep it enabled if you want to keep them hidden from
///   `/proc/self/(mounts|mountinfo|mountstat)` for non-su processes.
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
