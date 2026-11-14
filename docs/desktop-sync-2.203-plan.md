# 2-Pyramid 桌面版 → Android 版同步 · 分层修改流程

| 项 | 值 |
|---|---|
| 制定日期 | 2026-10-24 |
| 关联报告 | `docs/desktop-sync-2.203-report.md`（先读报告，再执行本文） |
| 桌面基准 | `2pyr-desktop/code` git `master` HEAD `3dd0e45`（2.203.0 + `[Unreleased]`） |
| Android 基准 | `2pyr-For-Android` HEAD `519f904`（2.0.0），本次分支 `sync/desktop-2.203` |
| 前置约束 | **Layer 0 未验收前不得改任何代码**；桌面仓库全程只读；每层可独立验收/回滚 |

---

## 0. 分层原则

1. **报告先行**：L0 是「文档层」，不改代码；L0 通过后按 L1→L7 施工。
2. **每层必须可验收**：每层结束都有一条可复现的验收命令与预期输出；不通过不进下一层。
3. **每层可回滚**：L1 前打 `pre-sync` tag/分支点；代码层（L2+）每层一个提交（由 Lead 统一提交），
   任一层失败 `git checkout` 回滚到上一层，不带着半成品前进。
4. **写入隔离**：同一时刻同一路径只有一个 owner；跨层共享文件通过契约冻结（L3 定稿 facade 后才允许 L4/L5 改 Kotlin 桥接）。
5. **引擎零重写**：L2/L3 只搬桌面代码 + 写适配层；**不重写算法**。所有适配点登记在报告 §4.2。
6. **验证分级**：编译 → 单测 → host e2e → Android 构建 → 差分 →（可选）设备。
7. **开关默认值以报告 §2.4 为唯一口径**；版本表以报告 §2.3 为唯一口径。
8. **品牌/logo、桌面专属功能不进入任何层**（见报告 §7.2）。

---

## 1. 分层总览

```
L0 报告 + 分层方案（文档，无代码）            ← 当前
      │
      ▼
L1 工具链基线 + 验证脚手架（不改源码）
      │
      ▼
L2 引擎移植（复制 + 适配层 + host 编译）
      │
      ▼
L3 引擎收口（facade / 进度 / 测试分诊 / release + cargo-ndk + bindings）
      │
      ├──────────────┐
      ▼              ▼
L4 Kotlin 数据层/桥接   （可与 L5 部分并行，但桥接文件串行）
      │              
      ▼              
L5 Compose UI（选项/进度/结果/主题）
      │
      ▼
L6 集成构建 + 差分验证 + APK 检查
      │
      ▼
L7 文档/版本号/最终审查/提交
```

- **可并行的层**：L1 与 L2（不同写入范围）；L4 的「设置持久化/版本表/主题」（不依赖 Rust）与 L2/L3 并行。
- **硬依赖**：L4 的桥接部分依赖 L3 的 facade 与绑定的 binding；L5 依赖 L4；L6 依赖 L2/L3/L4/L5；L7 依赖 L6。
- **串行热点**：`convert-core`（L2/L3 同一 owner）、`RustNative.kt`/`ZipQueueService.kt`（L4 桥接）、
  `app/build.gradle.kts`/`version.properties`（L6/L7）。

---

## 2. 各层定义（目标 / 步骤 / 验收 / 回滚）

### L0 — 报告与方案（当前层，只写文档）

- **目标**：给出基础报告（现状、变更清单、断代判定、难点、特殊情况、边界、验证策略、风险）+ 本分层方案。
- **写入范围**：`2pyr-For-Android/docs/desktop-sync-2.203-report.md`、`…-plan.md`、`_sync/CONTRACT.md`。
- **验收**：用户确认报告与分层方案；`git status` 中无任何源码改动。
- **回滚**：删除 `docs/` 新增文档即可（不影响代码）。
- **注意**：本层禁止改源码、禁止改桌面仓库、禁止提交。

### L1 — 工具链基线与验证脚手架（不改源码）

- **目标**：证明「改动前的现状在本机可构建」，并准备可复用的验证资产（fixture / 参考产物 / 差分脚本）。
- **前置**：L0 通过。
- **写入范围**：`_sync/**`（脚本、fixture、参考产物、报告）；构建产物目录（`convert-core/target`、
  `2fa-android/app/build`、`jniLibs`、生成的 `uniffi` binding）——这些均已 gitignore。
