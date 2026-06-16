use crate::android::susfs::{api, config::Config};
use anyhow::Result;

pub fn avc_spoofing(config: &Config) -> Result<()> {
    api::avc_spoofing(config.avc_spoofing)
}
