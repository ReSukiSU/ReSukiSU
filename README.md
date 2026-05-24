# KittiSU
<p align="center">
  <img src="assets/kittisu_logo.jpg" width="220px" alt="KittiSU Logo">
</p>

<p align="center">
  <strong>English</strong> | <a href="./docs/vn/README.md">Tiếng Việt</a> | <a href="./docs/zh/README.md">简体中文</a>
</p>

<p align="center">
  A fork of <a href="https://github.com/ReSukiSU/ReSukiSU">ReSukiSU</a>, based on <a href="https://github.com/SukiSU-Ultra/SukiSU-Ultra">SukiSU-Ultra</a> — with a custom manager, branding, and multi-backend support.
</p>

<p align="center">
  <a href="https://github.com/anotheranhiutangerine/KittiSU/releases/latest"><img src="https://img.shields.io/github/v/release/anotheranhiutangerine/KittiSU?label=Release&logo=github" alt="Latest release"></a>
  <a href="https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html"><img src="https://img.shields.io/badge/License-GPL%20v2-orange.svg?logo=gnu" alt="Kernel License: GPL v2"></a>
  <a href="/LICENSE"><img src="https://img.shields.io/github/license/anotheranhiutangerine/KittiSU?logo=gnu" alt="Other part License: GPL v3"></a>
</p>

---

## Features

1. Kernel-based `su` and root access management
2. Module system based on [metamodules](https://kernelsu.org/guide/metamodule.html): Pluggable infrastructure for systemless modifications
3. [App Profile](https://kernelsu.org/guide/app-profile.html): Lock up the root power in a cage
4. Support non-GKI and GKI 1.0
5. KPM Support
6. Custom manager theme with KittiSU branding
7. Built-in SuSFS management tool
8. Multi-manager support — works with [Official KernelSU](https://github.com/tiann/KernelSU) / [RKSU](https://github.com/rsuntk/KernelSU) / [MKSU](https://github.com/5ec1cff/KernelSU) / [SukiSU](https://github.com/SukiSU-Ultra/SukiSU-Ultra) backends


## Compatibility Status

- KittiSU officially supports Android GKI 2.0 devices (kernel 5.10+).
- Older kernels (3.4+) are also compatible, but the kernel will have to be built manually.
- Currently, only `arm64-v8a`, `armeabi-v7a` and `x86_64` are supported.
- [SuSFS](https://gitlab.com/simonpunk/susfs4ksu) in this project **only** supports backport to kernel 4.3+.
- `Tracepoint Syscall Redirect hook` only supports GKI2 (5.10+) kernels.

## Hook Mode

| Mode | Description |
|---|---|
| **Tracepoint Syscall Redirect hook** | Default hook mode from [upstream](https://github.com/tiann/KernelSU). Only supports GKI2 kernels with `arm64-v8a` or `x86_64`. |
| **Manual Hook** | Most compatible hook, supports Linux kernel 3.4 – 6.18. |
| **SuSFS Inline Hook** | Hook from [SuSFS](https://github.com/simonpunk/susfs4ksu), similar to Manual Hook but provided by the SuSFS project. |

## Integration
### Here is the command to add KittiSU to your kernel source.
```
curl -LSs "https://raw.githubusercontent.com/anotheranhiutangerine/KittiSU/main/kernel/setup.sh" | bash
```

## Building the Kernel

KittiSU uses GitHub Actions for kernel builds. See the [GKI_KernelSU_SUSFS](https://github.com/anotheranhiutangerine/GKI_KernelSU_SUSFS) repository for build workflows.

## Building the Manager

```bash
cd manager
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew assembleRelease
```

The APK will be at `manager/app/build/outputs/apk/release/`.

## KPM Support

- Based on KernelPatch, with redundant features removed — only KPM support retained.
- WIP: Expanding APatch compatibility by integrating additional functions.

**Open-source repository**: [https://github.com/ShirkNeko/SukiSU_KernelPatch_patch](https://github.com/ShirkNeko/SukiSU_KernelPatch_patch)

**KPM template**: [https://github.com/udochina/KPM-Build-Anywhere](https://github.com/udochina/KPM-Build-Anywhere)

> [!Note]
> 1. Requires `CONFIG_KPM=y`
> 2. Non-GKI devices require `CONFIG_KALLSYMS=y` and `CONFIG_KALLSYMS_ALL=y`
> 3. For kernels below `4.19`, backporting `set_memory.h` from `4.19` is required.

## Sponsor

- [ShirkNeko](https://afdian.com/a/shirkneko) (maintainer of SukiSU)
- [weishu](https://github.com/sponsors/tiann) (author of KernelSU)

<details>
<summary>ShirkNeko's sponsorship list</summary>

- [Ktouls](https://github.com/Ktouls) — Thanks so much for bringing me support.
- [zaoqi123](https://github.com/zaoqi123) — Thanks for the milk tea.
- [wswzgdg](https://github.com/wswzgdg) — Many thanks for supporting this project.
- [yspbwx2010](https://github.com/yspbwx2010) — Many thanks.
- [DARKWWEE](https://github.com/DARKWWEE) — 100 USDT
- [Saksham Singla](https://github.com/TypeFlu) — Provide and maintain the website
- [OukaroMF](https://github.com/OukaroMF) — Donation of website domain name
</details>

## License

- Files in the `kernel` directory are under [GPL-2.0-only](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html) license.
- Anime character sticker images (`ic_launcher(?!.*alt.*).*`) are copyrighted by [怡子曰曰](https://space.bilibili.com/10545509). Brand Intellectual Property owned by [明风 OuO](https://space.bilibili.com/274939213). Vectorization by @MiRinChan. Usage requires compliance with [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/legalcode.txt) and authorization from both authors.
- All other parts are under [GPL-3.0 or later](https://www.gnu.org/licenses/gpl-3.0.html) license.

## Credit

### KittiSU
- [anotheranhiutangerine](https://github.com/anotheranhiutangerine) — KittiSU fork maintainer

<details>
<summary>ReSukiSU credit</summary>

- [ReSukiSU/ReSukiSU](https://github.com/ReSukiSU/ReSukiSU) — upstream fork base
</details>

<details>
<summary>SukiSU credit</summary>

- [SukiSU-Ultra/SukiSU-Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra) — upstream
- [KernelSU](https://github.com/tiann/KernelSU) — upstream
- [MKSU](https://github.com/5ec1cff/KernelSU) — Magic Mount
- [RKSU](https://github.com/rsuntk/KernelsU) — non-GKI support
- [susfs](https://gitlab.com/simonpunk/susfs4ksu) — Root hiding kernel patches and userspace module
- [KernelPatch](https://github.com/bmax121/KernelPatch) — Kernel module for APatch
</details>

<details>
<summary>KernelSU credit</summary>

- [Kernel-Assisted Superuser](https://git.zx2c4.com/kernel-assisted-superuser/about/) — The KernelSU idea.
- [Magisk](https://github.com/topjohnwu/Magisk) — The powerful root tool.
- [genuine](https://github.com/brevent/genuine/) — APK v2 signature validation.
- [Diamorphine](https://github.com/m0nad/Diamorphine) — Some rootkit skills.
</details>
