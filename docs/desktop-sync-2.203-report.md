# 2-Pyramid 桌面版 → Android 版同步基础报告

| 项 | 值 |
|---|---|
| 报告日期 | 2026-10-24 |
| 桌面版基准 | `2pyr-desktop/code`，git `master` HEAD `3dd0e45`（2026-10-24 23:24），`package.json` version **2.203.0**，含 `[Unreleased]` 未发版改动 |
| Android 版基准 | `2pyr-For-Android`，HEAD `519f904`（2026-09-12），versionName **2.0.0** |
| 判定同步起点 | 桌面 **2.1.2 / `0635bdd`（2026-09-12）**（证据见 §1.2） |
| 报告状态 | **只读调研，未修改任何代码**；本报告完成前不进入编码阶段 |
| 配套文档 | `docs/desktop-sync-2.203-plan.md`（分层修改流程） |
| 调研方式 | 两仓库 git 历史 + 源码通读 + 桌面预构建 CLI 实测 + 本机工具链勘察 |

> 阅读顺序建议：§0 结论 → §5 难点 → §6 特殊情况 → §7 边界 → 其余细节按需查阅。

---

## 0. 结论摘要

1. **差距的性质变了**：上次同步是「同源文件增量移植」，这次桌面用 **2.200.0 的 M3 / A-ROM 内核重写**把旧引擎
   （`hurray/` + `converters/`，正是 Android 现在这份的祖先）**整体删除**，换成了
   `arom/` + `natives/` + `pack/` + `bedrock_convert/` + `task_registry.rs` + `native_run.rs`。
   因此 **不能再沿用 cherry-pick 的旧同步方式**，只能「整体替换式移植 + Android 适配层」。
2. **规模**：自 `0635bdd` 起，桌面 `master` 共 **367 个提交**；`src-tauri/src` 变更 **210 个文件、
   +34,580 / −12,486 行**；`src/`（Vue 前端）变更 **59 个文件、+14,953 / −5,888 行**。
   需要搬进 Android 的引擎为 **94 个 `.rs` 文件 + 1 个 488 KB JSON 资源、约 30,034 行**
   （含 227 个单测、仅 6 个 `#[ignore]`）。
3. **可行性**：桌面新引擎对 UI 框架的**真实耦合点很少且明确**——日志宏、`image_utils::get_uimage_path`、
   `commands::read_config_file`（仅用于输出命名）、编译期 `BUILD_NUMBER`、临时目录清理子进程。
   其余 `tauri` 字样全部只出现在注释里。结论：**整体移植可行，属中高风险、工作量集中在
   适配与验证，而非算法重写**。
4. **Android 侧缺口分两类**：
   - Rust：旧引擎结构/产物能力全量落后（无 26.3、无 1.21.4+ 物品定义、无 CIT、无 `2pyr-gen.json`、
     无 `delete_shaders`、无结构化 skipped/notes、无进度回调）；
   - Kotlin：版本表缺 26.3；**没有任何转换选项开关**；设置纯内存不持久化；进度是假进度
     （硬编码 0.1 / 0.9）；结果无 skipped/notes；主题默认 light 且不持久化；App 版本仍 2.0.0。
5. **本次边界**：只同步「转换引擎 + 转换相关前端能力 + 构建/验证 + 文档」。
   桌面专属的 Update/Installer/MSIX/OOBE/Overlay/Foray/文件浏览器/通知/CLI 工具链**不移植**；
   品牌/logo 资产**不同步**（用户明确桌面当前仍在改 logo，且 Android 有自己的图标）；
   UImage 原版资源**不打包**（保持现有「无 UImage 则相应生成任务跳过」的降级行为）。
6. **验证策略必须换**：桌面 2.203.0 起「冻结内容基线不再作为判定，只报告」，且产物 zip 的
   **时间戳/条目顺序跨运行本就不可比**。所以验证不能「zip 逐字节比对」，改为
   **条目集合 + 解压后逐条目 SHA-256 + 压缩方法契约 + `pack.mcmeta` 语义**，
   并以桌面 2.203.0 预构建 CLI 作参考 oracle 做差分。
7. **构建链在改动前就有两处硬阻塞**（与业务代码无关）：`buildRustAndroid` 的脚本路径指向不存在的
   `2fa-android/tools/`；NDK 期望 27.2 但本机 `D:\Android\ndk` 只有 27.3 且环境变量为空。
   L1 必须先修/绕过并跑通「现状 assembleDebug」，否则后续所有构建验证都不可信（详见 §3.3 / D10）。
8. **调研产物归档**：本轮 4 份只读调研稿在 `_sync/research/`（backend-inventory / frontend-inventory /
   android-state / port-feasibility），本报告与分层方案是它们的综合；代码未做任何修改。

---

## 1. 基准与证据

### 1.1 两个仓库的当前状态

| 维度 | 桌面版 | Android 版 |
|---|---|---|
| 代码位置 | `D:\develop\2pyr\2pyr-desktop\code`（git 仓库） | `D:\develop\2pyr\2pyr-For-Android`（git 仓库） |
| 版本 | 2.203.0（BUILD 22030），另有未发版改动 | 2.0.0（versionCode 20，`version.properties` 自动 +1） |
| HEAD | `3dd0e45`（2026-10-24） | `519f904`（2026-09-12） |
| 技术栈 | Rust + Tauri 2 + Vue 3/TS；Windows 桌面 | Rust（无 Tauri）+ UniFFI 0.32 + Kotlin 2.0 + Compose M3；Android 12+（minSdk 31 / target 37，arm64-v8a） |
| 跟踪文件 | —（含 `src-tauri/target` 预构建产物，可作参考） | 168 个文件 / 932 KB；`convert-core` 113 文件 / 15,538 行；Kotlin 20 文件 / 4,077 行（不含生成 binding） |
| 构建产物 | `src-tauri/target/debug/2-pyramid.exe`（2026-10-24 构建，可跑 `--convert` CLI） | `2fa-android` Gradle 工程；Rust 由 `tools/build-rust-android.bat` 经 cargo-ndk 编进 `jniLibs` |
| 发布/CI | 双通道（GitHub Releases + MSIX） | GitHub Actions：`build.yml`（debug APK）+ `release.yml`；无设备/模拟器测试 |

### 1.2 上次同步点判定（证据链）

Android 2.0.0 的两个同步提交：

- `b343bb6`（2026-08-24）`sync(convert-core): port 2pyr-desktop Rust engine changes`：11 文件、+763/−40；
- `8754bf4`（2026-09-12）`sync(engine+ui): desktop Bedrock modularization, 26.2/Bedrock targets, release prep`：
  39 文件、+5,646/−628；
- `519f904`（2026-09-12）`release: Android 2.0.0 formal`。

内容指纹（`git hash-object` + 桌面 `git log --find-object`）：

| Android 侧文件 | blob 一致于桌面提交 | 桌面日期/版本 |
|---|---|---|
| `convert-core/src/converters/gui_surgeon.rs` | `0635bdd`（2.1.2）、`b3d7029` | 2026-09-12 / 09-11 |
| `convert-core/src/converters/cut_gui.rs` | `0635bdd`、`b3d7029` | 同上 |
| `convert-core/src/converters/bedrock/mod.rs` | `b3d7029`（skybox 全套 j2b） | 2026-09-11 |
| `convert-core/src/converters/bedrock/skybox.rs` | `b3d7029` / `329d920` | 2026-09-11 |
| `convert-core/src/converters/version_converter.rs` | 桌面同文件不一致（Android 侧做过剥离适配） | — |

**结论**：Android 2.0.0 的引擎底座 ≈ 桌面 **2.1.2（`0635bdd`，2026-09-12）**，且带有 Android 专属裁剪
（去 Tauri、UniFFI facade、runtime 注入、87 个转换器结构）。**此后的 2.1.3 → 2.203.0 全部未同步**。

### 1.3 规模统计（用于工作量判断）

| 指标 | 桌面（新引擎，待移植） | Android（旧引擎，待替换） |
|---|---|---|
| 引擎文件 | 94 个 `.rs` + 1 个 488 KB JSON（`arom/` 15、`pack/` 5、`natives/` 53、`bedrock_convert/` 11、`color/` 3、根文件 7） | 113（`converters/` 98、`hurray/` 8、其它） |
| 引擎行数 | ≈ 30,034 | ≈ 15,538（其中 converters 12,804） |
| 单测 | 227 `#[test]`（仅 6 个 `#[ignore]`；5 个需 `AROM_REAL_PACK` 真实包、1 个计时；**无外部 fixture/jar 依赖**，测试几乎整体可搬） | 88 |
| 任务数 | 46 个原生任务 + 任务元数据 88→90 项（`task_registry.rs`） | 87 个转换器模块 |
| 前端 | Vue 3，`src/` 约 2 万行（59 文件有改动） | Compose，4,077 行 / 20 文件 |
| 构建依赖 | Tauri 2、winreg、windows-sys、reqwest 等 | Rust 1.92、cargo-ndk 4.1.2、uniffi 0.32、NDK 27.2、JDK 17/21、Gradle 8.13 |

---

## 2. 桌面版变更清单（2.1.2 → HEAD）

> 归并自桌面 `git log`（367 commits）与 `CHANGELOG.md`。每项标注 **Android 影响**：
> `适用`（必须同步）/ `需适配` / `桌面专属`（不移植）。

### 2.1 后端 / 引擎（Rust）

