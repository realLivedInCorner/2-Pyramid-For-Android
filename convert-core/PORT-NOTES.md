# convert-core M3 引擎移植报告（T1 / L2+L3）

| 项 | 值 |
|---|---|
| 日期 | 2026-10-31 |
| 桌面基准 | `2pyr-desktop/code` HEAD `3dd0e45`（2.203.0 + Unreleased），全程只读 |
| 目标 | `2pyr-For-Android/convert-core`（分支 `sync/desktop-2.203`，未提交） |
| 状态 | ✅ host `cargo check` / `cargo test --lib` / `cargo build --release` / facade smoke 全绿；`grep -ri tauri src` 为空 |
| 说明 | 本文是引擎移植的**事实记录**，供 L7 文档与后续与桌面 diff 使用；`_sync/reports/` 的 L3 报告由 Lead/verify-build 汇总 |

---

## 1. 移植文件清单

### 1.1 整体搬运（桌面 `src-tauri/src` → `convert-core/src`，算法零重写）

| 来源 | 文件数 | 行数 | 说明 |
|---|---:|---:|---|
| `arom/` | 15 | 6,535 | A-ROM 对象模型 / BFS 调度 / conversion_maps / 序列化 / pathview / limits |
| `natives/` | 53 | 11,140 | 46 个原生转换任务 + `architect/item-definitions.json`（488,306 B，`include_str!` 必须同目录） |
| `pack/` | 5 | 4,067 | `io` / `analysis`（生产调用）/ `diff`（测试 20+ 处）/ `version_converter`（生产入口） |
| `bedrock_convert/` | 11 | 3,800 | j2b / b2j / metadata / shaders / textures / skybox / ui / potions |
| `color/` | 3 | 180 | HSV 工具（原 `color_utils` 的替代） |
| 根：`chest_region.rs` | 1 | 107 | 箱子贴图区域变换 |
| 根：`gen_metadata.rs` | 1 | 411 | `2pyr-gen.json` |
| 根：`scale_factor.rs` | 1 | 54 | 分辨率倍率采样 |
| 根：`native_run.rs` | 1 | 3,368 | 驱动（`run_native` / `MixedRunOptions` / `MixedRunReport` / `native_for`） |
| 根：`task_registry.rs` | 1 | 231 | 90 项任务元数据 |
| 根：`invoke_conversion.rs` | 1 | 55 | 桌面空壳（保留 `register_tasks` 以兼容测试调用点） |

### 1.2 Android 适配层（新写 / 改写，非桌面拷贝）

| 文件 | 行数 | 内容 |
|---|---:|---|
| `lib.rs` | 281 | UniFFI facade 重写：`ConvertOptions` / `ConvertRequest` / `ConvertReport` / `ConvertProgress` / `convert_zip` + 保留 `init_runtime` / `get_recent_logs` / `set_dev_mode` / `is_dev_mode` / `convert_core_version` / `ConvertError` |
| `progress.rs` | 242 | **新写**：全局 `ProgressSink`、三段权重、单调钳制、节流、RAII 清理 |
| `commands/mod.rs` + `commands/config.rs` | 72 | 内存态 `AppConfig{output_naming}` + `read_config_file()`（桌面唯一引擎读取点 `version_converter.rs:524`） |
| `image_utils.rs` | 113 | 保留 `paste_region` / `swap_and_mirror` + 新写 `get_uimage_path()`（Android 语义） |
| `runtime.rs` | 87 | `RuntimePaths` 增加 `temp_dir`（app cache）+ `temp_dir()` 访问器 |
| `build.rs` | 28 | 注入 `cargo:rustc-env=BUILD_NUMBER`（默认 `21000`，`BUILD_NUMBER` 环境变量可覆盖） |
| `logger.rs` | 169 | **未改**（4 个日志宏早已存在，引擎 150 处调用零改动） |

### 1.3 删除的旧结构（可从 git HEAD `519f904` 恢复）

