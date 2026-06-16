use crate::android::susfs::{
    config::{
        model::OpenRedirectItem,
        operation::file::{read_config_or_default, save_config},
    },
    enums::UidScheme,
};
use anyhow::{Result, anyhow};
use std::path::Path;

pub fn add_open_redirect<P>(target_path: P, redirected_path: P, uid_scheme: i32) -> Result<()>
where
    P: AsRef<Path>,
{
    if UidScheme::try_from(uid_scheme).is_err() {
        return Err(anyhow::anyhow!("uid_scheme is invalid!"));
    }

    let target_str = target_path
        .as_ref()
        .to_str()
        .ok_or(anyhow!("Invalid path"))?;
    let redirected_str = redirected_path
        .as_ref()
        .to_str()
        .ok_or(anyhow!("Invalid path"))?;

    let mut config = read_config_or_default();

    config.open_redirect.insert(OpenRedirectItem {
        source: target_str.to_string(),
        target: redirected_str.to_string(),
        uid_scheme,
    });

    save_config(&config)
}

pub fn del_open_redirect<P>(target_path: P) -> Result<()>
where
    P: AsRef<Path>,
{
    let target_str = target_path
        .as_ref()
        .to_str()
        .ok_or(anyhow!("Invalid path"))?;

    let mut config = read_config_or_default();

    config.open_redirect.remove(target_str);

    save_config(&config)
}
