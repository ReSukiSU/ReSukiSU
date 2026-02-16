use std::{collections::HashMap, fs, path::Path};

use anyhow::Result;
use log::info;
use serde::{Deserialize, Serialize};

use crate::{
    android::ksucalls,
    defs::{self, MountInfo},
};

#[derive(Serialize, Default, Deserialize)]
struct Config {
    paths: HashMap<String, u32>,
}

fn read_config() -> Result<Config> {
    ensure_config()?;
    let content = fs::read_to_string(defs::UMOUNT_CONFIG_PATH)?;
    let config: Config = serde_json::from_str(&content).unwrap_or_default();
    Ok(config)
}

fn write_config(config: &Config) -> Result<()> {
    let content = serde_json::to_string_pretty(config)?;
    fs::write(defs::UMOUNT_CONFIG_PATH, content)?;
    Ok(())
}

pub fn load_umount_config() -> Result<()> {
    let json_raw = read_config()?;
    let mut count = 0;

    for (path, flags) in json_raw.paths {
        ksucalls::umount_list_add(path.as_str(), flags)?;
        count += 1;
    }
    info!("Loaded {count} umount entries from config");
    Ok(())
}

pub fn list_umount() -> Result<()> {
    let json_raw = read_config()?;

    let output: Vec<MountInfo> = json_raw
        .paths
        .into_iter()
        .map(|(path, flags)| MountInfo { path, flags })
        .collect();

    let json_output = serde_json::to_string(&output)?;
    println!("{json_output}");

    Ok(())
}

pub fn add_umount(target_path: &str, flags: u32) -> Result<()> {
    let mut json_raw = read_config()?;
    json_raw.paths.insert(target_path.to_string(), flags);
    write_config(&json_raw)
}

pub fn del_umount(target_path: &str) -> Result<()> {
    let mut json_raw = read_config()?;
    if json_raw.paths.remove(target_path).is_some() {
        write_config(&json_raw)?;
    }
    Ok(())
}

pub fn wipe_umount() -> Result<()> {
    let mut json_raw = read_config()?;
    json_raw.paths.clear();
    write_config(&json_raw)
}

fn ensure_config() -> Result<()> {
    let path = Path::new(defs::UMOUNT_CONFIG_PATH);

    if !path.exists() {
        let content = serde_json::to_string_pretty(&Config::default())?;
        fs::write(path, content)?;
        return Ok(());
    }

    let buf = fs::read_to_string(path)?;
    if buf.trim().is_empty() || serde_json::from_str::<Config>(&buf).is_err() {
        let content = serde_json::to_string_pretty(&Config::default())?;
        fs::write(path, content)?;
    }

    Ok(())
}
