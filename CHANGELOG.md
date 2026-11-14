## [Unreleased]

> 2.1.0 之后的改动记录在这里。发布前把相关条目移入对应版本段，并把 `[Unreleased]` 留在顶部。

## [2.1.0] - 2026-11-14（BUILD 21000）

> 本次主题：**内核换代 + 界面重做**。转换内核从桌面 2.1.x 时代的 pre-M3 实现整体替换为桌面
> **2.203.0 的 M3 / A-ROM 引擎**（约 3 万行 Rust，算法零重写），转换能力第一次与桌面版对齐；
> 界面按 **Precision Geometry** 设计语言重做（暖纸墨令牌、无投影、统一弹窗壳、可选强调色）。
> 本版同时把仓库层规范（BUILD / legal / 发布流程 / README）对齐桌面主仓库。
>
> 版本号说明：minor 从 2.0 提到 2.1，因为在 Android 侧这是一次**内核世代更替**，沿用 2.0.x
> 会让它看起来像一次普通的功能增量。转换器行为以桌面 2.203.0 为准，产物差异（例如 GUI 按钮
> sprite 描边修复）属预期。

### Added

- **桌面 2.203.0 M3 / A-ROM 引擎整体同步**：对象模型（`Pack` / `Tx` / `Layer` / 视图 / 序列化 / 限额）、
  BFS 调度与版本边、46 个原生任务、生产编排、Bedrock 互转、`2pyr-gen.json` 生成、结构分析与产物差分
  全部与桌面同源；Android 只保留一层薄适配（运行时路径、日志宏、进度、UniFFI facade）。
  - **Java 26.3（`pack_format` 97）成为新目标**：Java 目标 26 个版本区间 + Bedrock（实验性）= 27 项；
    `pack.mcmeta` 目标 ≥69 只写 `min_format`/`max_format`（26.3 为 `[97,0]`–`[97,1]`），保留
    `overlays` / `filter` 顶层字段；Bedrock 中间态同步升到 97。
  - **1.21.4+ 物品模型定义生成**（`generate_item_definitions`，版本边 `(42,46)`）：把旧包
    `models/item/*.json` 的 `overrides` + `CustomModelData` 迁成 `assets/<ns>/items/<name>.json`。
    不做这一步，旧包转新版本后自定义外观会**静默消失**（无报错、无警告）。参考数据来自官方客户端
    jar 提取，不是手抄。
  - **OptiFine CIT 迁移**（`generate_cit_item_definitions`，版本边 `(46,55)`）：`optifine/cit/**`
    与 `mcpatcher/cit/**` 的规则转成物品定义里的分支，支持 CMD、附魔名 + 等级（目标 ≥ 1.21.5）、
    `hand=` 显示上下文与弓的多状态贴图；翻不了的条件如实跳过并记录（把精确条件放宽成近似条件比
    不转换更糟）。**默认关闭**，见 Changed。
  - **`2pyr-gen.json` 转换参数记录**（默认开、仅 Java 目标）：产物包根目录写入生成器名/版本/BUILD、
    源与目标 `pack_format`、本次六个开关与 UTC 时间；**不含**路径、用户名、机器名、IP 等个人信息，
    Minecraft 客户端不读取，删除不影响包的使用。转换选项可关闭，`legal/EULA.md` 与
    `legal/PRIVACY.md`（中英）已披露。
  - **`skipped` / `notes` 上报**：桌面只写日志，Android 升级为 facade 返回值字段，结果页可见。
  - **M3 性能与 IO**：Java 目标产物直接序列化成 zip（不再先物化到临时目录再重新打包）、解压并行化、
    图片解码与条目表缓存、后台清理三档；debug 口径纯转换从 6.01s 降到 3.55s（桌面实测口径）。
- **转换选项（Compose 侧）**：六个暴露开关 `adapt_shaders` / `delete_shaders` /
  `convert_item_definitions` / `convert_cit` / `delete_cit` / `write_gen_metadata`，外加输出命名
  模板预设（`[Ver][Name]` / `[Ver][Time]` / `[Name]` / 自定义）与「恢复默认」；默认值严格对齐桌面。
  破坏性开关（删 shaders / 删 CIT）带「会删文件」标记与二次确认；`fix_alpha_layers` 是桌面死开关，
  UI 不暴露、字段保留以维持 `ConvertOptions` 契约。
