#![deny(clippy::all, clippy::pedantic)]
#![warn(clippy::nursery)]
#![allow(
    clippy::module_name_repetitions,
    clippy::cast_possible_truncation,
    clippy::cast_sign_loss,
    clippy::cast_precision_loss,
    clippy::doc_markdown,
    clippy::too_many_lines,
    clippy::cast_possible_wrap
)]

#[cfg(target_os = "android")]
mod android;
mod apk_sign;
mod assets;
mod boot_patch;
#[cfg(not(target_os = "android"))]
mod cli_non_android;
mod defs;

fn main() -> anyhow::Result<()> {
    #[cfg(target_os = "android")]
    {
        android::cli::run()
    }
    #[cfg(not(target_os = "android"))]
    {
        cli_non_android::run()
    }
}
