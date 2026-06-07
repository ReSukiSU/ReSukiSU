use std::{
    io::{Cursor, Read},
    path::{Path, PathBuf},
    sync::atomic::{AtomicBool, Ordering},
};

use android_bootimg::parser::BootImage;
use anyhow::{Context, Result, bail};
use bzip2::read::BzDecoder;
use flate2::read::MultiGzDecoder;
use lz4::{Decoder as Lz4FrameDecoder, block as lz4_block};
use lzma_rust2::{LzmaReader, XzReader};
use serde::Serialize;

const BY_NAME_DIR: &str = "/dev/block/by-name";
const LZ4_MAGIC: u32 = 0x184c_2102;
const GZIP_MAGIC_1: [u8; 2] = [0x1f, 0x8b];
const GZIP_MAGIC_2: [u8; 2] = [0x1f, 0x9e];
const LZOP_MAGIC: [u8; 4] = [0x89, b'L', b'Z', b'O'];
const XZ_MAGIC: [u8; 5] = [0xfd, b'7', b'z', b'X', b'Z'];
const BZIP_MAGIC: [u8; 3] = [b'B', b'Z', b'h'];
const LZ4_LEGACY_MAGIC: [u8; 4] = [0x02, 0x21, 0x4c, 0x18];
const LZ4_FRAME_MAGIC_1: [u8; 4] = [0x03, 0x21, 0x4c, 0x18];
const LZ4_FRAME_MAGIC_2: [u8; 4] = [0x04, 0x22, 0x4d, 0x18];

// Global verbose flag for debug output
static VERBOSE: AtomicBool = AtomicBool::new(false);

#[derive(Serialize)]
struct SlotInfo {
    slot_name: String,
    uname: String,
    build_time: String,
}

#[derive(Clone, Copy, PartialEq, Eq)]
enum CompressionFormat {
    Gzip,
    Lzop,
    Xz,
    Lzma,
    Bzip2,
    Lz4Frame,
    Lz4Legacy,
    Lz4Lg,
    Unknown,
}

impl std::fmt::Display for CompressionFormat {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            CompressionFormat::Gzip => write!(f, "GZIP"),
            CompressionFormat::Lzop => write!(f, "LZOP"),
            CompressionFormat::Xz => write!(f, "XZ"),
            CompressionFormat::Lzma => write!(f, "LZMA"),
            CompressionFormat::Bzip2 => write!(f, "BZIP2"),
            CompressionFormat::Lz4Frame => write!(f, "LZ4_FRAME"),
            CompressionFormat::Lz4Legacy => write!(f, "LZ4_LEGACY"),
            CompressionFormat::Lz4Lg => write!(f, "LZ4_LG"),
            CompressionFormat::Unknown => write!(f, "UNKNOWN"),
        }
    }
}

fn set_verbose(verbose: bool) {
    VERBOSE.store(verbose, Ordering::Relaxed);
}

fn is_verbose() -> bool {
    VERBOSE.load(Ordering::Relaxed)
}

fn debug_log(msg: &str) {
    if is_verbose() {
        eprintln!("[DEBUG] {}", msg);
    }
    log::debug!("{}", msg);
}

fn info_log(msg: &str) {
    eprintln!("[INFO] {}", msg);
    log::info!("{}", msg);
}

fn warn_log(msg: &str) {
    eprintln!("[WARN] {}", msg);
    log::warn!("{}", msg);
}

fn error_log(msg: &str) {
    eprintln!("[ERROR] {}", msg);
    log::error!("{}", msg);
}

pub fn show_slot_info_json(verbose: bool) -> Result<()> {
    set_verbose(verbose);

    debug_log("Starting slot_info enumeration from /dev/block/by-name");

    let mut result = Vec::<SlotInfo>::new();

    for (slot_name, slot_path) in list_boot_slots() {
        debug_log(&format!(
            "Processing slot: {} at {}",
            slot_name,
            slot_path.display()
        ));
        match extract_slot_kernel_info(&slot_path) {
            Ok((uname, build_time)) => {
                info_log(&format!("Successfully extracted info from {}", slot_name));
                debug_log(&format!("  uname: {}", uname));
                debug_log(&format!("  build_time: {}", build_time));
                result.push(SlotInfo {
                    slot_name,
                    uname,
                    build_time,
                });
            }
            Err(e) => {
                warn_log(&format!("Failed to extract info from {}: {}", slot_name, e));
            }
        }
    }

    println!("{}", serde_json::to_string(&result)?);
    Ok(())
}

