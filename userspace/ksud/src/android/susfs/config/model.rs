use std::collections::HashSet;

use monostate::MustBe;
use serde::{Deserialize, Serialize};

use crate::{android::susfs::enums::SusKstatType, impl_hashset_indexkey};

fn default_generator() -> String {
    "default".to_string()
}

#[derive(Serialize, Deserialize, Default)]
pub struct Config {
    #[serde(rename = "$schema", default)]
    pub schema: MustBe!("https://originalFactor.github.io/ReKittySU/schemas/v1.json"),

    #[serde(default = "default_generator")]
    pub cmdline: String,

    #[serde(default)]
    pub avc_spoofing: bool,

    #[serde(default)]
    pub logging: bool,

    #[serde(default)]
    pub ignore_umount: bool,

    #[serde(default)]
    pub uname: Uname,

    #[serde(default)]
    pub path: HashSet<SusPathItem>,

    #[serde(default)]
    pub kstat: HashSet<SusKstatItem>,

    #[serde(default)]
    pub open_redirect: HashSet<OpenRedirectItem>,

    #[serde(default)]
    pub map: HashSet<String>,
}

#[derive(Serialize, Deserialize, Default)]
pub struct Uname {
    #[serde(default = "default_generator")]
    pub version: String,

    #[serde(default = "default_generator")]
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
    pub ktype: SusKstatType,
    pub statically: Option<SusKstatStatically>,
}
impl_hashset_indexkey!(SusKstatItem, path);

#[derive(Serialize, Deserialize)]
pub struct SusKstatStatically {
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

#[derive(Serialize, Deserialize)]
pub struct OpenRedirectItem {
    pub source: String,
    pub target: String,
    pub uid_scheme: i32,
}
impl_hashset_indexkey!(OpenRedirectItem, source);