| # | 主题 | 内容 | 关键提交/文件 | Android 影响 |
|---|---|---|---|---|
| B1 | **M3 / A-ROM 内核重写** | 删除旧 `hurray/` + `converters/`，46 个任务改为 `natives/` 原生实现，元数据独立 `task_registry.rs`，调度在 `arom/engine/`，基岩互转 `bedrock_convert/`，Java 目标改为**内存内直接序列化 zip**；产物字节目标不变（冻结基线守住）；纯转换 debug 6.01s→3.55s | `2.200.0`；`63d64ed` 合并；`arom/`、`natives/`、`native_run.rs` | **适用（本次核心）** |
| B2 | **Java 26.3（pack_format 97）** | 版本选择器新增 26.3；`adapt_java_shaders` 支持 97 边；Bedrock 中间态 88→**97**；`pack.mcmeta` 26.x 只写 `min_format`/`max_format`（如 `[97,0]`–`[97,1]`） | `2.4.0`；`conversion_maps.rs`、`pack/version_converter.rs` | 适用 |
| B3 | **1.21.4+ 物品模型定义生成** | 旧 `models/item/*.json` 的 `overrides` + `CustomModelData` → `assets/<ns>/items/*.json`；参考数据从官方 jar 提取（`natives/architect/item-definitions.json`，488 KB，560 定义/5 档）；不迁移则 1.21.4+ 自定义外观**静默消失** | `2.202.0`；`natives/architect/item_definitions.rs` | 适用 |
| B4 | **OptiFine CIT → 物品定义** | `optifine/cit` / `mcpatcher/cit` 规则转 `select` 分支；扩展附魔/显示上下文（`enchantments=`/`enchantmentIDs=`+等级、`hand=`、弓多状态贴图）；无法表达的条件**如实跳过并记录**；**自 2026-10 起默认关闭**（用户报告 1.20.1→1.21.9 弓/钻石剑紫黑，codec 已修正但未实机复现，属产品止血决策） | `2.202.0`、`9b50a7a`、`8ba63a5`、`63ba04a`；`natives/surgeon/cit_item_definitions.rs` | 适用（默认值必须对齐） |
| B5 | **2pyr-gen.json 转换参数记录** | Java 目标产物根写 `2pyr-gen.json`（默认开、可关）：生成器/version/BUILD、源/目标 pack_format、目标标签、**六个转换开关**、UTC 时间；**不含**路径/用户名/机器信息；Bedrock 目标不写；法律文档同步披露 | `42f0621`；`gen_metadata.rs` | 适用（默认开；Android 无 legal 文档 → 记为后续项） |
| B6 | **彻底删除 shaders 选项** | 用户开关（默认关）：全部任务后删除 `assets/minecraft/shaders/` + 顶层 `shaders/`；与「着色器适配」互斥语义；延迟删除在清理点统一执行 | `69d8440`；`native_run.rs` | 适用 |
| B7 | **skipped / notes 上报** | 任务跳过原因不再被丢弃：逐条 warn + 总数；产物不受影响 | `8ba63a5`；`native_run.rs`、`MixedRunReport` | 适用（Android 结果页需要展示） |
| B8 | **产物修复** | ① 物品定义 codec 错误（`enchantments` 裸字符串 → 数组等）；② 按钮 sprite 描边被「统一边厚」抹平 → 改原样输出（**改变产物字节**）；③ 内容基线不再冻结（4018 条目/指纹改为只报告）；④ `pack.mcmeta` 保留 `overlays`/`filter` 顶层字段；⑤ `ConversionMaps` 静默覆盖修复（雪球/反向 shader/铜逆变换） | `2.202.0`/`2.203.0` 段；`a690527`、`63ba04a`、`8ba63a5`、`pack/version_converter.rs` | 适用（产物行为变化，验证需以新版为准） |
| B9 | **性能/IO 基础设施** | 进度 ticker Condvar 化（消除每任务 200ms 白等）、解压并行（读线程+有界队列+8 写线程）、清理异步/后台线程/脱离进程三档、逐任务耗时画像、相位计时（open/materialize/tasks/direct/harvest/tail/output/other）、`--fast`（关 GUI sprite 手术） | `2.7.0`、`2.200.0`；`pack/io.rs`、`arom/engine/scheduler.rs`、`perf.rs` | 部分适用（进度/清理策略需按 Android 改；性能画像 UI 可后续） |
| B10 | **结构分析（只读）** | 解析 `overlays` 分层、`supported_formats`、版本折叠目录、一包多根；转换启动时写日志摘要，不改变产物；桌面有 `--analyze` CLI 与设置页入口 | `2.7.0`；`pack/analysis.rs` | 可后续（引擎里已有，Android 可先只保留日志） |
| B11 | **分辨率三路采样 / 九宫格等化** | item/block/gui 分别采样取众数倍率，修复混合分辨率包误判；GUI 九宫格缩放后四边等宽 | `2.6.0`；`scale_factor.rs`、`natives/surgeon/*` | 适用 |
| B12 | **描述性/运维类** | 构建号改为仓库 `BUILD` 固定值（不再编译自增）；日志脱敏（导出时隐藏用户名/机器名/IP/API key/路径目录）；更新源功能移除、更新校验强制（桌面专属）；Foray AI、Overlay 分享码、安装器等 | `2.201.0`、`2.5.0`、`2.6.0` | 桌面专属 / 后续按需（日志脱敏可作为 Android 日志导出的后续项） |

> **注意（B1）**：M3 是**整体替换**而不是增量——旧 `hurray/`、`converters/` 在桌面已不存在，
> 因此「把桌面改动逐条搬到 Android 旧结构」既不可行也无意义。Android 必须换成新结构。

### 2.2 前端 / 用户能力（Vue → Compose 映射）

| # | 主题 | 桌面实现 | Android 当前 | 影响/映射 |
|---|---|---|---|---|
| F1 | **版本选择器** | `src/data/minecraftVersions.ts`：26 个 Java 区间 + Bedrock=1000；26.3=97 为 latest、26.2 stable、1.21.11 stable；默认 1.21-1.21.1（34） | `AppSettings.versions` 26 项（25 Java + Bedrock），**缺 97**；26.2 标 latest | 适用：补 26.3、修状态 |
| F2 | **转换选项（7 开关）** | `ConversionPage.vue`：图层修复 / 着色器适配 / 彻底删除 shaders / 物品定义升级 / CIT 转换 / 彻底删除 CIT / 写入转换参数文件；危险项有「会删文件」标记与文案；默认值见 §2.4；localStorage + 配置文件双写。**注意**：`fix_alpha_layers` 在 M3 后实现已被删除（字段仍在、无消费者，见 S14） | **无任何转换选项** | 适用：Android 需新增设置区/转换选项面板，默认值严格对齐；死开关处理见 S14 |
| F3 | **输出模式 / 命名模板** | 跟随/固定输出路径；命名模板 `[Ver][Name]`（默认）/`[Ver][Time]`/`[Name]` 或自定义，冲突时追加 `(n)` 不覆盖；Bedrock 产物扩展名 `.mcpack` | 有跟随/固定路径；**无命名模板**，命名由 Rust 旧引擎 `[Ver][Name]` 固定 | 需适配：模板选择 + 传给引擎 |
| F4 | **进度与结果** | 进度只有阶段清单 + spinner + 文案（**桌面没有数字进度条**；`_progress_callback` 参数从未使用）；取消按钮；结果列表 + 详情弹窗（总计/成功/失败/失败原因）；**桌面结果 UI 没有 skipped/notes**（引擎有，但只逐条写日志）；失败分类/retry | 假进度（0.1/0.9）、无 phase 文案、结果信息少 | **需适配 + Android 增强**：真进度回调、skipped/notes 展示属超出桌面现状的新能力（见 §4.3） |
| F5 | **设置持久化 / 主题** | 设置存配置文件（output/naming/theme_mode/delete_shaders/convert_* /write_gen_metadata 等）；主题模式值有 light/dark/system（默认 system、配置为真源），但**UI 只有二分切换**（无三态选择器）；深色含启动页/对话框 | 设置纯内存；主题默认 light（`Theme.kt` 已有 DarkScheme 但未持久化/未跟随系统） | 适用：SharedPreferences 持久化；主题做三档（比桌面完整，见 §4.3）；1/2 两项桌面只写 localStorage，Android 需全持久化 |
| F6 | **法律/关于/日志** | legal 6 份文档、版本信息弹窗、日志窗口、日志导出脱敏、作者/致谢 | 有历史/设置/日志查看；无 legal、无日志导出 | 边界：本次不移植 legal 正文，仅在「关于/转换参数文件」处给说明；日志脱敏后续 |
| F7 | **更新器 / 安装器 / OOBE** | 独立更新页、自研安装器、MSIX、首次运行向导（5 步→3 步）、文件浏览器（实验性） | 无（Android 未来可做 APK 自更新，AGENTS v2.0 计划已记） | 桌面专属，不移植 |
| F8 | **Overlay / Foray** | 覆盖包编辑器、分享码、Foray 分析工作台（AI） | 无（AGENTS v2.0 计划列为未来） | 桌面专属，不移植（引擎模块也不搬） |
| F9 | **深色模式与视觉规范** | 2.201.0 全量令牌化 + 深色 + 对话框/动效规范 | Material3 主题 + 自定义玻璃卡；深色方案存在但未启用 | 需适配（不是像素级复刻）：沿用 Android 现有视觉语言，补深色与主题切换 |
| F10 | **品牌/logo** | 2026-10-24 起换 Precision Geometry 资产（home/splash/onboarding/installer/version dialog） | 现有 2pyr-logo.svg / mipmap 图标 | **不同步**（用户明确桌面仍在改 logo；且 Android 图标独立） |

