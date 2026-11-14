# 2-Pyramid 品牌资产（Precision Geometry / Android 版）

本目录是 Android 仓库（2-Pyramid For Android）的**唯一矢量源**：Android 应用内 drawable、
启动器图标（adaptive icon / monochrome）与 Android 12 Splash 图标**全部由本目录 SVG 生成**，
不要手改生成物。

来源：桌面主仓库 `2pyr-desktop/code/brand/`（只读参考，HEAD `3dd0e45`，2.203.0）。
维护者 2026-10-24 采纳《2-Pyramid 品牌视觉方向提案》的 **Precision Geometry（精密几何）** 方向；
评审记录见桌面仓库 `brand/review-2026-10-24.md`（本目录不复制提案 PDF / 评审记录，只保留落地所需资产）。

## 方向摘要：四原则

1. **保持单色、几何** —— 只用黑色（或深色背景下的白色）线条，禁止渐变、多色与描边特效。
2. **内部斜线是结构 / 转换的暗示**，不是对几何形状的字面解释。
3. **清晰、一致优先** —— 拒绝通用渐变与主题陈词滥调；标识在任何尺寸下都必须可分辨。
4. **按真实使用尺寸验收** —— 必须在 16 / 24 / 32 / 48 / 128 / 256 px 以及启动器、Splash、
   深色底等真实场景看过再替换发行资产。

## 资产清单（矢量源）

| 文件 | 画布 / 线宽 | 用途 |
|---|---|---|
| [2-pyramid-mark.svg](./2-pyramid-mark.svg) | 512 / 12·9 | **主标识**。≥64dp 的界面、关于页、大尺寸展示 |
| [2-pyramid-mark-on-dark.svg](./2-pyramid-mark-on-dark.svg) | 512 / 12·9 | 主标识**白色版**。深色底 / 深色弹窗 |
| [2-pyramid-favicon.svg](./2-pyramid-favicon.svg) | 430 视口（40 40 430 430）/ 20·16 | **小尺寸加粗版**。≤48dp：小图标位、启动器前景 |
| [2-pyramid-favicon-white.svg](./2-pyramid-favicon-white.svg) | 430 视口 / 20·16 | 加粗版白色版，深色底上的小尺寸 |
| [2-pyramid-micro.svg](./2-pyramid-micro.svg) | 430 视口 / 30·24 | **16–24dp 光学变体**（线宽再加粗，避免亚像素发灰）；32dp 以上用 favicon 版 |
| [2-pyramid-horizontal.svg](./2-pyramid-horizontal.svg) | 960×240 / mark 0.42 缩放 | 横向组合（mark + “2-Pyramid” + 描述语，文字已转曲）；文档封面 / README 页脚等横版位 |
| [2-pyramid-horizontal-white.svg](./2-pyramid-horizontal-white.svg) | 960×240 | 横向组合白色版，深色背景用 |
| [logo-256.png](./logo-256.png) | 256×256 透明底 | README `<picture>` 亮色版（**生成物副本**，见下） |
| [logo-256-white.png](./logo-256-white.png) | 256×256 透明底 | README `<picture>` 深色版（白色线条 + 透明底） |

尺寸分档（**重要，不要跨档乱用**）：

- **16–24dp**：`micro`（加粗光学版）
- **≤48dp**：`favicon`（430 视口加粗版）
- **≥64dp**：`mark`（512 画布，透明底）
- **横向版位**：`horizontal`（含描述语，文字已转曲）
- 深色背景：用对应的 `-white` / `mark-on-dark` 变体；单色图标（monochrome）用 alpha mask。

`2-pyramid-horizontal*.svg` 与 `logo-*.png` **不参与** Android res 生成（供 README/文档用）；
`logo-*.png` 是桌面主仓库 `tools/generate-logos.mjs`（sharp）从同一套 SVG 生成的 PNG 副本，
**不要手改、不要当作矢量源**。

## Android 生成物（`res/` 下全部由脚本生成，不要手改）