pub fn analyze_boot_image(path: &str, verbose: bool) -> Result<()> {
    set_verbose(verbose);

    info_log(&format!("Analyzing boot image: {}", path));

    let path_buf = PathBuf::from(path);

    if !path_buf.exists() {
        error_log(&format!("Boot image file not found: {}", path));
        bail!("Boot image file not found: {}", path);
    }

    debug_log(&format!(
        "File exists, size: {} bytes",
        std::fs::metadata(&path_buf)?.len()
    ));

    match extract_slot_kernel_info(&path_buf) {
        Ok((uname, build_time)) => {
            let result = SlotInfo {
                slot_name: path.to_string(),
                uname: uname.clone(),
                build_time: build_time.clone(),
            };

            info_log("Successfully extracted kernel information");
            println!("{}", serde_json::to_string_pretty(&result)?);
            Ok(())
        }
        Err(e) => {
            error_log(&format!("Failed to extract kernel info: {}", e));
            bail!("Failed to extract kernel info: {}", e)
        }
    }
}

fn list_boot_slots() -> Vec<(String, PathBuf)> {
    let mut slots = Vec::new();
    for name in ["boot_a", "boot_b", "boot"] {
        let path = Path::new(BY_NAME_DIR).join(name);
        if path.exists() {
            debug_log(&format!("Found boot slot: {}", name));
            slots.push((name.to_string(), path));
        }
    }

    if slots.is_empty() {
        warn_log("No boot slots found in /dev/block/by-name");
    }

    slots
}

fn extract_slot_kernel_info(path: &Path) -> Result<(String, String)> {
    debug_log(&format!("Extracting kernel info from: {}", path.display()));

    let image =
        std::fs::read(path).with_context(|| format!("failed to read {}", path.display()))?;

    debug_log(&format!("Read boot image, size: {} bytes", image.len()));

    let boot =
        BootImage::parse(&image).with_context(|| format!("failed to parse {}", path.display()))?;

    debug_log("Boot image parsed successfully");

    let kernel = boot
        .get_blocks()
        .get_kernel()
        .ok_or_else(|| anyhow::anyhow!("kernel block not found"))?;

    // Try to dump kernel block, with fallback for abnormal boot images
    let mut raw_kernel = Vec::<u8>::new();
    match kernel.dump(&mut raw_kernel, false) {
        Ok(_) => {
            debug_log(&format!(
                "Extracted kernel block, size: {} bytes",
                raw_kernel.len()
            ));
        }
        Err(dump_err) => {
            warn_log(&format!(
                "Failed to dump kernel block normally: {}",
                dump_err
            ));
            debug_log("Attempting fallback direct kernel extraction from boot image buffer");

            // Fallback: try to extract kernel data directly from the boot image buffer
            // This handles abnormal boot images that the library's dump() method rejects
            match extract_kernel_fallback(&image, &boot) {
                Ok(fallback_kernel) => {
                    debug_log(&format!(
                        "Fallback extraction succeeded, size: {} bytes",
                        fallback_kernel.len()
                    ));
                    raw_kernel = fallback_kernel;
                }
                Err(fallback_err) => {
                    warn_log(&format!(
                        "Fallback extraction also failed: {}",
                        fallback_err
                    ));
                    return Err(anyhow::anyhow!(
                        "failed to extract kernel block: primary: {}, fallback: {}",
                        dump_err,
                        fallback_err
                    ));
                }
            }
        }
    }

    // Validate kernel data is not empty
    if raw_kernel.is_empty() {
        return Err(anyhow::anyhow!("kernel block is empty"));
    }

    // Log the first bytes for debugging
    if raw_kernel.len() >= 16 {
        let hex_str = raw_kernel[0..16]
            .iter()
            .map(|b| format!("{:02x}", b))
            .collect::<Vec<_>>()
            .join(" ");
        debug_log(&format!("First 16 bytes of kernel: {}", hex_str));

        // Try to detect format at offset 0
        let fmt = detect_format(&raw_kernel);
        debug_log(&format!(
            "Detected compression format at offset 0x00: {}",
            fmt
        ));
    }

    let decompressed = decompress_kernel_payload(&raw_kernel).unwrap_or_else(|e| {
        warn_log(&format!("Failed to decompress kernel: {}", e));
        debug_log("Falling back to raw kernel data");
        raw_kernel.clone()
    });

    debug_log(&format!("Final payload size: {} bytes", decompressed.len()));

    if let Some(info) = extract_linux_version_line(&decompressed) {
        debug_log("Successfully extracted Linux version line");
        return Ok(info);
    }

    bail!(
        "failed to extract kernel uname/build-time from {}",
        path.display()
    )
}

