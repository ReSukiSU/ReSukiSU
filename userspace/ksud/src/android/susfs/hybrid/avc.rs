use crate::android::susfs::{api, config};
use anyhow::Result;

pub fn avc_spoofing(enabled: bool) -> Result<()> {
    api::avc_spoofing(enabled)?;
    config::avc_spoofing(enabled)
}
