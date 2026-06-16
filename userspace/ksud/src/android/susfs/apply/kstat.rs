use crate::android::susfs::{api, config, enums};
use anyhow::{Result, anyhow};

pub fn kstat_initialize(config: &config::Config) -> Result<()> {
    let mut success = true;

    for item in &config.kstat {
        success &= match api::add_kstat(&item.path) {
            Ok(_) => true,
            Err(e) => {
                log::warn!("Add kstat failed for {}: {e}", item.path);
                false
            }
        }
    }

    if success {
        Ok(())
    } else {
        Err(anyhow!("Failed to add kstat."))
    }
}

pub fn kstat_finalize(config: &config::Config) -> Result<()> {
    let mut success = true;

    for item in &config.kstat {
        let result = match item.ktype {
            enums::SusKstatType::Normal => api::update_kstat(&item.path, false),
            enums::SusKstatType::FullClone => api::update_kstat(&item.path, true),
            enums::SusKstatType::Statically => {
                let Some(statically) = &item.statically else {
                    log::warn!("Statically config invalid for {}", item.path);
                    continue;
                };
                api::add_kstat_statically(
                    item.path.as_str(),
                    statically.ino.as_str(),
                    statically.dev.as_str(),
                    statically.nlink.as_str(),
                    statically.size.as_str(),
                    statically.atime.as_str(),
                    statically.atime_nsec.as_str(),
                    statically.mtime.as_str(),
                    statically.mtime_nsec.as_str(),
                    statically.ctime.as_str(),
                    statically.ctime_nsec.as_str(),
                    statically.blocks.as_str(),
                    statically.blksize.as_str(),
                )
            }
        };

        success &= match result {
            Ok(_) => true,
            Err(e) => {
                log::warn!("Kstat update fail for {}: {e}", item.path);
                false
            }
        };
    }

    if success {
        Ok(())
    } else {
        Err(anyhow!("Failed to update kstat"))
    }
}
