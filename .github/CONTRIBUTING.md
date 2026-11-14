# 贡献指南 / Contributing

> 本文件是 **GitHub 发现入口**。完整正文在 [legal/CONTRIBUTING.md](../legal/CONTRIBUTING.md)
> （English: [legal/en/CONTRIBUTING.md](../legal/en/CONTRIBUTING.md)）—— 请以那里为准，
> 本文件只列出 Android 仓库的快速检查清单与代码落点。

## 提交 PR 前请先跑 / Before opening a PR

```bash
# ── Rust（host 编译，Windows/Linux/macOS 均可）──────────────────────
cd convert-core
cargo test --lib                 # 220 passed / 6 ignored（真实包 E2E 需 AROM_REAL_PACK）
cargo test --test android_smoke  # 6 passed：facade 端到端（15→55 / gen-metadata / delete_shaders / 15→97 / 15→1000 / InputNotFound）
cargo build --release --examples # host 差分 runner（可选）

# ── Kotlin / APK ──────────────────────────────────────────────────
cd ../2fa-android
# 复用已生成的 .so + UniFFI binding，改 Kotlin 不必每次重编 Rust
SKIP_RUST_BUILD=true ./gradlew :app:compileDebugKotlin
SKIP_RUST_BUILD=true ./gradlew :app:assembleDebug
```

改到 **Rust（convert-core）** 时，除上面命令外还要重新生成 `.so` + UniFFI binding
（两者都已 gitignore，不提交）：

```bash
cmd /c tools\build-rust-android.bat arm64-v8a   # Windows
bash tools/build-rust-android.sh arm64-v8a      # Linux / macOS / CI
```

> **注释里禁止出现 `/*` 序列**（Rust doc 与 Kotlin KDoc 都算）：UniFFI 会把 Rust 文档注释原样写进
> Kotlin KDoc，而 Kotlin 支持嵌套块注释，一个 `/*` 会让生成的 `convert_core.kt` 语法崩坏。
> 写目录通配请用 `items/<name>.json` 这种形式。改完 binding 必须跑一次 `:app:compileDebugKotlin`。

### 可选：与桌面 2.203.0 的产物差分

改动转换行为时，用 host 差分 runner 与桌面版 CLI 比对产物（条目集合 + 逐条目 SHA-256 + 压缩方法 +
`pack.mcmeta` 语义；忽略时间戳/顺序）：

```bash
cd convert-core
cargo run --release --example convert_cli -- \
  --convert ../_sync/fixtures/format15.zip --to 55 --out /tmp/out55
# 对照桌面 CLI：2-pyramid.exe --convert <zip> --to 55 --out <dir>
```

`_sync/` 下的 fixtures / reference 是本地验证资产（不入版本控制），生成方式见 `docs/desktop-sync-2.203.md`。

## 版本号 / BUILD / 发版

- 版本号：`2fa-android/app/build.gradle.kts` 的 `versionName`；构建号：仓库根 `BUILD`（**构建只读不写**）。
- 自检：`python tools/set_version.py --check`（本地 Windows 用 `python`，CI 用 `python3`）。
  打 tag 前跑 `python tools/set_version.py --check --version Stable-<ver>`。
- 发布是显式动作：改 `BUILD` / 写 `CHANGELOG.md` 与 `docs/compose/releases/news-<ver>.md` →
  打渠道 tag（`Stable-*` / `Safe-*` / `Beta-*` / `UnStable-*`）→ `.github/workflows/release.yml` 构建并发布 APK + `.sha256`。
- **不要**手动改 `version.properties` 或为构建号 `+1` —— 该机制已删除。

## 代码放哪里

| 内容 | 位置 |
|---|---|
| 转换任务实现（按角色分组） | `convert-core/src/natives/{eraser,architect,surgeon,reverse}/` |
| 任务元数据（名字 / 类型 / 阶段） | `convert-core/src/task_registry.rs` |
| 对象模型与引擎（调度 / 版本边） | `convert-core/src/arom/`（调度在 `arom/engine/`） |
| 包 I/O、生产编排、结构分析、产物差分 | `convert-core/src/pack/` |
| Bedrock ↔ Java 互转 | `convert-core/src/bedrock_convert/` |
| UniFFI facade / 进度 / Android 适配层 | `convert-core/src/{lib.rs,progress.rs,runtime.rs,logger.rs,image_utils.rs,commands/}` |
| 队列 / 前台服务 / UniFFI 桥 | `2fa-android/app/src/main/kotlin/com/twopyramid/twofa/{native,service,queue}/` |
| Compose 页面 / 组件 / 设计令牌 | `.../ui/{screens,components,theme}/` |
| 设置 / 历史持久化 | `.../settings/`、`.../history/` |
| 品牌矢量源 + 生成器 | `brand/` + `tools/generate-android-brand.mjs` |
| Rust → `.so` + Kotlin binding 构建 | `tools/build-rust-android.{bat,ps1,sh}` |
| 版本 / 发布 | 根 `BUILD`、`tools/set_version.py`、`.github/workflows/` |

**不要**提交生成物：`convert-core/target/`、`jniLibs/`、`2fa-android/app/src/main/kotlin/com/twopyramid/twofa/uniffi/uniffi/`、
`2fa-android/app/build/`、`*.apk`。转换器行为以桌面 2.203.0 为准；改引擎时优先对齐桌面同路径文件做目录级 diff，
不要凭感觉重写。

## 提交信息

英文祈使句 + conventional commits，例如：

```
fix(ui): keep dialog scrim warm on dark theme
feat(convert): expose CIT toggle in conversion options
docs: sync legal documents with Android distribution facts
```

一个 PR 聚焦一件事；无关格式化请单独提交。

其余规范（依赖、测试要求、贡献内容的许可）见 [legal/CONTRIBUTING.md](../legal/CONTRIBUTING.md)。
