# 2.1.0

> 本次主题：**换上桌面 2.203.0 的内核，并把界面重做成 Precision Geometry**。
> 转换能力第一次与桌面版对齐（26.3 目标、物品定义、CIT、UImage 素材），
> 界面则从「玻璃卡 + 蓝主题」换成暖纸墨令牌、无投影发丝线与统一弹窗壳 ——
> 看起来更安静，读起来更清楚。

**构建号：** `BUILD 21000` ｜ **tag：** `Stable-2.1.0` ｜ **最低系统：** Android 12（API 31）

---

## 一、内核：与桌面 2.203.0 同源

Android 版此前还是桌面 2.1.x 时代的 pre-M3 引擎。这一版把桌面 **2.203.0 的 M3 / A-ROM 内核整体搬了过来**
（约 3 万行 Rust，算法零重写），并保留 Android 专属适配层（运行时路径、日志、进度、UniFFI facade）：

- **26.3 / `pack_format` 97** 成为新目标，Java 目标共 26 个版本区间 + Bedrock（实验性），合计 27 项；
- **1.21.4+ 物品模型定义生成**：旧包 `models/item/*.json` 里的 `overrides` + `CustomModelData` 迁成
  `assets/<ns>/items/<name>.json`，否则自定义外观会在新版本**静默消失**；
- **OptiFine CIT 迁移**：`optifine/cit/`、`mcpatcher/cit/` 规则转成物品定义（CMD、附魔/显示上下文），
  翻不了的条件如实跳过并记录；
- **`2pyr-gen.json`**：Java 目标产物根目录写入本次转换参数（版本/BUILD、源/目标 format、六个开关、UTC 时间），
  不含任何个人信息，Minecraft 不读取，可在转换选项里关闭；
- **着色器按目标版本适配**：核心着色器成组改名、`#moj_import` → `#include`、旧 `shaders/post` 与新
  `post_effect` 按目标只留一侧；
- **性能/IO**：Java 目标产物直接写 zip（不再先解压产物再重打包）、解压并行、图片与条目表缓存；
- 桌面 2.203.0 的产物差异（例如 GUI 按钮 sprite 的描边修复）一并继承，属预期。

## 二、转换选项、进度与结果

- 转换页新增**六开关**：着色器适配、彻底删除 shaders（破坏性，二次确认）、物品定义升级、
  OptiFine CIT 转换（默认关）、彻底删除 CIT、写入 `2pyr-gen.json`；外加**输出命名模板**预设；
- 选项在**入队时快照**：排队后改设置不会影响已排队任务；
- **真进度**：解压 0–12% / 转换任务 12–90% / 打包 90–100%，带阶段文案，不再是占位动画；
- **结果与历史**：显示源→目标、输出路径、耗时、`skipped` 数量与可展开的 `notes`
  （`notes` 里既有跳过原因也有普通进展，不统一标「跳过」）；
- **SAF 输出目录修复**：固定输出目录改为保存原始 `content://` tree URI，重启后仍能写入；
- 26.3 的 MC-311807（材质包目录按钮无响应）与 Bedrock 实验性都有非阻塞警示。

## 三、界面：Precision Geometry

- 品牌资产换成 **Precision Geometry** 单色几何标识：自适应图标 / 单色图标 / Android 12 启动图标，
  全部由 `brand/` 下的矢量源经 `tools/generate-android-brand.mjs` 生成（幂等，不手改）；
- 颜色改为**暖纸墨**令牌（浅色 `#F4F2EE` 纸面 + `#14171A` 墨；深色 `#1A1815` 近黑），
  不再有蓝色主题色与 Slate 冷灰；
- **默认无投影**：卡片/列表行改为「面色 + 1dp 暖色发丝线」，层级靠字号与留白；
- 字阶 8 档、字重上限 600、正文行高 1.65、中文标题零字距；动效收敛为 160/200/240/320ms 四档，
  「减少动效」语义按状态反馈保留；
- **弹窗统一一个壳**：暖色遮罩 + surface 面板 + 发丝线 + 12dp 圆角 + 20dp 内边距，
  转换页/设置页/历史的对话框不再各写一套；
- 新增**外观 → 强调色**：默认墨色，可用 HSV 选择器自选；深色下在 OKLab 空间与白色混合 42% 提亮
  （保色相，避免给出用户没挑过的颜色）；
- 设置页「关于」显示品牌标识、versionName、BUILD 与引擎版本。

## 四、UImage 原版素材与构建链

- 桌面项目同款 **80 张 UImage 素材（0.56 MB）随 APK 打包**，首次启动解压到应用私有目录；
  依赖原版素材的生成任务（tipped arrow、smithing overlay、snow bucket 等）恢复可用；
- **构建链修复**：Gradle 调 Rust 脚本的路径（`tools/` 在仓库根而非 `2fa-android/`）、
  NDK 版本统一到 `27.3.13750724`、SDK/NDK 由配置期解析并注入 cargo-ndk 子进程环境；
