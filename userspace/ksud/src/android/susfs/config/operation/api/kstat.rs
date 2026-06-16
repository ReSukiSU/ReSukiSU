use crate::android::susfs::{
    config::{
        model::{SusKstatItem, SusKstatStatically},
        operation::file::{read_config_or_default, save_config},
    },
    enums::SusKstatType,
};
use anyhow::{Result, anyhow, bail};
use std::path::Path;

pub fn add_kstat<P>(path: P, full_clone: bool) -> Result<()>
where
    P: AsRef<Path>,
{
    let mut config = read_config_or_default();
    let path_str = path.as_ref().to_str().ok_or(anyhow!("Path invalid"))?;

    config.kstat.insert(SusKstatItem {
        path: path_str.to_string(),
        ktype: if full_clone {
            SusKstatType::FullClone
        } else {
            SusKstatType::Normal
        },
        statically: None,
    });

    save_config(&config)
}

#[allow(clippy::too_many_arguments)]
pub fn add_kstat_statically(
    path: &str,
    ino: &str,
    dev: &str,
    nlink: &str,
    size: &str,
    atime: &str,
    atime_nsec: &str,
    mtime: &str,
    mtime_nsec: &str,
    ctime: &str,
    ctime_nsec: &str,
    blocks: &str,
    blksize: &str,
) -> Result<()> {
    let mut config = read_config_or_default();

    let not_valid = [
        path, ino, dev, nlink, size, atime, atime_nsec, mtime, mtime_nsec, ctime, ctime_nsec,
        blocks, blksize,
    ]
    .iter()
    .any(|x| x.is_empty());

    if not_valid {
        bail!("all param should not be empty!");
    }

    config.kstat.insert(SusKstatItem {
        path: path.to_string(),
        ktype: SusKstatType::Statically,
        statically: Some(SusKstatStatically {
            ino: ino.to_string(),
            dev: dev.to_string(),
            nlink: nlink.to_string(),
            size: size.to_string(),
            atime: atime.to_string(),
            atime_nsec: atime_nsec.to_string(),
            mtime: mtime.to_string(),
            mtime_nsec: mtime_nsec.to_string(),
            ctime: ctime.to_string(),
            ctime_nsec: ctime_nsec.to_string(),
            blocks: blocks.to_string(),
            blksize: blksize.to_string(),
        }),
    });

    save_config(&config)
}

pub fn del_kstat<P>(path: P) -> Result<()>
where
    P: AsRef<Path>,
{
    let mut config = read_config_or_default();
    let path_str = path.as_ref().to_str().ok_or(anyhow!("path not valid"))?;

    config.kstat.remove(path_str);

    save_config(&config)
}
