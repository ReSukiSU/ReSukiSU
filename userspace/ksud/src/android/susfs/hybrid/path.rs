use crate::android::susfs::{api, config};
use anyhow::{Result, anyhow};
use std::path::Path;

pub use config::del_path;

pub fn add_path<P>(path: P, is_loop: bool) -> Result<()>
where
    P: AsRef<Path>,
{
    api::add_path(
        &path
            .as_ref()
            .to_str()
            .ok_or(anyhow!("invalid path"))?
            .to_string(),
        is_loop,
    )?;
    config::add_path(&path, is_loop)
}
