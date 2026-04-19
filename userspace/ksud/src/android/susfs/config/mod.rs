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
    let json: Data = match serde_json::from_str(&string) {
        Ok(s) => s,
        Err(e) => {
            log::warn!("failed to serialize susfs config, Err: {e}");
            return None;
        }
    };

    Some(json)
}
