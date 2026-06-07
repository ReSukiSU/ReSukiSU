//! Helper functions

/// Write a &str to C-style char* with length cutdown.
pub(super) fn str_to_c_array<const N: usize>(s: &str, array: &mut [u8; N]) {
    let bytes = s.as_bytes();
    let len = bytes.len().min(N - 1);
    array[..len].copy_from_slice(&bytes[..len]);
    array[len] = 0;
}

// Get a string from C-style char*
pub(super) fn c_array_to_string<const N: usize>(array: &[u8; N]) -> String {
    let len = array.iter().position(|&x| x == 0).unwrap_or(N);
    let bytes = &array[..len].to_vec();
    String::from_utf8(bytes.to_vec()).unwrap_or_else(|_| "<invalid>".to_string())
}
