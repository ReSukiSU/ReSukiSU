use crate::android::susfs::config::operation::file::{read_config_or_default, save_config};
use anyhow::{Result, bail};

pub fn set_uname<S>(version: &S, release: &S) -> Result<()>
where
    S: ToString,
{
    let mut config = read_config_or_default();

    let version_string = version.to_string().trim().to_string();
    let release_string = release.to_string().trim().to_string();

    if version_string.is_empty() || release_string.is_empty() {
        bail!("both version and release cannot be empty. use 'default' if you mean.")
    }

    config.uname.version = version_string;
    config.uname.release = release_string;

    save_config(&config)
}

pub fn del_uname(target: &str) -> Result<()> {
    let mut config = read_config_or_default();

    match target {
        "version" => {
            config.uname.version = "default".to_string();
        }
        "release" => {
            config.uname.release = "default".to_string();
        }
        "all" => {
            config.uname.version = "default".to_string();
            config.uname.release = "default".to_string();
        }
        _ => {
            bail!(
                "invalid target '{}': expected 'version', 'release', or 'all'",
                target
            );
        }
    }

    save_config(&config)
}
