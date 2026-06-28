//! Helper functions
use regex_lite::Regex;

/// Write a &str to C-style char* with length cutdown.
pub fn str_to_c_array<const N: usize>(s: &str, array: &mut [u8; N]) {
    let bytes = s.as_bytes();
    let len = bytes.len().min(N - 1);
    array[..len].copy_from_slice(&bytes[..len]);
    array[len] = 0;
}

/// Get a string from C-style char*
pub fn c_array_to_string<const N: usize>(array: &[u8; N]) -> String {
    let len = array.iter().position(|&x| x == 0).unwrap_or(N);
    let bytes = &array[..len].to_vec();
    String::from_utf8(bytes.to_vec()).unwrap_or_else(|_| "<invalid>".to_string())
}

pub fn is_valid_uname_version(version: &str) -> bool {
    let regex = Regex::new(r"^([0-9]+)\.([0-9]+)(?:\.([0-9]+))?(?:-([a-zA-Z0-9.-]+))?$").unwrap();

    regex.is_match(version)
}

pub fn is_valid_uname_release(release: &str) -> bool {
    let regex = Regex::new(
        r"^#[0-9]+(?:\s+[A-Z0-9_]+)*\s+(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun)\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\s+[1-3]?[0-9]\s+[0-2][0-9]:[0-5][0-9]:[0-5][0-9]\s+[A-Z]{3,4}\s+[0-9]{4}\s+[a-zA-Z0-9_.-]+\s+Android$"
    ).unwrap();

    regex.is_match(release)
}