- **步骤**：
  1. **先修构建环境的两个硬阻塞**（与业务代码无关，必须在基线构建前处理）：
     - `app/build.gradle.kts` 的 `buildRustAndroid` 用了 `$rootDir\tools\build-rust-android.bat`，而 `rootDir` 是
       `2fa-android`、脚本在仓库根 `tools/` → 改为 `rootProject.file("../tools/build-rust-android.bat")`
       （或 `$rootDir/../tools/...`）；
     - NDK 版本/路径不一致：`gradle.properties`/`build-rust-android.bat` 期望 `27.2.12479018`，
       本机 `D:\Android\ndk` 只有 `27.3.13750724`（27.2 在 `D:\Android\Sdk\ndk`），且 `ANDROID_HOME/ANDROID_NDK_HOME` 为空。
       二选一：把 27.2 拷/装到 `D:\Android\ndk`，或把配置统一到 27.3 并设置 `ANDROID_NDK_HOME`。
     记录修法与影响（这两个修复属于「让基线可构建」，不算业务代码改动）。
  2. 用当前（未改业务代码）代码跑一次 `gradlew assembleDebug`，记录：耗时、APK 路径、`.so` 架构与体积、
     binding 是否生成。若失败，定位是环境问题还是代码问题——**这是环境闸门，必须在 L2 前通过**。
     体积基线：`.so` ≈4.06 MB、release APK ≈8.39 MB、debug APK ≈59.3 MB。
  3. 生成 fixture：手工构造两个小资源包（format 1 / format 15），覆盖
     `pack.mcmeta`、`assets/**`、`shaders/`、`optifine/cit/*.properties`、`models/item/*.json`（含 `overrides`+`CustomModelData`）；
     写入 `_sync/fixtures/`。**注意**：真实包 `TapL 16x.zip` 没有 item overrides（桌面转 97 后 `assets/minecraft/items/` 为 0 条），
     所以「物品定义 / CIT」必须靠手工 fixture 验证，不能只靠 TapL。
  4. 用桌面预构建 CLI 对 fixture 与 `TapL 16x.zip` 生成参考产物：
     `--convert … --to 55|97|1000 [--cit|--delete-shaders|--no-item-definitions|--no-gen-metadata]`，
     存入 `_sync/reference/`，并保存 `--report` JSON。参考产物生成后记录条目数/体积/耗时。
  5. 写差分脚本（PowerShell 或 Python）：解开两个 zip → 条目集合 diff、逐条目解压后 SHA-256、压缩方法
     （Stored/Deflated）、`pack.mcmeta` 语义比较；**忽略 zip 时间戳/条目顺序**（见报告 S7/S16）；输出
     「新增/缺失/内容变/压缩方法变」四类清单。用「同一文件比自身=0 差异」「故意改一个条目=能检出」自测脚本。
  6. 记录桌面 CLI 的行为基线（TapL→97 实测：4139 条目、纯转换 3.87s、总 4.99s、18.41→18.95 MB）。
- **验收**：
  - `_sync/reports/l1-baseline.md`：构建阻塞的修复记录 + 现状 assembleDebug 结果（PASS/FAIL + 证据 + 体积基线）；
  - `_sync/fixtures/*.zip`、`_sync/reference/**`、`_sync/compare.ps1|py` 就绪；
  - 差分脚本自测通过（0 差异 / 能检出）。
- **回滚**：`_sync/` 为新增目录，删除即可；构建脚本/NDK 配置的修复若在 Gradle 文件，属于基线修复，
  单独记录并在最终提交中保留（可在 L7 复核）。
- **风险**：Gradle 8.13 下载失败、SDK/NDK 路径、cargo-ndk 首次编译慢 → 记录并优先解决，不带着环境问题进 L2。

### L2 — 引擎移植（复制 + 适配层 + host 编译）