// Fallback kernel extraction when android-bootimg library's dump() fails
// This directly extracts kernel data from the boot image buffer
fn extract_kernel_fallback(boot_image: &[u8], _boot: &BootImage) -> Result<Vec<u8>> {
    debug_log("Using fallback kernel extraction method");

    // Last resort: try to extract from boot image buffer directly
    // Search for common kernel magic bytes within the image
    debug_log(&format!(
        "Searching for kernel signatures in {} byte boot image buffer",
        boot_image.len()
    ));

    // Look for compressed kernel signatures - try all of them, not just the first match
    let search_signatures = vec![
        (&[0x1fu8, 0x8bu8] as &[u8], "GZIP"), // GZIP header
        (&[0xfdu8, 0x37u8, 0x7au8, 0x58u8, 0x5au8] as &[u8], "XZ"), // XZ header
        (&[0x89u8, 0x4cu8, 0x5au8, 0x4fu8] as &[u8], "LZOP"), // LZOP header
        (&[0x42u8, 0x5au8, 0x68u8] as &[u8], "BZIP2"), // BZIP2 header
        (&[0x04u8, 0x22u8, 0x4du8, 0x18u8] as &[u8], "LZ4_FRAME"), // LZ4 frame magic
    ];

    // For each signature type, try to find and extract
    for (signature, name) in search_signatures {
        let mut search_offset = 0;
        let mut found_count = 0;

        // Search for all occurrences of this signature in the buffer
        while search_offset < boot_image.len() {
            if let Some(relative_offset) =
                find_signature_in_buffer(&boot_image[search_offset..], signature)
            {
                let absolute_offset = search_offset + relative_offset;
                found_count += 1;

                debug_log(&format!(
                    "Found {} signature #{} at offset 0x{:x}",
                    name, found_count, absolute_offset
                ));

                // Try to extract from this position
                if absolute_offset < boot_image.len() {
                    let extracted = boot_image[absolute_offset..].to_vec();

                    // Validate: should be reasonably large (> 256 bytes)
                    if extracted.len() > 256 {
                        debug_log(&format!(
                            "Fallback extraction candidate: {} bytes from offset 0x{:x}, signature {}",
                            extracted.len(),
                            absolute_offset,
                            name
                        ));

                        // For the first valid occurrence of each signature type, return it
                        // But log all findings for debugging
                        if found_count == 1 {
                            debug_log(&format!(
                                "Using first {} occurrence at offset 0x{:x}",
                                name, absolute_offset
                            ));
                            return Ok(extracted);
                        }
                    } else {
                        debug_log(&format!(
                            "Skipping {} at 0x{:x}: too small ({} bytes)",
                            name,
                            absolute_offset,
                            extracted.len()
                        ));
                    }
                }

                // Continue searching for more occurrences
                search_offset = absolute_offset + signature.len();
            } else {
                break;
            }
        }

        if found_count > 0 {
            debug_log(&format!(
                "Found {} total {} signature(s) in boot image",
                found_count, name
            ));
        }
    }

    // If no compressed format found, check for uncompressed kernel (Linux version string)
    if let Some(offset) = find_signature_in_buffer(boot_image, b"Linux version") {
        debug_log(&format!(
            "Found 'Linux version' signature at offset 0x{:x}",
            offset
        ));
        if offset < boot_image.len() {
            let extracted = boot_image[offset..].to_vec();
            debug_log(&format!(
                "Fallback extraction (uncompressed): {} bytes",
                extracted.len()
            ));
            return Ok(extracted);
        }
    }

    // Debug: log first few KB of boot image to understand structure
    if boot_image.len() >= 512 {
        let hex_str = boot_image[0..512]
            .iter()
            .map(|b| format!("{:02x}", b))
            .collect::<Vec<_>>()
            .join(" ");
        debug_log(&format!("First 512 bytes of boot image (hex): {}", hex_str));
    }

    Err(anyhow::anyhow!(
        "fallback kernel extraction: could not locate kernel data in boot image"
    ))
}

