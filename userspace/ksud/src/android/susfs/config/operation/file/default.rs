use crate::android::susfs::config::{model::Config, operation::file::save_config};

pub fn default_config() -> Config {
    let config = Config::default();
    if let Err(e) = save_config(&config) {
        log::warn!("ksud-susfs: unable to save default config: {e}");
    }
    config
}
