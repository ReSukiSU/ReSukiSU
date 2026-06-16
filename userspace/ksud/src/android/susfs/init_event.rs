use anyhow::{Result, bail};
use std::{
    fs,
    io::{self, Read, Seek},
    os::fd::AsRawFd,
};

use crate::android::{
    susfs::{apply, config::read_config_or_default},
    utils::daemonize,
};

fn is_fuse_mounted() -> bool {
    fs::metadata("/sdcard/Android").is_ok()
}

fn wait_for_fuse_mounted() -> Result<()> {
    if is_fuse_mounted() {
        return Ok(());
    }

    let mut file = fs::File::open("/proc/mounts")?;
    let fd = file.as_raw_fd();

    let mut pfd = libc::pollfd {
        fd,
        events: libc::POLLPRI | libc::POLLERR,
        revents: 0,
    };

    log::info!("Polling mount event");

    loop {
        let mut dummy = String::new();
        let _ = file.seek(io::SeekFrom::Start(0));
        let _ = file.read_to_string(&mut dummy);

        if is_fuse_mounted() {
            break;
        }

        let ret = unsafe { libc::poll(&mut pfd, 1, -1) };
        if ret < 0 {
            bail!("Poll failed");
        }
    }

    Ok(())
}

fn handle_result(ret: Result<()>, msg: &str) {
    match ret {
        Ok(_) => log::info!("successfully {msg}!"),
        Err(e) => log::warn!("{msg} failed: {e}!"),
    }
}

pub fn on_boot_completed() {
    log::info!("Start daemonize...");
    if let Err(e) = daemonize(false) {
        log::error!("unable to daemonize: {e}");
        return;
    }
    log::info!("Daemonize success.");

    log::info!("Waiting /sdcard to be mounted.");
    if let Err(e) = wait_for_fuse_mounted() {
        log::error!("Wait FUSE mount failed: {e}");
    }
    log::info!("Processing SUSFS.");

    let config = read_config_or_default();

    handle_result(apply::set_cmdline(&config), "sus_cmdline");
    handle_result(apply::kstat_finalize(&config), "finalize sus_kstat");
    handle_result(apply::map(&config), "sus_map");
    handle_result(apply::open_redirect(&config), "open_redirect");
    handle_result(apply::path(&config), "sus_path and sus_path_loop");

    log::info!("SUSFS finished");
}

pub fn on_post_fs_data() {
    let config = read_config_or_default();

    log::info!("on_post_fs_data triggered!");

    handle_result(apply::avc_spoofing(&config), "avc_config");
    handle_result(apply::kstat_initialize(&config), "initialize sus_kstat");
    handle_result(apply::log(&config), "sus_log");
    handle_result(apply::ignore_umount(&config), "ignore umount");
    handle_result(apply::uname(&config), "sus_uname");

    log::info!("on_post_fs_data finished!");
}
