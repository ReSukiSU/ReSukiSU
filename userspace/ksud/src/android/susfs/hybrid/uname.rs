use crate::android::susfs::{api, config};
use anyhow::Result;

pub use config::del_uname;

pub fn set_uname<S>(version: &S, release: &S) -> Result<()>
where
    S: ToString,
{
    api::set_uname(version, release)?;
    config::set_uname(version, release)
}
