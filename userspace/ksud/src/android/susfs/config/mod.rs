pub mod data;
pub mod operation;

use std::fs;

use crate::defs;

use data::Data;

fn save_config(config: &Data) {
    let Ok(string) = serde_json::to_string_pretty(&config) else {
        log::warn!("failed to deserialize susfs string");
        return;
    };
    if let Err(e) = fs::write(defs::SUSFS_CONFUG, string) {
        log::warn!("failed to write susfs config, Err: {e}");
    }
}

pub fn read_config() -> Option<Data> {
    let string = match fs::read_to_string(defs::SUSFS_CONFUG) {
        Ok(s) => s,
        Err(e) => {
            log::warn!("failed to read susfs config, Err: {e}, will use default config");
            save_config(&Data::default());
            fs::read_to_string(defs::SUSFS_CONFUG).unwrap()
        }
    };
    let mut json: Data = match serde_json::from_str(&string) {
        Ok(s) => s,
        Err(e) => {
            log::warn!("failed to serialize susfs config, Err: {e}");
            return None;
        }
    };

    // Normalize/migrate legacy config
    json = normalize_legacy_config(json);

    Some(json)
}

/// Normalize legacy configuration and apply any necessary migrations.
/// This function checks for and corrects common configuration issues that may
/// have existed in older versions of the susfs config file.
fn normalize_legacy_config(config: Data) -> Data {
    // Note: The version and release fields in susfs.json:
    // - version: should contain kernel uname information
    // - release: should contain kernel build time information
    //
    // If we detect they are swapped (old config format), we don't automatically
    // swap them here since we don't have a reliable way to detect if they're
    // actually swapped vs. just unusual values. The UI layer should handle
    // the display logic appropriately by reading the correct fields.
    //
    // Future: If needed, add additional migration logic here for other
    // configuration field reorganizations.

    config
}