- **目标**：把桌面 M3 引擎整体搬入 `convert-core`，完成最小适配层，使 **host `cargo check` 通过**。
- **前置**：L1 通过（环境闸门）。
- **写入范围**：`2pyr-For-Android/convert-core/src/**`、`convert-core/Cargo.toml`、`convert-core/build.rs`。
- **步骤**：
  1. **备份/基线点**：Lead 在 L2 前打 `pre-sync` tag（或记录 HEAD）；旧引擎在 git 历史可恢复。
  2. **删除旧结构（先迁后删）**：先把 `get_uimage_path` 从旧 `converters/mod.rs` 迁到 `image_utils.rs`、
     把 config shim/progress 建好，再删 `src/converters/`、`src/hurray/`、`src/overlay/`、旧 `src/invoke_conversion.rs`、
     根 `src/color_utils.rs`、旧 `src/lib.rs`；`bedrock_convert::{register_tasks}` 与 `invoke_conversion::register_tasks`
     的新签名要一起改（桌面 `bedrock_convert/mod.rs`、`arom/engine/scheduler.rs`）。
  3. **复制新引擎**（桌面 `src-tauri/src/` → Android `convert-core/src/`）：
     `arom/`、`pack/`、`natives/`（含 `architect/item-definitions.json`，`include_str!` 要求同目录）、
     `bedrock_convert/`、`color/`、`chest_region.rs`、`gen_metadata.rs`、`scale_factor.rs`、`native_run.rs`、
     `task_registry.rs`、`invoke_conversion.rs`（59 行空壳）。**只复制，不改算法。**
     - **必须搬**（测试/生产引用）：`pack/diff.rs`（~20 个测试依赖）、`pack/analysis.rs`（生产调用）、
       `invoke_conversion::register_tasks`（native_run 测试调用）。
     - **可不搬**：`perf.rs`（引擎 0 引用；若要复用 `detect_cores` 可搬 178 行，Windows 内存探测已 cfg 门控）、
       `process_extracted_dir_only`/`convert_resource_pack`（桌面测试/CLI 入口，Android 不需要）。
  4. **适配层**（新/改文件）：
     - `lib.rs`：模块声明 + `uniffi::setup_scaffolding!()` + 日志宏定义（`log_debug/info/warn/error`）；
     - `logger.rs`：保留 Android API，新增宏实现（转发到 `GLOBAL_LOGGER`）；
     - `image_utils.rs`：保留 `paste_region`/`swap_and_mirror`，补 `get_uimage_path()`（`runtime::uimage_dir()`）；
     - `commands/mod.rs` + `commands/config.rs`：`AppConfig{output_naming,…}` + `read_config_file()` 内存 shim；
     - `runtime.rs`：保留，新增 `temp_dir`/`cache_dir`（S10）；
     - `progress.rs`：全局 sink（L3 接调度器，L2 先建 API）；
     - `build.rs`：保留 uniffi bindgen；注入 `BUILD_NUMBER`（S9/S15）；
     - `Cargo.toml`：**+`sha2`、+`flate2`**（`arom/source.rs` 的 `DirSource::crc32` 用），image/zip feature 按编译收敛（S16）；
  5. 清理 `tauri` 字样（注释改为「桌面端」），`grep -ri tauri src` 必须为空；`MixedRunOptions` 生产路径显式 `NativeSwitches::all()`（S13）。
  6. 循环：`cargo check` → 逐错修复（区分「复制遗漏」与「适配缺失」，不擅自改算法）。
- **验收**：
  - `cd convert-core && cargo check` 0 error；
  - `grep -ri tauri src` 无输出；
  - 新增/修改文件清单与适配点清单（对照报告 §4.2，逐条打勾，含 S13/S14/S16 的处理记录）。
- **回滚**：`git checkout -- convert-core` + 删除新增目录，回到 L1 状态。
- **风险**：依赖 feature 不匹配（image/zip）、平台 cfg、测试占位导致编译错 → 先 `cargo check --lib` 收敛生产代码，
  测试模块可临时 `#[cfg(test)]` 保留在文件内但不要求 L2 编译通过（L3 处理）。

### L3 — 引擎收口（facade / 进度 / 测试 / 双目标构建）

- **目标**：UniFFI 契约落地、进度插桩、单测分诊、host release + cargo-ndk 双目标构建通过、binding 生成成功。
- **前置**：L2 通过。
- **写入范围**：`convert-core/src/{lib.rs,progress.rs,arom/engine/scheduler.rs,pack/io.rs,pack/version_converter.rs,native_run.rs}`、
  `convert-core/tests/**`、生成的 `uniffi/` binding（构建产物）。
