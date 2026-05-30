use std::collections::HashSet;

use serde::{Deserialize, Serialize};

fn default_uname_value() -> String {
    "default".to_string()
}

#[derive(Serialize, Deserialize)]
pub struct Data {
    #[serde(default)]
    pub common: Common,
    #[serde(default)]
    pub sus_path: SusPath,
    #[serde(default)]
    pub sus_map: HashSet<String>,
    #[serde(default)]
    pub kstat: SusKstat,
}

#[derive(Serialize, Deserialize)]
pub struct Common {
    #[serde(default = "default_uname_value")]
    pub version: String,
    #[serde(default = "default_uname_value")]
    pub release: String,
    #[serde(default)]
    pub avc_spoofing: bool,
    #[serde(default)]
    pub enable_susfs_log: bool,
    #[serde(default)]
    pub hide_sus_mnts_for_non_su_procs: bool,
}

#[derive(Serialize, Deserialize)]
pub struct SusPath {
    #[serde(default)]
    pub sus_path_loop: HashSet<String>,
    #[serde(default)]
    pub sus_path: HashSet<String>,
}

#[allow(clippy::struct_field_names)]
#[derive(Serialize, Deserialize)]
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
    #[serde(default = "default_uname_value")]
    pub ino: String,
    #[serde(default = "default_uname_value")]
    pub dev: String,
    #[serde(default = "default_uname_value")]
    pub nlink: String,
    #[serde(default = "default_uname_value")]
    pub size: String,
    #[serde(default = "default_uname_value")]
    pub atime: String,
    #[serde(default = "default_uname_value")]
    pub atime_nsec: String,
    #[serde(default = "default_uname_value")]
    pub mtime: String,
    #[serde(default = "default_uname_value")]
    pub mtime_nsec: String,
    #[serde(default = "default_uname_value")]
    pub ctime: String,
    #[serde(default = "default_uname_value")]
    pub ctime_nsec: String,
    #[serde(default = "default_uname_value")]
    pub blocks: String,
    #[serde(default = "default_uname_value")]
    pub blksize: String,
}

impl Default for Data {
    fn default() -> Self {
        Self {
            common: Common::default(),
            sus_path: SusPath::default(),
            sus_map: HashSet::new(),
            kstat: SusKstat::default(),
        }
    }
}

impl Default for Common {
    fn default() -> Self {
        Self {
            version: "default".to_string(),
            release: "default".to_string(),
            avc_spoofing: false,
            enable_susfs_log: false,
            hide_sus_mnts_for_non_su_procs: false,
        }
    }
}

impl Default for SusPath {
    fn default() -> Self {
        Self {
            sus_path_loop: HashSet::new(),
            sus_path: HashSet::new(),
        }
    }
}

impl Default for SusKstat {
    fn default() -> Self {
        Self {
            sus_kstat: HashSet::new(),
            update_kstat: HashSet::new(),
            full_clone: HashSet::new(),
            statically: HashSet::new(),
        }
    }
}
