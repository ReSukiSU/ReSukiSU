use crate::android::susfs::{api, config};
use anyhow::{Result, anyhow};

pub fn map(config: &config::Config) -> Result<()> {
    let mut success = true;

    for item in &config.map {
        success &= match api::add_map(item) {
            Ok(_) => true,
            Err(e) => {
                log::warn!("Add sus map failed for {item}: {e}");
                false
            }
        }
    }

    if success {
        Ok(())
    } else {
        Err(anyhow!("Add sus map failed"))
    }
}
