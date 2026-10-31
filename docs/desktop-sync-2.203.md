# 桌面版 → Android 版同步记录（Android 2.0.0 → 2.1.0）

> 本文件记录本次「桌面 2.203.0 → Android」同步的基准、范围、执行分层、验证证据、已知差异与后续项。
> 配套文档：`desktop-sync-2.203-report.md`（基础报告：变更清单/难点/特殊情况/边界/风险）、
> `desktop-sync-2.203-plan.md`（分层修改流程 L0–L7）。

## 1. 基准

| 项 | 值 |
|---|---|
| 桌面基准 | `2pyr-desktop/code` git `master` HEAD `3dd0e45`（2026-10-24，`package.json` 2.203.0 + `[Unreleased]`；BUILD 22030） |
| 桌面最后同步点（本次起点） | `0635bdd`（2.1.2，2026-09-12），Android 2.0.0 的 pre-M3 引擎底座 |
| 差距 | 367 commits；`src-tauri/src` 210 文件 / +34,580 −12,486；`src/` 59 文件 / +14,953 −5,888 |
| Android 同步前 | git `519f904`（2026-09-12，versionName 2.0.0），分支 `sync/desktop-2.203`，tag `pre-sync-2.0.0` |
| 同步后目标版本 | versionName **2.1.0**（versionCode 继续自动 +1），convert-core **2.1.0** |
| 同步策略 | **整体替换式移植**（M3/A-ROM 引擎替换 pre-M3 的 `hurray/`+`converters/`）+ Android 适配层 + Kotlin/Compose 转换能力对齐 |

## 2. 分层执行状态（对应 plan 文档）

| 层 | 内容 | 状态 | 证据 |
|---|---|---|---|
| L0 | 基础报告 + 分层方案（不写代码） | ✅ 完成 | `desktop-sync-2.203-report.md` / `-plan.md` |
| L1 | 构建基线修复 + fixtures + 桌面参考产物 + 差分脚本 | ⏳ 进行中 | `_sync/reports/l1-baseline.md` |
| L2 | M3 引擎整体移植（94 文件 ≈30k 行 + 488KB 资源） | ✅ 完成 | 旧 `converters/hurray/overlay` 已删、新模块就位 |
| L3 | facade / 进度 / skipped+notes / 测试分诊 / host 双目标 | ✅ 完成 | 220 tests + 6 smoke + release build（见 §4） |
| L4 | Kotlin 数据层 + 设置持久化 + 版本表 + 主题 + 进度骨架 | ✅ 完成 | `SettingsRepository`/`ProgressRepository`/`ConvertOptions`/`Texts` |
| L5 | UniFFI 桥接 + Compose 选项/进度/结果 + 编译 | ✅ 完成 | RustNative 新 API、4 参 initRuntime、Service 真进度、compileDebugKotlin PASS |
| L6 | 全链 assembleDebug/Release + 差分验证 + APK 检查 | ✅ 完成 | `_sync/reports/l6-verification.md`（15/15 PASS、含 UImage 后 APK 10.2 MB） |
| L7 | 版本号/README/AGENTS/同步记录/提交 | ✅ 完成 | 本文件 + 分支 `sync/desktop-2.203` |

## 3. 同步内容清单

### 3.1 Rust 引擎（`convert-core/`）

- **结构替换**：删除 pre-M3 的 `converters/`（98 文件）、`hurray/`（8 文件）、`overlay/`、旧 `invoke_conversion.rs`、
  旧 `color_utils.rs`；移入桌面 M3：
  - `arom/`（15 文件）：A-ROM 对象模型（`Pack`/`Tx`/`Layer`/视图/序列化/限额/结构分析）+ `arom/engine/`（BFS 调度 + `conversion_maps.rs` 版本边）；
  - `natives/`（53 文件 + `architect/item-definitions.json` 488,306 B）：46 个原生任务（eraser/architect/surgeon/reverse）；
  - `pack/`（5）：生产编排 `version_converter.rs`（`process_zip_timed`）、IO、结构分析 `analysis.rs`、产物差分 `diff.rs`；
  - `bedrock_convert/`（11）：j2b/b2j + mapping/textures/ui/potions/metadata/shaders/skybox；
  - 根文件：`native_run.rs`（驱动）、`task_registry.rs`（90 项元数据）、`gen_metadata.rs`（2pyr-gen.json）、
    `chest_region.rs`、`scale_factor.rs`、`color/`、空壳 `invoke_conversion.rs`。