- **真进度链路**：全局 `ProgressRepository`（Service 只写、UI 收集），引擎级回调
  extract 0–12% / 转换任务 12–90% / 打包 90–100%，单调且带阶段文案；通知栏显示队列进度。
- **结果与历史增强**：结果面板展示源→目标、输出路径、耗时、`skipped` 数量与可展开的 `notes`
  （`notes` 既有跳过原因也有普通进展，**不统一标「跳过」**）；历史记录兼容旧 JSON（`opt*` 读取），
  新增 `skipped` / `notes` 字段。
- **主题三档与「外观」设置**：light / dark / system 持久化（关闭 dynamic color，避免品牌色漂移）；
  新增强调色选择（默认墨色，HSV 选择器；深色下在 OKLab 空间与白色混合 42% 提亮以保色相）。
- **UImage 原版素材随 APK 打包**：桌面项目同款 80 张 PNG（16 个分类 / 570,387 B）以
  `assets/UImage/` 内置于 APK，首次启动解压到应用私有目录（`filesDir/UImage`，带素材版本标记，
  升级素材只重解压一次）；依赖原版素材的生成任务（tipped arrow、smithing overlay、snow bucket、
  overlay icons、brewing stand、villager、horse 等 7 组）恢复可用，APK 体积 +0.6 MB。
- **`brand/` 品牌资产与生成器**：Precision Geometry 的 mark / favicon / micro / horizontal 矢量源
  入库，`tools/generate-android-brand.mjs` 幂等生成应用内 drawable、adaptive icon 前景 +
  monochrome、Android 12 启动图标（尺寸分档：16–24 micro / ≤48 favicon / ≥64 mark；启动图标用
  favicon 加粗稿而不是 512 mark 直接缩，否则 48px 下线会消失）；`brand/logo-256[-white].png` 供
  README 的 `<picture>` 按 GitHub 深浅主题自动切换。
- **仓库规范文件**：`BUILD`（构建号）、`CHANGELOG.md`、`legal/`（中英各 6 份：CONTRIBUTING /
  DISCLAIMER / EULA / PRIVACY / SECURITY / THIRD-PARTY-NOTICES）、`.github/CONTRIBUTING.md`、
  `.github/SECURITY.md`、`docs/compose/releases/news-<版本>.md`、`tools/set_version.py`。
- **渠道化发布流程**：release workflow 改为 tag 前缀触发（`Stable-*` / `Safe-*` / `Beta-*` /
  `UnStable-*`），校验版本一致性、产出 APK + 同名 `.sha256`，Release 标题
  `2-Pyramid for Android {Stable|Beta} <版本>-BUILD<构建号>`，Beta/UnStable 标 prerelease、
  Stable/Safe 标 latest；`build.yml` 增加无 tag 的版本一致性自检。
- **仓库内发行签名 keystore**：新增 `tools/ci/debug.keystore`（Android 标准 debug keystore：
  alias `androiddebugkey`、口令 `android`、`CN=Android Debug`）。CI 与本地 release 构建共用同一把
  密钥 → 跨版本签名稳定、用户可直接覆盖安装；它仍是**公开的调试级密钥**（任何人都能重签），
  完整性以 Release 附带的 `.sha256` 为准。取代旧的「CI 每次现场生成新 debug keystore」
  （`tools/setup-ci-keystore.sh` 已删除，该做法会导致每次发布签名不同、无法覆盖升级）。
  Release 正文 = 仓库内 news 文件 + **本次 CI 构建产物的实际 SHA-256**（news 不再写死哈希）。
- **CI 工具链安装修正**：`android-actions/setup-android@v3` 没有 `ndk-version`/`sdk-version` 输入
  （写了会被忽略），改为在 `packages` 中显式安装 `platforms;android-37.0`、`build-tools;37.0.0`、
  `ndk;27.3.13750724`；`tools/build-rust-android.sh` 在 crate 目录内调用 `uniffi-bindgen`
  （0.32 `--library` 模式仍会跑 `cargo metadata`，仓库根 cwd 会失败）。