- **步骤**：
  1. **facade 定稿**（与 Kotlin 共享契约，报告 §4 / `_sync/CONTRACT.md` §4）：
     `ConvertOptions{target_version,fix_alpha_layers,adapt_shaders,delete_shaders,convert_item_definitions,convert_cit,delete_cit,write_gen_metadata,output_naming}`、
     `ConvertRequest{input_zip,output_dir,options}`、
     `ConvertReport{output_path,source_version,target_version,log_path,skipped,notes,elapsed_ms}`、
     `#[uniffi::export(callback_interface)] ConvertProgress{on_progress(progress,phase)}`、
     `convert_zip(req, progress)`；保留 `init_runtime`/`get_recent_logs`/`set_dev_mode`/`is_dev_mode`/`convert_core_version`。
     实现：options → `MixedRunOptions`（默认值见报告 §2.4，**`native` 显式 `NativeSwitches::all()`**）→
     `pack::version_converter::process_zip_timed`；`output_naming` 写入 config shim；
     `source_version` 由 facade 读输入包（Bedrock 源=1000）；
     **`skipped/notes` 必须回传**：桌面 `process_zip_timed` 只返回 `(path, timing)` 且把 `MixedRunReport.skipped/notes`
     丢弃（只 log_warn）。因为 Android 侧这份代码是我们自己的拷贝，允许做**最小改造**（如增加 `LastReport` 全局收集
     或改返回结构），但要在文件头注释标注「Android 适配点，与桌面差异」以便未来 diff。
     `fix_alpha_layers` 按 S14 决策处理（默认方案：保留字段、no-op、文档标注）。
  2. **进度（新写，无现成实现可搬）**：`progress.rs` sink；
     埋点 4 处（只读旁路，不改控制流）：
     ① `pack/io.rs` extract 循环 → "extract" 0.00–0.12；
     ② `native_run.rs::dispatch_native_into_pack` + `apply_parallel_layers` → 任务名 0.12–0.90（分母 `report.plan_len`，
        并行批按批记，用原子计数保证**单调不回退**）；
     ③ `arom/serialize.rs::write_zip` 循环 / `pack/io.rs` repack → "output" 0.90–1.00（4000+ 条目按 1/64 抽样，别每条 emit）；
     ④ `process_zip_timed` 相位边界兜底。
     `rayon` 并行批里 `emit` 需线程安全（Mutex 足够）；UniFFI→JNA→Kotlin 的**跨线程回调**必须在真机验证
     （JNA 需 attach 当前线程；失败则降级为 Rust 侧写状态 + Kotlin 轮询）。
     `panic="abort"` 下无法靠 Drop guard 清理 sink：release 建议改 `unwind`（S20），错误路径必须显式清理。
  3. **测试分诊**：`cargo test --lib` 全量跑；把失败分三类：
     直接可跑 / 需小改（路径、BUILD_NUMBER 注入、UImage 缺失时 skip 断言）/ 不可移植（桌面 tools/真实包/CLI）
     → 第三类 `#[ignore = "reason"]` 并在 L3 报告逐条列出；**不删能力代码**。
  4. **facade smoke test**（`convert-core/tests/android_smoke.rs`，程序化合成 fixture）：转 55/97/1000；
     断言 `pack.mcmeta`、`items/*.json`（≥46）、`2pyr-gen.json`（开关对照）、`delete_shaders`、`.mcpack`。
  5. **双目标构建**：`cargo build --release`（host）→ `cargo ndk -t aarch64-linux-android --platform 31 build --release`
     → `uniffi-bindgen generate --library libconvert_core.so --language kotlin --out-dir <app>/…/uniffi`。
- **验收**：
  - `cargo test --lib` 通过（`#[ignore]` 清单 + 理由）；`cargo build --release` 通过；
  - `cargo ndk` 产出 arm64 `.so`；binding 生成成功且包含 `ConvertOptions/ConvertProgress/ConvertReport`；
  - `_sync/reports/l3-engine.md`：契约、进度权重、测试分诊表、忽略清单、host/Android 构建证据。
- **回滚**：回退到 L2 提交；binding/.so 为生成产物，删除重生成即可。
- **风险**：UniFFI 0.32 对 `Box<dyn callback>`/`Vec<String>`/`Option<String>` 的生成行为；JNA 加载；
  `panic="abort"` 下的 panic 传播 → 用 `runCatching` 风格 facade 兜底。

### L4 — Kotlin 数据层与 UniFFI 桥接

- **目标**：Kotlin 侧新契约可编译、设置持久化、版本表对齐、Service 真进度、结果/历史数据结构就绪。
- **前置**：L3 通过（binding 可生成）；L4 的「非桥接部分」可与 L2/L3 并行。
- **写入范围**：`2fa-android/app/src/main/kotlin/com/twopyramid/twofa/**`（不含生成 binding 与 L5 的 UI 文件）、
  `2fa-android/app/src/main/res/**`（如资源）。
- **步骤**（可拆 4a 非桥接 / 4b 桥接）：
  - **4a（可先做）**：`SettingsRepository`（SharedPreferences）——7 开关、默认值（报告 §2.4）、
    输出模式/路径、命名模板、主题模式、上次目标版本、dev mode、`temp_dir` 派生；迁移旧 `AppSettings` 调用点；
    版本表补 26.3/97 + 状态修正（26.2 stable、26.3 latest、1.21.11 stable、34 去掉 RECOMMENDED、Bedrock label 统一）；
    **`QueueItem` 增 `ConvertOptions` 快照**（入队时冻结，S18）；History 增加 source_version/skipped/notes/选项快照字段
    （JSON 可选字段，兼容旧记录）。
  - **4b（依赖 L3）**：`RustNative.kt` 改新 API（`ConvertOptions`/`ConvertRequest`/`ConvertReport`/
    `ConvertProgress` 接口实现）；`TwoFAApp` 初始化注入 temp/cache 路径（若 L3 扩展了 runtime）；
    `ZipQueueService` 接真进度回调（替换 0.1/0.9 硬编码），把 phase 映射为中文（extract→解压中、convert→任务名、output→打包中）；
    转换调用传入**快照后的选项**与命名模板；错误分类/日志保留。
  - **4c（UI 进度链路，必须先建）**：当前 `ZipQueueService.progress` 是 Service 内部 StateFlow、`onBind` 返回 null，
    UI 根本拿不到；`ConvertScreen` 的 0.4f 是本地假动画；且入队后不启动 Service（只有"重新转换"路径启动）。
    需要：`QueueRepository`（或新 `ProgressRepository`）持有每项进度/phase → Service 只写、UI 收集；
    入队后 `ContextCompat.startForegroundService`（Android 12+ 后台启动限制 + `POST_NOTIFICATIONS` 运行时权限）；
    通知可选 `setProgress`。
