use crate::android::susfs::{api, config};
use anyhow::{Result, anyhow};

pub fn path(config: &config::Config) -> Result<()> {
    let mut success = true;

    for item in &config.path {
        success &= match api::add_path(&item.path, item.is_loop) {
            Ok(_) => true,
            Err(e) => {
                let path_type_str = match item.is_loop {
                    true => "loop",
                    false => "normal",
                };
                log::warn!("Add sus {path_type_str} path failed for {}: {e}", item.path);
                false
            }
        }
    }

    success.then_some(()).ok_or(anyhow!("Add sus path failed"))
}
