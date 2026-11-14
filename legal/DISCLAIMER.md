# 免责声明 / Disclaimer

**生效日期：** 2026-11-14
**适用产品：** 2-Pyramid for Android（Android 应用，含官方构建的 APK）

请在使用前仔细阅读本声明。以下内容供你了解使用风险；接受与否不影响你依 MIT 许可证取得的权利。

---

## 1. 按现状提供

本软件按「**现状**」（AS IS）提供，不附带任何明示或默示的担保，包括但不限于适销性、特定用途适用性
与不侵权。作者不保证软件功能完整、转换结果准确、不会出错或永不中断。

## 2. 转换风险由你承担

资源包版本转换涉及贴图、模型、着色器、界面等大量启发式处理，**无法保证**对所有资源包、所有目标版本
都得到可用结果。可能出现：

- 贴图错位、颜色偏差、UI 显示异常；
- 着色器在目标版本失效或导致客户端崩溃；
- 输出包结构不符合你的预期；
- **Java ↔ Bedrock（基岩）双向转换为实验性功能**，未完成且已知存在问题（选中该目标会弹出警示）。
  **请勿把它的产物用于正式发布或线上环境**，仅作测试用途；
- **OptiFine CIT 转换默认关闭**：曾有用户报告 1.20.1 → 1.21.9–1.21.10 转换后弓 / 钻石剑出现紫黑格。
  离线 codec 复核修复了确定的格式错误，但**未在实机渲染验证**；默认不生成 CIT 定义，需要时请在转换
  选项里显式打开，并先在游戏内验证。

**请在重要资源包上先备份原件，再在测试环境验证。** 因使用本软件导致的文件损坏、数据丢失、游戏存档
异常或其它损失，作者概不负责。

## 3. 与 Mojang / Microsoft 无关

2-Pyramid for Android 是**第三方社区工具**，与 Mojang Studios、Microsoft 及 Minecraft 官方
**无隶属、无合作、未获授权或认可**。Minecraft 名称与相关商标归其权利人所有，此处仅作指称用途。

你使用本软件转换、分发资源包时，须自行遵守 Minecraft EULA / 使用条款以及资源包原作者的授权。
是否合法合规由你自行判断。

应用内随包分发的 `UImage` 参照素材（见 `THIRD-PARTY-NOTICES.md`）用于「用原版风格素材生成/叠加贴图」
类任务，相关内容归 Mojang Studios / Microsoft；本项目**不主张**其著作权，仅作转换参照。若权利人认为
分发方式不妥，请通过 Issues 联系维护者，我们会评估移除。

## 4. 分发方式与更新

- 本应用通过**本仓库 GitHub Releases 侧载 APK** 分发，**没有上架 Google Play 等商店**；
- **没有应用内自动更新，也不会在后台检查更新**：升级需要你手动下载新版本 APK 覆盖安装；
- 官方 Release 附带同名 `.sha256`。**若你从非官方渠道获取 APK，其完整性与来源由你自行负责**——
  官方 APK 沿用项目一贯的 debug keystore 签名（见 `SECURITY.md`），该签名不能证明来源，
  只有官方发布页 + `.sha256` 才能。
- 覆盖安装旧版本会保留应用内设置与历史；若签名与旧版本不一致（例如你自行构建过），可能必须先卸载。

## 5. 开源组件

本软件包含若干第三方开源组件，详见 [`THIRD-PARTY-NOTICES.md`](./THIRD-PARTY-NOTICES.md)。
这些组件按其各自许可证提供，作者不对第三方组件的缺陷承担责任。

## 6. 无网络与无遥测

APK **不声明 `INTERNET` 权限**，因此应用无法访问网络：转换全程在本机完成，不上传资源包，没有账号、
没有遥测、没有崩溃自动上报，也不存在应用内更新检查。详见 [`PRIVACY.md`](./PRIVACY.md)。

## 7. 责任限制

在法律允许的最大范围内，2-Pyramid Studio 及贡献者不对任何间接、附带、特殊或后果性损失
（包括利润损失、数据丢失、业务中断）承担责任，无论基于合同、侵权或其它理论，也无论是否已被告知
可能发生此类损失。

## 8. 致谢说明

项目发展过程中曾参考或受益于社区中其它工具与作者的思路。致谢仅表示感谢，**不构成**共同著作权、
雇佣或代理关系。

---

**English summary:** The software is provided as-is without warranty. Conversion results are not guaranteed;
back up your packs and verify in a test environment. 2-Pyramid for Android is an unofficial third-party tool,
not affiliated with or endorsed by Mojang/Microsoft. It is sideloaded from GitHub Releases with no in-app
auto-update and no network permission at all. Official APKs are signed with the project's debug keystore —
that signature does not prove authenticity; verify the published `.sha256` and download only from this
repository. Bundled `UImage` reference assets belong to their respective rights holders; this project claims
no ownership and will review removal requests.

**联系方式：** https://github.com/realLivedInCorner/2-Pyramid-For-Android/issues
