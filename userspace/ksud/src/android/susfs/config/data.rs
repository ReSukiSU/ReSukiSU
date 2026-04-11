use std::collections::HashSet;

use serde::{Deserialize, Serialize};

#[derive(Serialize, Deserialize)]
pub struct Data {
    pub common: Common,
    pub sus_path: SusPath,
    pub sus_map: HashSet<String>,
    pub kstat: SusKstat,
}

#[derive(Serialize, Deserialize)]
pub struct Common {
    pub version: String,
    pub release: String,
    pub avc_spoofing: bool,
}

#[derive(Serialize, Deserialize)]
pub struct SusPath {
    pub sus_path_loop: HashSet<String>,
    pub sus_path: HashSet<String>,
}

#[allow(clippy::struct_field_names)]
#[derive(Serialize, Deserialize)]
pub struct SusKstat {
    pub sus_kstat: HashSet<String>,
    pub update_kstat: HashSet<String>,
    pub full_clone: HashSet<String>,
    pub statically: HashSet<SusKstatStatically>,
}

#[derive(Serialize, Hash, PartialEq, Eq, PartialOrd, Ord, Deserialize)]
pub struct SusKstatStatically {
    pub path: String,
    pub ino: String,
    pub dev: String,
    pub nlink: String,
    pub size: String,
    pub atime: String,
    pub atime_nsec: String,
    pub mtime: String,
    pub mtime_nsec: String,
    pub ctime: String,
    pub ctime_nsec: String,
    pub blocks: String,
    pub blksize: String,
}

impl Default for Data {
    fn default() -> Self {
        Self {
            common: Common {
                version: "default".to_string(),
                release: "default".to_string(),
                avc_spoofing: false,
            },
            sus_path: SusPath {
                sus_path_loop: HashSet::new(),
                sus_path: HashSet::new(),
            },
            sus_map: HashSet::new(),
            kstat: SusKstat {
                sus_kstat: HashSet::new(),
                update_kstat: HashSet::new(),
                full_clone: HashSet::new(),
                statically: HashSet::new(),
            },
        }
    }
}

pub enum ConfigType {
    Add,
    Remove,
}
