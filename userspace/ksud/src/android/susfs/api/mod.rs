mod avc_log_spoofing;
mod enable_log;
mod open_redirect;
mod show;
mod spoof_cmdline_or_bootconfig;
mod spoof_uname;
mod sus_kstat;
mod sus_map;
mod sus_path;

pub use avc_log_spoofing::enable_avc_log_spoofing;
pub use enable_log::enable_log;
pub use open_redirect::add_open_redirect;
pub use show::{show_features, show_variant, show_version};
pub use spoof_cmdline_or_bootconfig::set_cmdline_or_bootconfig;
pub use spoof_uname::set_uname;
pub use sus_kstat::{
    add_sus_kstat, add_sus_kstat_statically, update_sus_kstat, update_sus_kstat_full_clone,
};
pub use sus_map::add_sus_map;
pub use sus_path::{SusPathType, add_sus_path};
