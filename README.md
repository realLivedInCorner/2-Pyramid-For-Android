<!-- markdownlint-disable MD033 MD036 -->

<p align="center">
  <img src="./2pyr-logo.svg" width="160" alt="2-Pyramid logo">
</p>

<h1 align="center">2-Pyramid for Android</h1>

<p align="center">
  <strong>资源包版本转换器的 Android 原生版本 · Modern Universal Multi-Version Resource Pack Converter, on Android</strong>
</p>

<p align="center">
  <img alt="Version" src="https://img.shields.io/badge/version-2.1.0-007bff?style=flat-square">
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android%2012%2B-3DDC84?style=flat-square">
  <img alt="License" src="https://img.shields.io/badge/license-MIT-22c55e?style=flat-square">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=flat-square">
  <img alt="Compose" src="https://img.shields.io/badge/Compose-2024.10-4285F4?style=flat-square">
  <img alt="Rust" src="https://img.shields.io/badge/Rust-stable-orange?style=flat-square">
  <img alt="UniFFI" src="https://img.shields.io/badge/UniFFI-0.32-94a3b8?style=flat-square">
</p>

---

## 中文

**2-Pyramid for Android** 是桌面版 [2-Pyramid](https://github.com/realLivedInCorner/2-Pyramid) 的
Android 原生移植版。**v2.1.0** 起与桌面 **2.203.0 的 M3 / A-ROM 转换内核**同源（约 3 万行 Rust），
剥离 Tauri shell 后通过 UniFFI 直接桥到 Kotlin / Jetpack Compose 端。

### ✨ 特性

- **27 个目标** — 26 个 Java 版本区间（1.6 → 最新 26.3 / `pack_format` 97）+ Bedrock（实验性）
- **M3 / A-ROM 内核** — 单一对象模型（`Pack`/`Tx`/`Layer`）与 46 个原生任务；Java 目标产物直接写 zip
- **转换选项** — 着色器适配、彻底删 shaders、1.21.4+ 物品定义升级、OptiFine CIT 转换/删除、
  `2pyr-gen.json` 参数记录、输出命名模板（默认值严格对齐桌面）
- **UImage 素材已内置** — 桌面版同款 80 张原版素材（0.56 MB）随 APK 打包，首次启动解压到
  `filesDir/UImage`；依赖原版素材的生成类任务（tipped arrow / smithing overlay / snow bucket / overlay icons 等）可用
- **真实进度与结果** — 引擎级进度回调（解压 / 任务 / 打包）+ 结果页展示源→目标、耗时、
  跳过数 `skipped` 与说明 `notes`
- **原生 Android** — Kotlin 2.0 + Jetpack Compose Material 3，Foreground Service (`dataSync`) 后台队列，
  浅色/深色/跟随系统三档主题
- **本地处理** — 资源包全程在设备本地处理，不上传任何数据
- **轻量 APK** — R8 + 资源裁剪后约 **10.2 MB**（`arm64-v8a` only；`.so` ≈ 5.5 MB）
- **JNA 桥接** — UniFFI 0.32 生成的 Kotlin binding 通过 JNA 调 `.so` 中的 Rust 函数

### 🚀 快速开始

#### 用户（直接用）

从 [Releases](../../releases) 下载最新 `app-release.apk` 装到 Android 12+ 设备上即可。
首次启动从 `Downloads/2FA` 选 zip 文件，选目标版本与转换选项，点「开始转换」。

#### 开发者（本地 build）

```bash
git clone git@github.com:realLivedInCorner/2-Pyramid-For-Android.git
cd 2-Pyramid-For-Android

# 1. local.properties（2fa-android/local.properties.example 是模板）
cp 2fa-android/local.properties.example 2fa-android/local.properties
# 把 sdk.dir 改成你机器上的 Android SDK 路径

# 2. 工具链：Rust + cargo-ndk + uniffi-bindgen（NDK 27.3）
rustup target add aarch64-linux-android   # 若镜像 404，可手工 stage rust-std（见 AGENTS.md）
cargo install cargo-ndk
cargo install uniffi --features=cli --locked

# 3. 编 Rust + 生成 UniFFI binding（.so/binding 均 gitignore，改 Rust 才需要）
tools/build-rust-android.bat arm64-v8a

# 4. 构建 APK（Gradle 的 buildRustAndroid 会自动重跑第 3 步；只想编 Kotlin 可 SKIP_RUST_BUILD=true）
cd 2fa-android
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

> 构建系统会从 `ANDROID_HOME` / `local.properties` 解析 SDK，并注入 `ANDROID_NDK_HOME` 给 cargo-ndk；
> NDK 版本取 `gradle.properties` 的 `android.ndkVersion`（当前 **27.3.13750724**）。

### 🏗️ 架构

```
┌──────────────────────────────────────────────────────────────┐
│                  Android (Kotlin / Compose)                   │
│  ui/          Home / Convert(队列+选项+进度+结果) / History / Settings
│  service/     ZipQueueService (Foreground Service, dataSync)
│  queue/       QueueRepository + ProgressRepository             │
│  settings/    SettingsRepository (SharedPreferences + StateFlow)
│  native/      RustNative (UniFFI 桥 + 进度回调)                │
└──────────────────────────┬───────────────────────────────────┘
                           │ UniFFI 0.32 + JNA
                           ▼
┌──────────────────────────────────────────────────────────────┐
│                   convert-core/ (Rust crate)                  │
│  lib.rs           UniFFI facade：ConvertOptions/ConvertRequest │
│                   /ConvertReport/ConvertProgress/convert_zip  │
│  arom/            A-ROM 对象模型 + engine（BFS 调度/版本边）    │
│  natives/         46 个原生转换任务（eraser/architect/surgeon/reverse）
│  pack/            生产编排（version_converter）、IO、analysis、diff
│  bedrock_convert/ Bedrock ↔ Java 结构互转（j2b/b2j）           │
│  native_run.rs    驱动；task_registry.rs 90 项任务元数据       │
│  progress.rs      全局进度 sink；runtime/logger/image_utils    │
│                   /commands 为 Android 适配层                  │
└──────────────────────────────────────────────────────────────┘
```

- **Rust 核心**（`convert-core/`）— 完全独立，剥离 Tauri / Windows 依赖；通过
  `runtime::set_runtime(RuntimePaths)` 注入 Android 路径（log / app data / UImage / **temp**）
- **UniFFI 桥** — `lib.rs` 用 `#[uniffi::export]` / `#[uniffi::Record]` / callback interface 暴露 facade；
  Kotlin binding 由 `uniffi-bindgen` 生成（gitignore，不手改）
- **JNA 桥接** — binding 通过 JNA 找 `.so` 里的 `extern "C"` 符号
- **Android UI** — Compose Material 3 + 玻璃卡 + 蓝主题 + 深色模式；转换选项/进度/结果均在 Compose 侧

### 🧰 技术栈

| 层 | 技术 |
|---|---|
| Android UI | Kotlin 2.0.21 + Jetpack Compose（BOM 2024.10）+ Material 3 + Navigation Compose |
| 桥 | UniFFI 0.32 + JNA 5.14 + Kotlin Coroutines |
| 后台 | Foreground Service（`dataSync`，Android 14+ 强制） |
| 持久化 | SharedPreferences（设置/历史）+ SAF（`DocumentFile`） |
| 转换核心 | Rust 1.92 (stable) + `image` + `zip` + `rayon` + `regex` + `serde` + `sha2` + `flate2` + `uniffi` |
| 编译 | `cargo ndk` + `uniffi-bindgen` + AGP 8.11 + Gradle 8.13 + Android NDK 27.3 |
| API 范围 | `minSdk = 31` (Android 12) / `targetSdk = 37` / `compileSdk = 37` |

### 📁 项目结构

```
2-Pyramid-For-Android/
├── 2fa-android/                   Android Studio 项目
│   ├── app/src/main/
│   │   ├── kotlin/com/twopyramid/twofa/
│   │   │   ├── ui/          Compose 屏幕 + 主题 + 组件
│   │   │   ├── service/     Foreground Service 队列
│   │   │   ├── queue/       QueueRepository + ProgressRepository
│   │   │   ├── native/      RustNative（UniFFI 桥）
│   │   │   ├── settings/    SettingsRepository / ConvertOptions
│   │   │   ├── history/     历史记录
│   │   │   └── uniffi/      ← uniffi-bindgen 生成（gitignore）
│   │   ├── res/             主题 / 颜色 / 图标 / 字符串
│   │   └── jniLibs/         ← 编译产物（gitignore）
│   └── App 构建配置 / version.properties（versionCode 自动 +1）
│
├── convert-core/                  Rust 转换内核（M3 / A-ROM）
│   ├── src/{arom,natives,pack,bedrock_convert,color}/ + lib.rs/native_run.rs/…
│   ├── tests/android_smoke.rs     facade 端到端 smoke test
│   ├── examples/convert_cli.rs    host 差分 runner（不参与 Android 构建）
│   ├── PORT-NOTES.md              移植/适配/差异记录
│   └── Cargo.toml
│
├── tools/
│   ├── build-rust-android.bat     编 .so + 生成 Kotlin binding（Gradle 自动调用）
│   └── build-rust-android.{ps1,sh}
├── docs/                          同步报告 / 分层方案 / 同步记录
├── 2pyr-logo.svg · LICENSE（MIT） · README.md
```

### 🔧 关键决策

- **arm64-v8a only** — 现代设备均为 arm64；`jniLibs` 只打 `arm64-v8a`
- **M3 引擎整体替换** — 与桌面 2.203.0 同源，**不做算法重写**；后续同步=按目录 diff
- **适配层集中** — `runtime`/`logger`/`image_utils`/`commands`/`progress` 等 Android 专属 shim 独立，
  引擎目录尽量保持与桌面一致，便于未来对比
- **`panic = "unwind"`** — 让 UniFFI 的 `catch_unwind` 能把 Rust panic 转成 Kotlin 异常（而不是整 App 闪退）
- **BUILD_NUMBER 编译期注入** — `gen_metadata` / Bedrock manifest 的版本信息；默认 21000、可被 env 覆盖
- **临时目录注入** — Android 没有可写 `/tmp`：`init_runtime` 的 `tempDir`（cacheDir）会设置 `TMPDIR`，
  `tempfile` 全部落在 app cache
- **R8 minify** — release 启 `isMinifyEnabled` + `isShrinkResources`；`-keep class uniffi.**` 必须保留
- **versionCode 自动 +1** — `version.properties` 存当前号，构建时自动 +1；`versionName` 固定为 `2.1.0`

### 🤝 贡献

欢迎 PR。改动前请跑：

```bash
# Rust：单测 + smoke（host，Windows/Linux/macOS 均可）
cd convert-core
cargo test --lib              # 220 passed; 6 ignored（真实包 E2E，需 AROM_REAL_PACK，本机跳过）
cargo test --test android_smoke
cargo build --release --examples

# Kotlin：编译
cd ../2fa-android
SKIP_RUST_BUILD=true ./gradlew :app:compileDebugKotlin
./gradlew :app:assembleDebug
```

### ⚠️ 已知限制

- **未在真机验证**：本仓库的构建与差分验证在 PC 上完成；UImage 首次启动解压、TMPDIR、JNA 跨线程进度回调、
  SAF 输入/输出、256x/512x 大包内存与耗时需要实机确认。
- **Bedrock 双向转换是实验性**：j2b/b2j 未完成，仅用于测试。
- **OptiFine CIT 转换默认关闭**：有用户报告 1.20.1 → 1.21.9–1.21.10 弓/钻石剑紫黑；离线 codec 已修正但
  未实机复现，按产品决策默认不生成（转换页可显式打开，请自行在游戏内验证）。
- **Java 目标产物根会多出 `2pyr-gen.json`**：转换参数记录（可追溯/排障），Minecraft 不读取；
  转换页可关闭。Bedrock 目标不写。
- **转换器行为以桌面 2.203.0 为准**：转换产物差异（如按钮描边修复）属预期。

### 📄 版权与免责声明

- **Minecraft** 是 Mojang Synergies AB（微软旗下全资子公司）的注册商标。
- **2-Pyramid for Android** 由 2-Pyramid Studio 独立自主研发，与 Mojang Synergies AB、Microsoft 不存在任何关联。
- 本工具仅面向玩家自制内容，不包含任何 Mojang 官方源代码、游戏资源及受版权保护的核心内容。

---

## English

**2-Pyramid for Android** is the Android-native port of the desktop
[2-Pyramid](https://github.com/realLivedInCorner/2-Pyramid) resource-pack converter.
Since **v2.1.0** it shares the desktop **2.203.0 M3 / A-ROM conversion core** (~30 kLOC Rust),
with the Tauri shell stripped and the engine bridged into Kotlin / Jetpack Compose via UniFFI.

### ✨ Features

- **27 targets** — 26 Java version ranges (1.6 → latest 26.3 / `pack_format` 97) + Bedrock (experimental)
- **M3 / A-ROM core** — single object model (`Pack`/`Tx`/`Layer`) with 46 native tasks; Java-target outputs are
  streamed straight to zip
- **Conversion options** — shader adaptation, delete shaders, 1.21.4+ item definition upgrade,
  OptiFine CIT convert/delete, `2pyr-gen.json` metadata, output naming template
  (defaults strictly aligned with desktop)
- **Real progress & results** — engine-level progress callbacks (extract / tasks / output) and a result view
  showing source→target, elapsed time, `skipped` count and `notes`
- **Native Android** — Kotlin 2.0 + Jetpack Compose Material 3, Foreground Service (`dataSync`) queue,
  light/dark/system themes
- **Fully local** — files never leave the device
- **Tiny APK** — ~10.2 MB after R8 + resource shrink (`arm64-v8a` only; `.so` ≈ 5.5 MB)
- **UniFFI bridge** — UniFFI 0.32 generated Kotlin binding via JNA into the Rust `.so`

### 🚀 Quick Start

#### Users

Grab the latest `app-release.apk` from [Releases](../../releases), sideload onto any Android 12+ device.
Pick a `.zip` from `Downloads/2FA`, choose a target version and options, hit "Start conversion".

#### Developers

```bash
git clone git@github.com:realLivedInCorner/2-Pyramid-For-Android.git
cd 2-Pyramid-For-Android

# 1. local.properties from the template
cp 2fa-android/local.properties.example 2fa-android/local.properties
# edit sdk.dir to your Android SDK path

# 2. Rust toolchain (NDK 27.3)
rustup target add aarch64-linux-android
cargo install cargo-ndk
cargo install uniffi --features=cli --locked

# 3. Build Rust + generate UniFFI binding (gitignored; only needed after Rust changes)
tools/build-rust-android.bat arm64-v8a

# 4. Build the APK (Gradle's buildRustAndroid re-runs step 3 automatically)
cd 2fa-android
./gradlew :app:assembleDebug
```

### 🏗️ Architecture

The same M3 / A-ROM engine as the desktop 2.203.0, stripped of Tauri and bridged through UniFFI:

- **`convert-core/`** — pure Rust: `arom/` (object model + engine), `natives/` (46 tasks),
  `pack/` (orchestration/IO/analysis/diff), `bedrock_convert/`, `native_run.rs`, `task_registry.rs`;
  Android adapters (`runtime`/`logger`/`image_utils`/`commands`/`progress`) are kept separate from the engine
- **UniFFI 0.32** — `#[uniffi::export]` facade in `lib.rs` (`ConvertOptions` / `ConvertRequest` / `ConvertReport` /
  `ConvertProgress` callback); Kotlin binding is generated, never hand-edited
- **JNA bridge** — the generated Kotlin binding looks up `extern "C"` symbols in the `.so` via JNA
- **Android UI** — Jetpack Compose Material 3, glass cards, blue accent + dark mode
  (conversion options / progress / results live in Compose)

### 🧰 Tech Stack

| Layer | Tech |
|---|---|
| Android UI | Kotlin 2.0.21 + Jetpack Compose (BOM 2024.10) + Material 3 + Navigation Compose |
| Bridge | UniFFI 0.32 + JNA 5.14 + Kotlin Coroutines |
| Background | Foreground Service (`dataSync`, mandatory on Android 14+) |
| Persistence | SharedPreferences (settings/history) + SAF (`DocumentFile`) |
| Core | Rust 1.92 (stable) + `image` + `zip` + `rayon` + `regex` + `serde` + `sha2` + `flate2` + `uniffi` |
| Build | `cargo ndk` + `uniffi-bindgen` + AGP 8.11 + Gradle 8.13 + Android NDK 27.3 |
| API | `minSdk = 31` (Android 12) / `targetSdk = 37` / `compileSdk = 37` |

### 📁 Project Layout

```
2-Pyramid-For-Android/
├── 2fa-android/                   Android Studio project
├── convert-core/                  Rust conversion core (M3 / A-ROM)
│   ├── src/{arom,natives,pack,bedrock_convert,color}/ + lib.rs/native_run.rs/…
│   ├── tests/android_smoke.rs     end-to-end facade smoke test
│   ├── examples/convert_cli.rs    host-side differential runner (not part of the Android build)
│   └── PORT-NOTES.md              port/adaptation notes
├── tools/build-rust-android.bat   .so + Kotlin binding build (invoked by Gradle)
├── docs/                          sync report / layered plan / sync record
├── 2pyr-logo.svg · LICENSE (MIT) · README.md
```

### 🔧 Key Decisions

- **arm64-v8a only** — modern devices are all arm64
- **Wholesale M3 engine replacement** — algorithmically identical to desktop 2.203.0; no algorithm rewrites
- **Android adapters are isolated** — engine dirs stay close to desktop for future directory-level diffs
- **`panic = "unwind"`** — lets UniFFI's `catch_unwind` surface Rust panics as Kotlin exceptions
- **BUILD_NUMBER injected at compile time** — used by `2pyr-gen.json` / Bedrock manifest (default 21000, env-overridable)
- **Temp dir injection** — Android has no writable `/tmp`; `init_runtime`'s `tempDir` (cacheDir) is exported as
  `TMPDIR` so `tempfile` stays inside the app cache
- **R8 minify** — release enables `isMinifyEnabled` + `isShrinkResources`; `-keep class uniffi.**` is mandatory
- **Auto versionCode +1** — `version.properties` is auto-incremented; `versionName` is fixed at `2.1.0`

### 🤝 Contributing

```bash
cd convert-core
cargo test --lib              # 220 passed; 6 ignored (real-pack E2E, skipped without AROM_REAL_PACK)
cargo test --test android_smoke
cargo build --release --examples

cd ../2fa-android
SKIP_RUST_BUILD=true ./gradlew :app:compileDebugKotlin
./gradlew :app:assembleDebug
```

### ⚠️ Known Limitations

- **Not verified on a physical device**: build and differential verification were done on a PC; the
  first-launch UImage extraction, TMPDIR, cross-thread JNA progress callbacks, SAF I/O and large-pack
  memory usage still need device testing.
- **Bedrock conversion is experimental** — j2b/b2j are incomplete and for testing only.
- **OptiFine CIT conversion is off by default** — users reported purple/black bow and diamond sword after
  1.20.1 → 1.21.9–1.21.10; the codec issue was fixed offline but not reproduced on-device, so it is gated off
  (can be enabled explicitly in the conversion options; verify in-game).
- **Java-target outputs gain a `2pyr-gen.json`** — a conversion-parameter record (traceability/support);
  Minecraft does not read it, and it can be turned off in the conversion page (Bedrock targets do not get it).
- **Converter behaviour follows desktop 2.203.0** — output differences (e.g. the button-sprite border fix) are expected.

### 📄 Copyright & Disclaimer

- **Minecraft** is a registered trademark of Mojang Synergies AB (a wholly-owned subsidiary of Microsoft).
- **2-Pyramid for Android** is independently developed by 2-Pyramid Studio and is not affiliated with
  Mojang Synergies AB or Microsoft.
- This tool is for player-made content only; it does not include any Mojang official source code, game assets,
  or copyrighted core material.

---

## License / 许可

[MIT](./LICENSE) © 2025–2026 2-Pyramid Studio
