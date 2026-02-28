pub(super) const KSU_INSTALL_MAGIC1: u64 = 0xDEAD_BEEF;
pub(super) const SUSFS_MAGIC: u64 = 0xFAFA_FAFA;

pub(super) const CMD_SUSFS_ADD_SUS_PATH: u64 = 0x55550;
pub(super) const CMD_SUSFS_SET_ANDROID_DATA_ROOT_PATH: u64 = 0x55551;
pub(super) const CMD_SUSFS_SET_SDCARD_ROOT_PATH: u64 = 0x55552;
pub(super) const CMD_SUSFS_ADD_SUS_PATH_LOOP: u64 = 0x55553;
pub(super) const CMD_SUSFS_HIDE_SUS_MNTS_FOR_NON_SU_PROCS: u64 = 0x55561;
pub(super) const CMD_SUSFS_ADD_SUS_KSTAT: u64 = 0x55570;
pub(super) const CMD_SUSFS_UPDATE_SUS_KSTAT: u64 = 0x55571;
pub(super) const CMD_SUSFS_ADD_SUS_KSTAT_STATICALLY: u64 = 0x55572;
pub(super) const CMD_SUSFS_SET_UNAME: u64 = 0x55590;
pub(super) const CMD_SUSFS_ENABLE_LOG: u64 = 0x555a0;
pub(super) const CMD_SUSFS_SET_CMDLINE_OR_BOOTCONFIG: u64 = 0x555b0;
pub(super) const CMD_SUSFS_ADD_OPEN_REDIRECT: u64 = 0x555c0;
pub(super) const CMD_SUSFS_SHOW_VERSION: u64 = 0x555e1;
pub(super) const CMD_SUSFS_SHOW_ENABLED_FEATURES: u64 = 0x555e2;
pub(super) const CMD_SUSFS_SHOW_VARIANT: u64 = 0x555e3;
pub(super) const CMD_SUSFS_ENABLE_AVC_LOG_SPOOFING: u64 = 0x60010;
pub(super) const CMD_SUSFS_ADD_SUS_MAP: u64 = 0x60020;

pub(super) const SUSFS_MAX_LEN_PATHNAME: usize = 256;
pub(super) const SUSFS_FAKE_CMDLINE_OR_BOOTCONFIG_SIZE: usize = 8192;
pub(super) const SUSFS_ENABLED_FEATURES_SIZE: usize = 8192;
pub(super) const SUSFS_MAX_VERSION_BUFSIZE: usize = 16;
pub(super) const SUSFS_MAX_VARIANT_BUFSIZE: usize = 16;
pub(super) const NEW_UTS_LEN: usize = 64;
pub(super) const ERR_CMD_NOT_SUPPORTED: i32 = 126;
