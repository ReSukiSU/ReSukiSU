use anyhow::Result;

use crate::android::susfs::{
    magic::{
        CMD_SUSFS_SHOW_ENABLED_FEATURES, CMD_SUSFS_SHOW_VARIANT, CMD_SUSFS_SHOW_VERSION,
        ERR_CMD_NOT_SUPPORTED, SUSFS_ENABLED_FEATURES_SIZE, SUSFS_MAX_VARIANT_BUFSIZE,
        SUSFS_MAX_VERSION_BUFSIZE,
    },
    communicate::{communicate, parse_err},
    utils::c_array_to_string,
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

/// Get the current SuSFS version implemented in kernel
pub fn version() -> Result<String> {
    let mut info = SusfsVersion {
        susfs_version: [0; SUSFS_MAX_VERSION_BUFSIZE],
        err: ERR_CMD_NOT_SUPPORTED,
    };
    communicate(CMD_SUSFS_SHOW_VERSION, &mut info);
    parse_err(CMD_SUSFS_SHOW_VERSION, info.err)?;

    let ver = c_array_to_string(&info.susfs_version);

    if ver.starts_with('v') {
        Ok(ver)
    } else {
        Ok("Unsupported".to_string())
    }
}

/// Get the current variant (GKI/non-GKI)
pub fn variant() -> Result<String> {
    let mut info = SusfsVariant {
        susfs_variant: [0; SUSFS_MAX_VARIANT_BUFSIZE],
        err: ERR_CMD_NOT_SUPPORTED,
    };
    communicate(CMD_SUSFS_SHOW_VARIANT, &mut info);
    parse_err(CMD_SUSFS_SHOW_VARIANT, info.err)?;

    let variant = c_array_to_string(&info.susfs_variant);
    Ok(variant)
}

/// Get the current implemented SuSFS features in kernel
pub fn features() -> Result<String> {
    let mut info = Box::new(SusfsEnabledFeatures {
        enabled_features: [0; SUSFS_ENABLED_FEATURES_SIZE],
        err: ERR_CMD_NOT_SUPPORTED,
    });
    communicate(CMD_SUSFS_SHOW_ENABLED_FEATURES, &mut *info);
    parse_err(CMD_SUSFS_SHOW_ENABLED_FEATURES, info.err)?;

    let features = c_array_to_string(&info.enabled_features);

    Ok(features)
}
