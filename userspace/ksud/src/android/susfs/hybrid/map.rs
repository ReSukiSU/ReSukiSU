use crate::android::susfs::{api, config};
use anyhow::Result;
use std::path::Path;

pub use crate::android::susfs::config::del_map;

pub fn add_map<P>(path: P) -> Result<()>
where
    P: AsRef<Path>,
{
    api::add_map(&path)?;
    config::add_map(&path)
}
