use crate::android::susfs::{api, config};
use anyhow::Result;

pub fn log(config: &config::Config) -> Result<()> {
    api::log(config.logging)
}