`src/converters/`（98 文件）、`src/hurray/`（8）、`src/overlay/`（1）、旧 `src/color_utils.rs`、旧 `src/invoke_conversion.rs` / 旧 `src/lib.rs`（覆盖）。

### 1.4 依赖改动（`Cargo.toml`）

| 变更 | 值 | 理由 |
|---|---|---|
| 新增 | `sha2 = "0.10"` | `arom/pathview.rs` / `native_run.rs` 逐条目 SHA-256 |
| 新增 | `flate2 = "1.0"` | `arom/source.rs` 的 `DirSource::crc32`（`pack::diff` 用） |
| 修改 | `panic = "abort"` → `"unwind"` | UniFFI 0.32 `catch_unwind` 的异常边界；引擎有 14 处 `unwrap/expect`（CONTRACT §9-6） |
| 保持 | `image`(png,jpeg) / `zip`(deflate) / uniffi 0.32 / crate-type 三件套 | 与桌面差异只影响 zip 时间戳（差分按内容口径，见报告 S16/S7） |
| 保持 | version `2.0.0` | **L7（task-8）统一升到 2.1.0**；届时 `gen_metadata.generator_version` / Bedrock manifest 自动跟随 |

---

## 2. 适配点（与桌面的逐项差异）

1. **日志宏（零改动）**：`logger.rs:177-195` 已有与桌面同名同签名的 `log_debug/info/warn/error`（CONTRACT §9-1）。
2. **`commands::read_config_file()`**：桌面读 JSON 配置文件的 20+ 字段；Android 只保留 `output_naming` 的内存态。facade 每次 `convert_zip` 前 `set_output_naming(options.output_naming)`。
3. **`image_utils::get_uimage_path()`**：桌面走资源解析 + 用户目录 + **找不到时创建默认目录/README**；Android 只认 `runtime::uimage_dir()`：
   - 未注入 / 目录不存在 / 目录为空 → `Err`（引擎 7 个调用点已有 "UImage not available, skip" 分支）；
   - **不创建目录、不写文件**（避免污染 Android filesDir）。
4. **进度（新写，桌面没有）**：`crate::progress` 全局 sink；
   - 权重：`extract 0.00–0.12` / `pipeline 0.12–0.90`（phase = 任务名；并行批用阶段名）/ `output 0.90–1.00`；
   - 埋点：`pack/io.rs`（解压每 50 条、重打包每 10 条）、`native_run.rs`（早/并行/串行段 + 序列化前 `output_started`）、`arom/serialize.rs::write_zip`（每 64 条）、`version_converter.rs`（相位边界兜底）；
   - 线程安全（Mutex，回调在锁内执行保证顺序）、**单调不回退**（并行/串行交替被钳制）、同 phase 最小步长 0.002 节流；未注册 sink 时零成本（静态分支）。
   - RAII `SinkGuard`：Ok / Err / panic unwind 三条路径都清理。
   - `io::repack_resource_pack` 也用于 Bedrock 源预检（b2j 之前），用 `output_started` 门控避免把 0.12→0.90 提前跳死。
5. **`runtime::temp_dir` + `TMPDIR`**：`init_runtime` 增加第 4 参数 `temp_dir`（Kotlin 传 `cacheDir`），并在注入时 `std::env::set_var("TMPDIR", …)` 兜住引擎内 4 处直接 `std::env::temp_dir()`（CONTRACT §9-7）。
6. **rayon 线程预算（Android 增强）**：`init_runtime` 建全局池 `min(4, available_parallelism)`；桌面由命令层建池，Android 没有该层（报告 S21）。注意：`build_global` 只能成功一次，已建池则忽略。
7. **`BUILD_NUMBER`**：`build.rs` 注入 `21000`（`BUILD_NUMBER` 环境变量可覆盖）；桌面由构建脚本写入。
8. **`process_zip_timed` 返回值扩展（Android 适配点，与桌面差异）**：
   - 桌面：`Result<(String, ConversionTiming), String>`，`MixedRunReport.skipped/notes` 被丢弃（只写日志）；
   - Android：`Result<ConversionOutcome, String>`，`ConversionOutcome{output_path, timing, source_version, skipped, notes}`；
   - `source_version`：Bedrock 源报告 **1000**（管线内部 b2j 后是中间态 97）；
   - 文件头已标注「Android 适配点，与桌面差异」，未来 diff 只需看该段。
