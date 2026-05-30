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
