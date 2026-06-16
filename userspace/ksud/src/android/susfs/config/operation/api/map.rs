use crate::android::susfs::config::operation::file::{read_config_or_default, save_config};
use anyhow::{Result, anyhow};
use std::path::Path;

pub fn add_map<P>(path: P) -> Result<()>
where
    P: AsRef<Path>,
{
    let mut config = read_config_or_default();
    config.map.insert(
        path.as_ref()
            .to_str()
            .ok_or(anyhow!("Invalid path!"))?
            .to_string(),
    );

    save_config(&config)
}

pub fn del_map<P>(path: P) -> Result<()>
where
    P: AsRef<Path>,
{
    let mut config = read_config_or_default();
    config
        .map
        .remove(path.as_ref().to_str().ok_or(anyhow!("Invalid path!"))?);

    save_config(&config)
}
