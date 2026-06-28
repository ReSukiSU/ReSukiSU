use serde::{Deserialize, Serialize};
use std::collections::HashSet;

use crate::android::susfs::{enums::SusKstatType, macros::impl_hashset_indexkey};

pub(super) const CURRENT_VERSION: u8 = 1;

#[derive(Deserialize)]
pub(super) struct VersionProbe {
    pub version: u8,
}

#[derive(Serialize, Deserialize)]
pub struct Config {
    pub(super) version: u8,
    pub(super) cmdline_or_bootconfig: String,
    pub(super) avc_log_spoofing: bool,
    pub(super) logging: bool,
    pub(super) hide_sus_mnts_for_non_su_procs: bool,
    pub(super) uname: Uname,
    pub(super) sus_path: HashSet<SusPathItem>,
    pub(super) sus_kstat: HashSet<SusKstatItem>,
    pub(super) open_redirect: HashSet<OpenRedirectItem>,
    pub(super) sus_map: HashSet<String>,
}

impl Default for Config {
    fn default() -> Self {
        Self {
            version: CURRENT_VERSION,
            cmdline_or_bootconfig: "".to_string(),
            avc_log_spoofing: false,
            logging: false,
            hide_sus_mnts_for_non_su_procs: false,
            uname: Uname {
                version: "default".to_string(),
                release: "default".to_string(),
            },
            sus_path: HashSet::new(),
            sus_kstat: HashSet::new(),
            open_redirect: HashSet::new(),
            sus_map: HashSet::new(),
        }
    }
}

#[derive(Serialize, Deserialize)]
pub struct Uname {
    pub version: String,
    pub release: String,
}

#[derive(Serialize, Deserialize)]
pub struct SusPathItem {
    pub path: String,
    pub is_loop: bool,
}
impl_hashset_indexkey!(SusPathItem, path);

#[derive(Serialize, Deserialize)]
pub struct SusKstatItem {
    pub path: String,
    pub spoof_type: SusKstatType,
    pub statically: Option<SusKstatStatically>,
}
impl_hashset_indexkey!(SusKstatItem, path);

#[derive(Serialize, Deserialize)]
pub struct SusKstatStatically {
    pub ino: Option<i64>,
    pub dev: Option<i64>,
    pub nlink: Option<i64>,
    pub size: Option<i64>,
    pub atime: Option<i64>,
    pub atime_nsec: Option<i64>,
    pub mtime: Option<i64>,
    pub mtime_nsec: Option<i64>,
    pub ctime: Option<i64>,
    pub ctime_nsec: Option<i64>,
    pub blocks: Option<i64>,
    pub blksize: Option<i64>,
}

#[derive(Serialize, Deserialize)]
pub struct OpenRedirectItem {
    pub target_path: String,
    pub redirected_path: String,
    pub uid_scheme: i32,
}
impl_hashset_indexkey!(OpenRedirectItem, target_path);