### 2.3 版本 / 目标格式矩阵（同步后 Android 必须一致）

- Java 目标：**26 个 pack_format 区间**：1、2、3、4、5、6、7、8、9、12、13、15、18、22、32、34、42、46、55、63、64、69、75、84、88、**97（26.3，新增）**；
- Bedrock 目标：**1000**（`Bedrock Latest`，beta）；
- Bedrock 边：j2b `(84|88|97 → 1000)`、b2j `(1000 → 97|88|84)`；Java 中间态统一 **97**；
- 版本标签与 UI 状态：`26.2` = stable、`26.3` = latest、`1.21.11` = stable、`1.21-1.21.1` = recommended（默认）、Bedrock = beta；
- 命名与扩展名：Java 产物 `.zip`、Bedrock 产物 `.mcpack`（本质 zip，根目录 `manifest.json`）；
- `pack.mcmeta`：目标 ≥69（26.x）只写 `min_format`/`max_format`，旧目标写 `pack_format`；保留 `overlays`/`filter` 等顶层字段。

### 2.4 默认值与产物行为变更（必须对齐）

| 项 | 桌面 2.203.0/HEAD 默认 | Android 2.0.0 现状 |
|---|---|---|
| `fix_alpha_layers`（图层修复） | **false**；⚠️ M3 后**实现已删除**，字段仍在但无消费者（no-op） | 无此选项（旧引擎内有实现 205 行） |
| `adapt_shaders`（着色器适配） | **true**；M3 后一度退化成空开关，现为**计划级过滤**（不是任务内早退） | 无开关（旧引擎行为内建） |
| `delete_shaders`（彻底删 shaders） | **false**（破坏性） | 不存在 |
| `convert_item_definitions`（物品定义升级） | **true** | 不存在 |
| `convert_cit`（CIT 转换） | **false**（2026-10 止血；`delete_cit` 优先于它） | 不存在 |
| `delete_cit`（彻底删 CIT） | **false**（破坏性） | 不存在 |
| `write_gen_metadata`（写 2pyr-gen.json） | **true**（仅 Java 目标；Bedrock 不写） | 不存在 |
| `run_gui_surgeon` | 生产固定 true（桌面 `--fast` 仅关收尾直连 cut_gui；不暴露给 Android） | 无（旧引擎内建） |
| `native`（NativeSwitches） | 生产入口固定 `NativeSwitches::all()`；`default()` 是 `none()`，误用会直接报「no native implementation is registered」 | 不存在（迁移陷阱，见 S13） |
| 输出命名 | `[Ver][Name]` 模板；冲突追加 `(n)`；不覆盖 | 固定 `[Ver]Name` |
| Bedrock 中间态 | 97（26.3） | 88（26.2） |
| 内容基线 | 2.203.0 起「只报告不判定」 | 不存在 |
| 产物字节 | 2.203.0 相对上一版**有意改变**（按钮描边修复等） | 旧字节 |
| zip 容器时间戳 | 桌面 `zip` 默认 features（含 `time`）→ 写运行时刻 | Android `zip` 只开 `deflate` → 缺省 1980-01-01（差分时忽略，见 S16） |

---

## 3. Android 侧现状与缺口

### 3.1 Rust convert-core（`2pyr-For-Android/convert-core`）

| 模块 | 现状 | 同步后目标 |
|---|---|---|
| `converters/`（98 文件/12,804 行） | 87 个转换器 + 旧 `version_converter.rs`（`process_zip` 入口）；无 26.3/97；无物品定义/CIT/gen metadata/delete shaders；Bedrock 中间态 88 | **删除，换桌面 `natives/` 46 原生任务** |
| `hurray/` | 旧 DTD 调度器（Eraser/Architect/Surgeon + context/TexturePool 闭包） | **删除，换 `arom/engine/`（BFS 调度 + `ScheduledTask`）** |
| `invoke_conversion.rs` | 旧任务注册与闭包实现（27.8 KB） | 换成桌面空壳（59 行，仅保留调用点兼容） |
| `overlay/mod.rs` | 旧 Overlay 项目模块（14 KB），UI 未使用 | 桌面专属，**本次删除**（AGENTS 技术债记录「未来 Overlay 等价物」） |
| `lib.rs` | UniFFI facade：`init_runtime` / `convert_zip(ConvertRequest{input_zip,output_dir,target_version})` / `get_recent_logs` / dev mode / version | **重写**：`ConvertOptions`（7 开关+命名）、`ConvertProgress` callback、`ConvertReport`（source/target/skipped/notes/elapsed） |
| `runtime.rs` | `RuntimePaths{log_dir, app_data_dir, uimage_dir}` 注入 | 保留，**建议扩展 `temp_dir`/`cache_dir`**（见 S10） |
| `logger.rs` | 文件+内存双写、dev mode、`get_logs/log_file_path_str`；**已定义与桌面同名同签名的 4 个日志宏**（`log_debug/info/warn/error`） | 保留，**引擎 150 处日志调用零改动** |
| `image_utils.rs` | 仅 `paste_region` / `swap_and_mirror` | **补 `get_uimage_path()`**（基于 `runtime::uimage_dir()`；无资源时返回 Err 走 skip，不建目录） |
| `commands::config` | 不存在 | **新增最小 shim**：`AppConfig { output_naming }` + 内存态 set/get + `read_config_file()`（约 40 行，不需 serde/落盘） |
| 依赖/构建 | image(png/jpeg)、zip(deflate)、rayon、uniffi 0.32 等 | 需加 `sha2`、`flate2`；建议 `panic="unwind"`（14 处 `unwrap/expect`，见 S20）；`BUILD_NUMBER` 编译期注入（见 D3/S9） |
| 测试 | 88 | 227（仅 6 个已 ignore，无外部 fixture 依赖，见 D2） |

### 3.2 Kotlin / Compose（`2fa-android`）

| 区域 | 现状（文件） | 缺口 |
|---|---|---|
| 版本表 | `settings/AppSettings.kt` 26 项、era 分组、状态徽章 | 缺 26.3/97；26.2 状态错（应为 stable） |
| 转换链路 | SAF 选中 → `QueueRepository.enqueue` → `ZipQueueService`（前台服务 `dataSync`）→ `RustNative.convertZip(input, outputDir, targetVersion)` | 请求无选项；进度假（`_progress` 0.1/0.9）；失败无分类 |
| 设置 | `AppSettings` 的 outputMode/outputPath/devMode 均为内存 `@Volatile` | 不持久化（重启丢失），无转换开关/主题/命名 |
| 历史 | `HistoryRepository`（SharedPreferences） | 无 source_version/skipped/notes/选项快照 |
| 主题 | `Theme.kt` 有 LightScheme/DarkScheme + dynamic color，但 `darkTheme: Boolean = false` 固定 | 无 light/dark/system 持久化 |
| 结果展示 | `ConvertScreen` 的 `ConvertDetailSheet` 仅显示目标版本 + 输出路径等少量信息 | 无 skipped/notes/耗时/源版本 |
| 其他 | `SafUtils`（SAF↔cacheDir 转存）、`TwoFAApp` 注入 3 个 runtime 路径、UImage 目录=空 | 无 `temp_dir` 注入；无 UImage 资产 |

### 3.3 构建 / 工程

- `app/build.gradle.kts`：versionName 固定 `2.0.0`；versionCode 在**配置阶段**读 `version.properties` 并 +1 写回
  （构建失败/取消也会 +1）；`preBuild → buildRustAndroid → tools/build-rust-android.bat`（cargo-ndk + uniffi-bindgen），
  `SKIP_RUST_BUILD=true` 可跳过；`jniLibs/` 与生成的 `uniffi/uniffi/` 均在 `.gitignore`。
