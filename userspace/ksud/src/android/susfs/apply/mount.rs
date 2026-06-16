use crate::android::susfs::{api, config};
use anyhow::Result;

pub fn ignore_umount(config: &config::Config) -> Result<()> {
    api::ignore_umount(config.ignore_umount)
}
