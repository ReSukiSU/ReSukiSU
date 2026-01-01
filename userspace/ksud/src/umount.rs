use std::{collections::HashMap, fs, path::Path};

use anyhow::Result;
use log::info;
use serde::{Deserialize, Serialize};

use crate::{defs, ksucalls};

#[derive(Serialize, Deserialize)]
struct Config {
    paths: HashMap<String, u32>,
}

pub fn load_umount_config() -> Result<()> {
    let config_path = Path::new(defs::UMOUNT_CONFIG_PATH);
    let mut count = 0;

    if !config_path.exists() {
        info!("Umount config file does not exist, skipping");
        return Ok(());
    }

    let file = fs::read_to_string(config_path)?;
    let json_raw: Config = serde_json::from_str(&file)?;

    for (path, flags) in json_raw.paths {
        ksucalls::umount_list_add(&path, flags)?;
        count += 1;
    }
    info!("Loaded {count} umount entries from config");
    Ok(())
}

pub fn add_umount(target_path: &str, flags: u32) -> Result<()> {
    let config_path = Path::new(defs::UMOUNT_CONFIG_PATH);

    if !config_path.exists() {
        info!("Umount config file does not exist, skipping");
        return Ok(());
    }

    let file = fs::read_to_string(config_path)?;
    let mut json_raw: Config = serde_json::from_str(&file)?;

    ksucalls::umount_list_add(target_path, flags)?;
    json_raw.paths.insert(target_path.to_string(), flags);
    Ok(())
}

pub fn del_umount(target_path: &str) -> Result<()> {
    let config_path = Path::new(defs::UMOUNT_CONFIG_PATH);

    if !config_path.exists() {
        info!("Umount config file does not exist, skipping");
        return Ok(());
    }

    let file = fs::read_to_string(config_path)?;
    let mut json_raw: Config = serde_json::from_str(&file)?;

    ksucalls::umount_list_del(target_path)?;
    json_raw.paths.remove(target_path);
    Ok(())
}
