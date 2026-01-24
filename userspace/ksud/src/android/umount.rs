use std::{
    collections::HashMap,
    fs,
    io::{Read, Write},
    path::Path,
};

use anyhow::Result;
use log::info;
use serde::{Deserialize, Serialize};

use crate::{android::ksucalls, defs};

#[derive(Serialize, Default, Deserialize)]
struct Config {
    paths: HashMap<String, u32>,
}

pub fn load_umount_config() -> Result<()> {
    let config_path = Path::new(defs::UMOUNT_CONFIG_PATH);
    let mut count = 0;

    ensure_config()?;

    let file = fs::read_to_string(config_path)?;
    let json_raw: Config = serde_json::from_str(&file)?;

    for (path, flags) in json_raw.paths {
        ksucalls::umount_list_add(path.as_str(), flags)?;
        count += 1;
    }
    info!("Loaded {count} umount entries from config");
    Ok(())
}

pub fn list_umount() -> Result<()> {
    let config_path = Path::new(defs::UMOUNT_CONFIG_PATH);

    ensure_config()?;

    let file = fs::read_to_string(config_path)?;
    let json_raw: Config = serde_json::from_str(&file)?;

    for (path, flags) in json_raw.paths {
        println!("path: {path}, flag: {flags}");
    }

    Ok(())
}

pub fn add_umount(target_path: &str, flags: u32) -> Result<()> {
    let config_path = Path::new(defs::UMOUNT_CONFIG_PATH);

    ensure_config()?;

    let mut file = fs::OpenOptions::new()
        .write(true)
        .read(true)
        .open(config_path)?;
    let mut buf = String::new();
    file.read_to_string(&mut buf)?;
    let mut json_raw: Config = serde_json::from_str(&buf)?;

    ksucalls::umount_list_add(target_path, flags)?;
    json_raw.paths.insert(target_path.to_string(), flags);
    file.write_all(serde_json::to_string_pretty(&json_raw)?.as_bytes())?;
    Ok(())
}

pub fn del_umount(target_path: &str) -> Result<()> {
    let config_path = Path::new(defs::UMOUNT_CONFIG_PATH);

    ensure_config()?;

    let mut file = fs::OpenOptions::new()
        .write(true)
        .read(true)
        .open(config_path)?;
    let mut buf = String::new();
    file.read_to_string(&mut buf)?;
    let mut json_raw: Config = serde_json::from_str(&buf)?;

    ksucalls::umount_list_del(target_path)?;
    json_raw.paths.remove(target_path);
    file.write_all(serde_json::to_string_pretty(&json_raw)?.as_bytes())?;
    Ok(())
}

fn ensure_config() -> Result<()> {
    let path = Path::new(defs::UMOUNT_CONFIG_PATH);
    let mut file = fs::OpenOptions::new().write(true).read(true).open(path)?;
    let mut buf = String::new();
    file.read_to_string(&mut buf)?;

    if !path.exists() && serde_json::from_str::<Config>(&buf).is_err() {
        let json_raw = Config::default();

        file.write_all(serde_json::to_string_pretty(&json_raw)?.as_bytes())?;
    }

    Ok(())
}

pub fn wipe_umount() -> Result<()> {
    let config_path = Path::new(defs::UMOUNT_CONFIG_PATH);

    ensure_config()?;

    let mut file = fs::OpenOptions::new()
        .write(true)
        .read(true)
        .open(config_path)?;
    let mut buf = String::new();
    file.read_to_string(&mut buf)?;
    let mut json_raw: Config = serde_json::from_str(&buf)?;

    json_raw.paths.clear();
    ksucalls::umount_list_wipe()?;
    file.write_all(serde_json::to_string_pretty(&json_raw)?.as_bytes())?;
    Ok(())
}
