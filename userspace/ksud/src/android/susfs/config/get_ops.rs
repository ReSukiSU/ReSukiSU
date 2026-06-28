use crate::android::susfs::config::model::{Config, OpenRedirectItem, SusKstatItem, SusPathItem};

impl Config {
    pub fn get_version(&self) -> u8 {
        self.version
    }

    pub fn get_cmdline_or_bootconfig(&self) -> &str {
        &self.cmdline_or_bootconfig
    }

    pub fn is_avc_log_spoofing(&self) -> bool {
        self.avc_log_spoofing
    }

    pub fn is_logging(&self) -> bool {
        self.logging
    }

    pub fn is_hiding_sus_mnts_for_non_su_procs(&self) -> bool {
        self.hide_sus_mnts_for_non_su_procs
    }

    pub fn get_uname_version(&self) -> &str {
        &self.uname.version
    }

    pub fn get_uname_release(&self) -> &str {
        &self.uname.release
    }

    pub fn get_sus_paths(&self) -> impl Iterator<Item = &SusPathItem> {
        self.sus_path.iter()
    }

    pub fn get_sus_kstats(&self) -> impl Iterator<Item = &SusKstatItem> {
        self.sus_kstat.iter()
    }

    pub fn get_open_redirects(&self) -> impl Iterator<Item = &OpenRedirectItem> {
        self.open_redirect.iter()
    }

    pub fn get_sus_maps(&self) -> impl Iterator<Item = &str> {
        self.sus_map.iter().map(AsRef::as_ref)
    }
}
