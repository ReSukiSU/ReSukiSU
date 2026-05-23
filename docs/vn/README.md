# KittiSU
<p align="center">
  <img src="../../assets/kittisu_logo.jpg" width="220px" alt="KittiSU Logo">
</p>

<p align="center">
  <a href="../../README.md">English</a> | <strong>Tiếng Việt</strong> | <a href="../zh/README.md">简体中文</a>
</p>

<p align="center">
  Một bản fork của <a href="https://github.com/ReSukiSU/ReSukiSU">ReSukiSU</a>, dựa trên <a href="https://github.com/SukiSU-Ultra/SukiSU-Ultra">SukiSU-Ultra</a> — với manager tùy chỉnh, branding riêng và hỗ trợ đa backend.
</p>

<p align="center">
  <a href="https://github.com/anotheranhiutangerine/KittiSU/releases/latest"><img src="https://img.shields.io/github/v/release/anotheranhiutangerine/KittiSU?label=Release&logo=github" alt="Phiên bản mới nhất"></a>
  <a href="https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html"><img src="https://img.shields.io/badge/License-GPL%20v2-orange.svg?logo=gnu" alt="Kernel License: GPL v2"></a>
  <a href="/LICENSE"><img src="https://img.shields.io/github/license/anotheranhiutangerine/KittiSU?logo=gnu" alt="Phần khác License: GPL v3"></a>
</p>

---

## Tính năng

