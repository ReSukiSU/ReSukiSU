use crate::android::susfs::{api, config};
use anyhow::Result;

pub fn log(enabled: bool) -> Result<()> {
    api::log(enabled)?;
    config::log(enabled)
}
