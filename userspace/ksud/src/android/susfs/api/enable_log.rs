use anyhow::Result;

use crate::android::susfs::{
    magic::{CMD_SUSFS_ENABLE_LOG, ERR_CMD_NOT_SUPPORTED},
    utils::{handle_result, susfs_ctl},
};

#[repr(C)]
struct SusfsLog {
    enabled: bool,
    err: i32,
}

pub fn enable_log(enabled: u8) -> Result<()> {
    if enabled > 1 {
        return Err(anyhow::format_err!("Invalid value for enabled (0 or 1)"));
    }

    let mut info = SusfsLog {
        enabled: enabled == 1,
        err: ERR_CMD_NOT_SUPPORTED,
    };

    susfs_ctl(&mut info, CMD_SUSFS_ENABLE_LOG);
    handle_result(info.err, CMD_SUSFS_ENABLE_LOG)?;
    Ok(())
}
