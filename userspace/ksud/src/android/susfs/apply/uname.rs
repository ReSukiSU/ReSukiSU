use crate::android::susfs::{api, config};
use anyhow::Result;

pub fn uname(config: &config::Config) -> Result<()> {
    api::set_uname(&config.uname.version, &config.uname.release)
}
