# 第三方组件声明 / Third-Party Notices

**生效日期：** 2026-11-14

2-Pyramid for Android 包含若干第三方开源组件。这些组件按其**各自许可证**提供；本声明仅作汇总，
完整文本以各组件仓库 / `Cargo.lock` / `gradle/libs.versions.toml` 为准。

主程序许可证见仓库 [`LICENSE`](../LICENSE)（MIT © 2025–2026 2-Pyramid Studio）。
第三方许可证条款与 MIT 冲突时，**就该组件而言**以第三方自身许可证为准；主程序自身仍以 MIT 为准。

---

## 1. Rust 转换内核（`convert-core/`，随 APK 打包为 `libconvert_core.so`）

| 组件 | 版本 | 许可证（常见） | 用途 |
|---|---|---|---|
| [serde](https://serde.rs) / serde_json | 1.0.228 / 1.0.150 | MIT OR Apache-2.0 | 序列化（`pack.mcmeta`、物品定义、`2pyr-gen.json`） |
| [image](https://github.com/image-rs/image) | 0.24.9 | MIT OR Apache-2.0 | 贴图读写与生成 |
| [png](https://github.com/image-rs/image-png) / [jpeg-decoder](https://github.com/image-rs/jpeg-decoder) | 0.17.16 / 0.3.2 | MIT OR Apache-2.0 | PNG / JPEG 编解码（image 的可选后端） |
| [zip](https://github.com/zip-rs/zip) | 0.6.6 | MIT | 资源包解压/打包 |
| [flate2](https://github.com/rust-lang/flate2-rs) | 1.1.9 | MIT OR Apache-2.0 | Deflate 压缩 / CRC32 |
| [miniz_oxide](https://github.com/Frommi/miniz_oxide) | 0.8.9 | MIT OR Zlib OR Apache-2.0 | flate2 的纯 Rust 后端 |
| [walkdir](https://github.com/BurntSushi/walkdir) | 2.5.0 | Unlicense OR MIT | 目录遍历 |
| [rayon](https://github.com/rayon-rs/rayon) | 1.12.0 | MIT OR Apache-2.0 | 并行处理（转换任务 / 解压） |
| [regex](https://github.com/rust-lang/regex) | 1.13.0 | MIT OR Apache-2.0 | 文本与格式解析 |
| [chrono](https://github.com/chronotope/chrono) | 0.4.45 | MIT OR Apache-2.0 | 时间戳（`2pyr-gen.json` 的 UTC 时间） |
| [uuid](https://github.com/uuid-rs/uuid) | 1.23.4 | MIT OR Apache-2.0 | 临时标识 |
| [sha2](https://github.com/RustCrypto/hashes) | 0.10.9 | MIT OR Apache-2.0 | 逐条目 SHA-256 |
| [tempfile](https://github.com/Stebalien/tempfile) | 3.27.0 | MIT OR Apache-2.0 | 临时目录（落在应用 cache） |
| [dirs](https://github.com/dirs-dev/dirs-rs) | 5.0.1 | MIT OR Apache-2.0 | 系统目录兜底（Android 以注入路径为主） |
| [lazy_static](https://github.com/rust-lang-nursery/lazy-static.rs) | 1.5.0 | MIT OR Apache-2.0 | 静态初始化 |
| [once_cell](https://github.com/matklad/once_cell) | 1.21.4 | MIT OR Apache-2.0 | 静态初始化 |
| [UniFFI](https://github.com/mozilla/uniffi-rs)（uniffi / uniffi_core 等） | 0.32.0 | **MPL-2.0** | Rust ↔ Kotlin 桥（facade、scaffolding） |

> 权威清单以仓库内 [`convert-core/Cargo.lock`](../convert-core/Cargo.lock) 为准（含全部传递依赖）。
> UniFFI 采用 MPL-2.0：其源码在 [mozilla/uniffi-rs](https://github.com/mozilla/uniffi-rs) 公开可获取；
> 本项目未修改 UniFFI 源码。

## 2. Android / Kotlin 侧（随 APK 打包）

| 组件 | 版本 | 许可证（常见） | 用途 |
|---|---|---|---|
| [Kotlin](https://kotlinlang.org)（stdlib） | 2.0.21 | Apache-2.0 | 语言运行时 |
| [AndroidX core-ktx](https://developer.android.com/jetpack/androidx) | 1.15.0 | Apache-2.0 | Android 基础 API |
| [AndroidX Lifecycle](https://developer.android.com/jetpack/androidx/releases/lifecycle)（runtime / service / viewmodel-compose） | 2.8.7 | Apache-2.0 | 生命周期、前台服务、ViewModel |
| [AndroidX Activity Compose](https://developer.android.com/jetpack/androidx/releases/activity) | 1.9.3 | Apache-2.0 | Compose 宿主 Activity |
| [Jetpack Compose](https://developer.android.com/jetpack/compose)（BOM 2024.10.01 / ui 1.7.5 / material3 1.3.1 / material-icons-extended） | — | Apache-2.0 | UI 框架与组件 |
| [Navigation Compose](https://developer.android.com/jetpack/androidx/releases/navigation) | 2.8.3 | Apache-2.0 | 页面导航 |
| [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) | 1.9.0 | Apache-2.0 | 协程 |
| [AndroidX DocumentFile](https://developer.android.com/jetpack/androidx/releases/documentfile) | 1.0.1 | Apache-2.0 | SAF 文件访问 |
| [JNA](https://github.com/java-native-access/jna) | 5.14.0 | **LGPL-2.1-or-later OR Apache-2.0** | UniFFI binding 调 `.so` 的原生桥 |

> 权威清单以 [`2fa-android/gradle/libs.versions.toml`](../2fa-android/gradle/libs.versions.toml) 为准。
> JNA 采用双许可；本项目按其 **Apache-2.0** 选项使用，并随 APK 分发从 `jna-platform` AAR 抽取的
> Android 原生库 `libjnidispatch.so`。若需按 LGPL 条款获取/重链接，见 JNA 上游仓库说明。

## 3. 构建期工具（不随 APK 分发）

- **Android Gradle Plugin 8.11.1 / Gradle 8.13**（Apache-2.0）—— 构建；
- **Android SDK / NDK 27.3**、**Rust stable 工具链**、**cargo-ndk**、**uniffi-bindgen**
  —— 编译与绑定生成，不随 APK 分发（Rust std 采用 MIT OR Apache-2.0）；
- **libiconv / 系统 NDK 库** 等由工具链静态链接进 `.so`，属构建工具链范畴。

## 4. 平台与游戏资产

- **Minecraft** 的名称、商标与游戏内行为归 Mojang Studios / Microsoft 所有。本软件**不包含**
  Minecraft 客户端或官方资源包；转换对象为你自己合法持有的资源包。
- 转换过程中生成的贴图衍生自**你导入的**资源包，著作权仍归原权利人。
- **`UImage` 参照素材**：APK 内置 80 张 PNG（约 0.56 MB，`2fa-android/app/src/main/assets/UImage/`），
  来自维护者桌面项目 [2-Pyramid](https://github.com/realLivedInCorner/2-Pyramid) 的对应目录，
  用于「用原版风格素材生成/叠加贴图」类任务。其中涉及 Minecraft 材质的内容，权利归其权利人所有；
  本项目**不主张**著作权，仅作转换参照。若权利人认为分发方式不妥，请通过 Issues 联系维护者，我们会
  评估移除。
- `brand/` 目录中的 Precision Geometry 标识（SVG / PNG）为 2-Pyramid Studio 的品牌资产，
  随仓库提供以便构建与展示；商标使用规则见 [`EULA.md`](./EULA.md) 第 4 节。

## 5. 如何获取完整许可证文本

- **Rust**：`convert-core/Cargo.lock` 精确记录 `(name, version)`；各 crate 的 LICENSE 文件见
  [crates.io](https://crates.io) 对应页面或上游仓库；
- **Android / Kotlin**：`gradle/libs.versions.toml` 记录版本；各 AAR 在其 POM / 仓库页面附带许可证；
- 本仓库根目录 `LICENSE`（MIT，主程序许可证）。

APK **未内置**逐组件许可证正文文件。如果你是再分发者，请自行满足各组件许可证的署名与副本要求
（例如 Apache-2.0 的 NOTICE 保留、MPL-2.0 的源码可获取说明）。

若你认为本声明遗漏了应列组件，请开 Issue，我们会尽快补全。

---

**English summary:** 2-Pyramid for Android ships open-source libraries under MIT OR Apache-2.0 and similar
licenses: a Rust conversion core (serde, image, zip, flate2, rayon, regex, sha2, uniffi under MPL-2.0, …)
and an Android/Kotlin UI stack (Kotlin, AndroidX, Jetpack Compose under Apache-2.0; JNA under
LGPL-2.1-or-later OR Apache-2.0, used here under Apache-2.0). See `convert-core/Cargo.lock` and
`2fa-android/gradle/libs.versions.toml` for the authoritative dependency lists. Minecraft branding belongs to
Mojang/Microsoft; we ship no official game assets. The bundled `UImage` reference assets come from the
maintainer's desktop project, are used only as conversion reference material, and no ownership is claimed
over the underlying Minecraft-derived content.
