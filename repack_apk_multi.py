#!/usr/bin/env python3
"""
Batch APK repack tool - Multi-APK support based on repack_apk.py
"""

import sys
import argparse
from pathlib import Path
from typing import List, Dict, Optional
import re

import repack_apk as repack


def detect_arch_from_filename(apk_path: Path) -> Optional[str]:
    """Detect architecture from APK filename"""
    name = apk_path.stem.lower()
    
    arch_patterns = {
        r'arm64|aarch64': 'arm64-v8a',
        r'armv7|armeabi': 'armeabi-v7a',
        r'x86_64|amd64': 'x86_64',
        r'x86|i686': 'x86',
    }
    
    for pattern, arch in arch_patterns.items():
        if re.search(pattern, name):
            return arch
    return None


def find_all_apks(app_build_type: str) -> List[Path]:
    """Find all APKs"""
    pattern = repack.workspace_root() / "manager" / "app" / "build" / "outputs" / "apk" / app_build_type
    apks = sorted(pattern.glob("*.apk"), key=lambda p: p.stat().st_mtime, reverse=True)
    if not apks:
        raise FileNotFoundError(f"No APK found under: {pattern}")
    return apks


def process_single_apk(apk_path: Path, args: argparse.Namespace, out_dir: Path) -> Path:
    """Process a single APK, auto-detect architecture from filename"""
    import tempfile
    from zipfile import ZipFile, ZIP_DEFLATED, ZipInfo
    
    # Detect architecture from filename
    detected_arch = detect_arch_from_filename(apk_path)
    
    # Prepare architecture list
    if detected_arch:
        arch_filters = [detected_arch]
        print(f"[INFO] {apk_path.name} -> detected arch: {detected_arch}")
    elif args.arch:
        arch_filters = repack.normalize_arch_values(args.arch)
        print(f"[INFO] {apk_path.name} -> using CLI arch: {', '.join(arch_filters)}")
    else:
        # Auto-detect from APK contents
        arch_filters = repack.collect_existing_arches(apk_path)
        if not arch_filters:
            arch_filters = ["arm64-v8a"]
        print(f"[INFO] {apk_path.name} -> auto-detected from APK: {', '.join(arch_filters)}")
    
    # Find ksud binaries
    ksud_build_type = args.ksud_build_type or "debug"
    ksud_by_arch = repack.find_ksud_binaries_by_arch(ksud_build_type, arch_filters)
    
    # Handle missing ksud
    missing_ksud_arches = [arch for arch in arch_filters if arch not in ksud_by_arch]
    if missing_ksud_arches:
        existing_ksud_arches = set(repack.collect_existing_ksud_arches(apk_path))
        missing_in_apk = [arch for arch in missing_ksud_arches if arch not in existing_ksud_arches]
        if missing_in_apk:
            raise RuntimeError(
                f"ksud binary not found for {apk_path.name} architecture(s): {', '.join(missing_in_apk)}"
            )
        print(f"[WARN] {apk_path.name}: using existing libksud.so for {', '.join(missing_ksud_arches)}")
    
    # Generate output filename
    if args.output_name:
        output_name = f"{args.output_name}_{apk_path.stem}"
    else:
        output_name = apk_path.stem
    
    unsigned_path = out_dir / f"{output_name}-repack-unsigned.apk"
    aligned_path = out_dir / f"{output_name}-repack-aligned.apk"
    signed_path = out_dir / f"{output_name}.apk"
    
    # Clean stale files
    for stale in (unsigned_path, aligned_path, signed_path):
        if stale.exists():
            stale.unlink()
    
    # Handle strip
    strip_tool = None
    if args.strip:
        strip_tool = repack.find_strip_tool()
        if strip_tool is None:
            print(f"[WARN] {apk_path.name}: strip requested but tool not found")
    
    try:
        repack.repack_apk(apk_path, unsigned_path, arch_filters, ksud_by_arch, strip_tool)
        repack.assert_required_libs(unsigned_path, arch_filters)
        
        zipalign = repack.find_android_tool("zipalign")
        if zipalign is None:
            raise FileNotFoundError("zipalign not found")
        repack.run_cmd(
            [str(zipalign), "-P", "16", "-f", "4", str(unsigned_path), str(aligned_path)],
            "zipalign failed",
        )
        
        signing = {
            "keystore_path": args.keystore_path,
            "key_alias": args.key_alias,
            "keystore_pass": args.keystore_pass,
            "key_pass": args.key_pass,
        }
        # Load signing info from config file if available
        if hasattr(args, '_signing_from_config'):
            signing.update({k: v for k, v in args._signing_from_config.items() if v})
        
        repack.validate_signing_config(signing)
        
        apksigner = repack.find_android_tool("apksigner")
        if apksigner is None:
            raise FileNotFoundError("apksigner not found")
        
        repack.run_cmd(
            [
                str(apksigner),
                "sign",
                "--v1-signing-enabled",
                "false",
                "--v2-signing-enabled",
                "true",
                "--v3-signing-enabled",
                "false",
                "--v4-signing-enabled",
                "false",
                "--ks",
                str(Path(signing["keystore_path"]).resolve()),
                "--ks-key-alias",
                signing["key_alias"],
                "--ks-pass",
                f"pass:{signing['keystore_pass']}",
                "--key-pass",
                f"pass:{signing['key_pass']}",
                "--out",
                str(signed_path),
                str(aligned_path),
            ],
            "apksigner failed",
        )
    finally:
        for tmp in (unsigned_path, aligned_path):
            if tmp.exists():
                tmp.unlink()
    
    return signed_path


