pub mod magica;

use std::process::Command;

use anyhow::{Context, Result};
use log::{info, warn};
use rustix::cstr;

use crate::{
    android::{
        dynamic_manager, init_event,
        module::{handle_updated_modules, metamodule, prune_modules},
        restorecon, utils,
    },
    assets, defs,
};

fn dump_process_info(label: &str) {
    use rustix::process::{getgid, getgroups, getpid, getuid};

    let pid = getpid().as_raw_nonzero();
    let uid = getuid().as_raw();
    let gid = getgid().as_raw();
    let groups: Vec<String> = getgroups()
        .unwrap_or_default()
        .iter()
        .map(|g| g.as_raw().to_string())
        .collect();
    let selinux = std::fs::read_to_string("/proc/self/attr/current")
        .unwrap_or_else(|_| "unknown".to_string());
    let seccomp = std::fs::read_to_string("/proc/self/status")
        .ok()
        .and_then(|s| {
            s.lines()
                .find(|l| l.starts_with("Seccomp:"))
                .map(|l| l.trim().to_string())
        })
        .unwrap_or_else(|| "unknown".to_string());

    info!(
        "[{label}] pid={pid}, uid={uid}, gid={gid}, groups=[{}], selinux={}, {seccomp}",
        groups.join(","),
        selinux.trim(),
    );
}

fn manager_base_apk_path(pm_output: &str) -> Option<String> {
    let paths = pm_output
        .lines()
        .filter_map(|line| line.strip_prefix("package:"));
    let paths: Vec<_> = paths.collect();
    paths
        .iter()
        .find(|path| path.ends_with("/base.apk"))
        .or_else(|| paths.first())
        .map(|path| (*path).to_owned())
}

fn manager_base_apk(package_name: &str) -> Result<String> {
    let output = Command::new("pm")
        .args(["path", package_name])
        .output()
        .with_context(|| format!("failed to query manager package {package_name}"))?;
    if !output.status.success() {
        anyhow::bail!("pm path {package_name} exited with {}", output.status);
    }

    let output = String::from_utf8(output.stdout).context("manager package path was not UTF-8")?;
    manager_base_apk_path(&output).context("manager package has no APK path")
}

pub fn run(
    package_name: &String,
    kmi: Option<String>,
    allow_shell: bool,
    register_manager: bool,
) -> Result<()> {
    utils::daemonize(false)?;
    info!("late-load command triggered!");
    dump_process_info("late-load start");

    // 1. Check if KernelSU is already loaded
    if ksuinit::has_kernelsu() {
        info!("KernelSU already loaded, skip loading ko");
    } else {
        // 2. Detect current KMI version
        let kmi = kmi.map_or_else(
            || crate::boot_patch::get_current_kmi().context("Failed to detect current KMI version"),
            Ok,
        )?;
        info!("Detected KMI: {kmi}");

        // 3. Get kernelsu.ko from embedded assets
        let ko_name = format!("{kmi}_kernelsu.ko");
        let ko_data = assets::get_asset(&ko_name)
            .with_context(|| format!("Failed to get {ko_name} from assets"))?;

        // 4. Load kernelsu.ko from memory with manual relocation
        info!("Loading kernelsu.ko for KMI {kmi}...");
        let params = if allow_shell {
            cstr!("allow_shell=1")
        } else {
            cstr!("")
        };
        ksuinit::load_module(&ko_data, params).context("Failed to load kernelsu.ko")?;
        info!("kernelsu.ko loaded successfully!");
        dump_process_info("after load_module");
    }

    // We need to reset stdin/stdout/stderr; otherwise, sending file descriptors via cmd transactions
    // will be blocked by SELinux because its fsec->sid is still u:r:su:s0 instead of u:r:ksu:s0.
    utils::reset_std()?;

    utils::umask(0);

    if let Err(e) = crate::android::module::module_config::clear_all_temp_configs() {
        warn!("clear temp configs failed: {e}");
    }

    utils::install(None).context("Failed to install ksud")?;

    if register_manager {
        // Register the manager before its restart below. That restart is the point
        // at which KernelSU injects the per-process driver fd, avoiding a fallback
        // supercall from the app's seccomp-confined process.
        match manager_base_apk(package_name) {
            Ok(apk) => {
                if let Err(e) = dynamic_manager::set_apk(&apk) {
                    warn!("set dynamic manager for {package_name} failed: {e}");
                } else {
                    info!("registered dynamic manager {package_name}");
                }
            }
            Err(e) => warn!("find manager APK for {package_name} failed: {e}"),
        }
    }

    // 5. Handle module updates
    if let Err(e) = handle_updated_modules() {
        warn!("handle updated modules failed: {e}");
    }

    if let Err(e) = prune_modules() {
        warn!("prune modules failed: {e}");
    }

    if let Err(e) = restorecon::restorecon() {
        warn!("restorecon failed: {e}");
    }

    // 6. Load SELinux rules
    if crate::android::module::load_sepolicy_rule().is_err() {
        warn!("load sepolicy.rule failed");
    }

    if let Err(e) = crate::android::profile::apply_sepolies() {
        warn!("apply root profile sepolicy failed: {e}");
    }

    // 7. Initialize features
    if let Err(e) = crate::android::feature::init_features() {
        warn!("init features failed: {e}");
    }

    // 8. Execute late-load stage scripts (blocking)
    init_event::run_stage("late-load", true);

    // 9. Load system.prop
    if let Err(e) = crate::android::module::load_system_prop() {
        warn!("load system.prop failed: {e}");
    }

    // 10. Execute metamodule mount script (OverlayFS)
    if let Err(e) = metamodule::exec_mount_script(defs::MODULE_DIR) {
        warn!("execute metamodule mount failed: {e}");
    }
    // 11. Execute dynamic manager booted load
    if let Err(e) = dynamic_manager::booted_load() {
        warn!("set dynamic manager failed: {e}");
    }

    // 12. Execute post-mount stage scripts (blocking)
    init_event::run_stage("post-mount", true);

    // 13. Execute service stage scripts (non-blocking)
    init_event::run_stage("service", false);

    // 14. Execute boot-completed stage scripts (non-blocking)
    init_event::run_stage("boot-completed", false);

    // 15. Restart Manager so it gets a fresh ksu fd from the newly loaded kernel module
    info!("Restarting KernelSU Manager {package_name}...");
    let _ = Command::new("am")
        .args(["force-stop", package_name])
        .status();
    let _ = Command::new("am")
        .args(["start", "-n", &format!("{package_name}/.ui.MainActivity")])
        .status();

    Ok(())
}

#[cfg(test)]
mod tests {
    use super::manager_base_apk_path;

    #[test]
    fn manager_base_apk_path_prefers_base_apk_over_splits() {
        let output = concat!(
            "package:/data/app/example/split_config.arm64_v8a.apk\n",
            "package:/data/app/example/base.apk\n",
        );
        assert_eq!(
            manager_base_apk_path(output).as_deref(),
            Some("/data/app/example/base.apk"),
        );
    }
}
