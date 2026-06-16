use crate::android::susfs::config::operation::file::{read_config_or_default, save_config};
use anyhow::Result;

pub fn log(enabled: bool) -> Result<()> {
    let mut config = read_config_or_default();
    config.logging = enabled;

    save_config(&config)
}