- **验收**：
  - `gradlew :app:compileDebugKotlin`（`SKIP_RUST_BUILD=true`，复用 L3 产物）通过；
  - 设置项默认值与报告 §2.4 逐项一致（代码审阅 + 单测可有）；
  - 进度回调从 Rust 到 Service 的 StateFlow 链路打通（编译期 + host 侧 fake callback 验证）。
- **回滚**：回退 L4 提交；设置迁移加 fallback 老键，避免升级丢配置。
- **风险**：生成 binding 的包名/函数名与假设不符 → 以实际生成文件为准，调整 Kotlin 封装（不手改生成文件）。

### L5 — Compose UI（选项 / 进度 / 结果 / 主题）

- **目标**：用户可见能力对齐桌面转换页（不含桌面专属与品牌）。
- **前置**：L4 通过。
- **写入范围**：`ui/screens/ConvertScreen.kt`、`ui/screens/SettingsScreen.kt`、`ui/screens/HistoryScreen.kt`、
  `ui/theme/*`、`ui/components/*`、`ui/MainActivity.kt`（必要时）。
- **步骤**：
  1. **转换选项区**：7 个开关（危险项 `delete_shaders`/`delete_cit` 用警示色 + 「会删文件」标记 + 二次确认）；
     每个开关配一句 help/hint（可直接采用桌面 `zh-CN.json` 的 `conversion.*` 文案语义，见报告 §2.4 / 前端调研 §2.6）；
     命名模板选择（默认 `[Ver][Name]` / `[Ver][Time]` / `[Name]` / 自定义）；「重置为默认」。
     `fix_alpha_layers` 按 S14 决策呈现（默认方案：不暴露死开关，或在 UI 标注「当前版本不生效」）。
  2. **版本选择器**：26.3/97 新增；状态徽章（26.2 stable、26.3 latest、1.21.11 stable、默认 recommended）；
     **选择即警示**：26.3 → MC-311807 已知问题提示（不阻塞，S2）；Bedrock 1000 → 实验性警示（沿用桌面文案语义）；
     Bedrock label 统一为「Bedrock Latest」。
  3. **进度**：进度条 + phase 文案（解压中/转换中（任务名）/打包中）+ 百分比；队列卡片显示当前项进度；
     文件较多（≥10）时提示「可能需要等待较长时间」（沿用桌面阈值文案）。
  4. **结果**：转换完成区展示 source→target、输出路径、耗时、体积变化（如有）、
     `skipped` 数量 + `notes` 可展开列表（**注意 notes ≠ 跳过**：既有跳过原因也有普通进展，不能统一加「跳过」前缀，S17）；
     成功/失败/取消三态沿用队列现有语义；失败态显示错误分类与重试。
  5. **主题**：`ThemeMode{light,dark,system}` 持久化 + 跟随系统；DarkScheme 启用并校准对比度（沿用现有设计语言，
     不照搬桌面令牌；关闭/收敛 dynamic color 以免品牌色随壁纸漂移）；首帧前同步读取主题，避免深色启动白闪。
  6. **设置页**：CONVERT 分组放转换选项入口或摘要；关于页显示 App versionName（2.1.0）+ `convertCoreVersion()` 两行；
     版本信息弹窗清理旧的「v1.2 更新…1.6-1.21.6」写死文案。
  7. **SAF / 输出**：修固定输出目录分支的 URI 判断缺陷（S19）；「打开输出目录」改为 ACTION_VIEW/分享或引导用户；
     转换选项全局化后，队列执行使用入队快照（S18）。
  8. **文案**：新增文案集中到 `strings.xml`（或 `Strings.kt` 常量对象），为后续 i18n 留口；本次不引入多语言框架。
- **验收**：
  - `gradlew :app:assembleDebug` 通过（L6 复跑确认）；
  - UI 走查清单（报告 §2.2 F2/F4/F5）逐项打勾；默认值正确；设置重启保持（代码审阅）；
  - 无生成 binding 手改、无桌面专属入口残留。
- **回滚**：回退 L5 提交（设置/数据层保留，UI 退回旧版可用）。
- **风险**：Compose 布局信息密度（7 开关 + 帮助文案）需折叠/分组；中文文案较长 → 用可展开的 help 行。

### L6 — 集成构建 + 差分验证 + APK 检查