- **适配层（Android 新写/改写）**：
  - `lib.rs` facade 重写：`ConvertOptions`（9 字段）/`ConvertRequest`/`ConvertReport`（7 字段）/
    `ConvertProgress` callback/`convert_zip(req, progress)`；保留 `init_runtime`（**4 参数**，新增 `temp_dir`）/
    `get_recent_logs` / dev mode / version / `ConvertError` 变体；
  - `progress.rs`：全局 `ProgressSink`（extract 0–12% / pipeline 12–90% / output 90–100%，单调、节流、RAII 清理）；
  - `commands/{mod,config}.rs`：内存 `AppConfig{output_naming}` shim（引擎 `build_output_path` 唯一依赖）；
  - `image_utils.rs`：补 `get_uimage_path()`（`runtime::uimage_dir()`，无/空目录返回 Err，不建目录）；
  - `runtime.rs`：`RuntimePaths` 增加 `temp_dir`，init 时设置 `TMPDIR`（tempfile 落 app cache）；
  - `build.rs`：注入 `BUILD_NUMBER`（默认 21000，可被 env 覆盖）；
  - `Cargo.toml`：`+sha2`、`+flate2`；release `panic = "unwind"`（UniFFI `catch_unwind` 边界）；
  - `process_zip_timed` 返回 `ConversionOutcome{output_path,timing,source_version,skipped,notes}`（文件头标注 Android 差异）；
  - `examples/convert_cli.rs`：host 差分 runner（不参与 Android 构建），供 L6 与桌面 CLI 产物对比。

### 3.2 桌面行为变更（本版 Android 已对齐）

- 目标版本新增 **26.3 = pack_format 97**（共 26 Java 目标 + Bedrock = 27 项）；`pack.mcmeta` 目标 ≥69 只写
  `min_format`/`max_format`（26.3 = `[97,0]`–`[97,1]`），保留 `overlays`/`filter` 顶层字段；
- **Bedrock 中间态统一 97**（旧 Android 为 88）；j2b/b2j 边 `(84|88|97 ↔ 1000)`；
- **1.21.4+ 物品模型定义生成**（`generate_item_definitions`，`(42,46)` 边；参考表来自官方 jar 提取）；
- **OptiFine CIT → 物品定义**（`generate_cit_item_definitions`，`(46,55)` 边；支持附魔/显示上下文；
  **默认关闭**——产品止血，待实机验证；`delete_cit` 优先于转换）；
- **2pyr-gen.json 产物元数据**（默认开、仅 Java 目标；六开关 + 源/目标 format + 版本/BUILD + UTC 时间；无个人信息）；
- **delete_shaders**（破坏性，默认关）与 `adapt_java_shaders` 的 Wiki 白名单适配；`adapt_shaders` 改为计划级过滤；
- **skipped/notes 上报**（桌面只写日志，Android 升级为 ConvertReport 字段，结果页可见）；
- **按钮 sprite 描边修复**（产物字节变化，仅 `sprites/widget/`）；内容基线不再冻结（只报告）；
- `ConversionMaps` 静默覆盖修复、26.x mcmeta 顶层字段保留、分辨率三路采样、九宫格等边等修复；
- M3 性能/IO：Java 目标产物直写 zip（不物化再重打包）、解压并行、图片/条目表缓存、后台清理三档。

### 3.3 Kotlin / Compose（Android UI）

- **版本表**：27 项（+26.3/97 latest、26.2 stable、1.21.11 stable、26.1 无 status、Bedrock Latest beta，默认 34）；
- **转换选项**：`adapt_shaders` / `delete_shaders` / `convert_item_definitions` / `convert_cit` / `delete_cit` /
  `write_gen_metadata` 六开关（`fix_alpha_layers` 桌面已死开关，不暴露、字段保留默认 false）+ 输出命名预设
  （`[Ver][Name]` / `[Ver][Time]` / `[Name]` / 自定义）；
- **设置持久化**：SharedPreferences（7 开关/输出模式/路径/命名/主题/上次目标/dev mode），替换原纯内存 `AppSettings`；
- **主题**：light/dark/system 三档持久化 + 深色配色（关闭 dynamic color 以免品牌色漂移）；
- **进度**：全局 `ProgressRepository`（Service 只写、UI 收集）+ 真回调进度条 + phase 文案；
- **结果/历史**：展示 source→target、输出路径、耗时、`skipped` 数量 + `notes` 展开（notes 含跳过原因与普通进展，
  不统一标「跳过」）；历史记录兼容旧 JSON（`opt*` 读取）；
