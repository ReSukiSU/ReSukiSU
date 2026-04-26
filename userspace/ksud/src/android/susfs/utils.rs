use anyhow::Result;
use libc::{SYS_reboot, syscall};

use crate::android::susfs::magic::{ERR_CMD_NOT_SUPPORTED, KSU_INSTALL_MAGIC1, SUSFS_MAGIC};

pub(super) fn susfs_ctl<T>(info: &mut T, cmd: u64) {
    unsafe {
        syscall(
            SYS_reboot,
            KSU_INSTALL_MAGIC1,
            SUSFS_MAGIC,
            cmd,
            std::ptr::from_mut::<T>(info),
        );
    }
}

pub(super) fn str_to_c_array<const N: usize>(s: &str, array: &mut [u8; N]) {
    let bytes = s.as_bytes();
    let len = bytes.len().min(N - 1);
    array[..len].copy_from_slice(&bytes[..len]);
    array[len] = 0;
}
pub(super) fn handle_result(err: i32, cmd: u64) -> Result<()> {
    if err == ERR_CMD_NOT_SUPPORTED {
        return Err(anyhow::format_err!(
            "unsupported susfs operation, cmd: 0x{cmd:x}"
        ));
    }
    if err != 0 && err != ERR_CMD_NOT_SUPPORTED {
        return Err(anyhow::format_err!("{err}"));
    }

    Ok(())
}
