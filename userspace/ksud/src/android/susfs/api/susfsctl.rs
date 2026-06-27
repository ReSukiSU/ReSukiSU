//! Functions used to communicate with SuSFS

use anyhow::Result;
use libc::{SYS_reboot, syscall};

use crate::android::susfs::api::magic::{ERR_CMD_NOT_SUPPORTED, KSU_INSTALL_MAGIC1, SUSFS_MAGIC};

/// Communicate with SuSFS
pub(super) fn communicate<T>(cmd: u64, arg: &mut T) {
    unsafe {
        syscall(
            SYS_reboot,
            KSU_INSTALL_MAGIC1,
            SUSFS_MAGIC,
            cmd,
            std::ptr::from_mut::<T>(arg),
        );
    }
}

/// Parse error code to Err
pub(super) fn parse_err(cmd: u64, error: i32) -> Result<()> {
    if error == ERR_CMD_NOT_SUPPORTED {
        return Err(anyhow::format_err!("Unsupported SuSFS command: 0x{cmd:x}"));
    }
    if error != 0 {
        return Err(anyhow::format_err!("SuSFS error: {error}"));
    }
    Ok(())
}