// Helper function to find a signature pattern in a buffer
fn find_signature_in_buffer(buffer: &[u8], signature: &[u8]) -> Option<usize> {
    buffer
        .windows(signature.len())
        .position(|window| window == signature)
}

fn decompress_kernel_payload(input: &[u8]) -> Result<Vec<u8>> {
    debug_log(&format!(
        "Starting kernel payload decompression, size: {} bytes",
        input.len()
    ));

    // Detect initial format and log it
    let initial_fmt = detect_format(input);
    debug_log(&format!(
        "Initial detected compression format: {}",
        initial_fmt
    ));
    info_log(&format!("Kernel compression format: {}", initial_fmt));

    let mut payload = input.to_vec();
    let mut depth = 0usize;

    while depth < 3 {
        let fmt = detect_format(&payload);
        debug_log(&format!(
            "Decompression iteration {}: format={}",
            depth, fmt
        ));

        if fmt == CompressionFormat::Unknown {
            debug_log("Unknown format, searching for embedded compressed blob from 0x40");
            // If format is unknown, try to find embedded compressed blob
            // Start searching from a larger offset to skip boot partition headers
            if let Some(found) = find_embedded_compressed_blob(&payload) {
                debug_log("Found embedded compressed blob");
                payload = found;
                depth += 1;
                continue;
            }
            debug_log("No embedded compressed blob found, stopping decompression");
            break;
        }

        // Try to decompress
        debug_log(&format!(
            "Attempting {} decompression at depth {}",
            fmt, depth
        ));
        match decompress_bytes(&payload, fmt) {
            Ok(decompressed) => {
                debug_log(&format!(
                    "Successfully decompressed {}: {} -> {} bytes",
                    fmt,
                    payload.len(),
                    decompressed.len()
                ));
                payload = decompressed;
                depth += 1;
            }
            Err(e) => {
                // If decompression fails, it might be due to corrupted header
                // Try to find the actual compressed data after padding/header
                warn_log(&format!("Failed to decompress at depth {}: {}", depth, e));

                // Try searching for another compressed blob starting from offset
                debug_log("Searching for another compressed blob from 0x100");
                if let Some(found) = find_embedded_compressed_blob_from_offset(&payload, 0x100) {
                    debug_log("Found another compressed blob at offset 0x100+");
                    payload = found;
                    depth += 1;
                    continue;
                }

                // If all else fails, return what we have if it looks like valid data
                if depth == 0 && !payload.is_empty() {
                    // This might be an uncompressed or partially corrupted kernel
                    debug_log("Returning uncompressed/partially corrupted kernel data");
                    break;
                }

                // Final attempt: for small depth, try deep search for any compressible data
                if depth == 0 {
                    debug_log("Attempting deep search for valid compressed data in entire buffer");
                    if let Some(deep_payload) = try_deep_search_compressed(&payload) {
                        debug_log(&format!(
                            "Deep search found valid data: {} bytes",
                            deep_payload.len()
                        ));
                        payload = deep_payload;
                        break;
                    }
                }

                return Err(e);
            }
        }
    }

    debug_log(&format!(
        "Decompression complete, final payload: {} bytes",
        payload.len()
    ));
    Ok(payload)
}

