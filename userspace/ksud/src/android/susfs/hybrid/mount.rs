use crate::android::susfs::{api, config};
use anyhow::Result;

pub fn ignore_umount(enabled: bool) -> Result<()> {
    api::ignore_umount(enabled)?;
    config::ignore_umount(enabled)
}