- **构建链已有两个硬阻塞（L1 必须先解决）**：
  1. `app/build.gradle.kts` 调 `"$rootDir\\tools\\build-rust-android.bat"`，而 Gradle `rootDir` = `2fa-android`，
     实际脚本在**仓库根** `tools/` → 默认 `assembleDebug` 会在 `preBuild` 直接失败（`SKIP_RUST_BUILD=true` 只能验证 Kotlin 侧）；
  2. NDK 版本/路径不一致：`gradle.properties` 与 `build-rust-android.bat` 期望 `27.2.12479018`，
     本机 `D:\Android\ndk\` 只有 `27.3.13750724`（27.2 在另一个 SDK 根 `D:\Android\Sdk\ndk\`），
     且 `ANDROID_HOME/ANDROID_NDK_HOME/ANDROID_SDK_ROOT` 均为空 → cargo-ndk 会在脚本第一步找不到 NDK。
- CI：`build.yml`（PR/main 编译 debug APK，ubuntu，SKIP_RUST_BUILD=true 后自行 cargo ndk）、
  `release.yml`；**没有 `cargo test` / `gradlew test` / 模拟器或设备测试**；本机无 AVD/adb 设备。
- 现有产物（本机采样，作为体积基线）：`jniLibs/arm64-v8a/libconvert_core.so` ≈ 4.06 MB、
  `app-debug.apk` ≈ 59.3 MB、`app-release.apk` ≈ 8.39 MB（release 走 R8 + resource shrink + debug keystore 签名）。
  新引擎（约 30k 行 + 488 KB JSON + sha2/flate2）预计 `.so` 6–9 MB、APK 10–13 MB，需实测。
- 工具链（本机已勘察）：Rust 1.92、cargo-ndk 4.1.2、uniffi-bindgen 0.32、JDK 17/21、
  `D:\Android` SDK（platform 37、build-tools 37）、Gradle wrapper 8.13（需下载）、
  `aarch64-linux-android` rust-std 已手工 stage 在 rustup 工具链里（**不要跑 `rustup target add`**，历史 tuna 镜像 404）。
- 文档：README 仍写「85 个转换模块 / 22 个版本」、徽章 `2.0.0-alpha`；AGENTS 的遗留技术债表基于旧结构
  （注意 `AGENTS.md` 被根 `.gitignore` 忽略，是本地 agent 文档）；`docs/` 为空（本次新增本报告与方案）。

---

## 4. 同步策略判定

### 4.1 候选路线

| 路线 | 做法 | 优点 | 缺点 | 结论 |
|---|---|---|---|---|
| A. 继续 cherry-pick | 把桌面 2.1.3→HEAD 的改动逐条搬进 Android 旧结构 | 改动面小 | 桌面旧结构已删除，M3 的 210 文件变更无「源」，等于手工重写两套实现，长期永久分叉 | ❌ 不可行 |
| B. 部分功能移植 | 只搬物品定义/CIT/gen metadata/26.3 等新能力到旧引擎 | 风险局部化 | 要理解旧结构与新结构差异后重写；漏掉 M3 的性能/行为修复；未来每次同步继续付账 | ❌ 不推荐 |
| **C. 整体替换式移植** | 把桌面 M3 引擎整体搬入 `convert-core`，写 Android 适配层（日志宏/image_utils/config shim/进度/runtime/BUILD_NUMBER/temp），重写 UniFFI facade 与 Kotlin 层 | 与桌面同源，算法零重写；后续同步=按结构 diff；一步到位拿到全部新能力 | 一次改动量大（≈30k 行引擎 + ~2k 行 Kotlin）；UniFFI/构建链风险集中；测试需分诊 | ✅ **推荐** |

### 4.2 推荐路线（C）的适配面（已核实）

在桌面引擎目录中用 `grep` 核实到的**真实**跨模块依赖（排除注释）只有：

| 依赖 | 位置 | Android 适配 |
|---|---|---|
| 日志宏 `crate::log_info!/log_warn!/log_debug!/log_error!` | 引擎 150 处（26 文件） | **零改动**：Android `logger.rs:177-195` 已有同名同签名宏 |
| `crate::image_utils::get_uimage_path()` | `natives/` 7 个文件 7 处 | 基于 `runtime::uimage_dir()` 实现；无 UImage 时 Err（既有 skip 逻辑），**不要创建目录** |
| `crate::image_utils::paste_region` | 10 处（`chest_region.rs`、`natives/reverse/*`） | Android 已有同签名 |
| `crate::commands::read_config_file()` | 仅 `pack/version_converter.rs:524`（输出命名模板） | 最小 `commands` shim（内存态，约 40 行） |
| `env!("BUILD_NUMBER")` | `gen_metadata.rs`、`bedrock_convert/metadata.rs`（+测试） | `build.rs` 注入（`cargo:rustc-env`），值取 Android 版本/构建号 |
| `flate2::Crc` | `arom/source.rs`（`DirSource::crc32`，被 `pack::diff` 使用） | Android `Cargo.toml` **补齐 `flate2` 直接依赖**（CONTRACT 早期只写了 sha2，已更正） |
| `std::process`（Detached 清理） | `pack/io.rs` | Android 默认走 Background 线程；**不要让 CLI 的 Detached 分支生效**；必要时改分支 |
| `tempfile` 默认临时目录 | `pack/version_converter.rs` 3 处 + `native_run.rs` 内 workdir/测试 | Android 上 `std::env::temp_dir()` 可能回落到不存在的 `/tmp`；必须注入 `TMPDIR` 到 app cache（见 S10） |
| `panic = "abort"` | `convert-core/Cargo.toml:37` | 14 处 `unwrap/expect` 一旦触发即进程秒退、Kotlin 收不到异常；建议改 `unwind`（见 S20） |
| `MixedRunOptions::default().native = NativeSwitches::none()` | `native_run.rs` | facade 必须显式 `NativeSwitches::all()`（否则驱动报「no native implementation is registered」，见 S13） |
| `rayon` 全局线程池 | `native_run.rs`、`scheduler.rs`、`pack/io.rs` | Android 无桌面的池管理；建议 `ThreadPoolBuilder::new().num_threads(n).build_global()`（见 S21） |
| `tauri` | 仅 3 处注释（`arom/error.rs`、`pack/mod.rs`、`version_converter.rs`） | 删除/改写注释 |

工程上还需处理：`Cargo.toml`（`sha2`、`flate2`、feature 收敛）、`lib.rs`/`build.rs`、测试分诊、
旧模块删除、README/AGENTS 更新。

### 4.3 与桌面现状的有意偏离（Android 增强 / 必须写进文档）

同步不等于逐像素复刻桌面。以下 4 点是**Android 会比桌面现状更完整**的地方，执行时按「Android 增强」对待，
不要反过来把桌面缺失当需求：

| # | Android 计划 | 桌面现状 | 理由 |
|---|---|---|---|
| E1 | 真进度回调（`ConvertProgress`，0–100%） | 只有阶段清单 + spinner，无数字进度；引擎 `_progress_callback` 参数从未使用 | Android AGENTS v1.1 计划早就列了「接 UniFFI callback 进度」；移动端等待体验更需要 |
| E2 | 结果区展示 `skipped` + `notes` | 引擎有 `MixedRunReport.skipped/notes`，但**只写日志**；结果弹窗只有总计/成功/失败/失败原因 | 跳过原因对用户有价值（桌面 8ba63a5 的初衷）；Android 一次做进 UI |
| E3 | 主题三档选择器（light/dark/system） | 配置支持三值，但 UI 只有二分切换（首页/引导页） | Android 设置页有天然位置；系统主题跟随是移动端惯例 |
| E4 | 7 个开关全部持久化 | `fix_alpha_layers` / `adapt_shaders` 只写 localStorage（重装即丢） | 破坏性/产物形态选项必须随备份迁移（桌面自己在 config 注释里承认 1/2 项是历史遗留） |

> 除这 4 点外，其余 UI 语义（默认值、危险标记、警示文案、结果三态）以桌面为准。
> 反过来，桌面**没有**的「数字进度条/跳过列表/三态主题」不算 Android 的同步缺口。

---

## 5. 难点（技术难点清单，执行时按此逐项核对）

> 每条：**现象 → 原因/证据 → 影响 → 对策**。

**D1. 断代：旧引擎在桌面已删除，无法「增量移植」**
- 现象：桌面 `src-tauri/src/` 里已没有 `hurray/`、`converters/`（2.200.0 删除）；Android 现有结构正是旧结构。
- 影响：任何「按 commit 搬差异」的做法都要在旧结构里重写新能力，且 M3 的行为修复（B8）无处可搬。
- 对策：整体替换（路线 C），旧引擎只作为 git 历史参考。

**D2. 227 个引擎单测的迁移（成本比预想低，但要守住口径）**
- 现象：新引擎自带 227 个 `#[test]`（32 个文件含 `#[cfg(test)]`），**仅 6 个已 `#[ignore]`**：
  5 个依赖 `AROM_REAL_PACK`/`AROM_TARGET` 真实包（未设置即打印「跳过」并 return），1 个是 IO 计时稳定性用例。
- 已核查：**没有 `tools/`、jar、fixture 文件、CWD 的硬依赖**；测试几乎全部用 `tempfile` 现场造包；
  唯一外部资源是 `natives/architect/item-definitions.json`（`include_str!`，必须同目录复制）。
  另有 1 个 `test_full_pipeline_pika5k_to_121` 硬编码 `D:\GameTime\...Pika 5K`，路径不存在会自跳过。
- 影响：直接复制后 `cargo test --lib` 应大面积可跑；风险不在「能不能编译」，而在
  **Android 侧缺少 UImage/TMPDIR 时少数断言可能不同**、以及 zip `time` feature 差异导致的时间戳断言。
- 对策：全量复制 → `cargo test --lib` 分诊 → 仅对「依赖时间戳/整体 zip 字节」的用例补 `#[ignore = "Android: zip time feature 差异"]`，
  逐条列出；已有 6 个 ignore 保持；**不删能力代码**；facade 级另加 Android 端到端 smoke test。

**D3. Tauri / 桌面耦合点散但确实存在**
- 证据：见 §4.2 表（日志宏、UImage、config、BUILD_NUMBER、清理子进程、tempfile）。
- 影响：不处理就编译不过或运行期行为不对（尤其 `BUILD_NUMBER`、temp、清理）。
- 对策：建立**单一适配层目录**（`runtime`/`logger`/`image_utils`/`commands`/`progress`），引擎代码尽量零改动，
  便于未来与桌面 diff；所有适配点在报告里登记。

**D4. UniFFI 契约升级 + 绑定重生成**
- 现象：旧 facade 只有 `target_version`；新 facade 需要 `ConvertOptions`（7 开关+命名）、`ConvertProgress`
  callback、`ConvertReport`（skipped/notes/elapsed/source_version）；Kotlin binding 由构建脚本
  `uniffi-bindgen generate --library libconvert_core.so` 生成，**不在版本控制里**。
- 影响：Rust/Kotlin 必须同步改；生成 binding 依赖 cargo-ndk 编译成功；`Box<dyn ConvertProgress>` 的
  UniFFI 0.32 支持、JNA 加载顺序、callback 线程模型（Rust 后台线程回调 Kotlin）需实测。
- 对策：先定契约（报告附录/`_sync/CONTRACT.md`），Rust 与 Kotlin 并行开发；绑定生成与编译纳入 Layer 3/4 闸门。

**D5. 进度是「新写」而不是「搬过来」**
- 现象：桌面 `process_zip_timed(_timed)` 的 `_progress_callback: Option<fn(f64,&str)>` 参数**从未被调用**；
  `ProgressTracker` 只服务于旧调度器（Bedrock 边任务），A-ROM 主驱动**不发射任何进度**；桌面 UI 也没有数字进度条。
- 影响：Android 想要真进度必须自己在「解压 / 逐任务派发 / 序列化输出」三处插桩（不能指望移植现成回调）。
- 对策：全局 `progress` sink（只读旁路）；权重：extract 0–12%、pipeline 12–90%、output 90–100%；
  `#[cfg(test)]` 下不插桩或 no-op，保证产物无变化；转换结束（含 Err）必须清理 sink；UI 中文 phase 文案见前端调研 §2.6（extract/convert/output 三段）。

**D6. UImage 缺失导致的行为降级**
- 现象：`natives/` 7 处 `get_uimage_path()`（arch_gen2/3、surgeon_early2/machinery/mid2/smithing2/survival）；
  Android `assets/UImage` 目前为空，`TwoFAApp` 只建空目录。
- 影响：依赖原版素材的生成任务（ tipped arrow、smithing overlay、snow bucket、overlay icons 等）会
  「UImage not available, skip」，与桌面参考产物不同——**这是预期差异，不是 bug**。
- 对策：保持 graceful skip；差分验证对这类任务/条目显式豁免；文档记录；UImage 打包列为未来项。

**D7. 内存与存储策略变化**
- 现象：M3 的 Java 目标改为**全内存 zip**（不再解压→重打包）；但生产入口仍会先解压源包到 temp 目录做预检/结构规整；
  Bedrock 目标仍走 workdir + repack。桌面实测真实包 18.4 MB / 4139 条目；大包/低内存 Android 设备存在峰值风险。
- 影响：内存峰值、temp 空间、OOM 风险；桌面用 `detached rmdir` 清理，Android 不适用。
- 对策：`tempfile` 落在 app cache（S10）；清理默认 Background 线程（Android 进程长命）；大包基准纳入验证；
  日志输出内存峰值（可选）。

**D8. 版本表/默认值多处重复**
- 现象：目标版本与标签在 Rust（`TARGET_LABELS`/`pack_format_label`）、桌面 TS（`minecraftVersions.ts`）、
  Android Kotlin（`AppSettings.versions`）各有一份；默认开关在桌面 UI/config/Rust 也不止一处。
- 影响：漏改一处 → UI 选得到但引擎不认/标签不一致/默认值漂移。
- 对策：本次只维护 Rust + Kotlin 两份，并加一个「Kotlin 版本表覆盖 Rust 支持列表」的对照脚本/单测；
  默认值以报告 §2.4 为唯一口径。

**D9. 验证 oracle 变了：冻结基线不可重生成，zip 像素级比对不可行**
- 现象：2.200.0 删除旧实现 → 冻结基线**无法重新生成**（只剩 `tools/arom-baseline.txt` 的冻结值与指纹用例）；
  2.203.0 起基线只报告不判定；产物 zip 的时间戳取运行时刻、条目顺序未排序（跨运行不可比）。
- 影响：不能拿 zip 字节相等当验收；必须有新的差分口径。
- 对策：**条目集合 + 解压后逐条目 SHA-256 + 压缩方法（Stored/Deflated 白名单）+ pack.mcmeta 语义**；
  以桌面 2.203.0 预构建 CLI 为参考 oracle（本机已实测可跑通 TapL 16x → 97，见 §9）。

**D10. 构建链在改动前就先坏了两处（与代码无关）**
- 现象（已实测）：① `app/build.gradle.kts` 的 `buildRustAndroid` 调 `$rootDir\tools\build-rust-android.bat`，
  但 `rootDir` 是 `2fa-android`，脚本实际在仓库根 `tools/` → `assembleDebug` 在 `preBuild` 失败；
  ② `gradle.properties`/构建脚本期望 NDK `27.2.12479018`，本机 `D:\Android\ndk` 只有 `27.3.13750724`
  （27.2 在 `D:\Android\Sdk\ndk`），且 `ANDROID_HOME/ANDROID_NDK_HOME` 为空。
- 影响：L1 的「现状可构建」基线会直接失败，必须先修/绕过后才能有可信的构建闸门；
  这两个问题也可能在 CI 上以不同形态出现。
- 对策：L1 第一步修脚本路径（`rootProject.file("../tools/...")` 或 `$rootDir/../tools/...`）、
  统一 NDK 版本/路径（装 27.2 到 `D:\Android\ndk` 或把配置改成 27.3 并设 `ANDROID_NDK_HOME`）；
  再把「现状 assembleDebug PASS」记为基线。**环境问题与代码问题必须分开定位**。

**D11. 桌面正在改 logo / 品牌，不能混入功能同步**
- 现象：桌面 HEAD 最新 3 个提交（`d62c55e`/`6d52683`/`3dd0e45`）全是品牌/图标/UI mark 落地；
  用户明确「桌面还在改 logo，暂时不会动前后端」。
- 影响：若把品牌资产与 UI 改动一起同步，Android 会做无用功且与后续桌面变化冲突。
- 对策：同步内容**排除品牌/logo**；以引擎与转换功能为界；Android 现有图标/标识不动；文档记录排除项。

**D12. 文档与版本口径**
- 现象：Android README 写「85 个转换模块 / 22 个版本」、AGENTS 的遗留技术债表基于旧结构；版本 2.0.0。
- 影响：同步后文档与实际不符，后续 agent 会被误导。
- 对策：Layer 7 更新 README/AGENTS/新增 CHANGELOG 或同步记录；versionName → 2.1.0；记录桌面基准 commit。

---

## 6. 特殊情况（执行时必须单独处理的 case）

**S1. Bedrock 中间态从 88 改为 97**
Android 旧引擎 j2b 走 Java 26.2（88）；桌面 2.4.0 起统一 97（26.3）并新增 poplar 等生成。
同步后 j2b 产物会变（Java 中间态不同），这是**预期行为变化**，不是回归；b2j 边支持 1000→84/88/97。

**S2. 26.3（97）的已知游戏问题 MC-311807**
桌面选择 26.3 时弹「已知问题确认」（材质包目录按钮无响应）。Android 侧至少要在 UI 文案中提示；
是否做阻塞式确认可先选「提示不阻塞」（Android 无桌面按钮路径，风险更低），记录决策。

**S3. CIT 默认关闭是产品止血，不是实现完成**
桌面 2.203.0 已修一个确定的 codec 错误（`enchantments` 裸字符串→数组），但用户报告的紫黑未复现，
因此默认关。Android 必须：默认 false、文案解释「待实机验证」、打开时保留源文件；不删能力。

**S4. `2pyr-gen.json` 的元数据字段是对外契约**
字段清单与桌面 legal 文档是同一份承诺（生成器/版本/BUILD/源与目标 format/标签/六开关/UTC 时间/notice；
不含路径/用户名）。Android 无 legal 文档 → 本次在 UI 中如实说明内容与可关闭；legal 文案列入后续项。
注意：`generator_build` 依赖 `BUILD_NUMBER`；`generator_version` 来自 `CARGO_PKG_VERSION`（convert-core 版本需提到 2.1.0）。

**S5. 输出命名与冲突策略**
默认模板 `[Ver][Name]`；冲突**不覆盖**、追加 `(n)`；`[Name]`/`[Ver]`/`[Time]`/`[Date]` 占位符；
Bedrock 扩展名 `.mcpack`；已有 `[Java 1.21-1.21.1]` 前缀会被剥离（防重复转换堆叠）。
Android 旧实现固定命名，需按模板重做（Rust 侧由 `commands::config` shim 提供模板）。

**S6. `pack.mcmeta` 的两种写法**
目标 ≥69（26.x）：只写 `min_format`/`max_format`（26.3 = `[97,0]`–`[97,1]`），去掉 `pack_format` 与跨大版本
`supported_formats`；旧目标写 `pack_format`；必须保留 `overlays`/`filter` 顶层字段。
验证时要按「语义」比较而不是全文件字节（目标标签/描述可能不同）。

**S7. 产物容器契约（验证口径）**
条目 = 文件 ∪ 磁盘目录条目（空目录保留）；压缩方法 = 扩展名白名单（png/ogg/jpg 等 Stored，其余 Deflated）；
时间戳 = 运行时刻（跨运行不可比）；条目顺序无契约。→ 差分必须解开后按条目比内容。

**S8. 旧格式源包的多级转换**
真实测试包 `TapL 16x.zip`（3,725 条目，`pack_format: 1`，1.6–1.8 时代）→ 97 需走多级版本边；
桌面实测：4139 条目、18.41→18.95 MB、纯转换 3.87s / 总 4.99s。Android 验证应覆盖 format 1 与 15 两种源。

**S9. 编译期 `BUILD_NUMBER` 注入**
`gen_metadata.rs` 与 `bedrock_convert/metadata.rs` 用 `env!("BUILD_NUMBER")`；Android `build.rs` 现在只做
uniffi bindgen。需注入（如 `cargo:rustc-env=BUILD_NUMBER=<versionCode 或 21000>`），否则编译失败。
同时 `CARGO_PKG_VERSION` 用于 manifest/元数据版本 → `convert-core` 版本应同步 2.1.0。

**S10. Android 的临时目录**
旧引擎也走 `tempfile`，但新引擎解压/预检仍写 temp；Android app 的 `std::env::temp_dir()` 可能是
`/data/local/tmp`（不可写/空间小），而 Kotlin 侧 cacheDir 才是安全位置。
方案：`RuntimePaths` 增加 `temp_dir`（Kotlin 传 `cacheDir`），init 时 `set_var("TMPDIR", …)` 或把引擎内
`tempdir()` 改为 `tempdir_in(runtime::temp_dir())`；同时确认 `test` 构建不依赖它。

**S11. 清理策略在 Android**
桌面 `pack/io.rs` 的 `Detached` 模式 spawn `cmd rmdir` / `rm -rf`（CLI 用）；Android 无 `cmd`，
`rm` 虽在 `/system/bin` 但不应依赖。**Android 固定 Background（线程）或 Sync**，并确认应用退出/服务被杀时
不留下大量 cache 垃圾（可加启动时 `sweep_stale_work_dirs`，引擎已有该函数）。

**S12. 旧同步模式与旧文档失效**
ANDROID AGENTS.md 的「v1.1 计划/遗留技术债」、README 的「85 converters / 22 版本」「JNA 桥接说明」
都基于旧结构；本次同步后需重写相关段落，否则后续 agent 会按旧结构操作。

**S13. `NativeSwitches` 陷阱（移植第一坑）**
`MixedRunOptions::default()` 的 `native` 是 `NativeSwitches::none()`；生产入口必须显式 `NativeSwitches::all()`，
否则驱动在派发时直接报错「no native implementation is registered」。Android facade 也必须 `all()`，
且**不要**依赖 `MixedRunOptions::default()` 直接生产运行（其余字段可 default，native 不行）。

**S14. `fix_alpha_layers` 在桌面已是「死开关」——需要明确决策**
M3 删除旧转换器树时，Alpha 图层修复的实现（`textures/alpha_layers.rs`，9,992 字节）一并删除；
`MixedRunOptions.fix_alpha_layers` 字段仍在，但**没有任何消费者**；UI/config/`2pyr-gen.json` 仍保留该字段。
Android 旧引擎反而还有实现（`converters/fix_alpha_layers_in_textures.rs`）。
三个选项：① 照桌面（保留字段、行为 no-op、UI 不暴露或标注不可用）——**建议**（与桌面同源、不动产物）；
② 保留 Android 旧实现（行为分叉，未来同步要继续维护）；③ 删字段/开关（会破坏 `2pyr-gen` 字段契约与 UniFFI 结构）。
需在 L0 报告确认时决定，并写入文档；本报告默认按 ① 估算。

**S15. `2pyr-gen.json` 的 `generator_version` / `generator_build` 在 Android 写什么**
桌面：version=`CARGO_PKG_VERSION`（2.203.0）、build=编译期 `BUILD_NUMBER`（release 数字如 `22030`，debug `dev.N`）。
Android 建议：`generator_version` = App versionName（2.1.0，同时把 convert-core Cargo version 提到 2.1.0），
`generator_build` = Android versionCode（构建时注入，如 `21000`）。**字段对外可见**，需在文档写明口径。

**S16. zip/image feature 差异带来的容器级差异**
桌面 `zip = "0.6"` 用默认 features（含 `time`）→ 写入运行时刻；Android 只开 `deflate` → 缺省 DOS 时间
（1980-01-01）。**内容一致、容器元数据不同**：差分脚本不得比较整 zip 哈希/时间戳；
`pack::diff` 已把 MTIME 定为「只报告不判定」，沿用该口径即可。
`image` 桌面默认 features（多格式），Android 只开 png+jpeg；资源包贴图以 PNG 为主，风险低，
但若引擎 `load_from_memory` 遇到其它格式（旧包里的 .tga 等）行为会不同——L2 编译后按实际用法决定是否扩 feature。

**S17. skipped/notes 的展示是新 UI，不是桌面既有结构**
桌面结果弹窗只有「总计/成功/失败/失败原因」；`skipped/notes` 只在日志。Android 要在结果区展示需要自拟文案与结构；
注意 notes 里既有「跳过原因」也有普通进展（如「写出 12 个物品定义」），**不能统一标成「跳过」**。

**S18. 选项的作用域：全局设置 vs 每项队列**
Android 现有队列是「逐项选择 targetVersion」（`QueueItem.targetVersion`），桌面是「一次选择对整批生效」。
若转换选项做成全局设置，必须在**入队那一刻快照一份 `ConvertOptions` 进 `QueueItem`**，
否则用户改设置会影响已排队/正在转换的任务，与点击时看到的不一致。

**S19. 固定输出目录的 SAF 分支疑似已有 bug**
Android `SettingsScreen` 把 tree URI 转成伪路径存进 settings，而 `ZipQueueService` 用 `startsWith("content://")`
判断分支——两者对不上，固定输出目录可能永远走不到正确分支。移植输出模式/命名时必须一并修，并补一次端到端验证。

**S20. `panic = "abort"` 与 UniFFI 的异常边界**
`convert-core/Cargo.toml` release 是 `panic = "abort"`；UniFFI 0.32 用 `catch_unwind` 把 Rust panic 转成
Kotlin 异常，在 abort 下**失效**——引擎生产代码有 14 处 `unwrap/expect`（`chest_reverse.rs` 4、
`native_run.rs` 3、`item_definitions.rs` 2，其余 5 文件各 1），任一处触发 = 进程秒退、前台服务被杀、
用户看到"应用闪退"。建议 Android release 改 `panic = "unwind"`（UniFFI 的异常边界才成立），
并保留 `runCatching` 兜底；若坚持 abort，需审计/消除所有可达 `unwrap/expect`。

**S21. rayon 全局线程池无预算**
引擎在 `run_native`/`scheduler`/`pack::io` 直接用 `rayon::par_iter`（全局池，默认吃满核心）。
桌面由命令层建池（`perf.rs` + `commands/conversion.rs`），Android 没有这一层 → 默认会占满所有核，
前台服务期间可能拖慢系统/发热。建议在 `init_runtime`（或首次转换前）用
`rayon::ThreadPoolBuilder::new().num_threads(min(4, available_parallelism))` 建全局池；
`perf.rs` 引擎侧 0 引用，可不搬（若要复用 `detect_cores` 可搬 178 行，Windows 内存探测已 cfg 门控）。

**S22. `BlobStore` 全内存、无磁盘溢写**
A-ROM 的 `BlobStore` 是 `Vec<Arc<Vec<u8>>>`，`MixedRunOptions.blob_limit = None`；Java 目标 `Output::Zip`
不再物化产物目录，峰值 ≈ 任务写出字节 + 图片解码缓存 + 条目表缓存。TapL 16x（18.4 MB/4139 条目）无压力，
但 256x/512x 大包在低端 Android 设备有 OOM 风险。可选缓解：给 `blob_limit` 设一个上限
（超限引擎是**报错**不是降级）；或在 UI 对超大包给出提示。需在 L6 用大包实测内存曲线。

**S23. 旧引擎 → 新引擎的可见行为变化（要在更新说明里写清）**
| 项 | 旧 Android 2.0.0 | 新引擎（桌面 2.203.0） |
|---|---|---|
| Bedrock Java 中间态 | 88（26.2） | **97（26.3）** |
| 26.x `pack.mcmeta` | `pack_format:34` + `supported_formats` | **只写 `min_format`/`max_format`**（26.3=`[97,0]`–`[97,1]`），保留 overlays/filter |
| 输出命名冲突 | `_1` 后缀 | **` (1)` 后缀**，模板 `[Ver][Name]` 默认 |
| 输入限额 | ZIP bomb 上限 500 MB | `SafeLimits` 1 GiB / 100k 条目 / 深度 64 |
| 转换任务实现 | 87 个旧 converter 模块 | 46 个原生任务 + 90 项元数据（行为以新引擎为准） |
| 按钮 sprite 九宫格 | 等化边厚（抹平描边） | 原样输出（修 bug，产物字节变化） |
| 日志 | release 默认 Warn | 同（Info 级任务/跳过原因默认不进日志文件） |

**S24. UI 进度链路本身是断的（不只是假值）**
`ZipQueueService.progress` 是 Service 实例内 StateFlow，`onBind` 返回 null → UI **拿不到**；
`ConvertScreen` 的 0.4f 是本地假动画；且**入队后不会自动启动 Service**（`startForegroundService`
只在"重新转换"路径调用），用户必须再点详情页的"开始转换"才消费队列（MVP 遗留缺口）。
接真进度前必须先建**全局进度状态**（`QueueRepository` 或新 `ProgressRepository`），并补入队即启动服务
（Android 12+ 后台启动限制 + POST_NOTIFICATIONS 运行时权限）。

**S25. `get_uimage_path()` 的语义要先定死**
`TwoFAApp` 启动时 `mkdirs()` 了 `filesDir/UImage`（存在但为空），Android 没有 assets/UImage。
实现若返回 `Ok(空目录)`：7 组任务会在**具体文件 not found** 处逐条 skip；若返回 `Err`：整组任务在入口 skip。
两种都能跑，但**日志条数与差分结果不同**。建议：目录存在且至少含 1 个文件才返回 `Ok`，否则 `Err`
（语义清晰、差分稳定），并在报告/README 记录「Android 无 UImage → 相关生成能力降级」。

---

## 7. 边界（本次同步的 In / Out / 条件性）

### 7.1 In scope（本次必须做）

1. **Rust 引擎整体移植**：`arom/`、`pack/`、`natives/`（含 `item-definitions.json`）、`bedrock_convert/`、
   `color/`、`chest_region.rs`、`gen_metadata.rs`、`scale_factor.rs`、`perf.rs`、`native_run.rs`、
   `task_registry.rs`、`invoke_conversion.rs`；
2. **Android 适配层**：日志宏、`image_utils`（UImage/paste）、`commands::config` shim、`progress` sink、
   runtime 扩展（temp）、`build.rs`（BUILD_NUMBER）、清理策略、Cargo 依赖；
3. **UniFFI facade 重写**：`ConvertOptions`（7 开关 + 命名）/`ConvertRequest`/`ConvertReport`（source/target/
   skipped/notes/elapsed）/`ConvertProgress` callback；保留 `init_runtime`/日志/dev mode/version；
4. **Kotlin 转换能力对齐**：版本表（+26.3/97、状态修正）、7 个开关 + 命名模板、设置持久化（SharedPreferences）、
   主题 light/dark/system + 深色配色、真进度 + phase 文案、结果区展示 skipped/notes/耗时、历史补字段；
5. **构建与验证**：host `cargo check/test/build`、cargo-ndk aarch64 + uniffi bindgen、Gradle assemble APK、
   桌面 CLI 差分（TapL 16x + 合成边界 fixture）、APK 内容检查（arm64 .so、体积）；
6. **文档**：本报告、分层修改流程、README/AGENTS 更新、同步记录（基准 commit/差异/未同步项）、版本号 2.1.0。

### 7.2 Out of scope（明确不做，写进文档）

| 项 | 理由 | 后续 |
|---|---|---|
| 更新器/安装器/MSIX/OOBE/文件浏览器/通知/CLI 工具（`--pack-diff`/`--analyze`/`--report`） | 桌面专属 | Android 未来可做 APK 自更新（AGENTS v2.0 计划） |
| Overlay 编辑器 / 分享码 / Foray（AI 分析） | 桌面专属；Android UI 无入口 | AGENTS v2.0 计划（Overlay 等价物） |
| 品牌/logo/图标/首页视觉资产 | 用户明确桌面仍在改 logo；Android 图标独立 | 等桌面品牌定稿后单独同步 |
| UImage 原版资源资产打包 | 体积/版权/必要性；旧版也没有 | 需要时打包进 assets 并首次启动解压 |
| 桌面日志脱敏 / legal 文档正文 / 邀请码/更新通道等 | 非转换核心 | 按需后续 |
| 性能档位（平衡/性能 + 硬件探测）、逐任务画像 UI | 依赖桌面 Windows API/设置体系 | 可后续以固定 rayon 预算简化实现 |
| Play Store 发布/正式签名/多 ABI | 现有 debug keystore、arm64-only 策略 | 发版阶段处理 |
| 像素级 UI 复刻（桌面 2.201.0 设计系统） | 平台不同，且用户只要求功能同步 | 按 Android 现有设计语言实现 |

### 7.3 条件性 / 待确认（执行中决定，先在文档留决策点）

- **`fix_alpha_layers` 死开关**：①照桌面 no-op（建议）②保留 Android 旧实现 ③删字段（见 S14）。
- **`2pyr-gen.json` 的 generator 版本/BUILD 口径**：versionName / versionCode（见 S15）。
- **`panic = "abort"` 是否改 `unwind`**：建议改（UniFFI 异常边界，见 S20）。
- **rayon 线程预算**：固定 4 线程 vs `min(4, cores)` vs 跟随"性能档"（建议 `min(4, cores)`，见 S21）。
- **`blob_limit` 是否设上限**：不设（当前桌面口径）vs 设一个保护值（超限报错，见 S22）。
- **`get_uimage_path()` 语义**：空目录返回 Ok vs Err（建议"至少 1 个文件才 Ok"，见 S25）。
- **26.3 已知问题确认**：阻塞式确认框 vs 仅提示（建议仅提示）。
- **清理策略**：Background vs Sync（建议 Background + 启动清理）。
- **`init_runtime` 是否加参数**：加 `temp_dir`（破坏性但一次到位）vs 保持 3 参 + 另设函数（兼容）。建议直接加。
- **Kotlin 版本表 vs Rust 支持列表**：是否加自动对照测试/脚本（建议加）。
- **历史记录/设置存储位置**：沿用 SharedPreferences（简单）vs 迁到文件配置（与桌面 config 对齐）。建议 SharedPreferences。
- **convert-core 版本号**：2.0.0 → 2.1.0（与 App 一致）。
- **image/zip feature 是否与桌面对齐**：建议 zip 保持 Android 最简（差分按内容口径）、image 以编译/实际用法为准（见 S16）。
- **`skipped/notes` UI 是否本次做**：建议做（Android 增强 E2），但必须处理「notes ≠ 跳过」的语义（S17）。
- **转换选项的作用域**：建议全局设置 + 入队快照（S18）。
- **构建链修复方式**：改 Gradle 脚本路径 vs 在 `2fa-android/tools` 放转发脚本；NDK 装 27.2 vs 统一升 27.3（L1 决定，见 D10）。

---

## 8. 可行性结论与工作量

**结论：整体替换式移植可行。** 算法代码零重写；风险集中在适配层、UniFFI/构建链、测试分诊与验证口径。

粗略工作量（执行层，可由多人并行；以「层」为粒度在 plan 文档展开）：

| 层 | 内容 | 预估 |
|---|---|---|
| L0 | 本报告 + 分层方案（当前） | 0.5–1 人日（不含本轮调研） |
| L1 | 工具链基线 + fixture/参考产物/差分脚本 | 0.5–1 人日 |
| L2 | 引擎复制 + 适配层骨架 + host 编译 | 1–2 人日 |
| L3 | 引擎收口：facade/进度/测试分诊/release 构建 | 1–2 人日 |
| L4 | Kotlin 数据层 + UniFFI 桥接 + Service 进度 | 1 人日 |
| L5 | Compose 选项/进度/结果/主题 | 1–2 人日 |
| L6 | Android 构建 + 差分验证 + APK 检查 | 1 人日 |
| L7 | 文档/收尾/提交 | 0.5 人日 |
| 合计 | | **约 6–10 人日**（并行后约 1–2 个工作日墙钟） |

风险等级：**中高**。最大风险 D4（UniFFI/绑定/构建链）与 D2（测试迁移），其余均有明确对策。

---

## 9. 验证策略（Layer 化验收的基础）

### 9.1 验证金字塔（自上而下逐层收口）

1. **编译闸门**：`cargo check`（host）→ `cargo build --release`（host）→ `cargo ndk`（aarch64）→
   `gradlew :app:compileDebugKotlin` → `gradlew :app:assembleDebug`；
2. **单测**：引擎 227 个（分诊后）+ Android facade 端到端 smoke test + Kotlin 编译期契约检查；
3. **端到端（host）**：程序化合成 fixture（format 1/15；含 textures/shaders/CIT/overrides/模型）→
   经 facade 转 55/97/1000，断言：产物存在、`pack.mcmeta` 目标格式、`items/*.json`（≥46 时）、
   `2pyr-gen.json`（开关对照）、shaders 删除、`.mcpack` + `manifest.json`；
4. **差分（host，对照桌面 CLI）**：`2-pyramid.exe --convert <fixture|TapL> --to <55|97|1000> --out ref`
   与 Android 引擎产物按 §S7 口径比对：条目集合、逐条目 SHA-256、压缩方法、mcmeta 语义；
   对手工 fixture 期望**零差异**；对 TapL 需枚举 UImage 依赖差异（预期差异白名单）；
5. **Android 构建产物检查**：APK 内 `.so`（arm64-v8a）、binding 与 Rust 一致、APK 体积、`versionName=2.1.0`；
6. **设备验证（可选，本机当前不可用）**：本机无 AVD/已连接设备；如用户提供设备可做安装 + 一次真实转换；
   否则文档明确「未实机验证」，由用户侧安装验证。

**已实测的环境证据**（本轮调研，只读）：
- 桌面预构建 CLI 可跑：`2-pyramid.exe --convert "tools/TapL 16x.zip" --to 97 --out ... --report ...`
  → 成功，4139 条目，纯转换 3.87s / 总 4.99s，产物根有 `2pyr-gen.json`，`pack.mcmeta` 为
  `min_format:[97,0]` / `max_format:[97,1]`；
- 旧 Android convert-core 在本机可 `cargo check`（host 通过，6 个 legacy warning）；
- Gradle/安卓工具链要素齐备（NDK/SDK/JDK/cargo-ndk/uniffi-bindgen/手工 stage 的 rust-std）。

### 9.2 验收的「不变量」（回归红线）

- 关闭所有新能力开关时，产物仍满足 S7 容器契约与版本边行为；
- `convert_cit=false`（默认）时**不得**生成 CIT 物品定义，但两棵 CIT 源目录**原样保留**；
- `delete_shaders/delete_cit` 只在显式开启时才删除，且删除后条目确实消失（用报告计数验证）；
- `write_gen_metadata=false` 时产物根**不得**出现 `2pyr-gen.json`；
- Bedrock 目标不写 `2pyr-gen.json`，产物扩展名 `.mcpack` 且含 `manifest.json`；
- 进度回调的插桩不改变产物（同一输入 + 同一开关，插入前后差分 0 差异）。

---

## 10. 风险矩阵

| # | 风险 | 概率 | 影响 | 缓解 | 责任层 |
|---|---|---|---|---|---|
| R1 | UniFFI callback/绑定生成失败或 Kotlin 契约对不上 | 中 | 高（阻塞） | 先定契约；Rust/Kotlin 并行但以生成 binding 为唯一真源；L3/L4 闸门 | L3/L4 |
| R2 | 引擎复制后 host 编译不过（适配点遗漏） | 中 | 中 | 适配点表（§4.2）+ 逐点清理；grep `tauri`/`env!`/`std::process` 兜底 | L2 |
| R3 | 测试大面积依赖桌面环境 | 高 | 中 | 分诊 + `#[ignore]` 逐条理由；facade smoke 补位；不删能力 | L3 |
| R4 | 差分出现无法解释的产物差异 | 中 | 高 | 先用手工 fixture（零差异目标），再上 TapL；差异按任务/条目归因；UImage 差异白名单 | L6 |
| R5 | Android 设备侧内存/临时目录/OOM | 中 | 中 | temp 注入 cache；大包基准；不追求设备实测但留验证方法 | L2/L6 |
| R6 | 构建环境问题（Gradle 下载/NDK/rust-std）耗时 | 中 | 中 | L1 先做基线构建；失败保留证据 | L1/L6 |
| R7 | 默认值/版本表三处不一致 | 中 | 中 | 单一口径表（§2.4）+ 对照检查 | L4 |
| R8 | 品牌/logo 工作与同步冲突 | 低 | 低 | 明确排除品牌资产；以引擎/功能为界 | 全程 |
| R9 | 桌面仓库被误写/污染 | 低 | 高 | 桌面仓库**只读**；执行期用 `git status` 复核 | 全程 |
| R10 | 未实机验证导致线上体验问题 | 中 | 中 | 文档标注验证边界；用户侧安装验证；默认开关保持保守 | L6/L7 |

---

## 11. 后续动作

1. 本报告确认后，按 `docs/desktop-sync-2.203-plan.md` 的 **Layer 0→7** 分层执行；
2. **基础报告完成前不修改代码**（当前状态：仅调研与文档；代码未动，Android 仓库仅切出
   `sync/desktop-2.203` 分支且工作区无代码改动）；
3. 每层结束提交验收证据（命令 + 输出 + 差异报告），层间可回滚；
4. 桌面仓库全程只读；Android 侧统一由 Lead 审查后提交。

---

## 附录 A：关键文件 / commit 索引

### 桌面（只读参考）

| 主题 | 路径 |
|---|---|
| M3 引擎 | `src-tauri/src/arom/`（15 文件）、`natives/`（53+JSON）、`pack/`（5）、`bedrock_convert/`（11）、`color/`（3） |
| 入口与调度 | `src-tauri/src/native_run.rs`、`task_registry.rs`、`pack/version_converter.rs`、`arom/engine/scheduler.rs`、`arom/engine/conversion_maps.rs` |
| 新能力 | `natives/architect/item_definitions.rs` + `item-definitions.json`、`natives/surgeon/cit_item_definitions.rs`、`gen_metadata.rs` |
| 前端 | `src/data/minecraftVersions.ts`、`src/components/ConversionPage.vue`、`src/locales/zh-CN.json`（conversion.*）、`src/composables/useTheme.ts` |
| 设计/边界文档 | `docs/compose/spec/astray-summary.md`、`item-model-definitions-1.21.4.md`、`perf-verification.md`、`docs-review-2.201.0.md` |
| CHANGELOG | `CHANGELOG.md`（2.2.0 → [Unreleased]） |
| 参考二进制 | `src-tauri/target/debug/2-pyramid.exe`（2.203.0，2026-10-24 构建） |
| 真实测试包 | `tools/TapL 16x.zip`（3,725 条目，format 1） |

### Android（本次改动目标）

| 主题 | 路径 |
|---|---|
| 旧引擎（待删） | `convert-core/src/converters/`、`hurray/`、`overlay/`、`invoke_conversion.rs` |
| facade/适配（待改） | `convert-core/src/{lib.rs,runtime.rs,logger.rs,image_utils.rs}`、`convert-core/Cargo.toml`、`build.rs` |
| Kotlin | `2fa-android/app/src/main/kotlin/com/twopyramid/twofa/**`（20 文件） |
| 构建 | `2fa-android/app/build.gradle.kts`、`2fa-android/version.properties`、`tools/build-rust-android.{bat,ps1,sh}` |
| CI | `.github/workflows/{build,release}.yml` |
| 文档（待更新） | `README.md`、`AGENTS.md`；`docs/`（本次新增报告与方案） |

### 桌面基线证据

- 基线提交：`0635bdd`（2.1.2，2026-09-12）；`b3d7029`（2026-09-11，skybox）；
- Android 同步提交：`b343bb6`（2026-08-24）、`8754bf4`（2026-09-12）、`519f904`（2.0.0 正式）；
- 自基线起桌面 `master`：**367 commits**、`src-tauri/src` 210 文件（+34,580/−12,486）、`src/` 59 文件（+14,953/−5,888）。

---

## 附录 B：术语

| 术语 | 含义 |
|---|---|
| M3 / A-ROM | 桌面的第三世代转换内核（单一对象模型 `Pack`/`Tx`/`Layer`，内存 zip，原生任务） |
| `Tx` | A-ROM 事务：任务通过事务读写包，提交成 `Layer` |
| `natives` | 46 个原生转换任务，按 eraser/architect/surgeon/reverse 分角色 |
| 版本边（edge） | `(source_format, target_format) → 任务列表`，在 `conversion_maps.rs` 声明 |
| 冻结内容基线 | 逐条目路径+长度+哈希的聚合指纹；2.203.0 起只报告不判定 |
| UImage | 桌面版内置的原版贴图资源目录（含 `_up_/` 覆盖），生成类任务需要 |
| fixture | 本次验证用程序化合成的小资源包（format 1/15，含 shaders/CIT/overrides） |
| oracle | 参考标准：本次用桌面 2.203.0 预构建 CLI 的产物作差分基准 |

---

## 附录 C：实施期新增发现（2026-10-31 回填，供后续同步参考）

> 本报告为 L0 阶段（实施前）产出；以下是实际执行到 L6 时**新发现**的问题，补充记录。

### C1. UniFFI 生成 Kotlin binding 的「嵌套注释」坑（阻断级，已修）

- **症状**：`convert_core.kt` 编译报 `Unresolved reference 'uniffiCallbackInterfaceConvertProgress'`、
  `Primary constructor of data class must only have property parameters`、`Unclosed comment` 等一串看似无关的错误。
- **根因**：Rust facade（`convert-core/src/lib.rs`）某个 `uniffi::Record` 字段的文档注释里写了
  `` `assets/<ns>/items/*.json` ``。uniffi-bindgen 把 Rust doc 注释原样写进 Kotlin KDoc；**Kotlin 支持嵌套块注释**，
  KDoc 文本中的 `/*` 会开启一个嵌套注释，导致外层 KDoc 未闭合 → 从该处到文件末尾的语法全部崩坏。
- **修复**：注释改为 `items/<name>.json`，重新生成 binding + Kotlin 编译通过。
- **规则**：facade 的 Rust 文档注释中**禁止出现 `/*` 序列**（路径通配用 `<name>` 表达）；
  **binding 生成后必须跑一次 Kotlin 编译闸门**（host bindgen 成功不代表 Kotlin 可编译）。
- **证据**：`_sync/reports/l6-verification.md` §4.1。

### C2. 构建基础设施在本机是坏的（L1 已修，摘要）

- `buildRustAndroid` 脚本路径 `$rootDir\tools\...`（rootDir 是 `2fa-android`，脚本在仓库根 `tools/`）；
- NDK 版本/路径不一致（期望 27.2，本机 `D:\Android\ndk` 只有 27.3；27.2 在另一个 SDK 根）+ `ANDROID_HOME/ANDROID_NDK_HOME` 为空；
- `build-rust-android.bat` 的 `OUT_DIR` 少一层 `app`、uniffi-bindgen 需在 crate 目录内调用、target 已存在时不应启用
  已失效的 staging wrapper、cargo-ndk 嵌套 ABI 目录需清理；
- `convert-core/.cargo/config.toml`（gitignored 本地文件）aarch64 `--sysroot` 指向已删除 staging 目录。

### C3. 验证资产位置（本次产生，未入库）

`_sync/` 下有：`fixtures/`（format 1/15 合成包）、`reference/no-uimage/` + `with-uimage/`（桌面 CLI 两套 oracle）、
`reference/host-engine/`（Android 引擎 host 差分结果 manifest）、`compare.py`、`ndk-probe/`、
`reports/l6-verification.md`。这些是**本地验证资产**，按设计不进 Android 仓库；后续需要时可重跑。

### C4. UImage 边界在实施后调整（已补齐）

L0 报告把「UImage 原版资源不打包」列为 out-of-scope；实施完成后按用户要求补齐：
桌面 `src-tauri/UImage/` 的 80 张 PNG（570,387 B）打包进 `app/src/main/assets/UImage/`，
`Application.onCreate` 首次启动解压到 `filesDir/UImage` 后再 `initRuntime`。
host 差分对 `reference/with-uimage` 的 15 个 case 复测 **15/15 PASS**，证明依赖 UImage 的生成任务与桌面一致；
代价是 release APK +0.6 MB（10.1 → 10.2 MB）。真机首次解压与像素结果仍待验证。
同一个「Kotlin 嵌套注释」坑在新增 Kotlin 注释里复现（`assets/UImage/**`），规则已扩展见 C1。
