# 贡献指南 / Contributing

感谢你对 **2-Pyramid for Android** 的兴趣！欢迎 Issue 与 Pull Request。

本仓库是桌面版 [2-Pyramid](https://github.com/realLivedInCorner/2-Pyramid) 的 Android 原生移植：
转换内核（`convert-core/`，Rust）与桌面 2.203.0 同源，Android 侧是 Kotlin + Jetpack Compose。

---

## 1. 开始之前

- 先搜 [Issues](https://github.com/realLivedInCorner/2-Pyramid-For-Android/issues)，避免重复；
- 较大的功能或界面改动请先开 Issue 对齐方案，再动手；
- 行为准则：保持尊重、聚焦技术；不接受人身攻击或无关政治争论。

## 2. 开发环境

| 依赖 | 版本 / 说明 |
|---|---|
| Rust | stable（`rustup`；Android target 为 `aarch64-linux-android`） |
| Android NDK | **27.3.13750724**（与 `2fa-android/gradle.properties` 的 `android.ndkVersion` 一致） |
| JDK | 17 |
| Android SDK | `compileSdk = 37` / `targetSdk = 37` / `minSdk = 31`（Android 12+） |
| Python | 3.8+（仅发布/版本脚本 `tools/set_version.py` 使用，无第三方依赖） |
| 辅助工具 | `cargo-ndk`、`uniffi-bindgen`（`cargo install uniffi --features=cli --locked`） |

首次构建：

```bash
# 1) SDK 路径：2fa-android/local.properties（模板见 local.properties.example）
cp 2fa-android/local.properties.example 2fa-android/local.properties
# 编辑 sdk.dir

# 2) 编 Rust → .so + 生成 UniFFI Kotlin binding（改 Rust 才需要重跑）
tools/build-rust-android.bat arm64-v8a      # Windows
bash tools/build-rust-android.sh arm64-v8a  # Linux / macOS / CI

# 3) 打 APK
cd 2fa-android
./gradlew :app:assembleDebug
```

> `.so`、UniFFI binding、`target/`、`app/build/`、`jniLibs/` 都已 gitignore，**不要提交**。
> 本地 Windows 若没有 `ANDROID_HOME` / `ANDROID_NDK_HOME`，Gradle 会从 `local.properties`
> 解析并注入给 cargo-ndk（见 `app/build.gradle.kts`），不必改机器级环境变量。

## 3. 提交 PR 前请先跑

```bash
# Rust（host，Windows / Linux / macOS 均可）
cd convert-core
cargo test --lib                  # 220 passed / 6 ignored（真实包 E2E 需 AROM_REAL_PACK）
cargo test --test android_smoke   # 6 passed

# Kotlin / APK（复用现有 .so 与 binding，改 Kotlin 不必重编 Rust）
cd ../2fa-android
SKIP_RUST_BUILD=true ./gradlew :app:compileDebugKotlin
SKIP_RUST_BUILD=true ./gradlew :app:assembleDebug
```

改到 **Rust（convert-core）** 时，必须重新生成 `.so` + binding，并再跑一次
`:app:compileDebugKotlin` —— 生成物与 Rust 源码不一致时，问题会以很难排查的 JNA 符号错误出现。

**可选：与桌面 2.203.0 的产物差分**（改动转换行为时强烈建议）：

```bash
cd convert-core
cargo run --release --example convert_cli -- \
  --convert <你的包.zip> --to 55 --out /tmp/out55
# 对照桌面版：2-pyramid.exe --convert <同一包> --to 55 --out <另一目录>
# 比较口径：条目集合 + 逐条目解压后 SHA-256 + 压缩方法 + pack.mcmeta 语义；忽略时间戳/顺序。
```

## 4. 代码放哪里

| 内容 | 位置 |
|---|---|
| 转换任务实现（按角色分组） | `convert-core/src/natives/{eraser,architect,surgeon,reverse}/` |
| 任务元数据（名字 / 类型 / 阶段） | `convert-core/src/task_registry.rs` |
| 对象模型与引擎（BFS 调度 / 版本边） | `convert-core/src/arom/`（调度在 `arom/engine/`） |
| 包 I/O、生产编排、结构分析、产物差分 | `convert-core/src/pack/` |
| Bedrock ↔ Java 互转 | `convert-core/src/bedrock_convert/` |
| UniFFI facade / 进度 / Android 适配层 | `convert-core/src/{lib.rs,progress.rs,runtime.rs,logger.rs,image_utils.rs,commands/}` |
| 队列 / 前台服务 / UniFFI 桥 | `2fa-android/app/src/main/kotlin/com/twopyramid/twofa/{native,service,queue}/` |
| Compose 页面 / 组件 / 设计令牌 | `.../ui/{screens,components,theme}/` |
| 设置 / 历史持久化 | `.../settings/`、`.../history/` |
| 品牌矢量源 + 生成器 | `brand/` + `tools/generate-android-brand.mjs` |
| Rust → `.so` + binding 构建 | `tools/build-rust-android.{bat,ps1,sh}` |
| 版本 / 发布 | 根 `BUILD`、`tools/set_version.py`、`.github/workflows/` |

## 5. 代码规范

### Rust（convert-core）

- 与桌面 2.203.0 **同源**：改引擎时优先对齐桌面同路径文件做目录级 diff，不要凭感觉重写；
- 完全独立于 UI 框架：**不允许**引入 Tauri / Windows 依赖（`grep -ri tauri convert-core/src` 必须为空）；
- 路径一律走 `runtime::*_dir()`（Android 没有标准 HOME），不要硬编码 `dirs` 系统目录；
- 新代码避免在非测试路径新增 `unwrap()` / `expect()`；
- 改图像/路径/结构逻辑时尽量补 `#[cfg(test)]` 单测；新增 `#[ignore]` 用例必须写明理由；
- **注释与文档注释里禁止出现 `/*` 序列**：UniFFI 会把 Rust 文档注释原样写进 Kotlin KDoc，
  而 Kotlin 支持嵌套块注释，一个 `/*` 会让生成的 `convert_core.kt` 语法崩坏。
  写目录通配用 `items/<name>.json` 这种形式。

### Kotlin / Jetpack Compose

- Material 3 + Compose，不引入 XML View 依赖；设计令牌集中在 `ui/theme/`（颜色/字阶/圆角/动效），
  **不要在 Screen 里写死颜色或时长**；
- 字重上限 600（品牌名例外）；中文标题字距 0；默认无投影，层级靠字号与留白；
- 弹窗/底部面板统一走同一套壳组件，不要各页面自写；
- 后台转换在 `service/ZipQueueService`（前台服务类型固定 `dataSync`）；进度只经
  `ProgressRepository` 流向 UI，**回调线程不是 UI 线程，禁止直接操作 Compose 状态**；
- 转换选项在入队时快照进 `QueueItem.options`，之后改设置不影响已排队任务；
- 用户文件一律经 SAF（`DocumentFile`）访问，不申请宽泛存储权限；
- **Kotlin 源码/注释同样禁止 `/*` 序列**（KDoc 嵌套注释会报 `Unclosed comment`）。

### 资源与品牌

- `brand/` 是**唯一矢量源**：`res/drawable/brand_*.xml`、启动图标、splash 图标都由
  `node tools/generate-android-brand.mjs` 生成（幂等、带自检），**不要手改生成物**；
- 更新品牌素材时连 `brand/*.svg` 一起改，并重跑生成器确认输出无 diff。

### 版本号与构建号

- 版本号在 `2fa-android/app/build.gradle.kts` 的 `versionName`；构建号在仓库根 `BUILD`；
- **构建只读不写**：不要为构建号 `+1`，也没有 `version.properties`（该机制已删除）；
- 发布前：`python tools/set_version.py --set <ver> [--build <n>]`，
  再 `python tools/set_version.py --check --version Stable-<ver>`；
- 发版说明放 `docs/compose/releases/news-<版本>.md`，Release 正文直接用它。

## 6. Pull Request

1. Fork 后从 `main` 拉分支（如 `feat/xxx`）；
2. 确保第 3 节的命令通过；涉及 UI 时说明如何验证（截图/录屏可附，但不要提交大二进制）；
3. PR 描述写清：动机、改动点、测试情况、相关 Issue；
4. 保持 diff 可读；生成物与大文件说明来源，能不入库就不入库。

## 7. 提交信息

英文祈使句 + conventional commits，例如：

```
fix(ui): keep dialog scrim warm on dark theme
feat(convert): expose CIT toggle in conversion options
docs: sync legal documents with Android distribution facts
```

一个 PR 聚焦一件事；无关格式化请单独提交。

## 8. 报告 Bug

请尽量附上：

- 应用版本与 BUILD（设置 → 关于）；
- Android 版本与设备型号；
- 源包版本 / 目标版本（Java `pack_format` 或 Bedrock）；
- 转换选项（六个开关与命名模板）的勾选情况；
- 复现步骤与期望结果；设置 → 开发者模式里可查看最近日志（可复制）。

转换类问题若能提供**可公开的小样例包**，修复会快很多。

## 9. 功能建议

请说明：使用场景、现有流程为何不够、你期望的交互。带截图或草图更佳。

## 10. 贡献内容的许可

本项目管理方式等效于 Developer Certificate of Origin (DCO)。你无需签署额外 CLA，但需确保有权按 MIT
授权提交内容。除非另有书面约定，你提交的代码默认按 **MIT 许可证**向公众授权，允许 2-Pyramid Studio
**及所有后续接收者**使用、修改与再分发。提交即表示你有权做此授权（例如不违反雇佣协议或第三方版权）。

**请勿提交：**

- 未授权的 Minecraft 官方素材、他人资源包或破解内容（例外：维护者维护的 `UImage/` 参照素材，
  变更前请先开 Issue 讨论）;
- 体积过大的二进制（截图请压缩；测试贴图尽量自造，或放进 `_sync/` 本地目录不入库）；
- 含个人信息的路径、日志或账号数据。

---

**English summary:** Discuss large changes in an issue first. The Rust core must stay UI-framework-free
(no Tauri/Windows deps) and path access goes through `runtime::*_dir()`. Keep generated artifacts
(`.so`, UniFFI bindings, `target/`, `app/build/`) out of git. Never write `/*` inside Rust doc comments or
Kotlin comments — it breaks the generated UniFFI binding. Run `cargo test --lib`, `cargo test --test
android_smoke`, and `SKIP_RUST_BUILD=true ./gradlew :app:compileDebugKotlin` before opening a PR; use clear
English imperative conventional-commit messages. Contributions are MIT-licensed to the public; do not commit
unauthorized Minecraft assets or personal data. Version bumps are explicit: `python tools/set_version.py`.
