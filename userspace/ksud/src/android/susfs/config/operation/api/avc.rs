use crate::android::susfs::config::operation::file::{read_config_or_default, save_config};
use anyhow::Result;

pub fn avc_spoofing(enabled: bool) -> Result<()> {
    let mut config = read_config_or_default();
    config.avc_spoofing = enabled;

    save_config(&config)
}