// Try to find valid compressed data by searching at different offsets
fn try_deep_search_compressed(buf: &[u8]) -> Option<Vec<u8>> {
    use flate2::read::DeflateDecoder;

    debug_log("Starting deep search for valid compressed data");

    // Try to find gzip streams at different offsets
    // Look for gzip magic (0x1f 0x8b) and try to decompress from there
    for offset in (0..buf.len()).step_by(4096) {
        if offset + 18 >= buf.len() {
            break;
        }

        // Try raw deflate decompression from this offset
        let mut out = Vec::<u8>::new();
        if DeflateDecoder::new(&buf[offset..])
            .read_to_end(&mut out)
            .is_ok()
            && !out.is_empty()
            && out.len() > 512
        {
            debug_log(&format!(
                "Deep search found valid deflate at offset 0x{:x}: {} bytes",
                offset,
                out.len()
            ));
            return Some(out);
        }
    }

    None
}

fn detect_format(buf: &[u8]) -> CompressionFormat {
    if buf.len() >= GZIP_MAGIC_1.len()
        && (buf.starts_with(&GZIP_MAGIC_1) || buf.starts_with(&GZIP_MAGIC_2))
    {
        return CompressionFormat::Gzip;
    }
    if buf.starts_with(&LZOP_MAGIC) {
        return CompressionFormat::Lzop;
    }
    if buf.starts_with(&XZ_MAGIC) {
        return CompressionFormat::Xz;
    }
    if guess_lzma(buf) {
        return CompressionFormat::Lzma;
    }
    if buf.starts_with(&BZIP_MAGIC) {
        return CompressionFormat::Bzip2;
    }
    if buf.starts_with(&LZ4_FRAME_MAGIC_1) || buf.starts_with(&LZ4_FRAME_MAGIC_2) {
        return CompressionFormat::Lz4Frame;
    }
    if buf.starts_with(&LZ4_LEGACY_MAGIC) {
        return detect_lz4_legacy_kind(buf);
    }
    CompressionFormat::Unknown
}

fn detect_lz4_legacy_kind(buf: &[u8]) -> CompressionFormat {
    let mut off = 4usize;
    while off + 4 <= buf.len() {
        let block_size =
            u32::from_le_bytes([buf[off], buf[off + 1], buf[off + 2], buf[off + 3]]) as usize;
        off += 4;
        if off + block_size > buf.len() {
            return CompressionFormat::Lz4Lg;
        }
        off += block_size;
    }
    CompressionFormat::Lz4Legacy
}

fn decompress_bytes(buf: &[u8], fmt: CompressionFormat) -> Result<Vec<u8>> {
    let mut out = Vec::<u8>::new();
    match fmt {
        CompressionFormat::Gzip => {
            // For gzip, try to be more tolerant of header issues
            match MultiGzDecoder::new(Cursor::new(buf)).read_to_end(&mut out) {
                Ok(_) => {
                    debug_log("GZIP decompression successful");
                    Ok(out)
                }
                Err(e) => {
                    warn_log(&format!("GZIP decompression failed: {}", e));

                    // Try raw deflate decompression (skip invalid gzip header)
                    // This handles cases where gzip header is corrupted but deflate stream is valid
                    debug_log("Attempting raw deflate decompression to handle invalid gzip header");
                    match try_raw_deflate_decompression(buf) {
                        Ok(decompressed) => {
                            debug_log(&format!(
                                "Raw deflate decompression succeeded: {} bytes",
                                decompressed.len()
                            ));
                            return Ok(decompressed);
                        }
                        Err(deflate_err) => {
                            warn_log(&format!(
                                "Raw deflate decompression also failed: {}",
                                deflate_err
                            ));
                        }
                    }

                    Err(anyhow::anyhow!("gzip decompression failed: {}", e))
                }
            }
        }
        CompressionFormat::Xz => {
            debug_log("Attempting XZ decompression");
            XzReader::new(Cursor::new(buf), true).read_to_end(&mut out)?;
            debug_log("XZ decompression successful");
            Ok(out)
        }
        CompressionFormat::Lzma => {
            debug_log("Attempting LZMA decompression");
            LzmaReader::new_mem_limit(Cursor::new(buf), u32::MAX, None)?.read_to_end(&mut out)?;
            debug_log("LZMA decompression successful");
            Ok(out)
        }
        CompressionFormat::Bzip2 => {
            debug_log("Attempting BZIP2 decompression");
            BzDecoder::new(Cursor::new(buf)).read_to_end(&mut out)?;
            debug_log("BZIP2 decompression successful");
            Ok(out)
        }
        CompressionFormat::Lz4Frame => {
            debug_log("Attempting LZ4_FRAME decompression");
            Lz4FrameDecoder::new(Cursor::new(buf))?.read_to_end(&mut out)?;
            debug_log("LZ4_FRAME decompression successful");
            Ok(out)
        }
        CompressionFormat::Lz4Legacy | CompressionFormat::Lz4Lg => {
            debug_log(&format!("Attempting {} decompression", fmt));
            decompress_lz4_blocks(buf)
        }
        CompressionFormat::Lzop => {
            bail!("lzop kernel payload is not supported yet");
        }
        CompressionFormat::Unknown => {
            bail!("unknown compressed kernel payload format");
        }
    }
}