### Changed

- **转换内核整体替换**：删除 pre-M3 的 `converters/`、`hurray/`、`overlay/`、旧 `invoke_conversion.rs`、
  旧 `color_utils.rs`，`convert-core/src/` 现在只有一套执行路径（`arom/` + `natives/` + `pack/` +
  `bedrock_convert/` + `native_run.rs`）。适配层（`runtime` / `logger` / `image_utils` / `commands` /
  `progress`）与引擎目录分离，后续与桌面同步改为**目录级 diff**，不再逐 commit cherry-pick。
- **界面按 Precision Geometry 重做**：颜色改为暖纸墨令牌（浅色 `#F4F2EE` 纸面 + `#14171A` 墨，
  深色 `#1A1815` 近黑）；**默认无投影**，卡片/列表行改为「面色 + 1dp 暖色发丝线」，层级靠字号与留白；
  字阶 8 档（24 / 18 / 15 / 14 / 13 / 12 / 11.5 / 10.5sp）、字重上限 600、正文行高 1.65、中文标题零字距；
  动效收敛为 160 / 200 / 240 / 320ms 四档并支持「减少动效」语义；弹窗统一为一个壳
  （暖色遮罩 + surface 面板 + 1dp 发丝线 + 12dp 圆角 + 20dp 内边距），转换页 / 设置页 / 历史不再
  各写一套；品牌标识改用 `brand_mark` 系列 drawable。
  - 全应用移除蓝色主题色（`#007BFF` / `BlueAccent`）与 Slate 冷灰；语义色统一为
    danger / warn / ok。
  - 26.3 的 MC-311807（材质包目录按钮无响应）与 Bedrock 实验性提示改为 warn 色非阻塞警示。
- **OptiFine CIT 转换默认关闭**（`convert_cit`：true → false；设置缺省与 UI 默认四处同步）：
  有用户报告 1.20.1 布吉岛包转 1.21.9–1.21.10 后弓与钻石剑为紫黑格。2.203.0 已修掉一个**确定**的
  codec 错误（`enchantments` 裸字符串 → 数组），离线复核显示修复后的形状在补齐附魔注册表后解析通过，
  但本环境没有实机渲染验证、该报告原因尚未复现，故按产品决策先默认关 —— 关闭只跳过转换，
  `optifine/cit/**` 源文件原样保留（OptiFine 客户端仍会读取）。
- **版本号/构建号机制对齐桌面**：`versionCode` 改为读取仓库根 `BUILD`（**构建只读不写**），
  删除 `version.properties` 自动 +1 —— 此前同一版本在 CI 与本地构建会得到不同编号，
  无法回答「这个 APK 对应哪次构建」。要推进编号用 `tools/set_version.py --build N`，与版本号一样是
  显式动作；版本号一致性由 `tools/set_version.py --check` 统一校验（build.gradle / README 徽章 /
  `docs/compose/releases/news-<版本>.md` / tag）。
- **发版流程改为显式 tag 动作**：版本号、BUILD、CHANGELOG、发版说明四者就绪后打渠道 tag；release
  workflow 不再回写任何版本文件，也不再依赖 auto release notes（正文固定使用 news 文件 +
  本次 CI 构建产物的 SHA-256）。
- **README 重写**：顶部 `<picture>` 按 GitHub 深浅主题切品牌 PNG；徽章对齐当前版本与工具链；
  补 CHANGELOG / legal / BUILD 说明；功能、架构、限制按本版事实更新。
- `legal/` 从桌面版结构重写为 **Android 事实**：APK / GitHub Releases 侧载分发、无应用内自动更新、
  无网络权限（无联网 / 无遥测）、权限（前台服务 + 通知、SAF 选择文件与输出目录）、完全本地处理、
  UImage 素材来源、`2pyr-gen.json` 披露、日志与缓存位置、debug keystore 签名说明与自行构建说明、
  第三方组件（Rust crates / Kotlin / Compose / JNA / Coroutines）与 MIT 许可。
  **不再包含**桌面专属的 Windows 安装器 / 更新器 / MSIX / Foray 条款。