| 生成物 | 源 | 规格 / 尺寸档 |
|---|---|---|
| `res/drawable/brand_mark.xml` | mark | 512 视口 / stroke 12·9 / `#111111` / 64dp 内建尺寸。**≥64dp 场景**（Home 品牌块、关于页） |
| `res/drawable/brand_mark_white.xml` | mark-on-dark | 同上白色（`#FFFFFF`）。深色底 / 深色弹窗 |
| `res/drawable/brand_favicon.xml` | favicon | 430 视口 / stroke 20·16 / `#111111` / 32dp。**≤48dp 场景**（OOBE/首页副标识等） |
| `res/drawable/brand_favicon_white.xml` | favicon-white | 同上白色。深色底小尺寸 |
| `res/drawable/brand_micro.xml` | micro | 430 视口 / stroke 30·24 / 24dp。**16–24dp 场景** |
| `res/drawable/ic_launcher_foreground.xml` | favicon | **108dp 画布**；加粗稿墨迹 bbox 缩放为 **62dp**（≤66dp 中心安全区，留 4dp 余量）并居中。**不要用 512 mark 直接缩**（48px 启动器下线会消失） |
| `res/drawable/ic_launcher_monochrome.xml` | favicon | 同前景几何的 **alpha mask**（黑路径，颜色不参与渲染；Android 13 单色图标） |
| `res/drawable/brand_splash.xml` | mark | **288dp 画布**；512 视口映射到 190dp 内容区（图标安全区 ≤192dp），日间黑 mark |
| `res/drawable-night/brand_splash.xml` | mark-on-dark | 同上几何，夜间白 mark（资源名不变，夜间自动覆盖） |
| `res/mipmap-anydpi-v26/ic_launcher.xml` / `ic_launcher_round.xml` | — | adaptive icon：`background=@color/launcher_background`（**#FFFFFF 白底板**）+ foreground + monochrome |

配套平台资源（手写，不属生成物）：`values/colors.xml` 的 `launcher_background=#FFFFFF` 与
`launch_background_light/dark=#F4F2EE/#1A1815`；`values/themes.xml` + `values-night/themes.xml` 的
Android 12 SplashScreen（`android:windowSplashScreenBackground` / `android:windowSplashScreenAnimatedIcon`）。

启动图标层级规则（主仓库评审结论）：**白底圆角板 + 尺寸分档 artwork**；adaptive icon 前景用
**favicon 加粗稿**放在 108dp 画布中心安全区（内容 ≤66dp）；`monochrome` 用同一 artwork。深色底用白色版。

## 生成 / 校验命令

```powershell
cd D:\develop\2pyr\2pyr-For-Android

# 生成 / 刷新 res 下全部品牌 XML（纯 Node、无第三方依赖、幂等）
node tools/generate-android-brand.mjs

# 只校验不写盘（CI / 验收用；有不一致时退出码 1）
node tools/generate-android-brand.mjs --check
```

- 脚本读取 `brand/*.svg` 的 `viewBox`、`path d`、`stroke-width`，输出顺序固定、不含时间戳，
  **连续运行两次输出逐字节相同**（第二次运行会显示「未变化」）。
- 内置自检：11 个生成文件存在、每个 vector 的 path 数（2 条：外三角 + 内斜线）、`pathData`
  与 SVG 的 `d` 逐字一致、viewport 正确、adaptive icon 的 background/foreground/monochrome 接线正确。
- 生成物提交进仓库，便于 review 与构建；但**永远以本目录 SVG 为准**。

## PNG 来源（README `<picture>` 用）

`logo-256.png` / `logo-256-white.png` 复制自桌面主仓库的**已生成物**：

- `2pyr-desktop/code/src/assets/logo-256.png`（mark，黑色，透明底）
- `2pyr-desktop/code/src/assets/logo-256-white.png`（mark-on-dark，白色，透明底）

由桌面 `tools/generate-logos.mjs` 用 sharp 从 `2pyr-desktop/code/brand/` 的同名 SVG 生成（桌面 HEAD `3dd0e45`，
2.203.0）；Android 应用内**不使用这两张 PNG**（应用内一律用上面的 vector drawable），它们只给 GitHub README
的 `<picture>` 亮/暗两版用。桌面 PNG 若重新生成，按同样路径重新复制即可（保持透明底）。

## 落地规则

1. **改品牌 = 改 SVG + 重跑脚本**：本目录 SVG 与桌面 `2pyr-desktop/code/brand/` 保持同源；
   Android 侧只在下游生成器里做尺寸分档换算（见 `tools/generate-android-brand.mjs` 顶部注释）。
2. **不要手改 `res/drawable/brand_*` / `ic_launcher_*` / `res/drawable-night/brand_splash.xml`**，
   重跑生成器会覆盖。
3. **不要跨档**：16–24dp 用 micro，≤48dp 用 favicon，≥64dp 用 mark；启动器前景用 favicon 加粗稿，
   不要用 512 mark 直接缩放。
4. **深色用白色版**；单色图标用 alpha mask；不要为每个主题存一份无必要的位图。
5. **小尺寸验收**：16 / 24 / 32 / 48 / 128 / 256 px 在浅底与深底上都必须可辨认；改动 SVG 后重跑生成器，
   并检查启动器图标（adaptive icon 圆形/方形遮罩）与 Splash 在日/夜两套下的观感。
6. 桌面仓库全程只读参考；Android 侧新增资产只走本目录 + 生成器。
