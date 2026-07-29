use std::{
    env,
    fs::{self, File},
    io::Write,
    path::{Path, PathBuf},
    process::Command,
};

fn get_git_version() -> Result<(u32, String), std::io::Error> {
    let output = Command::new("git")
        .args(["rev-list", "--count", "HEAD"])
        .output()?;

    let output = output.stdout;
    let version_code = String::from_utf8(output).expect("Failed to read git count stdout");
    let version_code: u32 = version_code
        .trim()
        .parse()
        .map_err(|_| std::io::Error::other("Failed to parse git count"))?;
    let version_code = 30000 + 700 + version_code; // For historical reasons

    let version_name = String::from_utf8(
        Command::new("git")
            .args(["describe", "--tags", "--always"])
            .output()?
            .stdout,
    )
    .map_err(|_| std::io::Error::other("Failed to parse git count"))?;
    let version_name = version_name.trim_start_matches('v').to_string();
    Ok((version_code, version_name))
}

fn configure_bindgen() {
    // The bindgen::Builder is the main entry point
    // to bindgen, and lets you build up options for
    // the resulting bindings.
    let bindings = bindgen::Builder::default()
        // The input header we would like to generate
        // bindings for.
        .header("src/android/uapi/ksu_uapi.h")
        .clang_args(["-x", "c++", "-I../../"])
        // Tell cargo to invalidate the built crate whenever any of the
        // included header files changed.
        .parse_callbacks(Box::new(bindgen::CargoCallbacks::new()))
        // Finish the builder and generate the bindings.
        .generate()
        // Unwrap the Result and panic on failure.
        .expect("Unable to generate bindings");

    // Write the bindings to the $OUT_DIR/bindings.rs file.
    let out_path = std::path::PathBuf::from(env::var("OUT_DIR").unwrap());
    // for debug, uncomment below
    // let out_path = std::path::PathBuf::from(env::var("CARGO_MANIFEST_DIR").unwrap());
    bindings
        .write_to_file(out_path.join("bindings.rs"))
        .expect("Couldn't write bindings!");
}

fn build_mkbootfs(out_directory: &Path) {
    const API_LEVEL: u32 = 26;
    const LIBRARY_NAME: &str = "mkbootfs";

    let target = env::var("TARGET").expect("TARGET not set");
    let manifest_directory =
        PathBuf::from(env::var_os("CARGO_MANIFEST_DIR").expect("CARGO_MANIFEST_DIR not set"));
    let (clang_target, asset_subdirectory) = match target.as_str() {
        "aarch64-linux-android" => (
            format!("aarch64-linux-android{API_LEVEL}"),
            Path::new("bin/aarch64"),
        ),
        "armv7-linux-androideabi" => (
            format!("armv7a-linux-androideabi{API_LEVEL}"),
            Path::new("bin/arm"),
        ),
        "x86_64-linux-android" => (
            format!("x86_64-linux-android{API_LEVEL}"),
            Path::new("bin/x86_64"),
        ),
        _ => panic!("mkbootfs is not configured for Android target {target}"),
    };

    let source = manifest_directory.join("src/mkbootfs.cpp");
    let asset_directory = manifest_directory.join(asset_subdirectory);
    let output = asset_directory.join("mkbootfs");
    println!("cargo:rerun-if-changed={}", source.display());
    println!("cargo:rerun-if-changed={}", output.display());
    println!("cargo:rerun-if-env-changed=ANDROID_NDK_HOME");
    println!("cargo:rerun-if-env-changed=ANDROID_NDK_ROOT");

    let mut build = cc::Build::new();
    build
        .cpp(true)
        .target(&target)
        .cargo_metadata(false)
        .out_dir(out_directory)
        .file(&source)
        .std("c++20")
        .opt_level_str("z")
        .define("_FILE_OFFSET_BITS", "64")
        .define("_FORTIFY_SOURCE", "2")
        .flag(format!("--target={clang_target}"))
        .flags([
            "-fPIE",
            "-fstack-protector-strong",
            "-ffunction-sections",
            "-fdata-sections",
            "-fvisibility=hidden",
            "-fno-exceptions",
            "-fno-rtti",
        ])
        .warnings(true)
        .extra_warnings(true)
        .warnings_into_errors(true);
    let compiler = build.get_compiler();
    if !compiler.is_like_clang() {
        panic!(
            "mkbootfs requires the Android NDK clang++ compiler, got {:?}",
            compiler.path()
        );
    }

    fs::create_dir_all(&asset_directory).unwrap_or_else(|error| {
        panic!(
            "failed to create mkbootfs asset directory {}: {error}",
            asset_directory.display()
        )
    });

    build.compile(LIBRARY_NAME);
    let archive = out_directory.join(format!("lib{LIBRARY_NAME}.a"));
    if !archive.is_file() {
        panic!(
            "cc did not produce the expected mkbootfs archive {}",
            archive.display()
        );
    }

    let temporary_output = out_directory.join("mkbootfs");
    let mut linker = cc::Build::new();
    linker
        .cpp(true)
        .target(&target)
        .cargo_metadata(false)
        .no_default_flags(true)
        .flag(format!("--target={clang_target}"));
    let mut command = linker.get_compiler().to_command();
    command
        .arg(&archive)
        .args([
            "-pie",
            "-static-libstdc++",
            "-Wl,--gc-sections",
            "-Wl,--build-id=none",
            "-Wl,--exclude-libs,ALL",
            "-Wl,-z,relro,-z,now",
            "-Wl,--strip-all",
        ])
        .arg("-o")
        .arg(&temporary_output);

    let status = command
        .status()
        .unwrap_or_else(|error| panic!("failed to execute Android clang++ for mkbootfs: {error}"));
    if !status.success() {
        panic!("failed to build mkbootfs for {target}: {status}");
    }

    let built = fs::read(&temporary_output).unwrap_or_else(|error| {
        panic!(
            "failed to read built mkbootfs {}: {error}",
            temporary_output.display()
        )
    });
    if fs::read(&output).ok().as_deref() != Some(built.as_slice()) {
        fs::write(&output, built).unwrap_or_else(|error| {
            panic!(
                "failed to install mkbootfs asset {}: {error}",
                output.display()
            )
        });
    }
}

fn main() {
    let (code, name) = match get_git_version() {
        Ok((code, name)) => (code, name),
        Err(_) => {
            // show warning if git is not installed
            println!("cargo:warning=Failed to get git version, using 0.0.0");
            (0, "0.0.0".to_string())
        }
    };
    let out_dir = env::var("OUT_DIR").expect("Failed to get $OUT_DIR");
    let out_dir = Path::new(&out_dir);
    File::create(Path::new(out_dir).join("VERSION_CODE"))
        .expect("Failed to create VERSION_CODE")
        .write_all(code.to_string().as_bytes())
        .expect("Failed to write VERSION_CODE");

    File::create(Path::new(out_dir).join("VERSION_NAME"))
        .expect("Failed to create VERSION_NAME")
        .write_all(name.trim().as_bytes())
        .expect("Failed to write VERSION_NAME");

    let target_os = env::var("CARGO_CFG_TARGET_OS").expect("CARGO_CFG_TARGET_OS not set");
    if target_os == "android" {
        build_mkbootfs(out_dir);
        configure_bindgen();
    }
}