def load_config_and_merge(args: argparse.Namespace) -> None:
    """Load config file and merge into args"""
    ws_root = repack.workspace_root()
    config_path = Path(args.config) if args.config else ws_root / "repack-config.json"
    
    if config_path.exists():
        file_cfg = repack.load_jsonc(config_path)
        # Save signing info
        signing = file_cfg.get("signing", {})
        args._signing_from_config = signing
        
        # Merge other configs (if not specified via CLI)
        if not args.app_build_type:
            args.app_build_type = file_cfg.get("app_build_type", "debug")
        if not args.ksud_build_type:
            args.ksud_build_type = file_cfg.get("ksud_build_type", "debug")
        if not args.arch:
            args.arch = file_cfg.get("arch", [])
        if args.strip is None:
            args.strip = file_cfg.get("strip", False)
        if not args.output_name:
            args.output_name = file_cfg.get("output_name", "")
        if not args.keystore_path:
            args.keystore_path = signing.get("keystore_path", "")
        if not args.key_alias:
            args.key_alias = signing.get("key_alias", "")
        if not args.keystore_pass:
            args.keystore_pass = signing.get("keystore_pass", "")
        if not args.key_pass:
            args.key_pass = signing.get("key_pass", "")
    else:
        args._signing_from_config = {}


def main():
    # Reuse original module's parser
    parser = repack.build_parser()
    
    # Update help description
    parser.description = "Repack manager APK with ksud injection (supports multiple APKs with filename arch detection)"
    
    args = parser.parse_args()
    
    if args.command != "repack":
        return args.func(args)
    
    # Load config file
    load_config_and_merge(args)
    
    # Find all APKs
    app_build_type = args.app_build_type or "debug"
    try:
        apks = find_all_apks(app_build_type)
    except FileNotFoundError as e:
        print(f"[ERROR] {e}", file=sys.stderr)
        return 1
    
    print(f"[INFO] Found {len(apks)} APK(s) to process:")
    for apk in apks:
        print(f"  - {apk.name}")
    
    # Output directory
    out_dir = Path(args.out_dir) if args.out_dir else repack.workspace_root() / "dist"
    out_dir.mkdir(parents=True, exist_ok=True)
    
    # Process one by one
    success_count = 0
    for i, apk in enumerate(apks, 1):
        print(f"\n{'='*50}")
        print(f"[{i}/{len(apks)}] Processing: {apk.name}")
        print(f"{'='*50}")
        
        try:
            signed_path = process_single_apk(apk, args, out_dir)
            print(f"[SUCCESS] Output: {signed_path}")
            success_count += 1
        except Exception as e:
            print(f"[ERROR] Failed: {e}", file=sys.stderr)
            continue
    
    # Summary
    print(f"\n{'='*50}")
    print(f"[SUMMARY] Processed {success_count}/{len(apks)} APKs successfully")
    if success_count == 0:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())