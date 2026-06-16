use anyhow::{Result, anyhow};
use std::{fs, os::unix::fs::PermissionsExt, path::Path};

use crate::{
    android::susfs::{config::model::Config, utils::tmp_path},
    defs::SUSFS_CONFIG,
};

pub fn save_config(config: &Config) -> Result<()> {
    let string = serde_json::to_string_pretty(&config)?;
    write_config(&string)
}

fn write_config(string: &str) -> Result<()> {
    let path = Path::new(SUSFS_CONFIG);
    let parent = path
        .parent()
        .ok_or(anyhow!("{} is not valid", path.display()))?;
    fs::create_dir_all(parent)?;

    let tmp_path = tmp_path(path);
    fs::write(&tmp_path, string)?;

    // Android must be unix, so don't need to check the platform.

    fs::set_permissions(&tmp_path, fs::Permissions::from_mode(0o600))?;

    fs::rename(&tmp_path, path)?;
    let _ = fs::remove_file(&tmp_path);

    Ok(())
}
