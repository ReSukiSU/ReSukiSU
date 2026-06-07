use std::collections::{HashSet, HashMap};

use serde::{Deserialize, Serialize};

fn default_generator() -> String {
    "default".to_string()
}

#[derive(Serialize, Deserialize, Default)]
pub struct Data {
    #[serde(default)]
    pub common: Common,
    #[serde(default)]
    pub sus_path: SusPath,
    #[serde(default)]
    pub sus_map: HashSet<String>,
    #[serde(default)]
    pub kstat: SusKstat,
    #[serde(default)]
    pub open_redirect: HashMap<String, String>,
}

#[derive(Serialize, Deserialize, Default)]
pub struct Common {
    #[serde(default = "default_generator")]
    pub version: String,
    #[serde(default = "default_generator")]
    pub release: String,
    #[serde(default)]
    pub avc_spoofing: bool,
    #[serde(default)]
    pub enable_susfs_log: bool,
    #[serde(default)]
    pub hide_sus_mnts_for_non_su_procs: bool,
    #[serde(default = "default_generator")]
    pub cmdline: String,
}

#[derive(Serialize, Deserialize, Default)]
pub struct SusPath {
    #[serde(default)]
    pub sus_path_loop: HashSet<String>,
    #[serde(default)]
    pub sus_path: HashSet<String>,
}

#[allow(clippy::struct_field_names)]
#[derive(Serialize, Deserialize, Default)]
pub struct SusKstat {
    #[serde(default)]
    pub sus_kstat: HashSet<String>,
    #[serde(default)]
    pub update_kstat: HashSet<String>,
    #[serde(default)]
    pub full_clone: HashSet<String>,
    #[serde(default)]
    pub statically: HashSet<SusKstatStatically>,
}

#[derive(Serialize, Hash, PartialEq, Eq, PartialOrd, Ord, Deserialize)]
pub struct SusKstatStatically {
    #[serde(default)]
    pub path: String,
    #[serde(default = "default_generator")]
    pub ino: String,
    #[serde(default = "default_generator")]
    pub dev: String,
    #[serde(default = "default_generator")]
    pub nlink: String,
    #[serde(default = "default_generator")]
    pub size: String,
    #[serde(default = "default_generator")]
    pub atime: String,
    #[serde(default = "default_generator")]
    pub atime_nsec: String,
    #[serde(default = "default_generator")]
    pub mtime: String,
    #[serde(default = "default_generator")]
    pub mtime_nsec: String,
    #[serde(default = "default_generator")]
    pub ctime: String,
    #[serde(default = "default_generator")]
    pub ctime_nsec: String,
    #[serde(default = "default_generator")]
    pub blocks: String,
    #[serde(default = "default_generator")]
    pub blksize: String,
}

