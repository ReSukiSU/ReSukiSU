use crate::android::susfs::{api, config};
use anyhow::Result;
use std::path::Path;

pub fn set_cmdline<P>(path: P) -> Result<()>
where
    P: AsRef<Path>,
{
    api::set_cmdline(&path)?;
    config::set_cmdline(&path)
}
