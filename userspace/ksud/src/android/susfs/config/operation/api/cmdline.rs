use crate::android::susfs::config::operation::file::{read_config_or_default, save_config};
use anyhow::{Result, anyhow};
use std::path::Path;

pub fn set_cmdline<P>(path: P) -> Result<()>
where
    P: AsRef<Path>,
{
    let mut config = read_config_or_default();
    let path = path.as_ref().to_str().ok_or(anyhow!("Invalid path"))?;

    config.cmdline = path.to_string();

    save_config(&config)
}