9. **UI 框架名清理**：`arom/error.rs`、`pack/mod.rs`、`pack/version_converter.rs` 的 3 处注释已中性化；`grep -ri tauri src` 为空（验收项）。
10. **`fix_alpha_layers`**：保留字段并透传，桌面已是死开关（任务在 `AUXILIARY`、不在任何计划边）→ 行为 no-op（CONTRACT §9-4）。
11. **`adapt_shaders`**：计划级过滤（`native_run::plan_task_enabled` 过滤 `adapt_java_shaders`），非任务内早退（CONTRACT §9-5）。
12. **`NativeSwitches::all()`**：`process_zip_timed` 内显式设置（`mopts.native = NativeSwitches::all()`），不依赖 `MixedRunOptions::default()`（后者是 `none()`，会报 "no native implementation registered"，§9-5）。
13. **清理策略**：不设置 `CleanupMode::Detached`（默认 `Background`）；`Detached` 分支的 `rm -rf` / `cmd rmdir` 代码保留但 Android 永不进入（报告 S11）。

---

## 3. facade 契约（已由真实 binding 生成验证）

Rust 端（`lib.rs`）→ 用本地 `uniffi-bindgen generate --library target/release/convert_core.dll --language kotlin` 实测生成成功（62,795 B `convert_core.kt`，package `uniffi.convert_core`），关键签名：

```kotlin
data class ConvertOptions(
    var targetVersion: UInt,          // u32 → UInt
    var fixAlphaLayers: Boolean,
    var adaptShaders: Boolean,
    var deleteShaders: Boolean,
    var convertItemDefinitions: Boolean,
    var convertCit: Boolean,
    var deleteCit: Boolean,
    var writeGenMetadata: Boolean,
    var outputNaming: String?,        // Option<String>
)
data class ConvertRequest(var inputZip: String, var outputDir: String, var options: ConvertOptions)
data class ConvertReport(
    var outputPath: String, var sourceVersion: UInt, var targetVersion: UInt, var logPath: String,
    var skipped: UInt,                // u32 → UInt
    var notes: List<String>,
    var elapsedMs: ULong,             // u64 → ULong（!!）
)
interface ConvertProgress { fun onProgress(progress: Double, phase: String) }   // callback interface
fun convertZip(req: ConvertRequest, progress: ConvertProgress): ConvertReport
fun initRuntime(logDir: String, appDataDir: String, uimageDir: String, tempDir: String)  // 4 参
fun getRecentLogs(limit: UInt): List<String>
fun setDevMode(enabled: Boolean) / isDevMode(): Boolean / convertCoreVersion(): String
```

`ConvertError`：`InputNotFound{path}` / `BedrockNotSupported`（保留兼容，不主动抛）/ `Conversion{msg}` → Kotlin `ConvertException` 子类。

**给 android-ui 的注意点**：
- `targetVersion` 是 `UInt`（旧契约也是），`skipped` 是 `UInt`，**`elapsedMs` 是 `ULong`**（`report.elapsedMs.toLong()`）；
- `initRuntime` 现在是 **4 参数**（第 4 个传 `cacheDir.absolutePath`），`TwoFAApp.kt` 需同步改；
- 回调从 **Rust worker 线程**（rayon / 主调线程）触发，Kotlin 实现需线程安全；只做 UI 状态更新、不要回调里再调 Rust；
- progress 的 `phase`：`extract` / 任务名（如 `delete_font_folder`；并行批时为阶段名如 `architect`）/ `output`；fraction 已单调，UI 可直接用；
- `notes` 是「跳过原因 + 普通进展」混合，**不要统一标注为“跳过”**（报告 S17）。

