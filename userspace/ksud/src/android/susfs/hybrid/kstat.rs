use crate::android::susfs::{api, config};
use anyhow::Result;
use std::path::Path;

pub use crate::android::susfs::{api::add_kstat, config::del_kstat};

pub fn update_kstat<P>(path: P, full_clone: bool) -> Result<()>
where
    P: AsRef<Path>,
{
    api::update_kstat(&path, full_clone)?;
    config::add_kstat(&path, full_clone)
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
    api::add_kstat_statically(
        path, ino, dev, nlink, size, atime, atime_nsec, mtime, mtime_nsec, ctime, ctime_nsec,
        blocks, blksize,
    )?;
    config::add_kstat_statically(
        path, ino, dev, nlink, size, atime, atime_nsec, mtime, mtime_nsec, ctime, ctime_nsec,
        blocks, blksize,
    )
}