fn decompress_lz4_blocks(buf: &[u8]) -> Result<Vec<u8>> {
    let mut out = Vec::<u8>::new();
    let mut pos = 0usize;
    if buf.len() >= 4 {
        let header = u32::from_le_bytes([buf[0], buf[1], buf[2], buf[3]]);
        if header == LZ4_MAGIC {
            debug_log("LZ4 magic header found");
            pos = 4;
        }
    }

    let mut block_count = 0;
    while pos + 4 <= buf.len() {
        let block_size =
            u32::from_le_bytes([buf[pos], buf[pos + 1], buf[pos + 2], buf[pos + 3]]) as usize;
        pos += 4;
        if block_size == 0 || pos + block_size > buf.len() {
            debug_log(&format!(
                "LZ4 block stream ended after {} blocks",
                block_count
            ));
            break;
        }

        let block = &buf[pos..pos + block_size];
        let decompressed = lz4_block::decompress(block, Some(8 * 1024 * 1024))
            .context("lz4 legacy block decompression failed")?;
        debug_log(&format!(
            "Decompressed LZ4 block {}: {} -> {} bytes",
            block_count,
            block_size,
            decompressed.len()
        ));
        out.extend_from_slice(&decompressed);
        pos += block_size;
        block_count += 1;
    }

    if out.is_empty() {
        bail!("empty lz4 output");
    }
    debug_log(&format!(
        "LZ4 decompression complete: {} blocks, {} bytes total",
        block_count,
        out.len()
    ));
    Ok(out)
}

// Try raw deflate decompression by skipping gzip header
// Gzip format: 10-18 byte header + deflate stream + trailer
// When gzip header is corrupted, we can try to decompress from different offsets
fn try_raw_deflate_decompression(buf: &[u8]) -> Result<Vec<u8>> {
    use flate2::read::DeflateDecoder;

    debug_log("Attempting raw deflate decompression with different header skips");

    // Try common gzip header sizes (10, 11, 12, 16, 18, 20 bytes)
    let header_sizes_to_try = [10, 11, 12, 16, 18, 20];

    for skip_size in header_sizes_to_try.iter() {
        if *skip_size >= buf.len() {
            debug_log(&format!(
                "Skipping offset 0x{:x}: buffer too small",
                skip_size
            ));
            continue;
        }

        debug_log(&format!(
            "Trying raw deflate decompression skipping first 0x{:x} bytes",
            skip_size
        ));

        let mut out = Vec::<u8>::new();
        match DeflateDecoder::new(&buf[*skip_size..]).read_to_end(&mut out) {
            Ok(_) => {
                if !out.is_empty() && out.len() > 512 {
                    debug_log(&format!(
                        "Raw deflate decompression succeeded with skip=0x{:x}: {} bytes",
                        skip_size,
                        out.len()
                    ));
                    return Ok(out);
                } else {
                    debug_log(&format!(
                        "Raw deflate produced invalid result (too small): {} bytes",
                        out.len()
                    ));
                }
            }
            Err(e) => {
                debug_log(&format!(
                    "Raw deflate decompression failed with skip=0x{:x}: {}",
                    skip_size, e
                ));
            }
        }
    }

    // Also try without skipping, in case the gzip header is already parsed away
    debug_log("Trying raw deflate decompression without header skip");
    let mut out = Vec::<u8>::new();
    match DeflateDecoder::new(buf).read_to_end(&mut out) {
        Ok(_) => {
            if !out.is_empty() && out.len() > 512 {
                debug_log(&format!(
                    "Raw deflate decompression succeeded (no skip): {} bytes",
                    out.len()
                ));
                return Ok(out);
            }
        }
        Err(e) => {
            debug_log(&format!(
                "Raw deflate decompression (no skip) failed: {}",
                e
            ));
        }
    }

    Err(anyhow::anyhow!(
        "raw deflate decompression failed: could not decompress with any header skip offset"
    ))
}

