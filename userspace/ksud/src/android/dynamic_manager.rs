use std::{
    fs::{self, OpenOptions},
    io::Write,
    path::Path,
};

use anyhow::Result;
use serde::{Deserialize, Serialize};

use crate::{android::ksucalls, defs};

#[derive(Debug, Deserialize, Serialize)]
struct Config {
    size: u32,
    hash: String,
}

impl Config {
    fn hash_bytes(&self) -> Result<[u8; 65], String> {
        parse_hash(&self.hash)
    }

    pub fn set_hash_from_bytes(&mut self, hash: [u8; 65]) {
        self.hash = String::from_utf8_lossy(&hash).to_string();
    }
}

pub fn booted_load() -> Result<()> {
    let path = Path::new(defs::DYNAMIC_MANAGER);

    if !path.exists() {
        return Ok(());
    }

    let buf = fs::read_to_string(path)?;

    let json: Config = serde_json::from_str(&buf)?;
    let Ok(hash) = json.hash_bytes() else {
        return Ok({});
    };

    ksucalls::dynamic_manager_set(json.size, hash)?;
    Ok(())
}

pub fn parse_hash(s: &str) -> Result<[u8; 65], String> {
    let bytes = s.as_bytes();

    if bytes.len() != 65 {
        return Err(format!("Incorrect hash"));
    }

    let mut hash = [0u8; 65];
    hash.copy_from_slice(bytes);

    Ok(hash)
}

pub fn set(size: u32, hash: [u8; 65]) -> Result<()> {
    let mut json_raw = Config {
        size,
        hash: String::new(),
    };
    json_raw.set_hash_from_bytes(hash);

    let string = serde_json::to_string_pretty(&json_raw)?;
    let mut file = OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(defs::DYNAMIC_MANAGER)?;

    file.write_all(string.as_bytes())?;

    ksucalls::dynamic_manager_set(size, hash)?;
    Ok(())
}
