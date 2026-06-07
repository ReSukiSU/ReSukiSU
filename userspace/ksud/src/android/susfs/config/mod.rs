pub mod data;
pub mod operation;

use std::{
    fs,
    path::{Path, PathBuf},
};

use crate::defs;

use data::Data;

const INIT_NAMESPACE_SUSFS_CONFIG: &str = "/proc/1/root/data/adb/ksu/.susfs.json";

fn save_config(config: &Data) {
    let Ok(string) = serde_json::to_string_pretty(&config) else {
        log::warn!("failed to serialize susfs config");
        return;
    };

    let mut last_error = None;
    for path in config_paths() {
        match write_config_to_path(path, &string) {
            Ok(()) => return,
            Err(e) => last_error = Some((path.to_string(), e)),
        }
    }

    if let Some((path, e)) = last_error {
        log::warn!("failed to write susfs config to {path}, Err: {e}");
    }
}

pub fn read_config() -> Data {
    let string = match read_config_string() {
        Some(s) => s,
        None => {
            log::warn!("failed to read susfs config, will use default config");
            let config = Data::default();
            save_config(&config);
            return config;
        }
    };

    let mut value: serde_json::Value = match serde_json::from_str(&string) {
        Ok(s) => s,
        Err(e) => {
            log::warn!("failed to deserialize susfs config, Err: {e}, will use default config");
            let config = Data::default();
            save_config(&config);
            return config;
        }
    };
    let migrated_legacy_uname = migrate_legacy_uname_fields(&mut value);

    let json: Data = match serde_json::from_value(value) {
        Ok(s) => s,
        Err(e) => {
            log::warn!("failed to deserialize susfs config, Err: {e}, will use default config");
            let config = Data::default();
            save_config(&config);
            return config;
        }
    };

    if migrated_legacy_uname {
        save_config(&json);
    }

    json
}

fn config_paths() -> [&'static str; 2] {
    [INIT_NAMESPACE_SUSFS_CONFIG, defs::SUSFS_CONFUG]
}

fn read_config_string() -> Option<String> {
    let mut last_error = None;
    for path in config_paths() {
        match fs::read_to_string(path) {
            Ok(s) => return Some(s),
            Err(e) => last_error = Some((path.to_string(), e)),
        }
    }
    if let Some((path, e)) = last_error {
        log::warn!("failed to read susfs config from {path}, Err: {e}");
    }
    None
}

fn write_config_to_path(path: &str, string: &str) -> std::io::Result<()> {
    let path = Path::new(path);
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)?;
    }

    let tmp_path = temp_config_path(path);
    fs::write(&tmp_path, string)?;

    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        let _ = fs::set_permissions(&tmp_path, fs::Permissions::from_mode(0o600));
    }

    if let Err(e) = fs::rename(&tmp_path, path) {
        let _ = fs::remove_file(&tmp_path);
        return Err(e);
    }

    Ok(())
}

fn temp_config_path(path: &Path) -> PathBuf {
    let mut file_name = path
        .file_name()
        .map(|name| name.to_os_string())
        .unwrap_or_default();
    file_name.push(".tmp");
    path.with_file_name(file_name)
}

fn migrate_legacy_uname_fields(config: &mut serde_json::Value) -> bool {
    let Some(common) = config
        .get_mut("common")
        .and_then(serde_json::Value::as_object_mut)
    else {
        return false;
    };

    let has_legacy_version = common.remove("version").is_some();
    let has_legacy_release = common.remove("release").is_some();
    if !(has_legacy_version || has_legacy_release) {
        return false;
    }

    log::warn!(
        "legacy susfs uname fields detected; resetting spoof_version/spoof_release to kernel defaults"
    );
    common.insert(
        "spoof_version".to_string(),
        serde_json::Value::String("default".to_string()),
    );
    common.insert(
        "spoof_release".to_string(),
        serde_json::Value::String("default".to_string()),
    );
    true
}
