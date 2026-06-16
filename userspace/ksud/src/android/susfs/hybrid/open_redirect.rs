use crate::android::susfs::{api, config};
use anyhow::Result;
use std::path::Path;

pub use config::del_open_redirect;

pub fn add_open_redirect<P>(target_path: P, redirected_path: P, uid_scheme: i32) -> Result<()>
where
    P: AsRef<Path>,
{
    api::add_open_redirect(&target_path, &redirected_path, uid_scheme)?;
    config::add_open_redirect(&target_path, &redirected_path, uid_scheme)
}
