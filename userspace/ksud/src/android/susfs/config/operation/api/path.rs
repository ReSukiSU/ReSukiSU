use crate::android::susfs::config::{
    model::SusPathItem,
    operation::file::{read_config_or_default, save_config},
};
use anyhow::{Result, anyhow};
use std::path::Path;

pub fn add_path<P>(path: P, is_loop: bool) -> Result<()>
where
    P: AsRef<Path>,
{
    let mut config = read_config_or_default();
    let path = path.as_ref().to_str().ok_or(anyhow!("Invalid path!"))?;

    config.path.insert(SusPathItem {
        path: path.to_string(),
        is_loop,
    });

    save_config(&config)
}

pub fn del_path<P>(path: P) -> Result<()>
where
    P: AsRef<Path>,
{
    let mut config = read_config_or_default();
    let path = path.as_ref().to_str().ok_or(anyhow!("Invalid path!"))?;

    config.path.remove(path);

    save_config(&config)
}
