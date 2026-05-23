# KittiSU
<p align="center">
  <img src="../../assets/kittisu_logo.jpg" width="220px" alt="KittiSU Logo">
</p>

<p align="center">
  <a href="../../README.md">English</a> | <a href="../vn/README.md">Tiếng Việt</a> | <strong>简体中文</strong>
</p>

<p align="center">
  一个基于 <a href="https://github.com/ReSukiSU/ReSukiSU">ReSukiSU</a> 的分支，上游为 <a href="https://github.com/SukiSU-Ultra/SukiSU-Ultra">SukiSU-Ultra</a> — 拥有自定义管理器、品牌标识和多后端支持。
</p>

<p align="center">
  <a href="https://github.com/anotheranhiutangerine/KittiSU/releases/latest"><img src="https://img.shields.io/github/v/release/anotheranhiutangerine/KittiSU?label=Release&logo=github" alt="最新发行"></a>
  <a href="https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html"><img src="https://img.shields.io/badge/License-GPL%20v2-orange.svg?logo=gnu" alt="Kernel License: GPL v2"></a>
  <a href="/LICENSE"><img src="https://img.shields.io/github/license/anotheranhiutangerine/KittiSU?logo=gnu" alt="其他部分 License: GPL v3"></a>
</p>

---

## 特性

1. 基于内核的 `su` 和 root 权限管理
2. 基于 [metamodules](https://kernelsu.org/zh_CN/guide/metamodule.html) 的模块系统：可插拔的无系统修改架构
3. [App Profile](https://kernelsu.org/zh_CN/guide/app-profile.html)：把 Root 权限关进笼子里
4. 支持 non-GKI 与 GKI 1.0
5. KPM 支持
6. KittiSU 品牌自定义管理器主题
7. 内置 SuSFS 管理工具
8. 多管理器支持 — 兼容 [官方 KernelSU](https://github.com/tiann/KernelSU) / [RKSU](https://github.com/rsuntk/KernelSU) / [MKSU](https://github.com/5ec1cff/KernelSU) / [SukiSU](https://github.com/SukiSU-Ultra/SukiSU-Ultra) 后端


## 兼容状态

- KittiSU 官方支持 Android GKI 2.0 设备（内核版本 5.10 以上）。
- 旧内核也是兼容的（3.4+），不过需要自己编译内核。
- 目前支持架构：`arm64-v8a`、`armeabi-v7a`、`x86_64`。
- [SuSFS](https://gitlab.com/simonpunk/susfs4ksu) 在本项目中**仅**支持 backport 到内核 4.3+。
- `Tracepoint Syscall Redirect hook` 只支持 GKI2 内核（5.10+）。

## Hook 模式

| 模式 | 描述 |
|---|---|
| **Tracepoint Syscall Redirect hook** | 默认 hook 模式，来自于[上游](https://github.com/tiann/KernelSU)。只支持 GKI2 内核且为 `arm64-v8a` 或 `x86_64` 架构。 |
| **Manual Hook** | 兼容性最强的钩子，支持 Linux Kernel 3.4 – 6.18。 |
| **SuSFS Inline Hook** | 来自 [SuSFS](https://github.com/simonpunk/susfs4ksu) 的 Hook，类似于 Manual Hook，但由 SuSFS 项目提供。 |

## 构建内核

KittiSU 使用 GitHub Actions 构建内核。详见 [GKI_KernelSU_SUSFS](https://github.com/anotheranhiutangerine/GKI_KernelSU_SUSFS) 仓库。

## 构建管理器

```bash
cd manager
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew assembleRelease
```

APK 输出路径：`manager/app/build/outputs/apk/release/`

## KPM 支持

- 基于 KernelPatch 开发，移除了与 KernelSU 重复的功能 — 仅保留 KPM 支持。
- 正在开发（WIP）：通过集成附加功能来扩展 APatch 兼容性。

**开源仓库**：[https://github.com/ShirkNeko/SukiSU_KernelPatch_patch](https://github.com/ShirkNeko/SukiSU_KernelPatch_patch)

**KPM 模板**：[https://github.com/udochina/KPM-Build-Anywhere](https://github.com/udochina/KPM-Build-Anywhere)

> [!Note]
> 1. 需要 `CONFIG_KPM=y`
> 2. Non-GKI 设备需要 `CONFIG_KALLSYMS=y` 和 `CONFIG_KALLSYMS_ALL=y`
> 3. 对于低于 `4.19` 的内核，需要从 `4.19` 的 `set_memory.h` 进行反向移植。

## 赞助

- [ShirkNeko](https://afdian.com/a/shirkneko)（SukiSU 主要维护者）
- [weishu](https://github.com/sponsors/tiann)（KernelSU 作者）

<details>
<summary>ShirkNeko 的赞助列表</summary>

- [Ktouls](https://github.com/Ktouls) — 非常感谢你给我带来的支持
- [zaoqi123](https://github.com/zaoqi123) — 请我喝奶茶也不错
- [wswzgdg](https://github.com/wswzgdg) — 非常感谢对此项目的支持
- [yspbwx2010](https://github.com/yspbwx2010) — 非常感谢
- [DARKWWEE](https://github.com/DARKWWEE) — 100 USDT
- [Saksham Singla](https://github.com/TypeFlu) — 网站的提供以及维护
- [OukaroMF](https://github.com/OukaroMF) — 网站域名捐赠
</details>

## 许可证

- 目录 `kernel` 下所有文件为 [GPL-2.0-only](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html)。
- 动漫人物图片表情包文件 `ic_launcher(?!.*alt.*).*` 的图像版权为[怡子曰曰](https://space.bilibili.com/10545509)所有，图像中的知识产权由[明风 OuO](https://space.bilibili.com/274939213)所有，矢量化由 @MiRinChan 完成。使用这些文件前，除了必须遵守 [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/legalcode.txt) 以外，还需要获得两位作者的授权。
- 除上述文件及目录外，其他部分均为 [GPL-3.0 或更高版本](https://www.gnu.org/licenses/gpl-3.0.html)。

## 鸣谢

### KittiSU
- [anotheranhiutangerine](https://github.com/anotheranhiutangerine) — KittiSU 分支维护者

<details>
<summary>ReSukiSU 鸣谢</summary>

- [ReSukiSU/ReSukiSU](https://github.com/ReSukiSU/ReSukiSU) — 上游分支基础
</details>

<details>
<summary>SukiSU 鸣谢</summary>

- [SukiSU-Ultra/SukiSU-Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra) — 上游
- [KernelSU](https://github.com/tiann/KernelSU) — 上游
- [MKSU](https://github.com/5ec1cff/KernelSU) — 魔法挂载支持
- [RKSU](https://github.com/rsuntk/KernelsU) — non-GKI 支持
- [susfs](https://gitlab.com/simonpunk/susfs4ksu) — 隐藏内核补丁以及用户空间模块的 KernelSU 附件
- [KernelPatch](https://github.com/bmax121/KernelPatch) — 内核模块 APatch 实现的关键部分
</details>

<details>
<summary>KernelSU 鸣谢</summary>

- [Kernel-Assisted Superuser](https://git.zx2c4.com/kernel-assisted-superuser/about/) — KernelSU 的灵感。
- [Magisk](https://github.com/topjohnwu/Magisk) — 强大的 root 工具箱。
- [genuine](https://github.com/brevent/genuine/) — APK v2 签名验证。
- [Diamorphine](https://github.com/m0nad/Diamorphine) — 一些 rootkit 技巧。
</details>