1. Quản lý `su` và root dựa trên kernel
2. Hệ thống module dựa trên [metamodules](https://kernelsu.org/guide/metamodule.html): Kiến trúc plug-in cho các sửa đổi không cần thay đổi hệ thống
3. [App Profile](https://kernelsu.org/guide/app-profile.html): Đóng quyền root vào lồng
4. Hỗ trợ non-GKI và GKI 1.0
5. Hỗ trợ KPM
6. Giao diện manager với branding KittiSU tùy chỉnh
7. Công cụ quản lý SuSFS tích hợp sẵn
8. Hỗ trợ đa quản lý — hoạt động với các backend [Official KernelSU](https://github.com/tiann/KernelSU) / [RKSU](https://github.com/rsuntk/KernelSU) / [MKSU](https://github.com/5ec1cff/KernelSU) / [SukiSU](https://github.com/SukiSU-Ultra/SukiSU-Ultra)


## Trạng thái tương thích

- KittiSU chính thức hỗ trợ các thiết bị Android GKI 2.0 (kernel 5.10+).
- Các kernel cũ hơn (3.4+) cũng tương thích, nhưng phải tự build kernel.
- Hiện chỉ hỗ trợ kiến trúc: `arm64-v8a`, `armeabi-v7a` và `x86_64`.
- [SuSFS](https://gitlab.com/simonpunk/susfs4ksu) trong dự án này **chỉ** hỗ trợ backport đến kernel 4.3+.
- `Tracepoint Syscall Redirect hook` chỉ hỗ trợ kernel GKI2 (5.10+).

## Chế độ Hook

| Chế độ | Mô tả |
|---|---|
| **Tracepoint Syscall Redirect hook** | Chế độ hook mặc định từ [upstream](https://github.com/tiann/KernelSU). Chỉ hỗ trợ kernel GKI2 với `arm64-v8a` hoặc `x86_64`. |
| **Manual Hook** | Hook tương thích nhất, hỗ trợ Linux kernel 3.4 – 6.18. |
| **SuSFS Inline Hook** | Hook từ [SuSFS](https://github.com/simonpunk/susfs4ksu), tương tự Manual Hook nhưng được cung cấp bởi dự án SuSFS. |

## Build Kernel

KittiSU sử dụng GitHub Actions để build kernel. Xem repository [GKI_KernelSU_SUSFS](https://github.com/anotheranhiutangerine/GKI_KernelSU_SUSFS) để biết thêm chi tiết.

## Build Manager

```bash
cd manager
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew assembleRelease
```

APK sẽ nằm tại `manager/app/build/outputs/apk/release/`.

## Hỗ trợ KPM

- Dựa trên KernelPatch, đã loại bỏ các tính năng trùng lặp — chỉ giữ lại hỗ trợ KPM.
- Đang phát triển: Mở rộng khả năng tương thích APatch bằng cách tích hợp thêm các hàm.

**Repository mã nguồn mở**: [https://github.com/ShirkNeko/SukiSU_KernelPatch_patch](https://github.com/ShirkNeko/SukiSU_KernelPatch_patch)

**KPM template**: [https://github.com/udochina/KPM-Build-Anywhere](https://github.com/udochina/KPM-Build-Anywhere)

> [!Note]
> 1. Yêu cầu `CONFIG_KPM=y`
> 2. Thiết bị non-GKI yêu cầu `CONFIG_KALLSYMS=y` và `CONFIG_KALLSYMS_ALL=y`
> 3. Đối với kernel dưới `4.19`, cần backport `set_memory.h` từ `4.19`.

## Tài trợ

- [ShirkNeko](https://afdian.com/a/shirkneko) (người duy trì SukiSU)
- [weishu](https://github.com/sponsors/tiann) (tác giả KernelSU)

<details>
<summary>Danh sách tài trợ của ShirkNeko</summary>

- [Ktouls](https://github.com/Ktouls) — Cảm ơn rất nhiều vì đã ủng hộ.
- [zaoqi123](https://github.com/zaoqi123) — Cảm ơn vì trà sữa.
- [wswzgdg](https://github.com/wswzgdg) — Cảm ơn rất nhiều vì đã ủng hộ dự án.
- [yspbwx2010](https://github.com/yspbwx2010) — Cảm ơn rất nhiều.
- [DARKWWEE](https://github.com/DARKWWEE) — 100 USDT
- [Saksham Singla](https://github.com/TypeFlu) — Cung cấp và duy trì website
- [OukaroMF](https://github.com/OukaroMF) — Tài trợ tên miền website
</details>

## Giấy phép

- Các file trong thư mục `kernel` thuộc giấy phép [GPL-2.0-only](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html).
- Hình ảnh sticker nhân vật anime (`ic_launcher(?!.*alt.*).*`) được bản quyền bởi [怡子曰曰](https://space.bilibili.com/10545509). Sở hữu trí tuệ thương hiệu thuộc về [明风 OuO](https://space.bilibili.com/274939213). Vector hóa bởi @MiRinChan. Việc sử dụng yêu cầu tuân thủ [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/legalcode.txt) và sự cho phép từ cả hai tác giả.
- Tất cả các phần khác thuộc giấy phép [GPL-3.0 trở lên](https://www.gnu.org/licenses/gpl-3.0.html).

## Ghi công

### KittiSU
- [anotheranhiutangerine](https://github.com/anotheranhiutangerine) — Người duy trì fork KittiSU

<details>
<summary>Ghi công ReSukiSU</summary>

- [ReSukiSU/ReSukiSU](https://github.com/ReSukiSU/ReSukiSU) — Cơ sở fork upstream
</details>

<details>
<summary>Ghi công SukiSU</summary>

- [SukiSU-Ultra/SukiSU-Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra) — upstream
- [KernelSU](https://github.com/tiann/KernelSU) — upstream
- [MKSU](https://github.com/5ec1cff/KernelSU) — Magic Mount
- [RKSU](https://github.com/rsuntk/KernelsU) — Hỗ trợ non-GKI
- [susfs](https://gitlab.com/simonpunk/susfs4ksu) — Bản vá kernel ẩn root và module userspace
- [KernelPatch](https://github.com/bmax121/KernelPatch) — Module kernel cho APatch
</details>

<details>
<summary>Ghi công KernelSU</summary>

- [Kernel-Assisted Superuser](https://git.zx2c4.com/kernel-assisted-superuser/about/) — Ý tưởng KernelSU.
- [Magisk](https://github.com/topjohnwu/Magisk) — Công cụ root mạnh mẽ.
- [genuine](https://github.com/brevent/genuine/) — Xác thực chữ ký APK v2.
- [Diamorphine](https://github.com/m0nad/Diamorphine) — Một số kỹ thuật rootkit.
</details>
