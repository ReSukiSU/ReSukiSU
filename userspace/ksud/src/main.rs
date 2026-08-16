#![feature(decl_macro)]

#[cfg(target_os = "android")]
mod android;
#[cfg(target_os = "android")]
mod anykernel3;
mod apk_sign;
mod assets;
mod banner;
mod boot_patch;
#[cfg(not(target_os = "android"))]
mod cli_non_android;
mod defs;
mod lkm_image;
mod lkm_image_btf;

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