---

## 4. 测试与忽略清单

- `cargo test --lib`：**220 passed / 0 failed / 6 ignored**（桌面 227 个 `#[test]` 中与本 crate 有关的部分全量可跑；另有新增适配层单测 4 个）。
- **6 个 ignore 全部来自桌面原样保留**（CONTEXT §9-2：5 个真实包 + 1 个计时），无新增 ignore：

| 用例 | 忽略原因 |
|---|---|
| `arom::serialize::tests::real_pack_roundtrip_matches_old_pipeline` | 需要 `AROM_REAL_PACK` 真实包（桌面开发机路径），Android 仓库无该 fixture |
| `arom::source::tests::handle_pool_vs_reopen_timing` | IO 计时稳定性用例（桌面机器相关，CI 上不稳定） |
| `native_run::tests::native_output_dir_matches_zip_on_a_real_pack` | 同上，需要真实包 |
| `native_run::tests::real_pack_absolute_output_is_pinned` | 同上 |
| `native_run::tests::real_pack_content_baseline_is_frozen` | 同上（冻结内容基线只能报告不能重生成） |
| `native_run::tests::trace_stepwise_on_a_real_pack` | 同上 |

- **facade smoke test**（`tests/android_smoke.rs`，6 个用例全绿）：程序化合成 format 15 小包（含 60 个带 `custom_model_data` 的 `models/item/*.json`、两棵 shaders 树、两棵 CIT 源目录）：
  1. 15→55：`pack.mcmeta.pack_format==55`、`items/*.json` ≥46（实生成 60 个中参考表命中的部分）、`2pyr-gen.json` 字段正确（source 15 / target 55 / options 六个开关 / `generator_build==env!("BUILD_NUMBER")`）、shaders 默认保留、进度回调 extract+任务名+output 且单调到 1.0；
  2. `write_gen_metadata=false` → 无 `2pyr-gen.json`，物品定义仍生成；
  3. `delete_shaders=true` → `shaders/` 与 `assets/minecraft/shaders/` 整棵消失；
  4. 15→97：`min_format [97,0]` / `max_format [97,1]`，无 `pack_format`/`supported_formats`；
  5. 15→1000：产物 `.mcpack` + 根 `manifest.json`；
  6. 输入不存在 → `ConvertError::InputNotFound`。

---

## 5. 验证证据（host Windows，Rust 1.92.0）

```
cd 2pyr-For-Android/convert-core
cargo check                     # exit 0, Finished dev profile   (58 warnings, 0 errors)
cargo test --lib                # exit 0  test result: ok. 220 passed; 0 failed; 6 ignored
cargo test --test android_smoke # exit 0  test result: ok. 6 passed; 0 failed
cargo build --release           # exit 0  Finished release profile；convert_core.dll 5,500,928 B
grep -ri tauri src              # 无输出（PASS）
```

- 58 个 warning：全部是 `dead_code` / `unused_import` 类（桌面命令层/CLI 才用到的 API 在 Android 没有被调用）——**有意不修**，保持与桌面源码最小 diff，便于未来按结构同步。
- 本机 `uniffi-bindgen` 对 host dll 的生成验证通过（见 §3），bindings 的正式生成仍由 verify-build 在 aarch64 `.so` 上执行（task-5）。

---

## 6. 已知差异 / 缺口（写进 L7 文档）

