use crate::android::susfs::{
    config::model::{Config, OpenRedirectItem, SusKstatItem, SusKstatStatically, SusPathItem},
    enums::{SusKstatType, UidScheme},
    macros::ensure_path_exists,
    utils::{is_valid_uname_release, is_valid_uname_version},
};
use anyhow::{Result, bail};

impl Config {
    pub fn set_cmdline_or_bootconfig(&mut self, path: &str) -> Result<&mut Self> {
        ensure_path_exists!(path);
        self.cmdline_or_bootconfig = path.to_string();
        Ok(self)
    }

    pub fn set_avc_log_spoofing(&mut self, enabled: bool) -> &mut Self {
        self.avc_log_spoofing = enabled;
        self
    }

    pub fn set_logging(&mut self, enabled: bool) -> &mut Self {
        self.logging = enabled;
        self
    }

    pub fn set_hide_sus_mnts_for_non_su_procs(&mut self, enabled: bool) -> &mut Self {
        self.hide_sus_mnts_for_non_su_procs = enabled;
        self
    }

    pub fn set_uname(&mut self, version: &str, release: &str) -> Result<&mut Self> {
        let version = version.trim();
        let release = release.trim();

        if !is_valid_uname_version(version) || !is_valid_uname_release(release) {
            bail!(
                "Uname version ({}) or release ({}) is not valid!",
                version,
                release
            );
        }

        self.uname.version = version.to_string();
        self.uname.release = release.to_string();

        Ok(self)
    }

    pub fn add_sus_path(&mut self, path: &str, is_loop: bool) -> Result<&mut Self> {
        ensure_path_exists!(path);
        self.sus_path.replace(SusPathItem {
            path: path.to_string(),
            is_loop,
        });
        Ok(self)
    }

    pub fn del_sus_path(&mut self, path: &str) -> &mut Self {
        self.sus_path.remove(path);
        self
    }

    pub fn add_sus_kstat(&mut self, path: &str, full_clone: bool) -> Result<&mut Self> {
        ensure_path_exists!(path);
        self.sus_kstat.replace(SusKstatItem {
            path: path.to_string(),
            spoof_type: if full_clone {
                SusKstatType::FullClone
            } else {
                SusKstatType::Normal
            },
            statically: None,
        });
        Ok(self)
    }

    #[allow(clippy::too_many_arguments)]
    pub fn add_sus_kstat_statically(
        &mut self,
        path: &str,
        ino: Option<i64>,
        dev: Option<i64>,
        nlink: Option<i64>,
        size: Option<i64>,
        atime: Option<i64>,
        atime_nsec: Option<i64>,
        mtime: Option<i64>,
        mtime_nsec: Option<i64>,
        ctime: Option<i64>,
        ctime_nsec: Option<i64>,
        blocks: Option<i64>,
        blksize: Option<i64>,
    ) -> Result<&mut Self> {
        ensure_path_exists!(path);
        self.sus_kstat.replace(SusKstatItem {
            path: path.to_string(),
            spoof_type: SusKstatType::Statically,
            statically: Some(SusKstatStatically {
                ino,
                dev,
                nlink,
                size,
                atime,
                atime_nsec,
                mtime,
                mtime_nsec,
                ctime,
                ctime_nsec,
                blocks,
                blksize,
            }),
        });
        Ok(self)
    }

    pub fn del_sus_kstat(&mut self, path: &str) -> &mut Self {
        self.sus_kstat.remove(path);
        self
    }

    pub fn add_open_redirect(
        &mut self,
        target_path: &str,
        redirected_path: &str,
        uid_scheme: i32,
    ) -> Result<&mut Self> {
        ensure_path_exists!(target_path);
        ensure_path_exists!(redirected_path);
        UidScheme::try_from(uid_scheme)?;
        self.open_redirect.replace(OpenRedirectItem {
            target_path: target_path.to_string(),
            redirected_path: redirected_path.to_string(),
            uid_scheme,
        });
        Ok(self)
    }

    pub fn del_open_redirect(&mut self, target_path: &str) -> &mut Self {
        self.open_redirect.remove(target_path);
        self
    }

    pub fn add_sus_map(&mut self, path: &str) -> Result<&mut Self> {
        ensure_path_exists!(path);
        self.sus_map.insert(path.to_string());
        Ok(self)
    }

    pub fn del_sus_map(&mut self, path: &str) -> &mut Self {
        self.sus_map.remove(path);
        self
    }
}
