use anyhow::Result;
use rust_embed::RustEmbed;

#[cfg(target_os = "android")]
mod android {
    use const_format::concatcp;

    use crate::{
        android::utils::ensure_binary,
        assets::Asset,
        defs::{BINARY_DIR, DAEMON_PATH},
    };

    pub const RESETPROP_PATH: &str = concatcp!(BINARY_DIR, "resetprop");
    pub const KSU_SUSFS: &str = concatcp!(BINARY_DIR, "ksu_susfs");
    pub const BUSYBOX_PATH: &str = concatcp!(BINARY_DIR, "busybox");
    pub const BOOTCTL_PATH: &str = concatcp!(BINARY_DIR, "bootctl");
    pub const MKBOOTFS_PATH: &str = concatcp!(BINARY_DIR, "mkbootfs");

    /// Create the ksu_susfs -> ksud hard link (it shares the same inode as ksud).
    pub fn ensure_susfs_link() -> anyhow::Result<()> {
        let ksu_susfs = KSU_SUSFS;
        let _ = std::fs::remove_file(ksu_susfs);
        std::fs::hard_link(DAEMON_PATH, ksu_susfs)?;
        Ok(())
    }

    /// Remove the ksu_susfs hard link, but only if it is genuinely a hard link
    /// to `/data/adb/ksud` (same device + inode). Any other file at that path is
    /// left untouched so a third-party manager's `ksu_susfs` is never clobbered.
    pub fn remove_susfs_link() -> anyhow::Result<()> {
        use std::os::unix::fs::MetadataExt;
        let ksu_susfs = std::path::Path::new(KSU_SUSFS);
        let daemon = std::path::Path::new(DAEMON_PATH);

        let is_daemon_hard_link = (|| -> std::io::Result<bool> {
            let link = std::fs::metadata(ksu_susfs)?;
            let daemon = std::fs::metadata(daemon)?;
            Ok(link.dev() == daemon.dev() && link.ino() == daemon.ino())
        })();

        match is_daemon_hard_link {
            // Path (or its target) does not exist: nothing to do.
            Err(e) if e.kind() == std::io::ErrorKind::NotFound => Ok(()),
            // Not a hard link to ksud; leave it alone to avoid clobbering a
            // third-party manager's link.
            Ok(false) | Err(_) => Ok(()),
            Ok(true) => std::fs::remove_file(ksu_susfs).map_err(Into::into),
        }
    }

    /// Align the ksu_susfs hard link with the persisted SUSFS manager state:
    /// create it when management is enabled and SUSFS is available, otherwise
    /// remove it (only when it is genuinely a hard link to ksud). Used after an
    /// explicit state change (enable / disable / restore).
    pub fn reconcile_susfs_link() -> anyhow::Result<()> {
        if crate::android::susfs::config::model::Config::read_or_default().is_enabled() {
            if crate::android::susfs::api::features::show::version().is_ok() {
                ensure_susfs_link()?;
            }
        } else {
            remove_susfs_link()?;
        }
        Ok(())
    }

    pub fn ensure_binaries(ignore_if_exist: bool) -> anyhow::Result<()> {
        for file in Asset::iter() {
            if file == "ksuinit" || file.ends_with(".ko") {
                // don't extract ksuinit and kernel modules
                continue;
            }
            let asset =
                Asset::get(&file).ok_or_else(|| anyhow::anyhow!("asset not found: {file}"))?;
            ensure_binary(format!("{BINARY_DIR}{file}"), &asset.data, ignore_if_exist)?;
        }

        // Create resetprop -> ksud symlink (resetprop is now built into ksud)
        let resetprop_link = RESETPROP_PATH;
        let _ = std::fs::remove_file(resetprop_link);
        std::os::unix::fs::symlink("/data/adb/ksud", resetprop_link)?;

        // Create the ksu_susfs -> ksud hard link only while the SUSFS manager is
        // enabled. Never remove the link here at boot: it may belong to a
        // third-party manager, and removal only happens when the user explicitly
        // disables the SUSFS manager (see `config disable`).
        if crate::android::susfs::config::model::Config::read_or_default().is_enabled()
            && crate::android::susfs::api::features::show::version().is_ok()
        {
            ensure_susfs_link()?;
        }
        Ok(())
    }
}

#[cfg(target_os = "android")]
pub use android::*;

#[cfg(all(target_arch = "x86_64", target_os = "android"))]
#[derive(RustEmbed)]
#[folder = "bin/x86_64"]
struct Asset;

#[cfg(all(target_arch = "aarch64", target_os = "android"))]
#[derive(RustEmbed)]
#[folder = "bin/aarch64"]
struct Asset;

#[cfg(all(target_arch = "arm", target_os = "android"))]
#[derive(RustEmbed)]
#[folder = "bin/arm"]
struct Asset;

// If not Android, ie. macos, linux, windows, include both
#[cfg(not(target_os = "android"))]
#[derive(RustEmbed)]
#[folder = "bin"]
struct Asset;

pub fn list_supported_kmi() -> std::vec::Vec<std::string::String> {
    let mut list = Vec::new();
    for file in Asset::iter() {
        // kmi_name = "xxx_kernelsu.ko"
        if let Some(kmi) = file.strip_suffix("_kernelsu.ko") {
            list.push(kmi.to_string());
        }
    }
    list
}

pub fn get_asset(name: &str) -> Result<std::borrow::Cow<'static, [u8]>> {
    let asset = Asset::get(name).ok_or_else(|| anyhow::anyhow!("asset not found: {name}"))?;
    Ok(asset.data)
}