| # | 项 | 状态/影响 |
|---|---|---|
| 1 | **UImage 资源不打包**（Android `assets/UImage` 为空） | 7 组依赖 UImage 的生成任务走既有 skip 分支 → 产物与桌面参考有预期差异（报告 D6/S25）；`get_uimage_path` 语义 = 非空目录才 Ok |
| 2 | `process_zip_timed` 返回结构扩展（`ConversionOutcome`） | 与桌面源码的**有意差异**；文件头已标注，未来 diff 时跳过该函数头部即可 |
| 3 | `fix_alpha_layers` no-op | 桌面同样（字段保留、2pyr-gen 开关字段仍在） |
| 4 | `perf.rs` 未移植 | 引擎 0 引用；rayon 线程预算改在 `init_runtime` 固定 4 线程（与桌面"性能档位"不同） |
| 5 | `TempDir::into_path` deprecated 警告等桌面遗留 warning | 保持原样不修 |
| 6 | `convert-core` 版本仍 2.0.0 | L7（task-8）升 2.1.0；届时 `2pyr-gen.json.generator_version` 才变 2.1.0 |
| 7 | release profile 由 `abort` 改 `unwind` | `.so` 体积略增（预计 <2%），换取 UniFFI 异常边界 |
| 8 | `image` features 仍 png+jpeg（桌面默认全格式） | 引擎只用 PNG/JPEG 编解码；其它格式贴图按既有 `let Ok(..) else` 跳过（报告 S16） |
| 9 | zip 时间戳 | Android `zip` 无 `time` feature → 产物容器时间戳与桌面不同，内容口径一致（差分按 S7） |
| 10 | 桌面 `process_extracted_dir_only` / `convert_resource_pack` / `process_zip` | 保留（编译 + 单测覆盖），Android facade 不走它们 |

---

## 7. host 差分 runner（task-9，`examples/convert_cli.rs`）

用途：在**本机（host）**跑「Android 这份引擎」的转换产物，与桌面 2.203.0 CLI 的参考产物做
逐条目差分（L6 核心证据）。它不是另一个实现——内部完全走与 Android 相同的
`convert_zip` → `process_zip_timed` → `run_native` 路径；输出命名/冲突策略由引擎内
`build_output_path` 决定，与桌面一致。

```powershell
cd D:\develop\2pyr\2pyr-For-Android\convert-core
cargo build --release --examples        # 产出 target\release\examples\convert_cli.exe

# 例：fixture 15 → 55，写机器可读报告
cargo run --release --example convert_cli -- `
  --convert D:\develop\2pyr\_sync\fixtures\fixture-f15.zip --to 55 `
  --out $env:TEMP\2pyr-diff\f15-55 --report $env:TEMP\2pyr-diff\f15-55.json

# 差分（桌面参考产物由 verify-build 的 run_reference.ps1 生成在 _sync\reference\no-uimage\）
python D:\develop\2pyr\_sync\compare.py `
  D:\develop\2pyr\_sync\reference\no-uimage\f15-to-55-default\artifact.zip `
  "$env:TEMP\2pyr-diff\f15-55\[Java 1.21.5]fixture-f15.zip"
```

参数（与桌面 CLI 对齐）：`--convert <zip>`（唯一入口）、`--to <55|97|1000|标签>`（缺省 97）、
`--out <dir>`（缺省=输入包目录）、`--cit`、`--delete-shaders`、`--no-item-definitions`、
`--delete-cit`、`--no-gen-metadata`、`--naming <模板>`（默认 `[Ver][Name]`，与桌面
`build_output_path` 一致）、`--report <json>`、`--uimage <dir>`、`--progress`。
`--fast` 仅为桌面脚本兼容而接受，**只告警不生效**（Android 生产口径固定 `run_gui_surgeon=true`）；
与桌面 CLI 差分时两边都不要传 `--fast`。

**默认 UImage 为空目录**（模拟 Android 无 UImage assets），正好对齐 verify-build 的
`_sync/reference/no-uimage` 参考集；需要排除 UImage 差异时显式传 `--uimage <桌面 UImage 目录>`。

### 7.1 本机实测（2026-10-31）：15/15 差分 PASS

对 `_sync/reference/no-uimage/` 全部 15 个参考 case 用本 runner 重跑 + `compare.py` 比对：
**15/15 PASS，unexplained=0**（唯一 known diff 是 `2pyr-gen.json` 的 volatile 字段：
generated_at / generator_version / generator_build）。
覆盖：fixture-f15 → 55/97（default / --cit / --delete-shaders / --no-item-definitions /
--no-gen-metadata / cit+delete-shaders）、fixture-f1 → 55/97/1000、TapL 16x → 55/97/1000/97+delete-shaders。
`pack.mcmeta` 全部 `semantic_equal=True`（多数同时 byte_equal）。TapL→97 约 1.8s（release, host）。

