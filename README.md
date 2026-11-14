<!-- markdownlint-disable MD033 MD036 -->

<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="./brand/logo-256-white.png">
    <img src="./brand/logo-256.png" width="160" alt="2-Pyramid logo">
  </picture>
</p>

<h1 align="center">2-Pyramid for Android</h1>

<p align="center">
  <strong>Minecraft 资源包多版本转换器 · Modern Universal Multi-Version Resource Pack Converter, on Android</strong>
</p>

<p align="center">
  <img alt="Version" src="https://img.shields.io/badge/version-2.1.0-14171A?style=flat-square">
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android%2012%2B-3DDC84?style=flat-square">
  <img alt="License" src="https://img.shields.io/badge/license-MIT-22c55e?style=flat-square">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=flat-square">
  <img alt="Compose" src="https://img.shields.io/badge/Compose-BOM%202024.10-4285F4?style=flat-square">
  <img alt="Rust" src="https://img.shields.io/badge/Rust-stable-orange?style=flat-square">
  <img alt="UniFFI" src="https://img.shields.io/badge/UniFFI-0.32-5F5A51?style=flat-square">
</p>

---

## 中文

**2-Pyramid for Android** 是桌面版 [2-Pyramid](https://github.com/realLivedInCorner/2-Pyramid) 的
Android 原生移植。**v2.1.0** 起与桌面 **2.203.0 的 M3 / A-ROM 转换内核**同源（约 3 万行 Rust），
剥离 Tauri shell 后通过 UniFFI 桥到 Kotlin / Jetpack Compose；界面按 **Precision Geometry** 设计语言
重做（暖纸墨令牌、无投影发丝线、统一弹窗壳、可选强调色）。

> **版本 / 构建号**：`versionName = 2.1.0`，`versionCode` 来自仓库根 [`BUILD`](./BUILD)（当前 **21000**）。
> 构建只读不写 —— 发布前用 `python tools/set_version.py --set X.Y.Z --build N` 显式推进，
> 一致性由 `python tools/set_version.py --check` 校验。**没有** `version.properties` 自动 +1。

### ✨ 特性

- **27 个目标** —— 26 个 Java 版本区间（1.6 → 最新 26.3 / `pack_format` 97）+ Bedrock（实验性）。
  `pack.mcmeta` 目标 ≥69 只写 `min_format`/`max_format`，并保留 `overlays` / `filter` 顶层字段。
- **M3 / A-ROM 内核** —— 与桌面 2.203.0 同源的单一对象模型（`Pack` / `Tx` / `Layer`）与 46 个原生任务；
  Java 目标产物直接序列化成 zip，解压并行、图片与条目表缓存。算法零重写，后续同步 = 目录级 diff。
- **转换选项** —— 着色器适配、彻底删除 shaders（破坏性，二次确认）、1.21.4+ 物品定义升级、
  OptiFine CIT 转换 / 彻底删除 CIT、`2pyr-gen.json` 参数记录；外加输出命名模板预设
  （`[Ver][Name]` / `[Ver][Time]` / `[Name]` / 自定义），默认值严格对齐桌面。
- **1.21.4+ 物品定义与 OptiFine CIT** —— 旧包 `overrides` + `CustomModelData` 迁成
  `assets/<ns>/items/<name>.json`；CIT 规则转成物品定义分支（CMD、附魔名 + 等级、双手上下文、弓的多状态
  贴图），翻不了的条件如实跳过并记录。**CIT 转换默认关闭**（用户实机报告过黑紫问题，待实机验证）。
- **真进度与结果** —— 引擎级进度回调（解压 0–12% / 任务 12–90% / 打包 90–100%）；
  结果页展示源→目标、输出路径、耗时、`skipped` 数量与可展开的 `notes`（notes 不等同于「跳过」）。
- **UImage 素材已内置** —— 桌面项目同款 80 张参照素材（0.56 MB）随 APK 打包，首次启动解压到
  `filesDir/UImage`；依赖原版素材的生成任务（tipped arrow / smithing overlay / snow bucket / overlay icons 等）可用。
- **原生 Android** —— Kotlin 2.0 + Jetpack Compose Material 3；前台服务队列（`dataSync`，Android 14+
  强制类型）；浅色 / 深色 / 跟随系统三档主题 + 自选强调色（深色下 OKLab 42% 白混合提亮，保色相）。
- **完全本地** —— 资源包全程在设备本地处理；**APK 未声明 `INTERNET` 权限**，无联网、无账号、无遥测、
  **无应用内自动更新**。
- **轻量 APK** —— R8 + 资源裁剪后约 **10.2 MB**（`arm64-v8a` only；`.so` ≈ 5.5 MB）。
- **JNA 桥接** —— UniFFI 0.32 生成的 Kotlin binding 通过 JNA 调 `.so` 中的 Rust 函数。

### ⚠️ 已知限制（请先读）

2-Pyramid 是**结构转换器**，不是原版素材库。以下几点是设计取舍，不是待修 bug：

- **未在真机验证**：构建与差分验证在 PC 完成；UImage 首次启动解压、TMPDIR、JNA 跨线程进度回调、
  SAF 输入/输出、256x/512x 大包内存与耗时仍需要真机确认。
- **目标版本新增内容为「近似生成」**：升级时若目标版本引入了原包不存在的新方块 / 新物品，程序会从最接近
  的现有贴图按手工标定的色相/饱和度/明度参数派生近似贴图。**这些不是原版资源，与原版视觉存在差异**。
- **Bedrock 双向转换为实验性**：j2b / b2j 未完成且已知存在问题，仅用于测试；选中该目标会弹出警示。
- **OptiFine CIT 转换默认关闭**：用户报告 1.20.1 → 1.21.9–1.21.10 转换后弓 / 钻石剑紫黑；离线 codec
  复核已修正确定的格式错误，但未实机渲染验证、原因尚未复现，故默认不生成。开启前请在游戏内验证；
  关闭时 CIT 源文件原样保留，OptiFine 客户端仍会读取。
- **Java 目标产物根会多出 `2pyr-gen.json`**：转换参数记录（可追溯 / 排障），Minecraft 不读取，可在转换
  选项关闭；Bedrock 目标不写。里面不含任何个人信息。
- **发行版默认只写 Warn / Error 日志**：Release 构建下 Info 级日志（含进度明细）不写文件；需要完整日志
  时，在设置 → 开发者模式打开后再复现。
- **底层差异**：Android 版 `zip` 未开 `time` feature → 产物容器时间戳为 1980（内容一致；差分按条目内容
  哈希比对，不比整 zip 字节）。桌面专属能力（更新器 / 安装器 / Overlay / Foray / 文件浏览器 / 日志脱敏 /
  CLI 工具）未移植。

### 🚀 快速开始

#### 用户（直接安装）

1. 前往本仓库 [Releases](https://github.com/realLivedInCorner/2-Pyramid-For-Android/releases)，
   下载最新 `2-Pyramid-Android-<版本>.apk` 与同名 `.apk.sha256`；
2. 校验完整性（可选但推荐）：

   ```bash
   sha256sum -c 2-Pyramid-Android-2.1.0.apk.sha256
   ```

   ```powershell
   Get-FileHash .\2-Pyramid-Android-2.1.0.apk -Algorithm SHA256
   ```

3. 允许「安装未知应用」后侧载安装（Android 12+ / arm64-v8a；覆盖安装保留设置与历史）；
4. 首次启动从系统文件选择器选 zip 包与输出目录，选目标版本与转换选项，点「开始转换」。

> 本应用**没有应用内自动更新**，升级 = 手动下载新 APK 覆盖安装。官方 APK 使用仓库内公开的
> debug keystore（`tools/ci/debug.keystore`，Android 标准 debug 口令）签名（便于覆盖安装与自建包互操作），
> 该签名**不能证明来源**；完整性以官方 Release 页 + `.sha256` 为准，
> 需要更强保证请从源码自行构建（见下）。详见 [`legal/SECURITY.md`](./legal/SECURITY.md)。

#### 开发者（本地构建）

```bash
git clone git@github.com:realLivedInCorner/2-Pyramid-For-Android.git
cd 2-Pyramid-For-Android

# 0. 工具链：Rust stable + cargo-ndk + uniffi-bindgen + JDK 17 + Android SDK（NDK 27.3.13750724）
cargo install cargo-ndk
cargo install uniffi --features=cli --locked

# 1. local.properties（模板：2fa-android/local.properties.example）
cp 2fa-android/local.properties.example 2fa-android/local.properties
# 把 sdk.dir 改成你机器上的 Android SDK 路径

# 2. 编 Rust + 生成 UniFFI binding（.so/binding 均 gitignore，改 Rust 才需要）
tools/build-rust-android.bat arm64-v8a      # Windows
bash tools/build-rust-android.sh arm64-v8a  # Linux / macOS / CI

# 3. 构建 APK（Gradle 会自动重跑第 2 步；只想编 Kotlin 可 SKIP_RUST_BUILD=true）
cd 2fa-android
./gradlew :app:assembleDebug     # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease   # 仓库内 debug keystore 签名（tools/ci/debug.keystore），R8 + 资源裁剪
```

> SDK/NDK 由 Gradle 配置期从 `ANDROID_HOME` / `local.properties` 解析并注入 cargo-ndk 子进程；
> NDK 版本取 `gradle.properties` 的 `android.ndkVersion`（**27.3.13750724**）。
> 本机 `ANDROID_HOME` / `ANDROID_NDK_HOME` 为空也能构建。

### 🔄 更新与发布

**没有应用内自动更新，也不检查更新。** 升级是一个显式动作：

| 步骤 | 命令 / 位置 |
|---|---|
| 1. 写变更记录 | `CHANGELOG.md` 的 `[Unreleased]` → `[<版本>]` 段 |
| 2. 写发版说明 | `docs/compose/releases/news-<版本>.md`（Release 正文直接用它） |
| 3. 版本 / BUILD | `python tools/set_version.py --set <版本> --build <N>`，再 `--check --version Stable-<版本>` |
| 4. 打渠道 tag | `Stable-*` / `Safe-*` / `Beta-*` / `UnStable-*`（前缀决定 Release 是 latest 还是 prerelease） |
| 5. CI | `.github/workflows/release.yml`：校验版本 → `build-rust-android.sh` → `assembleRelease` → `sha256sum` → `gh release create` |

发布资产固定为 **APK + 同名 `.apk.sha256`**，Release 标题形如
`2-Pyramid for Android Stable 2.1.0-BUILD21000`。日常 PR / main 验证走 `build.yml`（含无 tag 的版本自检）。

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

- **Rust 核心**（`convert-core/`）—— 完全独立于 UI 框架：`grep -ri tauri convert-core/src` 为空；
  路径通过 `runtime::set_runtime(RuntimePaths { log_dir, app_data_dir, uimage_dir, temp_dir })` 注入
  （`temp_dir` 同时设置 `TMPDIR`，因为 Android 没有可写的 `/tmp`）。
- **UniFFI 桥** —— `lib.rs` 用 `#[uniffi::export]` / `#[uniffi::Record]` / callback interface 暴露 facade；
  Kotlin binding 由 `uniffi-bindgen` 生成（gitignore，不手改）；`panic = "unwind"` 让 Rust panic 转成
  Kotlin 异常而不是整 App 闪退。
- **Android UI** —— Compose Material 3 + Precision Geometry 设计令牌（暖纸墨色、8 档字阶、字重 ≤600、
  默认无投影、统一弹窗壳、160/200/240/320ms 动效档）；转换选项 / 进度 / 结果均在 Compose 侧。
- **品牌资产** —— `brand/` 是唯一矢量源，`tools/generate-android-brand.mjs` 幂等生成应用内 drawable、
  adaptive icon 前景 + monochrome、Android 12 启动图标（不手改生成物）。

### 🧰 技术栈

| 层 | 技术 |
|---|---|
| Android UI | Kotlin 2.0.21 + Jetpack Compose（BOM 2024.10）+ Material 3 + Navigation Compose |
| 桥 | UniFFI 0.32 + JNA 5.14 + Kotlin Coroutines 1.9 |
| 后台 | Foreground Service（`dataSync`，Android 14+ 强制） |
| 持久化 | SharedPreferences（设置/历史）+ SAF（`DocumentFile`） |
| 转换核心 | Rust stable + `image` + `zip` + `rayon` + `regex` + `serde` + `sha2` + `flate2` + `uniffi` |
| 编译 | `cargo ndk` + `uniffi-bindgen` + AGP 8.11 + Gradle 8.13 + Android NDK 27.3 |
| API 范围 | `minSdk = 31`（Android 12）/ `targetSdk = 37` / `compileSdk = 37` |

### 📁 项目结构

```
2-Pyramid-For-Android/
├── BUILD                          构建号（版本 21000；versionCode 直接读取）
├── CHANGELOG.md                   变更记录（Keep a Changelog 风格）
├── brand/                         Precision Geometry 矢量源 + logo-256[-white].png（README 用）
├── legal/                         法律文本（中英各 6 份）
│   └── en/
├── docs/compose/releases/         逐版本发版说明 news-<版本>.md（Release 正文）
├── tools/
│   ├── set_version.py             版本号/BUILD/徽章/news/tag 一致性校验与同步
│   ├── generate-android-brand.mjs 品牌素材生成器（纯 Node、幂等）
│   ├── ci/debug.keystore          发行用公开 debug keystore（CI 与本地共用，跨版本签名稳定）
│   └── build-rust-android.{bat,ps1,sh}  编 .so + 生成 Kotlin binding（Gradle 自动调用）
├── 2fa-android/                   Android Studio 项目
│   ├── app/src/main/
│   │   ├── kotlin/com/twopyramid/twofa/
│   │   │   ├── ui/          Compose 屏幕 + 主题令牌 + 组件
│   │   │   ├── service/     Foreground Service 队列
│   │   │   ├── queue/       QueueRepository + ProgressRepository
│   │   │   ├── native/      RustNative（UniFFI 桥）
│   │   │   ├── settings/    SettingsRepository / ConvertOptions
│   │   │   ├── history/     历史记录
│   │   │   ├── util/        SAF / UImage 解压
│   │   │   └── uniffi/      ← uniffi-bindgen 生成（gitignore）
│   │   └── assets/UImage/   80 张参照素材（随 APK 打包）
│   └── gradle/libs.versions.toml  依赖版本唯一来源
├── convert-core/                  Rust 转换内核（M3 / A-ROM）
│   ├── src/{arom,natives,pack,bedrock_convert,color}/ + lib.rs/native_run.rs/…
│   ├── tests/android_smoke.rs     facade 端到端 smoke test
│   ├── examples/convert_cli.rs    host 差分 runner（不参与 Android 构建）
│   └── PORT-NOTES.md              移植/适配/差异记录
├── docs/                          与桌面 2.203.0 的同步报告 / 分层方案 / 同步记录
└── LICENSE（MIT） · README.md
```

### 🤝 贡献

欢迎 PR —— 完整规范见 [`legal/CONTRIBUTING.md`](./legal/CONTRIBUTING.md)
（English: [`legal/en/CONTRIBUTING.md`](./legal/en/CONTRIBUTING.md)）；GitHub 入口见
[`.github/CONTRIBUTING.md`](./.github/CONTRIBUTING.md)。推送前请先跑：

```bash
cd convert-core
cargo test --lib                 # 220 passed / 6 ignored（真实包 E2E 需 AROM_REAL_PACK）
cargo test --test android_smoke  # 6 passed
cargo build --release --examples # 可选：host 差分 runner

cd ../2fa-android
SKIP_RUST_BUILD=true ./gradlew :app:compileDebugKotlin
SKIP_RUST_BUILD=true ./gradlew :app:assembleDebug
```

提交信息用英文祈使句 + conventional commits（如 `feat(ui): adopt Precision Geometry tokens`）。
`BUILD` 与版本号是显式动作，不要为构建号 `+1`。

### ⚖️ 法律

| 文件 | 用途 |
|---|---|
| [LICENSE](./LICENSE) | MIT —— 源代码与官方构建产物 |
| [legal/EULA.md](./legal/EULA.md) | 最终用户须知；**不**限制 MIT 权利 |
| [legal/DISCLAIMER.md](./legal/DISCLAIMER.md) | 免责声明；非 Mojang / Microsoft 官方工具 |
| [legal/PRIVACY.md](./legal/PRIVACY.md) | 本地优先隐私；无网络权限、无遥测 |
| [legal/THIRD-PARTY-NOTICES.md](./legal/THIRD-PARTY-NOTICES.md) | 开源依赖声明（Rust / Kotlin / Compose / JNA …） |
| [legal/SECURITY.md](./legal/SECURITY.md) | 漏洞报告与安全边界 |
| [legal/CONTRIBUTING.md](./legal/CONTRIBUTING.md) | 贡献指南 |

英文版在 [`legal/en/`](./legal/en/)。完整变更记录见 [CHANGELOG.md](./CHANGELOG.md)。

---

## English

**2-Pyramid for Android** is the Android-native port of the desktop
[2-Pyramid](https://github.com/realLivedInCorner/2-Pyramid) resource-pack converter.
Since **v2.1.0** it shares the desktop **2.203.0 M3 / A-ROM conversion core** (~30 kLOC Rust), with the Tauri
shell stripped and the engine bridged into Kotlin / Jetpack Compose via UniFFI. The UI follows the
**Precision Geometry** design language — warm-paper ink tokens, no default shadows (1dp hairlines), a single
dialog shell, and a user-selectable accent color.

> **Version / build number**: `versionName = 2.1.0`; `versionCode` comes from the repository-root
> [`BUILD`](./BUILD) file (currently **21000**). Builds are read-only — a release explicitly advances it with
> `python tools/set_version.py --set X.Y.Z --build N`, and `python tools/set_version.py --check` verifies
> consistency. There is **no** `version.properties` auto-increment.

### ✨ Features

- **27 targets** — 26 Java version ranges (1.6 → latest 26.3 / `pack_format` 97) + Bedrock (experimental).
  `pack.mcmeta` for targets ≥69 writes only `min_format`/`max_format` while preserving `overlays` / `filter`.
- **M3 / A-ROM core** — the same single object model (`Pack` / `Tx` / `Layer`) and 46 native tasks as desktop
  2.203.0; Java-target output is streamed straight to zip, with parallel extraction and image/entry-table
  caching. No algorithm rewrites; future syncs are directory-level diffs.
- **Conversion options** — shader adaptation, delete shaders (destructive, double-confirmed), 1.21.4+ item
  definition upgrade, OptiFine CIT conversion / delete CIT, `2pyr-gen.json` metadata, plus output naming
  template presets (`[Ver][Name]` / `[Ver][Time]` / `[Name]` / custom). Defaults strictly aligned with desktop.
- **1.21.4+ item definitions and OptiFine CIT** — legacy `overrides` + `CustomModelData` migrate to
  `assets/<ns>/items/<name>.json`; CIT rules become item-definition branches (CMD, enchantment name + level,
  hand contexts, bow state textures); conditions that cannot be expressed exactly are skipped and reported.
  **CIT conversion is off by default** (a user measured purple/black models; pending in-game verification).
- **Real progress & results** — engine-level progress callbacks (extract 0–12% / tasks 12–90% / output
  90–100%); the result view shows source→target, output path, elapsed time, `skipped` count and expandable
  `notes` (notes are not the same as "skipped").
- **UImage assets bundled** — the same 80 reference PNGs (0.56 MB) as the desktop project ship inside the APK
  and are extracted to `filesDir/UImage` on first launch; generation tasks that need vanilla reference
  textures (tipped arrow / smithing overlay / snow bucket / overlay icons …) work again.
- **Native Android** — Kotlin 2.0 + Jetpack Compose Material 3; foreground-service queue (`dataSync`, required
  on Android 14+); light / dark / system themes and a custom accent color (dark mode lightens it in OKLab with
  42% white while preserving hue).
- **Fully local** — packs never leave the device; the APK declares **no `INTERNET` permission** — no network,
  no account, no telemetry, **no in-app auto-update**.
- **Tiny APK** — ~**10.2 MB** after R8 + resource shrinking (`arm64-v8a` only; `.so` ≈ 5.5 MB).
- **JNA bridge** — the UniFFI 0.32 generated Kotlin binding calls into the Rust `.so` via JNA.

### ⚠️ Known Limitations (read first)

2-Pyramid is a **structure converter**, not a vanilla asset library. The following are design trade-offs, not
bugs to be fixed:

- **Not validated on a physical device**: builds and differential verification were done on a PC; the
  first-launch UImage extraction, TMPDIR, cross-thread JNA progress callbacks, SAF I/O and large-pack
  memory usage still need device testing.
- **New content in newer target versions is approximated**: blocks/items introduced by the target version but
  absent from your pack are derived from the closest existing texture using hand-calibrated HSV parameters.
  **These are not vanilla assets and will differ visually.**
- **Bedrock conversion is experimental**: j2b / b2j are incomplete and known to be broken — testing only;
  selecting that target shows a warning.
- **OptiFine CIT conversion is off by default**: users reported purple/black bow and diamond-sword models after
  1.20.1 → 1.21.9–1.21.10; the offline codec review fixed a definite format error but in-game rendering is
  unverified and the cause is not reproduced, so the path is not generated by default. Verify in-game before
  enabling; when off, the CIT sources stay as they are and OptiFine clients still read them.
- **Java-target output roots gain a `2pyr-gen.json`**: a conversion-parameter record for traceability/support;
  Minecraft does not read it, it can be disabled in the conversion options, and it contains no personal data
  (Bedrock targets do not get it).
- **Release builds log Warn / Error only**: Info-level logs (including progress details) are not written in
  release builds; enable **Settings → Developer mode** first if you need full logs.
- **Under the hood**: Android's `zip` has no `time` feature, so output container timestamps are 1980 (contents
  are identical; diffs compare per-entry content hashes, not whole-zip bytes). Desktop-only capabilities
  (updater / installer / overlay projects / Foray / in-app file browser / log redaction / CLI tools) are not
  ported.

### 🚀 Quick Start

#### Users

1. Open this repository's [Releases](https://github.com/realLivedInCorner/2-Pyramid-For-Android/releases) and
   download the latest `2-Pyramid-Android-<version>.apk` plus its `.apk.sha256`;
2. Verify integrity (recommended):

   ```bash
   sha256sum -c 2-Pyramid-Android-2.1.0.apk.sha256
   ```

3. Allow "install unknown apps" and sideload it (Android 12+, arm64-v8a). Installing over an older version
   keeps settings and history.
4. On first launch, pick a pack and an output folder with the system file picker, choose a target version and
   options, then hit "Start conversion".

> There is **no in-app auto-update**; upgrading means downloading the new APK manually. Official APKs are
> signed with the repository's public debug keystore (`tools/ci/debug.keystore`, standard Android debug
> credentials; keeps overwrite-installs and self-built packages interoperable);
> that signature **does not prove provenance** — integrity comes from the official Release page plus the
> `.sha256`. Build from source if you need stronger guarantees (see below). Details:
> [`legal/en/SECURITY.md`](./legal/en/SECURITY.md).

#### Developers

```bash
git clone git@github.com:realLivedInCorner/2-Pyramid-For-Android.git
cd 2-Pyramid-For-Android

# 0. Toolchain: Rust stable + cargo-ndk + uniffi-bindgen + JDK 17 + Android SDK (NDK 27.3.13750724)
cargo install cargo-ndk
cargo install uniffi --features=cli --locked

# 1. local.properties (template: 2fa-android/local.properties.example)
cp 2fa-android/local.properties.example 2fa-android/local.properties
# edit sdk.dir

# 2. Build Rust + generate the UniFFI binding (gitignored; only needed after Rust changes)
tools/build-rust-android.bat arm64-v8a      # Windows
bash tools/build-rust-android.sh arm64-v8a  # Linux / macOS / CI

# 3. Build the APK (Gradle's buildRustAndroid re-runs step 2; use SKIP_RUST_BUILD=true for Kotlin-only)
cd 2fa-android
./gradlew :app:assembleDebug     # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease   # signed with the repository debug keystore (tools/ci/debug.keystore); R8 + resource shrinking
```

> Gradle resolves the SDK/NDK from `ANDROID_HOME` / `local.properties` at configuration time and injects them
> into the cargo-ndk child process; the NDK version comes from `gradle.properties`
> (`android.ndkVersion`, **27.3.13750724**). It builds even when `ANDROID_HOME` / `ANDROID_NDK_HOME` are empty.

### 🔄 Updates & Releases

**There is no in-app auto-update and no update check.** Upgrading is an explicit action:

| Step | Command / location |
|---|---|
| 1. Changelog | `CHANGELOG.md`: `[Unreleased]` → `[<version>]` |
| 2. Release notes | `docs/compose/releases/news-<version>.md` (used as the Release body) |
| 3. Version / BUILD | `python tools/set_version.py --set <version> --build <N>`, then `--check --version Stable-<version>` |
| 4. Channel tag | `Stable-*` / `Safe-*` / `Beta-*` / `UnStable-*` (prefix decides latest vs. prerelease) |
| 5. CI | `.github/workflows/release.yml`: verify version → `build-rust-android.sh` → `assembleRelease` → `sha256sum` → `gh release create` |

Release assets are always **APK + same-named `.apk.sha256`**, with titles like
`2-Pyramid for Android Stable 2.1.0-BUILD21000`. PR / main validation runs in `build.yml` (including a
tag-less version self-check).

### 🏗️ Architecture

```
Android (Kotlin / Compose)
  ui/        Home / Convert (queue + options + progress + results) / History / Settings
  service/   ZipQueueService (foreground service, dataSync)
  queue/     QueueRepository + ProgressRepository
  settings/  SettingsRepository (SharedPreferences + StateFlow)
  native/    RustNative (UniFFI bridge + progress callbacks)
        │ UniFFI 0.32 + JNA
        ▼
convert-core/ (Rust crate, same source as desktop 2.203.0)
  lib.rs    UniFFI facade: ConvertOptions / ConvertRequest / ConvertReport / ConvertProgress / convert_zip
  arom/     A-ROM object model + engine (BFS scheduler / version edges)
  natives/  46 native conversion tasks (eraser/architect/surgeon/reverse)
  pack/     production orchestration (version_converter), IO, analysis, diff
  bedrock_convert/  Bedrock ↔ Java structural conversion (j2b/b2j)
  native_run.rs     driver; task_registry.rs (90 metadata entries)
  progress.rs       global progress sink; runtime/logger/image_utils/commands are Android adapters
```

- **Rust core** (`convert-core/`) — UI-framework free (`grep -ri tauri convert-core/src` is empty). Paths are
  injected via `runtime::set_runtime(RuntimePaths { log_dir, app_data_dir, uimage_dir, temp_dir })`;
  `temp_dir` also sets `TMPDIR` because Android has no writable `/tmp`.
- **UniFFI bridge** — `#[uniffi::export]` / `#[uniffi::Record]` / callback interface facade in `lib.rs`; the
  Kotlin binding is generated (gitignored, never hand-edited); `panic = "unwind"` turns Rust panics into Kotlin
  exceptions instead of app crashes.
- **Android UI** — Compose Material 3 with Precision Geometry tokens (warm paper ink colors, 8-step type
  scale, max weight 600, no default shadows, one dialog shell, 160/200/240/320ms motion tokens).
- **Brand assets** — `brand/` is the single vector source; `tools/generate-android-brand.mjs` idempotently
  generates drawables, adaptive-icon foreground + monochrome and the Android 12 splash icon (never hand-edit
  generated files).

### 🧰 Tech Stack

| Layer | Tech |
|---|---|
| Android UI | Kotlin 2.0.21 + Jetpack Compose (BOM 2024.10) + Material 3 + Navigation Compose |
| Bridge | UniFFI 0.32 + JNA 5.14 + Kotlin Coroutines 1.9 |
| Background | Foreground Service (`dataSync`, mandatory on Android 14+) |
| Persistence | SharedPreferences (settings/history) + SAF (`DocumentFile`) |
| Core | Rust stable + `image` + `zip` + `rayon` + `regex` + `serde` + `sha2` + `flate2` + `uniffi` |
| Build | `cargo ndk` + `uniffi-bindgen` + AGP 8.11 + Gradle 8.13 + Android NDK 27.3 |
| API | `minSdk = 31` (Android 12) / `targetSdk = 37` / `compileSdk = 37` |

### 📁 Project Layout

```
2-Pyramid-For-Android/
├── BUILD                          build number (currently 21000; read by versionCode)
├── CHANGELOG.md                   changelog (Keep a Changelog style)
├── brand/                         Precision Geometry vector sources + logo-256[-white].png
├── legal/                         legal documents (6 Chinese + 6 English)
│   └── en/
├── docs/compose/releases/         per-version release notes: news-<version>.md
├── tools/
│   ├── set_version.py             version/BUILD/badge/news/tag consistency check & sync
│   ├── generate-android-brand.mjs brand asset generator (pure Node, idempotent)
│   ├── ci/debug.keystore          public release debug keystore (CI + local; stable signatures)
│   └── build-rust-android.{bat,ps1,sh}  .so + Kotlin binding build
├── 2fa-android/                   Android Studio project
│   ├── app/src/main/
│   │   ├── kotlin/com/twopyramid/twofa/  ui / service / queue / native / settings / history / util
│   │   └── assets/UImage/         80 reference PNGs bundled in the APK
│   └── gradle/libs.versions.toml  single source of dependency versions
├── convert-core/                  Rust conversion core (M3 / A-ROM)
│   ├── src/{arom,natives,pack,bedrock_convert,color}/ + lib.rs/native_run.rs/…
│   ├── tests/android_smoke.rs     end-to-end facade smoke test
│   ├── examples/convert_cli.rs    host-side differential runner (not part of the Android build)
│   └── PORT-NOTES.md              port/adaptation notes
├── docs/                          desktop 2.203.0 sync report / layered plan / sync record
└── LICENSE (MIT) · README.md
```

### 🤝 Contributing

PRs are welcome — see [`legal/en/CONTRIBUTING.md`](./legal/en/CONTRIBUTING.md) (Chinese:
[`legal/CONTRIBUTING.md`](./legal/CONTRIBUTING.md)); the GitHub entry point is
[`.github/CONTRIBUTING.md`](./.github/CONTRIBUTING.md). Run this before pushing:

```bash
cd convert-core
cargo test --lib                 # 220 passed / 6 ignored (real-pack E2E needs AROM_REAL_PACK)
cargo test --test android_smoke  # 6 passed

cd ../2fa-android
SKIP_RUST_BUILD=true ./gradlew :app:compileDebugKotlin
SKIP_RUST_BUILD=true ./gradlew :app:assembleDebug
```

Use English imperative conventional-commit messages (e.g. `feat(ui): adopt Precision Geometry tokens`).
`BUILD` and version bumps are explicit actions — never auto-increment the build number.

### ⚖️ Legal

| File | Purpose |
|---|---|
| [LICENSE](./LICENSE) | MIT — source code and official build artifacts |
| [legal/en/EULA.md](./legal/en/EULA.md) | End-user notice; does **not** restrict MIT rights |
| [legal/en/DISCLAIMER.md](./legal/en/DISCLAIMER.md) | Disclaimer; not an official Mojang/Microsoft tool |
| [legal/en/PRIVACY.md](./legal/en/PRIVACY.md) | Local-first privacy; no network permission, no telemetry |
| [legal/en/THIRD-PARTY-NOTICES.md](./legal/en/THIRD-PARTY-NOTICES.md) | Open-source notices (Rust / Kotlin / Compose / JNA …) |
| [legal/en/SECURITY.md](./legal/en/SECURITY.md) | Vulnerability reporting and security boundaries |
| [legal/en/CONTRIBUTING.md](./legal/en/CONTRIBUTING.md) | Contributing guide |

Chinese originals are in [`legal/`](./legal/). Full change history: [CHANGELOG.md](./CHANGELOG.md).

---

## License / 许可

[MIT](./LICENSE) © 2025–2026 2-Pyramid Studio