- **目标**：从干净状态完成 Android 构建，并用桌面 CLI 做产物差分，形成验收报告。
- **前置**：L2/L3/L4/L5 全部通过。
- **写入范围**：`_sync/**`（报告/脚本/产物）；`jniLibs`、生成 binding、`app/build`（构建产物）。
- **步骤**：
  1. **干净构建**：删 `convert-core/target/android`（如需）→ `gradlew :app:assembleDebug` 全链（cargo ndk + bindgen + Kotlin）；
     记录耗时与产物。
  2. **差分**：
     - 手工 fixture（format 15，含 shaders/CIT/overrides）：转 55 / 97，开关组合：
       默认、`--cit`、`--delete-shaders`、`--no-item-definitions`、`--no-gen-metadata`；
       期望「零差异」（UImage 无关 fixture）；
     - `TapL 16x.zip`（format 1）→ 55 / 97 / 1000：按报告 S7 口径比对；把 UImage 依赖任务造成的差异
       逐条归因（白名单），其余差异必须为零或解释清楚；
     - Bedrock：97→1000（`.mcpack` + `manifest.json`）；b2j 1000→84/88/97（若 fixture 可构造）。
  3. **APK 检查**：`unzip -l` 确认 `lib/arm64-v8a/libconvert_core.so`；`aapt dump badging`（或 apkanalyzer）
     确认 `versionName=2.1.0`、`versionCode` 递增；APK 体积对比 2.0.0（记录，不设硬阈值）。
  4. **报告**：`_sync/reports/l6-verification.md`：命令、耗时、差异清单/归因、APK 证据、遗留问题。
- **验收**：全链构建 PASS；差分 0 未解释差异；APK 检查项通过；L6 报告完成。
- **回滚**：构建问题定位到代码层回退对应层；不把「产物差异未解释」带进 L7。
- **风险**：UImage 差异归因不清 → 用手工 fixture 先证明引擎一致，再用 TapL 定位差异任务；
  桌面 CLI 与 Android 引擎时间戳/顺序差异 → 差分脚本按 S7 口径而非 zip 字节。

### L7 — 文档 / 版本号 / 最终审查与提交

- **目标**：可交付状态（文档一致、版本号提升、提交干净、报告完整）。
- **前置**：L6 通过；用户确认可提交。
- **写入范围**：`2pyr-For-Android/README.md`、`AGENTS.md`、`docs/**`、`2fa-android/app/build.gradle.kts`
  （versionName 2.1.0）、`convert-core/Cargo.toml`（version 2.1.0）、`version.properties`（构建时自动）。
- **步骤**：
  1. 版本号：App `versionName = "2.1.0"`；convert-core `version = "2.1.0"`（`gen_metadata.generator_version`/Bedrock manifest 同步）；
     versionCode 保持自动 +1；README badge 更新。
  2. README/AGENTS 重写落后段落：架构图（`arom/natives/…`）、转换器数量（85→46 原生任务 + 引擎结构）、
     版本表（26 区间 + Bedrock）、构建步骤（依赖 sha2/BUILD_NUMBER/temp 注入）、
     技术债表（旧条目清理，新条目：UImage、桌面专属未同步、未实机验证）。
  3. 新增 `docs/desktop-sync-2.203.md`（同步记录：基准 commit、已同步项、未同步项、已知差异/限制）。
  4. `git status` 审查：不应包含生成产物（.so/binding/target/build 均 gitignore）；桌面仓库 `git status` 必须干净。
  5. 统一提交（Lead）：建议 `sync(desktop): port 2.203.0 M3 engine and conversion UI to Android`（可分多个逻辑提交：
     引擎/facade/UI/文档，便于回滚）。
  6. 交付摘要给用户：改了什么、验证证据、边界/未验证项、后续建议。
- **验收**：文档与代码一致；版本号正确；提交可复现构建；L6 证据归档；用户可安装 APK。
- **回滚**：`git revert` 对应提交；`pre-sync` tag 可整体回退。

---

## 3. 写入范围矩阵（防冲突）

| 路径 | L1 | L2 | L3 | L4 | L5 | L6 | L7 |
|---|---|---|---|---|---|---|---|
| `convert-core/src/**`（引擎/适配） | — | **W** | W | — | — | — | — |
| `convert-core/{Cargo.toml,build.rs}` | — | W | W | — | — | — | W |
| `convert-core/tests/**` | — | — | W | — | — | — | — |
| `2fa-android/…/kotlin/**`（非 UI 桥接） | — | — | — | **W** | — | — | — |
| `ui/**`（Compose） | — | — | — | — | **W** | — | — |
| `app/build.gradle.kts` / `version.properties` | — | — | — | — | — | R | **W** |
| 生成 binding / `jniLibs` / `app/build` | W(基线) | — | W | R | R | W | R |
| `_sync/**`（脚本/fixture/参考/报告） | **W** | R | R | R | R | **W** | R |
| `docs/**`、`README.md`、`AGENTS.md` | — | — | — | — | — | — | **W** |
| `2pyr-desktop/**` | R | R | R | R | R | R | R（全程只读） |