fn guess_lzma(buf: &[u8]) -> bool {
    if buf.len() <= 13 {
        return false;
    }
    if buf[0] != 0x5d {
        return false;
    }
    let dict_sz = u32::from_le_bytes([buf[1], buf[2], buf[3], buf[4]]);
    if dict_sz == 0 || (dict_sz & (dict_sz - 1)) != 0 {
        return false;
    }
    buf[5..13].iter().all(|&b| b == 0xff)
}

fn find_embedded_compressed_blob(buf: &[u8]) -> Option<Vec<u8>> {
    if buf.len() <= 0x40 {
        return None;
    }
    debug_log("Searching for embedded compressed blob from offset 0x40");
    // Skip potential boot partition header (typically 0x40 bytes or more)
    // and start searching from 0x40 to avoid misidentifying boot headers as kernel
    for i in 0x40..buf.len() {
        let rest = &buf[i..];
        if detect_format(rest) != CompressionFormat::Unknown {
            debug_log(&format!(
                "Found {} at offset 0x{:x}",
                detect_format(rest),
                i
            ));
            return Some(rest.to_vec());
        }
    }
    None
}

fn find_embedded_compressed_blob_from_offset(buf: &[u8], start_offset: usize) -> Option<Vec<u8>> {
    if buf.len() <= start_offset {
        return None;
    }
    debug_log(&format!(
        "Searching for embedded compressed blob from offset 0x{:x}",
        start_offset
    ));
    // Search from a specific offset for compressed blob markers
    for i in start_offset..buf.len() {
        let rest = &buf[i..];
        if detect_format(rest) != CompressionFormat::Unknown {
            debug_log(&format!(
                "Found {} at offset 0x{:x}",
                detect_format(rest),
                i
            ));
            return Some(rest.to_vec());
        }
    }
    None
}

fn extract_linux_version_line(buf: &[u8]) -> Option<(String, String)> {
    let needle = b"Linux version ";
    let mut best: Option<(String, String)> = None;
    let mut found = 0usize;

    debug_log("Searching for 'Linux version' string");

    for idx in find_all(buf, needle) {
        debug_log(&format!("Found 'Linux version' at offset 0x{:x}", idx));
        let tail = &buf[idx..buf.len().min(idx + 1024)];
        let end = tail
            .iter()
            .position(|b| *b == b'\n' || *b == 0)
            .unwrap_or(tail.len());
        let line = String::from_utf8_lossy(&tail[..end]).trim().to_string();

        debug_log(&format!("Line content: {}", line));

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
            debug_log("Incomplete version line, skipping");
            continue;
        }
        found += 1;
        debug_log(&format!(
            "Found valid version line #{}: {} {}",
            found, release, build_time
        ));
        if found >= 2 {
            return Some((release, build_time));
        }
        if best.is_none() {
            best = Some((release, build_time));
        }
    }

    if best.is_some() {
        debug_log("Using first found version line");
    } else {
        debug_log("No valid Linux version line found");
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
