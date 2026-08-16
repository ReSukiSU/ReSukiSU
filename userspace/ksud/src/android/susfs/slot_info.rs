use std::{
    path::{Path, PathBuf},
    sync::mpsc::channel,
};

use android_bootimg::parser::BootImage;
use anyhow::{Context, Result, bail};
use serde::Serialize;

const BY_NAME_DIR: &str = "/dev/block/by-name";

// Global verbose flag for debug output

#[derive(Serialize, Clone)]
struct SlotInfo {
    slot_name: String,
    uname: String,
    build_time: String,
}

pub fn show_slot_info_json() -> Result<()> {
    log::debug!("Starting slot_info enumeration from /dev/block/by-name");

    let (send, recv) = channel::<SlotInfo>();
    let mut jobs = Vec::<std::thread::JoinHandle<_>>::new();

    for (slot_name, slot_path) in list_boot_slots() {
        let send = send.clone();
        jobs.push(
            std::thread::Builder::new()
                .name(format!("analyze_{slot_name}"))
                .spawn(move || {
                    log::debug!("Processing slot: {} at {}", slot_name, slot_path.display());

                    match extract_slot_kernel_info(&slot_path) {
                        Ok((uname, build_time)) => {
                            log::info!("Successfully extracted info from {}", slot_name);
                            log::debug!("  build_time: {}", build_time);
                            let _ = send.send(SlotInfo {
                                slot_name,
                                uname,
                                build_time,
                            });
                        }
                        Err(e) => {
                            log::warn!("Failed to extract info from {}: {}", slot_name, e);
                        }
                    }
                })?,
        );
    }

    let mut result = Vec::new();
    for job in jobs {
        job.join().unwrap();
        result.push(recv.recv()?);
    }

    println!("{}", serde_json::to_string(&result)?);

    Ok(())
}

pub fn analyze_boot_image(path: &str) -> Result<()> {
    log::info!("Analyzing boot image: {}", path);

    let path_buf = PathBuf::from(path);

    if !path_buf.exists() {
        log::error!("Boot image file not found: {}", path);
        bail!("Boot image file not found: {}", path);
    }

    log::debug!(
        "File exists, size: {} bytes",
        std::fs::metadata(&path_buf)?.len()
    );

    match extract_slot_kernel_info(&path_buf) {
        Ok((uname, build_time)) => {
            let result = SlotInfo {
                slot_name: path.to_string(),
                uname: uname.clone(),
                build_time: build_time.clone(),
            };

            log::info!("Successfully extracted kernel information");
            println!("{}", serde_json::to_string_pretty(&result)?);
            Ok(())
        }
        Err(e) => {
            log::error!("Failed to extract kernel info: {}", e);
            bail!("Failed to extract kernel info: {}", e)
        }
    }
}

fn list_boot_slots() -> Vec<(String, PathBuf)> {
    let mut slots = Vec::new();
    for name in ["boot_a", "boot_b", "boot"] {
        let path = Path::new(BY_NAME_DIR).join(name);
        if path.exists() {
            log::debug!("Found boot slot: {}", name);
            slots.push((name.to_string(), path));
        }
    }

    if slots.is_empty() {
        log::warn!("No boot slots found in /dev/block/by-name");
    }

    slots
}

fn extract_slot_kernel_info(path: &Path) -> Result<(String, String)> {
    log::debug!("Extracting kernel info from: {}", path.display());

    let image =
        std::fs::read(path).with_context(|| format!("failed to read {}", path.display()))?;

    log::debug!("Read boot image, size: {} bytes", image.len());

    let boot =
        BootImage::parse(&image).with_context(|| format!("failed to parse {}", path.display()))?;

    log::debug!("Boot image parsed successfully");

    let kernel = boot
        .get_blocks()
        .get_kernel()
        .ok_or_else(|| anyhow::anyhow!("kernel block not found"))?;

    // Try to dump kernel block, with fallback for abnormal boot images
    let mut raw_kernel = Vec::<u8>::new();
    kernel.dump(&mut raw_kernel, false)?;
    log::debug!("Final payload size: {} bytes", raw_kernel.len());

    if let Some(info) = extract_linux_version_line(&raw_kernel) {
        log::debug!("Successfully extracted Linux version line");
        return Ok(info);
    }

    bail!(
        "failed to extract kernel uname/build-time from {}",
        path.display()
    )
}

fn extract_linux_version_line(buf: &[u8]) -> Option<(String, String)> {
    let needle = b"Linux version ";
    let mut best: Option<(String, String)> = None;
    let mut found = 0usize;

    log::debug!("Searching for 'Linux version' string");

    for idx in find_all(buf, needle) {
        log::debug!("Found 'Linux version' at offset 0x{:x}", idx);
        let tail = &buf[idx..buf.len().min(idx + 1024)];
        let end = tail
            .iter()
            .position(|b| *b == b'\n' || *b == 0)
            .unwrap_or(tail.len());
        let line = String::from_utf8_lossy(&tail[..end]).trim().to_string();

        log::debug!("Line content: {}", line);

        if line.is_empty() {
            continue;
        }
        let release = line
            .split_whitespace()
            .nth(2)
            .unwrap_or("")
            .trim()
            .to_string();
        let build_time = line
            .split_once('#')
            .map(|(_, v)| format!("#{}", v.trim()))
            .unwrap_or_default();
        if release.is_empty() || build_time.is_empty() {
            log::debug!("Incomplete version line, skipping");
            continue;
        }
        found += 1;
        log::debug!(
            "Found valid version line #{}: {} {}",
            found,
            release,
            build_time
        );
        if found >= 2 {
            return Some((release, build_time));
        }
        if best.is_none() {
            best = Some((release, build_time));
        }
    }

    if best.is_some() {
        log::debug!("Using first found version line");
    } else {
        log::debug!("No valid Linux version line found");
    }

    best
}

fn find_all(haystack: &[u8], needle: &[u8]) -> Vec<usize> {
    if needle.is_empty() || haystack.len() < needle.len() {
        return Vec::new();
    }
    let mut result = Vec::<usize>::new();
    let mut i = 0usize;
    while i + needle.len() <= haystack.len() {
        if &haystack[i..i + needle.len()] == needle {
            result.push(i);
            i += needle.len();
        } else {
            i += 1;
        }
    }
    result
}