- **`versionCode` 改为读取仓库根 `BUILD`**（构建只读不写），删除 `version.properties` 自动 +1 机制 ——
  同一版本在 CI 与本地出的 APK 编号终于一致。

## 五、安装 / 升级

本版通过 **GitHub Releases 侧载**分发，**没有应用内自动更新**：

1. 在 Releases 页下载 `2-Pyramid-Android-2.1.0.apk` 与同名 `.apk.sha256`；
2. 校验完整性（任选其一）：

   ```bash
   sha256sum -c 2-Pyramid-Android-2.1.0.apk.sha256
   ```

   ```powershell
   # Windows：把文件里的哈希与本机值对比
   Get-FileHash .\2-Pyramid-Android-2.1.0.apk -Algorithm SHA256
   ```

   本版 APK 的 SHA-256 由 CI 在构建后计算，并随 Release 附带同名 `.apk.sha256`、
   同时写在本 Release 正文末尾（仓库里的 news 不写死哈希，避免本地构建与 CI 构建的哈希混淆）。<!-- SHA256: CI-computed; see release body and .apk.sha256 asset -->

3. 允许「安装未知应用」后安装。覆盖安装旧版会保留设置与历史；
   **2.0.0 及更早的包用各自机器的 `~/.android/debug.keystore` 签名，与本版仓库内
   `tools/ci/debug.keystore` 不同，升级时可能需要先卸载一次**；之后各版本签名稳定、可直接覆盖安装。

> **签名说明**：官方打包使用仓库内公开的 **debug keystore**（`tools/ci/debug.keystore`，
> alias/password 为 Android 标准 debug 值），便于覆盖安装与自建包互操作；
> 它不是发行级签名密钥，**任何人都能重签**。请只从本仓库 Releases 下载，并用 `.sha256` 校验；
> 需要更强保证请按 README 从源码自行构建。

## 六、已知边界

- **未在真机验证**：构建与差分验证在 PC 完成；UImage 首次解压、TMPDIR、JNA 跨线程进度回调、
  SAF 输入/输出、256x/512x 大包内存与耗时仍需要真机确认；
- **Bedrock 双向转换是实验性**：j2b / b2j 未完成，仅用于测试；
- **OptiFine CIT 转换默认关闭**：有用户报告 1.20.1 → 1.21.9–1.21.10 弓/钻石剑紫黑，离线 codec 已修正但
  未实机复现，按产品决策默认不生成；需要的请在转换选项里显式打开并先在游戏内验证；
- **Java 目标产物会多一个 `2pyr-gen.json`**：转换参数记录，Minecraft 不读取，可关闭；
- **仅 arm64-v8a**：旧 32 位设备与模拟器不在支持范围；
- **发行版默认只写 Warn/Error 日志**：需要完整日志时在设置 → 开发者模式打开后再复现。

---

## English summary

**2.1.0** brings the Android app onto the desktop **2.203.0 M3 / A-ROM engine** (~30k lines of Rust,
ported wholesale with an Android adapter layer) and rebuilds the UI in the **Precision Geometry** design language.

- **Engine parity** — 26.3 / `pack_format` 97 target, 1.21.4+ item model definitions, OptiFine CIT migration,
  `2pyr-gen.json` conversion metadata, shader adaptation, direct-to-zip output and parallel extraction.
- **Conversion UX** — six toggles + naming template, per-queue option snapshots, real progress
  (extract / tasks / output), result view with source→target, elapsed time, `skipped` and `notes`,
  SAF output fix, MC-311807 and Bedrock warnings.
- **UI** — warm-paper ink tokens, no default shadows (1dp hairlines), 8-step type scale with 600 max weight,
  one dialog shell, and a user-selectable accent color (OKLab 42% white mix in dark mode).
- **Brand & assets** — Precision Geometry launcher/adaptive/monochrome/splash artwork generated from
  `brand/`, plus the 80 bundled UImage vanilla assets that generation tasks rely on.
- **Build** — path/NDK fixes, `versionCode` now read from the repository-root `BUILD` file
  (build never writes it), `version.properties` auto-increment removed.

**Install** — sideload `2-Pyramid-Android-2.1.0.apk` from GitHub Releases (Android 12+, arm64-v8a) and
verify it against the published `.apk.sha256`. There is **no in-app auto-update**. Release APKs are signed
with the project's debug keystore for reproducibility, not a distribution key — download only from this
repository and build from source if you need stronger guarantees.

**Known limits** — not yet validated on a physical device (UImage extraction, TMPDIR, SAF I/O, large packs);
Bedrock conversion is experimental; OptiFine CIT conversion is off by default; release builds log Warn/Error only.

---

完整变更记录见 [CHANGELOG.md](../../../CHANGELOG.md) · 法律与隐私见 [legal/EULA.md](../../../legal/EULA.md) 与 [legal/PRIVACY.md](../../../legal/PRIVACY.md)