---

## 8. 下一步（不在本任务范围）

1. **verify-build / task-5**：`cargo ndk --target aarch64-linux-android --platform 31 … build --release` → `uniffi-bindgen generate --library libconvert_core.so --language kotlin --out-dir …/uniffi`；本 crate 的 `.cargo/config.toml` 已把 aarch64 rustflags 指向 staged rust-std，勿动。
2. **android-ui / task-6**：按 §3 的 binding 类型改 `RustNative.kt`（4 参 `initRuntime`、`UInt`/`ULong`、回调接口）与 `TwoFAApp.kt`（传 `cacheDir`）。
3. **L6 差分**：以桌面 CLI（`--convert`）为 oracle，按 `_sync` 报告 S7 口径比对；UImage 差异按任务白名单归因。
4. **L7**：`convert-core/Cargo.toml` version → 2.1.0；README/AGENTS 更新（46 原生任务 / 26+1 版本 / 2.1.0）。

---

## 9. 集成收口记录（2026-10-31，L6/L7 阶段回填）

§8 的「下一步」已全部完成，以下是实施期新增的坑与最终状态（含对前文的更正）：

### 9.1 更正：`.cargo/config.toml` 的 aarch64 sysroot 覆盖已被移除

§8.1 说「已把 aarch64 rustflags 指向 staged rust-std，勿动」是**当时**的状态；该 staging 目录
（`%USERPROFILE%\Downloads\rust-std-staging2`）已不存在，保留覆盖会导致 `error[E0463]: can't find crate for core`。
现改为**不覆盖 sysroot**（rust-std 已 stage 在 rustup toolchain 的 `lib\rustlib\aarch64-linux-android\` 内，
`cargo check --target aarch64-linux-android` 可过）；备份在 `_sync/tmp/stale-config-backup/`。

### 9.2 新坑（阻断级，已修）：facade 文档注释里的 `/*` 会毁掉生成的 Kotlin binding

- `lib.rs` 的 `convert_item_definitions` 字段 doc 里写了 `` `assets/<ns>/items/*.json` ``；
  uniffi-bindgen 把 doc 原样写进 Kotlin KDoc，而 **Kotlin 支持嵌套块注释**：KDoc 文本中的 `/*` 开启嵌套注释，
  外层未闭合 → `convert_core.kt` 从该行起语法崩坏（表现为 `Unresolved reference
  'uniffiCallbackInterfaceConvertProgress'`、`Unclosed comment` 等一串「看起来无关」的错误）。
- 修复：注释改为 `items/<name>.json` 后重新生成 `.so` + binding，Kotlin 编译通过。
- **规则**：facade 的 Rust doc 注释中禁止出现 `/*` 序列；**每次 `uniffi-bindgen` 之后必须跑一次
  `:app:compileDebugKotlin`**（host bindgen 成功 ≠ Kotlin 可编译）。

### 9.3 最终构建/验证状态

- 版本：`convert-core` 2.1.0（`.so` 内嵌 2.1.0）、App `versionName=2.1.0` / `versionCode` 自动 +1；
- `.so`：5,736,688 B（arm64-v8a，6 个 uniffi 导出符号）；binding 62,800 B；
- `:app:assembleDebug`（含 Rust 构建）**BUILD SUCCESSFUL 16.4s**；`:app:assembleRelease` **64.1s**；
  release APK **10,106,073 B（10.1 MB）**，`minSdk 31` / `targetSdk 37` / arm64-v8a；
- host 差分：`_sync/reference/run_host_engine.ps1` 对 15 个 no-UImage oracle case **15/15 PASS，unexplained=0**；
- 详细证据：`_sync/reports/l6-verification.md`；仓库内同步记录：`docs/desktop-sync-2.203.md`。