> W=写入方；R=只读。同一层内一个路径只有一个 W；跨层 W 交接点：L2→L3 同 owner，L4→L5 同 owner。

---

## 4. 团队分工（执行阶段）

| 角色 | 负责层 | 写入范围 | 关键产出 |
|---|---|---|---|
| Lead | L0、L7、全程集成 | `docs/**`、`README.md`、`AGENTS.md`、最终提交 | 报告、方案、集成审查、提交、交付摘要 |
| engine-core | L2、L3 | `convert-core/**` | 引擎移植 + 适配层 + facade + 进度 + 测试分诊 + 双目标构建 |
| android-ui | L4、L5 | `2fa-android/…/kotlin/**`（UI/桥接） | 设置持久化、版本表、RustNative 桥、Service 进度、Compose UI、主题 |
| verify-build | L1、L6 | `_sync/**`、构建产物目录 | 基线构建、fixtures、参考产物、差分脚本、APK 构建与检查、验收报告 |

协作约定（沿用 `_sync/CONTRACT.md`）：任务板 claim/complete；写入不越界；桌面只读；不自行 commit；
每层完成向 Lead 报「做了什么/证据/下一步」；构建命令由 verify-build 统一执行避免资源争抢。

---

## 5. 验证矩阵（层 ↔ 验证手段）

| 验证手段 | L1 | L2 | L3 | L4 | L5 | L6 | L7 |
|---|---|---|---|---|---|---|---|
| 现状 assembleDebug（基线） | **V** | — | — | — | — | — | — |
| `cargo check` | — | **V** | V | — | — | — | — |
| `cargo test --lib` | — | — | **V** | — | — | — | — |
| `cargo build --release`（host） | — | — | **V** | — | — | — | — |
| facade smoke（fixture 55/97/1000） | — | — | **V** | — | — | — | — |
| `cargo ndk` aarch64 + bindgen | — | — | **V** | — | — | V | — |
| `compileDebugKotlin` | — | — | — | **V** | V | V | — |
| `assembleDebug` 全链 | （基线） | — | — | — | — | **V** | V |
| 差分 vs 桌面 CLI（fixture） | — | — | — | — | — | **V** | — |
| 差分 vs 桌面 CLI（TapL） | — | — | — | — | — | **V** | — |
| APK 内容/版本检查 | — | — | — | — | — | **V** | V |
| 设备实测（可选，本机无 AVD/adb） | — | — | — | — | — | 可选 | 记录 |

---

## 6. 回滚策略

| 层 | 失败时回滚方式 | 备注 |
|---|---|---|
| L0 | 删除新增文档 | 无代码影响 |
| L1 | 删除 `_sync/**` 新增内容 | 无代码影响 |
| L2 | `git checkout -- convert-core` + 清新增目录（回到 pre-sync tag） | 旧引擎可从 git 历史恢复 |
| L3 | 回退 L3 提交（L2 可编译状态仍可用） | 生成 binding/.so 删除重生成 |
| L4 | 回退 L4 提交；设置迁移保留旧键 fallback | 防升级丢配置 |
| L5 | 回退 L5 提交（数据层保留） | UI 可独立回滚 |
| L6 | 定位到对应层回退；构建产物删除重来 | — |
| L7 | `git revert` 提交；`pre-sync` tag 整体回退 | 提交前需用户确认 |

**分支/提交策略**：`sync/desktop-2.203` 分支（已创建）；L1 前打 `pre-sync-2.0.0` tag；
L2/L3/L4/L5 各一个逻辑提交或一组；L7 统一整理 + 最终提交；**不 push**（由用户决定）。

---

## 7. 每层 DoD（Definition of Done）清单

- [ ] L0：报告 + 方案文档完成并经用户确认；源码零改动。
- [ ] L1：现状 assembleDebug 结果记录；fixture/参考产物/差分脚本就绪且自测通过。
- [ ] L2：host `cargo check` 0 error；`grep tauri` 为空；适配点清单全打勾。
- [ ] L3：`cargo test --lib` 绿（ignore 有理由）；facade smoke 绿；release host + cargo ndk + bindgen 绿。
- [ ] L4：`compileDebugKotlin` 绿；默认值/版本表对齐；进度链路打通。
- [ ] L5：UI 清单打勾；设置持久化；主题三档；`assembleDebug` 绿。
- [ ] L6：全链构建 PASS；差分 0 未解释差异；APK 检查通过；验收报告归档。
- [ ] L7：版本号 2.1.0；文档一致；提交干净；交付摘要完成。

---

## 8. 需要在执行前/执行中与用户确认的决策点