- **警示**：破坏性开关「会删文件」+ 二次确认；26.3 MC-311807 非阻塞提示；Bedrock 实验性提示；
- **队列/生命周期**：转换选项入队时快照（`QueueItem.options`），入队即 `startForegroundService` + 通知权限；
- **SAF**：修复固定输出目录的 URI 存/判不一致问题（存原始 `content://` tree URI）；
- **UImage 素材（后补）**：桌面 `src-tauri/UImage/` 的 80 张 PNG（16 个分类，570,387 B）随 APK 以
  `assets/UImage/` 打包；新增 `util/UImageAssets` 在 `Application.onCreate` 时（首次/素材版本变化）同步解压到
  `filesDir/UImage`，再传给 `init_runtime` 的 `uimageDir`；`get_uimage_path()` 因此可用，
  7 组依赖原版素材的生成任务恢复运行（APK +0.6 MB，release 10.1 → 10.2 MB）。

### 3.4 构建基础设施修复（L1，与业务代码无关）

- `app/build.gradle.kts`：`buildRustAndroid` 脚本路径 `$rootDir\tools` → `rootProject.file("../tools/...")`；
  配置期解析 SDK/NDK 并显式注入子进程环境（`ANDROID_HOME`/`ANDROID_SDK_ROOT`/`ANDROID_NDK_HOME`/`ANDROID_NDK_VERSION`）；
- `gradle.properties`：`android.ndkVersion` 27.2.12479018 → **27.3.13750724**（本机实际安装版本；27.2 不在 `D:\Android\ndk`）；
- `tools/build-rust-android.bat`：SDK/NDK 解析与 `local.properties` 回退、`DRY_RUN`、target 已存在则跳过
  `rustup target add` 且不启用已失效的 staging wrapper、修正 `OUT_DIR` 缺 `app` 层、在 crate 目录内调用
  `uniffi-bindgen`（0.32 `--library` 模式仍跑 `cargo metadata`）、清理 cargo-ndk 嵌套 ABI 目录；
- `convert-core/.cargo/config.toml`（**gitignored 本地文件**）：移除指向已删除 staging 目录的 aarch64 `--sysroot` 覆盖。

## 4. 验证证据（随任务推进补齐）

### 4.1 引擎（host）
- `cargo check`：0 error（58 warnings 为桌面命令层 dead_code，有意保留以最小化与桌面 diff）；
- `cargo test --lib`：**220 passed / 0 failed / 6 ignored**（6 个 ignore 均为桌面原样：5×`AROM_REAL_PACK` + 1×IO 计时）；
- `cargo test --test android_smoke`：**6 passed**（15→55 / gen-metadata 开关 / delete_shaders / 15→97 min/max_format /
  15→1000 .mcpack+manifest / InputNotFound；进度回调单调且覆盖三段）；
- `cargo build --release`：host `convert_core.dll` 5,500,928 B；
- `grep -ri tauri src`：无输出。

### 4.2 Android 构建
- 基线（同步前）：`SKIP_RUST_BUILD=true assembleDebug` BUILD SUCCESSFUL（2m37s），APK 59,554,375 B（旧 .so 4,056,784 B）；
- 新引擎 arm64 `.so`：**5,736,688 B**（ELF64 AArch64，6 个 uniffi 导出符号；内嵌版本字符串 2.1.0），
  binding **62,800 B**（uniffi 0.32 生成，含 `ConvertOptions`/`ConvertRequest`/`ConvertReport`/`ConvertProgress`/4 参 `initRuntime`）；
- 全链 `:app:assembleDebug`（含 Rust 构建）：**BUILD SUCCESSFUL 16.4s**；
  `:app:assembleRelease`（R8 + resource shrink）：**BUILD SUCCESSFUL 64.1s**；
- release APK（打包 UImage 后最终）：**10,706,339 B（10.2 MB）**，`versionName=2.1.0`、`versionCode` 每次构建自动 +1
  （最终构建 37）、`minSdk=31`/`targetSdk=37`；APK 内含 `lib/arm64-v8a/libconvert_core.so` 5,736,688 B 与
  `assets/UImage/` 80 个文件（570,387 B）；`grep -ri tauri convert-core/src` 无输出。

### 4.3 产物差分（Android 引擎 host runner vs 桌面 2.203.0 CLI）
- 对 `_sync/reference/no-uimage` 全部 **15 个 case**：**15/15 PASS，unexplained=0**
  （f1/f15/TapL × 55/97/1000 × 默认/`--cit`/`--delete-shaders`/`--no-item-definitions`/`--no-gen-metadata` 组合；
  唯一 known diff 为 `2pyr-gen.json` 的 volatile 字段 `generated_at`/`generator_version`/`generator_build`）；
- **UImage 加入后**：host runner 显式传 `--uimage <桌面 UImage/>`，对 `reference/with-uimage` 全部 15 个 case
  重新差分：**15/15 PASS，unexplained=0** —— 说明原版素材可让 7 组生成任务与桌面产物一致；
  （加入前 UImage 缺失的预期差异已用 A/B 量化：6 个 case only-in 1–7 条、内容不同 12–53 条）。