### Fixed

- **Gradle 调 Rust 脚本的路径错误**：`buildRustAndroid` 原用 `$rootDir\tools\build-rust-android.bat`，
  而 Gradle `rootDir` 是 `2fa-android/`、脚本实际在仓库根 `tools/` —— 默认 `assembleDebug` 在
  `preBuild` 直接失败。改为 `rootProject.file("../tools/build-rust-android.bat")`。
- **NDK 版本不一致**：`gradle.properties` 统一到本机/CI 实际安装的 **27.3.13750724**（原
  27.2.12479018 在机器上不存在）；Gradle 配置期解析 SDK/NDK 并显式注入 `ANDROID_HOME` /
  `ANDROID_SDK_ROOT` / `ANDROID_NDK_HOME` 给 cargo-ndk 子进程（本机这些环境变量为空也能构建）。
- **`tools/build-rust-android.bat` 健壮性**：`OUT_DIR` 少一层 `app`、`uniffi-bindgen` 需在 crate 目录内
  运行（0.32 的 `--library` 模式仍会跑 `cargo metadata`）、target 已存在时不再重复 `rustup target add`
  并禁用已失效的 staging wrapper、清理 cargo-ndk 产出的嵌套 ABI 目录。
- **`.cargo/config.toml` 失效 `--sysroot` 覆盖**：本地 gitignored 配置里指向已删除的 staging 目录，
  会导致 `E0463`；已移除（不加配置即用 rustup 自带的 target）。
- **SAF 固定输出目录失效**：设置里保存的是 `DocumentFile` tree URI 的派生值，重启后与原始
  `content://` URI 不一致导致写入失败；改为保存原始 tree URI 并在使用时持久化权限。
- **UniFFI binding 的 Kotlin 嵌套注释坑**：facade 的 Rust 文档注释里出现 `/*` 序列（如
  `assets/<ns>/items/*.json`）被原样写进 Kotlin KDoc，Kotlin 支持嵌套块注释 → 生成的
  `convert_core.kt` 语法崩坏。已改为 `items/<name>.json` 并在 CONTRIBUTING 里写成硬性规则；
  Kotlin 源码/注释同样禁止该序列（`UImageAssets.kt` 也踩过一次）。
- **`26.x` 旧目标写 `pack.mcmeta` 丢顶层字段**、**`ConversionMaps` 静默覆盖**、**分辨率三路采样**、
  **九宫格边框等厚**、**按钮 sprite 描边被抹平**等桌面 2.203.0 修复随内核同步一并生效。

### Removed

- `convert-core/src/converters/`（pre-M3 旧转换器树）、`hurray/`（旧引擎）、`overlay/`、
  旧 `invoke_conversion.rs` 与旧 `color_utils.rs`。
- `2fa-android/version.properties` 与构建期自动 +1 机制；release workflow 里的
  「把 version.properties 写回 main」步骤与 `softprops/action-gh-release`（改用 `gh release create`）。
- 旧的 PI 前品牌素材与旧 logo 引用（`res/drawable/ic_pyramid_*`、根 `2pyr-logo.svg`），
  统一由 `brand/` 生成物替代。
- 应用内蓝色主题色与 Slate 冷灰、旧玻璃卡投影样式、各页面各自实现的弹窗壳。

---

## 发布说明

- 本版发版说明（Release 正文）：[`docs/compose/releases/news-2.1.0.md`](docs/compose/releases/news-2.1.0.md)
- 与桌面 2.203.0 的同步记录（基准、清单、验证证据、已知差异）：
  [`docs/desktop-sync-2.203.md`](docs/desktop-sync-2.203.md)、
  [`docs/desktop-sync-2.203-report.md`](docs/desktop-sync-2.203-report.md)
- 法律文本：[`legal/`](legal/)（English: [`legal/en/`](legal/en/)）· 安全：[`legal/SECURITY.md`](legal/SECURITY.md)
  · 贡献：[`legal/CONTRIBUTING.md`](legal/CONTRIBUTING.md)