| # | 决策点 | 建议 | 影响 |
|---|---|---|---|
| 1 | Android `versionName` 升到 **2.1.0** 是否接受 | 建议接受（本次能力增量大） | README/badge/APK 标识 |
| 2 | **`fix_alpha_layers` 死开关**怎么处理（S14） | 建议「照桌面 no-op、UI 不暴露或标注不生效、保留字段」 | 选项契约/2pyr-gen 字段/UI |
| 3 | `2pyr-gen.json` 的 generator version/build 口径（S15） | versionName=2.1.0、build=versionCode | 产物元数据对外字段 |
| 4 | 26.3 已知问题（MC-311807）是否阻塞式确认 | 建议仅提示（Android 无桌面按钮路径） | UI 交互 |
| 5 | CIT 默认关（止血）是否在 Android 保持一致 | 建议一致（报告 §2.4） | 用户实际产物 |
| 6 | 是否接受「本次不做 UImage 打包」 | 建议接受（旧版也没有；生成类任务降级） | 部分外观生成能力 |
| 7 | 桌面专属功能（更新器/Overlay/Foray/legal）确认不纳入 | 建议确认（报告 §7.2） | 范围 |
| 8 | 提交方式：分支 `sync/desktop-2.203` 本地提交、不 push | 建议是 | 交付方式 |
| 9 | 是否允许在最终提交前跑一次完整 `assembleRelease`（debug keystore 签名） | 建议跑（只验证构建） | 验证完整性 |
| 10 | `skipped/notes` 是否本次进结果 UI（桌面没有） | 建议做（Android 增强 E2） | 新 UI 文案/结构 |
| 11 | 转换选项作用域：全局设置 + 入队快照（S18） | 建议是 | 队列数据模型 |
| 12 | 主题三档（桌面 UI 只有二分切换） | 建议做三档（Android 增强 E3） | 设置页/首帧读取 |

---

## 9. 附：执行顺序速览（TL;DR）

```
[已完成·当前] L0 报告+方案（无代码）
 → 用户确认
 → L1 基线构建 + fixture/参考/差分脚本
 → L2 引擎复制 + 适配层（cargo check）
 → L3 facade/进度/测试分诊/双目标构建 + bindgen
 → L4 Kotlin 数据层 + 桥接 + 真进度
 → L5 Compose 选项/进度/结果/主题
 → L6 全链 assembleDebug + 桌面 CLI 差分 + APK 检查
 → L7 版本号/README/AGENTS/同步记录/最终提交
```

## 10. L8（追加，2026-11-14）：按主仓库规范更新 UI 与 GitHub 仓库

> 用户要求在主仓库（桌面 2.203.0）品牌/设计/仓库规范下重做 Android UI 并发布到 GitHub。
> 详细结果见 `desktop-sync-2.203.md` §8；仓库根文件（README/legal/BUILD/CHANGELOG/workflows）同步落地。

| 子层 | 内容 | 验收 |
|---|---|---|
| L8-a 品牌 | `brand/` 唯一矢量源 + `tools/generate-android-brand.mjs` 幂等生成 drawable/adaptive/monochrome/splash；删除旧 logo | 生成器两次 0 diff；aapt2 编译通过；旧引用 0 |
| L8-b 设计令牌 | `ui/theme/{Color,Type,Motion,Oklab}.kt`：暖纸墨 30+ token、8 档字阶、圆角 12/8/6、无默认投影、动效 token/减少动效、OKLab 深色派生 | `compileDebugKotlin`；蓝/Slate 冷色 grep 0 |
| L8-c 组件/页面 | 唯一弹窗壳 `BrandDialog`/`BrandSheet`、`BrandPanel`、HSV 强调色选择器；Home/Convert/History/Settings/MainActivity/AppNavHost 重做 | 编译 + 功能回归（选项/进度/结果/SAF 逻辑不变） |
| L8-d 仓库规范 | `BUILD`/`CHANGELOG`/`legal` 中英 12 份/`.github/CONTRIBUTING|SECURITY`/`news-2.1.0.md`/`tools/set_version.py`/README；删除 `version.properties` 自增 | `set_version.py --check`（tag=Stable-2.1.0）通过 |
| L8-e 发布 | release workflow 渠道 tag、版本校验、APK+`.apk.sha256`、标题/正文（news + CI 实际 SHA-256）；`build.yml` 自检；`tools/ci/debug.keystore` 稳定签名 | CI 实跑（发布后观察）；失败则本地兜底 `gh release create` |
| L8-f 验证/发布 | verify-release 独立构建/静态检查/回归/sha256；Lead 提交、push main、tag `Stable-2.1.0`、GitHub Release | 两 APK PASS；15/15 差分不变；Release 资产/标题/notes 正确 |

回滚：L8-a/b/c 可整体 revert 到 `feat(ui)` 之前；L8-d/e 的发布由 tag 决定，撤回 Release/tag 即可。
