use crate::android::susfs::{api, config};
use anyhow::{Result, anyhow};

pub fn open_redirect(config: &config::Config) -> Result<()> {
    let mut success = true;

    for item in &config.open_redirect {
        success &= match api::add_open_redirect(&item.source, &item.target, item.uid_scheme) {
            Ok(_) => true,
            Err(e) => {
                log::warn!("Failed to add open redirect for {}: {e}", item.source);
                false
            }
        }
    }

    success
        .then_some(())
        .ok_or(anyhow!("Add open redirect failed!"))
}
