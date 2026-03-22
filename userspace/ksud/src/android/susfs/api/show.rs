use anyhow::Result;

use crate::android::susfs::{
    magic::{
        CMD_SUSFS_SHOW_ENABLED_FEATURES, CMD_SUSFS_SHOW_VARIANT, CMD_SUSFS_SHOW_VERSION,
        ERR_CMD_NOT_SUPPORTED, SUSFS_ENABLED_FEATURES_SIZE, SUSFS_MAX_VARIANT_BUFSIZE,
        SUSFS_MAX_VERSION_BUFSIZE,
    },
    utils::{handle_result, susfs_ctl},
};

#[repr(C)]
struct SusfsEnabledFeatures {
    enabled_features: [u8; SUSFS_ENABLED_FEATURES_SIZE],
    err: i32,
}

#[repr(C)]
struct SusfsVariant {
    susfs_variant: [u8; SUSFS_MAX_VARIANT_BUFSIZE],
    err: i32,
}

#[repr(C)]
struct SusfsVersion {
    susfs_version: [u8; SUSFS_MAX_VERSION_BUFSIZE],
    err: i32,
}

pub fn show_version() -> Result<()> {
    let mut info = SusfsVersion {
        susfs_version: [0; SUSFS_MAX_VERSION_BUFSIZE],
        err: ERR_CMD_NOT_SUPPORTED,
    };
    susfs_ctl(&mut info, CMD_SUSFS_SHOW_VERSION);
    handle_result(info.err, CMD_SUSFS_SHOW_VERSION)?;

    if info.err == 0 {
        let len = info
            .susfs_version
            .iter()
            .position(|&b| b == 0)
            .unwrap_or(SUSFS_MAX_VERSION_BUFSIZE);
        let bytes: Vec<u8> = info.susfs_version[..len].to_vec();
        let ver = String::from_utf8(bytes).unwrap_or_else(|_| "<invalid>".to_string());

        if ver.starts_with('v') {
            println!("{ver}");
        } else {
            println!("unsupport");
        }
    }

    Ok(())
}

pub fn show_variant() -> Result<()> {
    let mut info = SusfsVariant {
        susfs_variant: [0; SUSFS_MAX_VARIANT_BUFSIZE],
        err: ERR_CMD_NOT_SUPPORTED,
    };
    susfs_ctl(&mut info, CMD_SUSFS_SHOW_VARIANT);
    handle_result(info.err, CMD_SUSFS_SHOW_VARIANT)?;

    if info.err == 0 {
        let len = info
            .susfs_variant
            .iter()
            .position(|&b| b == 0)
            .unwrap_or(SUSFS_MAX_VARIANT_BUFSIZE);
        let bytes: Vec<u8> = info.susfs_variant[..len].to_vec();
        let variant = String::from_utf8(bytes).unwrap_or_else(|_| "<invalid>".to_string());
        println!("{variant}");
    }

    Ok(())
}

pub fn show_features() -> Result<()> {
    let mut info = Box::new(SusfsEnabledFeatures {
        enabled_features: [0; SUSFS_ENABLED_FEATURES_SIZE],
        err: ERR_CMD_NOT_SUPPORTED,
    });
    susfs_ctl(&mut *info, CMD_SUSFS_SHOW_ENABLED_FEATURES);
    handle_result(info.err, CMD_SUSFS_SHOW_ENABLED_FEATURES)?;

    if info.err == 0 {
        let len = info
            .enabled_features
            .iter()
            .position(|&b| b == 0)
            .unwrap_or(SUSFS_ENABLED_FEATURES_SIZE);
        let bytes: Vec<u8> = info.enabled_features[..len].to_vec();
        let features = String::from_utf8(bytes).unwrap_or_else(|_| "<invalid>".to_string());
        print!("{features}");
    }

    Ok(())
}
