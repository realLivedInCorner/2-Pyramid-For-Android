<!-- markdownlint-disable MD033 MD036 -->

<p align="center">
  <img src="./2pyr-logo.svg" width="160" alt="2-Pyramid logo">
</p>

<h1 align="center">2-Pyramid for Android</h1>

<p align="center">
  <strong>资源包版本转换器的 Android 原生版本 · The Nextgen Multi-Version Universal Resource Pack Converter, on Android</strong>
</p>

<p align="center">
  <img alt="Version" src="https://img.shields.io/badge/version-2.0.0--alpha-007bff?style=flat-square">
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android%2012%2B-3DDC84?style=flat-square">
  <img alt="License" src="https://img.shields.io/badge/license-MIT-22c55e?style=flat-square">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=flat-square">
  <img alt="Compose" src="https://img.shields.io/badge/Compose-2024.10-4285F4?style=flat-square">
  <img alt="Rust" src="https://img.shields.io/badge/Rust-stable-orange?style=flat-square">
  <img alt="UniFFI" src="https://img.shields.io/badge/UniFFI-0.32-94a3b8?style=flat-square">
</p>

---

## 中文

**2-Pyramid for Android** 是桌面版 [2-Pyramid](https://github.com/realLivedInCorner/2-Pyramid) 的 Android 原生移植版。把桌面端 1.2 万行 Rust 转换核心剥离 Tauri shell 之后,通过 UniFFI 直接桥到 Kotlin/Jetpack Compose 端。

### ✨ 特性

- **跨 22 个版本** — 涵盖 Classic / Modern / Caves & Cliffs / Trails & Tales / Tricky Trials / Bundles of Bravery 六个时代
- **原生 Android** — Kotlin 2.0 + Jetpack Compose Material 3,Foreground Service (`dataSync`) 后台队列
- **本地处理** — 资源包全程在设备本地处理,不上传任何数据
- **轻量 APK** — R8 + 资源裁剪后约 **8 MB**(`arm64-v8a` only)
- **JNA 桥接** — UniFFI 0.32 生成的 Kotlin binding 通过 JNA 调 .so 中的 Rust 函数
- **Material 3 主题** — Light 主题 + 蓝 (`#007BFF`) 强调色 + 玻璃卡 + 慢节奏弹性 stagger 动画

### 🚀 快速开始

#### 用户(直接用)

从 [Releases](../../releases) 下载最新 `app-release.apk` 装到 Android 12+ 设备上即可。首次启动从 `Downloads/2FA` 选 zip 文件,选目标版本,点「开始转换」。

#### 开发者(本地 build)

```bash
git clone git@github.com:realLivedInCorner/2-Pyramid-For-Android.git
cd 2-Pyramid-For-Android

# 1. 创建 local.properties(2fa-android/local.properties.example 是模板)
cp 2fa-android/local.properties.example 2fa-android/local.properties
# 把 sdk.dir / ndk.dir 改成你机器上的 Android SDK 路径

# 2. 装 Rust + cargo-ndk + uniffi-bindgen
rustup target add aarch64-linux-android
cargo install cargo-ndk
cargo install uniffi --features=cli --locked

# 3. 一次性编 Rust + 生成 UniFFI binding(可选 — 已被 gitignore, 改了 Rust 才需要)
tools/build-rust-android.bat arm64-v8a

# 4. 用 Android Studio 打开 2fa-android/ 目录
#    或者直接用 Gradle:
cd 2fa-android
./gradlew :app:assembleDebug
# APK 在 app/build/outputs/apk/debug/app-debug.apk
```

### 🏗️ 架构

```
┌──────────────────────────────────────────────────────────┐
│                Android (Kotlin / Compose)                 │
│                                                          │
│   ui.screens/   ui.components/   ui.theme/               │
│   ├ HomeScreen (Bento 主卡 + 玻璃卡)                     │
│   ├ ConvertScreen (队列 + VersionDialog)                  │
│   ├ HistoryScreen (历史记录 + 搜索)                      │
│   └ SettingsScreen (5 组玻璃卡)                          │
│                                                          │
│   service.ZipQueueService (Foreground Service 队列消费)  │
│   queue.QueueRepository    (内存队列)                    │
│   native.RustNative        (UniFFI 桥)                   │
│   settings.AppSettings     (SharedPreferences 持久化)   │
└─────────────────────┬────────────────────────────────────┘
                      │ UniFFI 0.32 + JNA
                      ▼
┌──────────────────────────────────────────────────────────┐
│             convert-core/ (Rust crate)                   │
│                                                          │
│   lib.rs             UniFFI 入口                         │
│   converters/        85 个版本转换模块                   │
│   hurray/            DTD Pipeline + BFS 调度             │
│   runtime.rs         运行时路径注入(替代 dirs crate)     │
│   build.rs           uniffi-bindgen build 钩子            │
└──────────────────────────────────────────────────────────┘
```

- **Rust 核心** (`convert-core/`) — 完全独立,剥离了 Tauri / Windows 依赖,通过 `runtime::set_runtime(RuntimePaths)` 注入 Android 路径
- **UniFFI 桥** — `lib.rs` 用 `#[uniffi::export]` 暴露 facade,`build.rs` 触发 `uniffi-bindgen generate` 抽 Kotlin binding
- **JNA 桥接** — UniFFI 0.32 生成的 Kotlin binding 调 JNA 找到 .so 里的 `extern "C" fn` 符号
- **Android UI** — Compose Material 3 + SlowElastic 入场动画 + 玻璃风卡片 + 蓝主题

### 🧰 技术栈

| 层 | 技术 |
|---|---|
| Android UI | Kotlin 2.0.21 + Jetpack Compose (BOM 2024.10) + Material 3 + Navigation Compose |
| 桥 | UniFFI 0.32 + JNA 5.13 + Kotlin Coroutines 1.7 |
| 后台 | Foreground Service (`dataSync`,Android 14+ 强制) |
| 持久化 | SharedPreferences + SAF (`DocumentFile`) |
| 转换核心 | Rust 1.92 (stable) + `image` + `zip` + `rayon` + `regex` + `serde` + `uniffi` |
| 编译 | `cargo ndk` + `uniffi-bindgen` + AGP 8.7.2 + Gradle 8.10.2 + Android NDK 27.2 |
| API 范围 | `minSdk = 31` (Android 12) / `targetSdk = 36` (Android 16) / `compileSdk = 36` |

### 📁 项目结构

```
2-Pyramid-For-Android/
├── 2fa-android/                   Android Studio 项目
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── kotlin/com/twopyramid/twofa/
│   │   │   │   ├── ui/             Compose 屏幕 + 主题 + 组件
│   │   │   │   ├── service/        Foreground Service
│   │   │   │   ├── queue/          内存队列 repo
│   │   │   │   ├── native/         RustNative (UniFFI 桥)
│   │   │   │   ├── settings/       AppSettings (SharedPreferences)
│   │   │   │   ├── history/        历史记录 repo
│   │   │   │   ├── data/           数据模型
│   │   │   │   └── uniffi/         ← 由 uniffi-bindgen 自动生成 (.gitignore)
│   │   │   ├── res/                资源(主题 / 颜色 / 图标)
│   │   │   └── jniLibs/            ← 编译产物 (.gitignore)
│   │   ├── build.gradle.kts
│   │   ├── proguard-rules.pro
│   │   └── version.properties      自动 versionCode +1
│   ├── gradle/libs.versions.toml
│   ├── build.gradle.kts
│   ├── gradle.properties
│   ├── local.properties.example
│   └── settings.gradle.kts
│
├── convert-core/                  Rust 转换核心(剥离自 Hurricane)
│   ├── src/
│   │   ├── lib.rs                 UniFFI 入口 + facade
│   │   ├── converters/            85 个版本转换模块
│   │   ├── hurray/                DTD Pipeline + BFS 调度
│   │   ├── runtime.rs             运行时路径注入
│   │   └── ...
│   ├── build.rs                   uniffi-bindgen build 钩子
│   ├── Cargo.toml
│   └── Cargo.lock
│
├── tools/
│   ├── build-rust-android.bat     一次性手工 build + 复制 .so + 生成 UniFFI binding
│   └── build-rust-android.ps1     (PowerShell 版本,等效)
│
├── 2pyr-logo.svg
├── LICENSE                        MIT
└── README.md                      (本文件)
```

### 🔧 关键决策

- **arm64-v8a only** — Pixel 6 / 现代设备都是 arm64;armeabi-v7a + x86_64 的 jnidispatch 砍掉
- **LTO + strip = "debuginfo"** — `convert-core/src/lib.rs` 的 release profile 用 thin LTO + 仅剥 DWARF debug,保留 UniFFI 导出符号
- **R8 minify** — release build 启 `isMinifyEnabled = true` + `isShrinkResources = true`,`.dex` 从 42MB 砍到 3.8MB
- **JNA desktop natives 排除** — `packaging.resources.excludes` 砍 `com/sun/jna/{aix-*,win32-*,darwin-*}/**`,Android 不用
- **ProGuard 规则** — 必须 `-keep class uniffi.** { *; }`(按 package 名,不是目录),不然 R8 会把 JNA `external fun` 删了导致 `v(version unavailable)`
- **versionCode 自动 +1** — `version.properties` 存当前号,build 时自动 +1,`versionName` 拼成 `2.0.0-alpha.<N>`

### 🤝 贡献

欢迎 PR。改动前请先跑:

```bash
# Rust 单测(85 个 converter 自测,大多需要 Pika 5K 16x 测试夹具,缺了自动 skip)
cd convert-core
PIKA_5K_16X_PATH=/path/to/pika-5k-16x cargo test

# Kotlin 编译
cd ../2fa-android
./gradlew :app:assembleDebug
```

Converter 新增或修改时,务必对照桌面版 [2-Pyramid](https://github.com/realLivedInCorner/2-Pyramid) 仓库的同源文件,确保核心算法一致。

### 📄 版权与免责声明

- **Minecraft** 是 Mojang Synergies AB(微软旗下全资子公司)的注册商标。
- **2-Pyramid for Android** 由 2-Pyramid Studio 独立自主研发,与 Mojang Synergies AB、Microsoft 不存在任何关联。
- 本工具仅面向玩家自制内容,不包含任何 Mojang 官方源代码、游戏资源及受版权保护的核心内容。

---

## English

**2-Pyramid for Android** is the Android-native port of the desktop [2-Pyramid](https://github.com/realLivedInCorner/2-Pyramid) resource-pack converter. The 12 kLOC Rust core is extracted from the Tauri shell and bridged into Kotlin / Jetpack Compose via UniFFI.

### ✨ Features

- **22 versions across six eras** — Classic / Modern / Caves & Cliffs / Trails & Tales / Tricky Trials / Bundles of Bravery
- **Native Android** — Kotlin 2.0 + Jetpack Compose Material 3, Foreground Service (`dataSync`) for the conversion queue
- **Fully local** — Files never leave the device
- **Tiny APK** — ~8 MB after R8 + resource shrink (`arm64-v8a` only)
- **UniFFI bridge** — UniFFI 0.32 generated Kotlin binding via JNA into the Rust `.so`
- **Material 3 theming** — Light theme + blue accent (`#007BFF`) + glass cards + slow elastic stagger animation

### 🚀 Quick Start

#### Users

Grab the latest `app-release.apk` from [Releases](../../releases), sideload onto any Android 12+ device. Pick a `.zip` from `Downloads/2FA`, choose a target version, hit "Start conversion".

#### Developers

```bash
git clone git@github.com:realLivedInCorner/2-Pyramid-For-Android.git
cd 2-Pyramid-For-Android

# 1. local.properties from template
cp 2fa-android/local.properties.example 2fa-android/local.properties
# edit sdk.dir / ndk.dir to your machine

# 2. Rust toolchain
rustup target add aarch64-linux-android
cargo install cargo-ndk
cargo install uniffi --features=cli --locked

# 3. One-time Rust build + UniFFI binding (gitignored; only needed after Rust changes)
tools/build-rust-android.bat arm64-v8a

# 4. Open 2fa-android/ in Android Studio, or:
cd 2fa-android
./gradlew :app:assembleDebug
```

### 🏗️ Architecture

The same DTD Pipeline and BFS Scheduler from the desktop version, stripped of Tauri and bridged through UniFFI:

- **`convert-core/`** — Pure Rust, Tauri/Windows-free, takes runtime paths via `runtime::set_runtime(RuntimePaths)` (instead of hardcoding `dirs::data_local_dir()`)
- **UniFFI 0.32** — `#[uniffi::export]` facade in `lib.rs`; `build.rs` triggers `uniffi-bindgen generate` for the Kotlin binding
- **JNA bridge** — UniFFI 0.32's Kotlin binding uses JNA to look up `extern "C" fn` symbols in the `.so` by method name
- **Android UI** — Compose Material 3 + slow elastic stagger entrance animation + glass cards + blue theme

### 🧰 Tech Stack

| Layer | Tech |
|---|---|
| Android UI | Kotlin 2.0.21 + Jetpack Compose (BOM 2024.10) + Material 3 + Navigation Compose |
| Bridge | UniFFI 0.32 + JNA 5.13 + Kotlin Coroutines 1.7 |
| Background | Foreground Service (`dataSync`, mandatory on Android 14+) |
| Persistence | SharedPreferences + SAF (`DocumentFile`) |
| Core | Rust 1.92 (stable) + `image` + `zip` + `rayon` + `regex` + `serde` + `uniffi` |
| Build | `cargo ndk` + `uniffi-bindgen` + AGP 8.7.2 + Gradle 8.10.2 + Android NDK 27.2 |
| API | `minSdk = 31` (Android 12) / `targetSdk = 36` (Android 16) / `compileSdk = 36` |

### 📁 Project Layout

```
2-Pyramid-For-Android/
├── 2fa-android/                   Android Studio project
├── convert-core/                  Rust conversion core (extracted from Hurricane)
├── tools/
│   ├── build-rust-android.bat     One-time Rust + UniFFI binding build
│   └── build-rust-android.ps1     (PowerShell equivalent)
├── 2pyr-logo.svg
├── LICENSE                        MIT
└── README.md
```

### 🔧 Key Decisions

- **arm64-v8a only** — modern devices are all arm64; jnidispatch for armeabi-v7a / x86_64 dropped
- **LTO + strip = "debuginfo"** — thin LTO across crates + strip only DWARF debug info, preserves UniFFI export symbols
- **R8 minify** — `isMinifyEnabled = true` + `isShrinkResources = true`, `.dex` 42 MB → 3.8 MB
- **JNA desktop natives excluded** — `packaging.resources.excludes` drops `com/sun/jna/{aix-*,win32-*,darwin-*}/**`
- **ProGuard rule** — must keep `uniffi.**` (by package name, not directory path) or R8 will strip the JNA `external fun` declarations and the engine version pill shows "v(version unavailable)"
- **Auto versionCode +1** — `version.properties` stores the current number; build auto-increments; `versionName` becomes `2.0.0-alpha.<N>`

### 🤝 Contributing

PRs welcome. Before pushing, please run:

```bash
cd convert-core
PIKA_5K_16X_PATH=/path/to/pika-5k-16x cargo test    # 85 converter self-tests; skip if fixture missing

cd ../2fa-android
./gradlew :app:assembleDebug
```

When adding or modifying a converter, cross-check the homonymous function in the desktop [2-Pyramid](https://github.com/realLivedInCorner/2-Pyramid) repo to keep the algorithm in sync.

### 📄 Copyright & Disclaimer

- **Minecraft** is a registered trademark of Mojang Synergies AB (a wholly-owned subsidiary of Microsoft).
- **2-Pyramid for Android** is independently developed by 2-Pyramid Studio and is not affiliated with Mojang Synergies AB or Microsoft.
- This tool is for player-made content only; it does not include any Mojang official source code, game assets, or copyrighted core material.

---

## License / 许可

[MIT](./LICENSE) © 2025–2026 2-Pyramid Studio
