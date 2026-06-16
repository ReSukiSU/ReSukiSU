use crate::android::susfs::config::operation::file::{read_config_or_default, save_config};
use anyhow::Result;

pub fn ignore_umount(enabled: bool) -> Result<()> {
    let mut config = read_config_or_default();
    config.ignore_umount = enabled;

    save_config(&config)
}
