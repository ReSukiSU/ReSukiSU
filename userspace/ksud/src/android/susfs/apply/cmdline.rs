use crate::android::susfs::{api, config::Config};
use anyhow::Result;

pub fn set_cmdline(config: &Config) -> Result<()> {
    api::set_cmdline(&config.cmdline)
}