- 详细证据：`_sync/reports/l6-verification.md`、`_sync/reference/host-engine{,-with-uimage}/manifest.json`。

## 5. 已知差异 / 边界（本次不处理或预期差异）

| 项 | 说明 |
|---|---|
| UImage | **已随 APK 打包**（80 张 PNG / 570,387 B，`assets/UImage/` → 首次启动解压到 `filesDir/UImage`）；host 差分已用
  `with-uimage` oracle 验证 15/15 PASS；**首次启动解压与真机生成任务仍待实机确认** |
| zip 容器时间戳 | Android `zip` 未开 `time` feature → 缺省 1980 时间戳；桌面写运行时刻。差分按「条目集合+内容哈希」口径，不比整 zip 字节 |
| `fix_alpha_layers` | 桌面 M3 后实现已删，仅剩字段（no-op）；Android 保留字段但不暴露 UI，`2pyr-gen.json` 契约保持一致 |
| 未实机验证 | 本机无 AVD/adb 设备：UImage 首次解压、TMPDIR 注入、JNA 跨线程进度回调、SAF 输入/输出、大包内存均未在真机验证 |
| 桌面专属未同步 | 更新器/安装器/MSIX/OOBE/Overlay/Foray/文件浏览器/通知/CLI（`--pack-diff`/`--analyze`）/性能档位 UI/log 脱敏/legal 正文 |
| 品牌/logo | 用户明确桌面仍在改 logo，本次不同步；Android 使用现有图标 |
| 性能档位 | 未做桌面「平衡/性能」两档；Android 固定 rayon 4 线程预算（`perf.rs` 未移植） |
| 发布链路 | 仍用 debug keystore 签名、arm64-v8a only、无 ABI split（沿用现状） |

## 6. 实施期发现并修复的问题（值得记住）

1. **UniFFI binding 的 Kotlin 嵌套注释坑（阻断编译）**：facade 的 Rust 文档注释里写了
   `` `assets/<ns>/items/*.json` ``，uniffi-bindgen 原样放进 Kotlin KDoc；Kotlin 支持嵌套块注释，
   KDoc 里的 `/*` 开启嵌套注释 → 外层未闭合，整个 `convert_core.kt` 语法崩坏（一串无厘头错误）。
   修复：注释改成 `items/<name>.json`；**今后 facade 文档注释禁止出现 `/*` 序列**，
   且 binding 生成后必须跑一次 Kotlin 编译闸门。
2. **Gradle buildRustAndroid 路径与 NDK 环境**：脚本实际在仓库根 `tools/`（rootDir 是 `2fa-android`）；
   NDK 27.2/27.3 本机不一致且 `ANDROID_HOME/ANDROID_NDK_HOME` 为空 → 已在 Gradle 配置期解析并注入子进程环境。
3. **`tools/build-rust-android.bat` 的 `OUT_DIR` 少一层 `app`**、uniffi-bindgen 需在 crate 目录内运行、
   target 已存在时不应启用已失效的 staging wrapper、cargo-ndk 嵌套 ABI 目录需清理。
4. **`.cargo/config.toml`（gitignored 本地文件）** 的 aarch64 `--sysroot` 指向已删除的 staging 目录 → 移除覆盖。
5. **同一个「嵌套注释」坑在 Kotlin 侧又踩了一次**：新增的 `UImageAssets.kt` 注释里写 `` `assets/UImage/**` ``，
   KDoc 中的 `/*` 让 Kotlin 文件报 `Unclosed comment`。规则扩展为：**Rust 与 Kotlin 的注释/文档里都不要出现
   `/*` 序列**（写目录通配用 `<name>` 或去掉 `**`）。
6. **UImage 后补**：初始同步按边界未打包 UImage；后按用户要求补齐（80 张 PNG/570KB → assets + 首次启动解压），
   并用 `with-uimage` oracle 做 host 差分 15/15 PASS。

## 7. 后续 TODO

1. 真机验证：UImage 首次启动解压、TMPDIR、进度回调（JNA 跨线程）、SAF 输出、256x/512x 大包内存与耗时；
2. 未同步的桌面能力按需逐项评估（更新器/Overlay/Foray/文件浏览器/日志脱敏/legal）；
3. 桌面 logo 定稿后单独同步品牌资产；
4. CI 增强：`cargo test`、Kotlin 编译校验、差分回归（当前 `build.yml` 只 assembleDebug）；
5. 下次同步流程：桌面合并后按 `desktop-sync-2.203-report.md` 的「结构映射」做引擎目录级 diff（不再 cherry-pick）。
