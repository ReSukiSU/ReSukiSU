use std::{
    io::{Cursor, Read},
    path::{Path, PathBuf},
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

pub fn show_slot_info_json() -> Result<()> {
    let mut result = Vec::<SlotInfo>::new();

    for (slot_name, slot_path) in list_boot_slots() {
        if let Ok((uname, build_time)) = extract_slot_kernel_info(&slot_path) {
            result.push(SlotInfo {
                slot_name,
                uname,
                build_time,
            });
        }
    }

    println!("{}", serde_json::to_string(&result)?);
    Ok(())
}

fn list_boot_slots() -> Vec<(String, PathBuf)> {
    let mut slots = Vec::new();
    for name in ["boot_a", "boot_b", "boot"] {
        let path = Path::new(BY_NAME_DIR).join(name);
        if path.exists() {
            slots.push((name.to_string(), path));
        }
    }
    slots
}

fn extract_slot_kernel_info(path: &Path) -> Result<(String, String)> {
    let image =
        std::fs::read(path).with_context(|| format!("failed to read {}", path.display()))?;

    let boot =
        BootImage::parse(&image).with_context(|| format!("failed to parse {}", path.display()))?;
    let kernel = boot
        .get_blocks()
        .get_kernel()
        .ok_or_else(|| anyhow::anyhow!("kernel block not found"))?;

    let mut raw_kernel = Vec::<u8>::new();
    kernel.dump(&mut raw_kernel, false)
        .with_context(|| format!("failed to dump kernel block from {}", path.display()))?;

    log::debug!("Extracted kernel size: {} bytes", raw_kernel.len());

    // Validate kernel data is not empty
    if raw_kernel.is_empty() {
        return Err(anyhow::anyhow!("kernel block is empty"));
    }

    // Log the first bytes for debugging
    if raw_kernel.len() >= 16 {
        let hex_str = raw_kernel[0..16].iter()
            .map(|b| format!("{:02x}", b))
            .collect::<Vec<_>>()
            .join(" ");
        log::debug!("First 16 bytes of kernel: {}", hex_str);
    }

    let decompressed = decompress_kernel_payload(&raw_kernel).unwrap_or_else(|e| {
        log::warn!("Failed to decompress kernel: {}", e);
        // If decompression fails, the kernel might be uncompressed or partially corrupted
        // Try to extract Linux version line anyway
        raw_kernel.clone()
    });

    if let Some(info) = extract_linux_version_line(&decompressed) {
        return Ok(info);
    }

    bail!("failed to extract kernel uname/build-time from {}", path.display())
}

fn decompress_kernel_payload(input: &[u8]) -> Result<Vec<u8>> {
    let mut payload = input.to_vec();
    let mut depth = 0usize;

    while depth < 3 {
        let fmt = detect_format(&payload);
        if fmt == CompressionFormat::Unknown {
            // If format is unknown, try to find embedded compressed blob
            // Start searching from a larger offset to skip boot partition headers
            if let Some(found) = find_embedded_compressed_blob(&payload) {
                payload = found;
                depth += 1;
                continue;
            }
            break;
        }

        // Try to decompress
        match decompress_bytes(&payload, fmt) {
            Ok(decompressed) => {
                payload = decompressed;
                depth += 1;
            }
            Err(e) => {
                // If decompression fails, it might be due to corrupted header
                // Try to find the actual compressed data after padding/header
                log::warn!("Failed to decompress at depth {}: {}", depth, e);

                // Try searching for another compressed blob starting from offset
                if let Some(found) = find_embedded_compressed_blob_from_offset(&payload, 0x100) {
                    payload = found;
                    depth += 1;
                    continue;
                }

                // If all else fails, return what we have if it looks like valid data
                if depth == 0 && payload.len() > 0 {
                    // This might be an uncompressed or partially corrupted kernel
                    break;
                }
                return Err(e);
            }
        }
    }

    Ok(payload)
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
                Ok(_) => Ok(out),
                Err(e) => {
                    log::warn!("Gzip decompression failed with error: {}", e);
                    // Try to recover by searching for actual gzip start if current position isn't valid
                    Err(anyhow::anyhow!("gzip decompression failed: {}", e))
                }
            }
        }
        CompressionFormat::Xz => {
            XzReader::new(Cursor::new(buf), true).read_to_end(&mut out)?;
            Ok(out)
        }
        CompressionFormat::Lzma => {
            LzmaReader::new_mem_limit(Cursor::new(buf), u32::MAX, None)?.read_to_end(&mut out)?;
            Ok(out)
        }
        CompressionFormat::Bzip2 => {
            BzDecoder::new(Cursor::new(buf)).read_to_end(&mut out)?;
            Ok(out)
        }
        CompressionFormat::Lz4Frame => {
            Lz4FrameDecoder::new(Cursor::new(buf))?.read_to_end(&mut out)?;
            Ok(out)
        }
        CompressionFormat::Lz4Legacy | CompressionFormat::Lz4Lg => {
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
            pos = 4;
        }
    }

    while pos + 4 <= buf.len() {
        let block_size =
            u32::from_le_bytes([buf[pos], buf[pos + 1], buf[pos + 2], buf[pos + 3]]) as usize;
        pos += 4;
        if block_size == 0 || pos + block_size > buf.len() {
            break;
        }

        let block = &buf[pos..pos + block_size];
        let decompressed = lz4_block::decompress(block, Some(8 * 1024 * 1024))
            .context("lz4 legacy block decompression failed")?;
        out.extend_from_slice(&decompressed);
        pos += block_size;
    }

    if out.is_empty() {
        bail!("empty lz4 output");
    }
    Ok(out)
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
    // Skip potential boot partition header (typically 0x40 bytes or more)
    // and start searching from 0x40 to avoid misidentifying boot headers as kernel
    for i in 0x40..buf.len() {
        let rest = &buf[i..];
        if detect_format(rest) != CompressionFormat::Unknown {
            return Some(rest.to_vec());
        }
    }
    None
}

fn find_embedded_compressed_blob_from_offset(buf: &[u8], start_offset: usize) -> Option<Vec<u8>> {
    if buf.len() <= start_offset {
        return None;
    }
    // Search from a specific offset for compressed blob markers
    for i in start_offset..buf.len() {
        let rest = &buf[i..];
        if detect_format(rest) != CompressionFormat::Unknown {
            return Some(rest.to_vec());
        }
    }
    None
}

fn extract_linux_version_line(buf: &[u8]) -> Option<(String, String)> {
    let needle = b"Linux version ";
    let mut best: Option<(String, String)> = None;
    let mut found = 0usize;

    for idx in find_all(buf, needle) {
        let tail = &buf[idx..buf.len().min(idx + 1024)];
        let end = tail
            .iter()
            .position(|b| *b == b'\n' || *b == 0)
            .unwrap_or(tail.len());
        let line = String::from_utf8_lossy(&tail[..end]).trim().to_string();
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
            continue;
        }
        found += 1;
        if found >= 2 {
            return Some((release, build_time));
        }
        if best.is_none() {
            best = Some((release, build_time));
        }
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
