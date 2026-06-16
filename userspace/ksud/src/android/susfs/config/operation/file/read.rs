use crate::{
    android::susfs::config::{model::Config, operation::file::default::default_config},
    defs::SUSFS_CONFIG,
};
use anyhow::Result;
use std::fs;

pub fn read_config() -> Result<Config> {
    let string = fs::read_to_string(SUSFS_CONFIG)?;
    Ok(serde_json::from_str(&string)?)
}

pub fn read_config_or_default() -> Config {
    match read_config() {
        Ok(config) => config,
        Err(err) => {
            log::warn!("Unable to read SUSFS config: {err}. Using default...");
            default_config()
        }
    }
}
