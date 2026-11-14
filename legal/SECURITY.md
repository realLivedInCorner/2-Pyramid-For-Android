# 安全策略 / Security Policy

**生效日期：** 2026-11-14
**适用范围：** 2-Pyramid for Android 应用与官方发布渠道的 APK。

---

## 1. 支持版本

| 版本线 | 支持状态 |
|---|---|
| 当前版本线（**2.1.x**，最新正式版 Stable tag） | 安全修复优先 |
| 最新 Beta / UnStable | 尽力修复，不保证 SLA |
| 更早版本（**2.0.x 及以前**，pre-M3 内核） | 建议升级；一般不再单独出补丁 |

## 2. 如何报告漏洞

**请勿**在公开 Issue 里贴出可直接利用的细节。

推荐路径：

1. 打开仓库 [Security Advisories](https://github.com/realLivedInCorner/2-Pyramid-For-Android/security/advisories)
   提交 **Private vulnerability report**；若该入口不可用，请开带 `[SECURITY]` 前缀的 Issue，
   维护者会引导转入私下渠道；
2. 附上：受影响版本与 BUILD（设置 → 关于）、Android 版本、设备型号、复现步骤、预期与实际行为、
   PoC 或样本包（可私传）。

我们会在收到报告后 **7 个工作日内**初步回应；确认为有效漏洞后会协调修复与披露时间。

## 3. 安全设计要点（便于你评估风险）

- **无网络能力**：APK 不声明 `INTERNET` 权限，应用无法联网——不存在数据外传通道，
  也没有远程更新/远程配置面。详见 [`PRIVACY.md`](./PRIVACY.md)。
- **最小文件权限**：不申请宽泛存储权限；只通过 SAF 访问你主动选择的输入包与输出目录，
  授权范围由系统限制在你选中的位置。
- **无 root / 无系统级权限**：应用以普通应用沙箱运行，不请求 root、无障碍、设备管理等敏感权限。
- **输入解析边界**：转换引擎解析任意用户提供的 zip / PNG / JSON / 文本；对 Zip Slip、解压炸弹、
  条目数与解压体积设有上限校验（源自桌面 2.203.0 引擎）。异常输入一般表现为转换失败而非执行包内代码。
- **不执行不可信二进制**：着色器为 GLSL 源码改写，不会在设备 GPU 上编译执行不可信二进制；
  应用不加载外部代码或插件。
- **Rust panic 边界**：转换内核以 `panic = "unwind"` 编译，UniFFI 的 `catch_unwind` 会把 Rust panic
  转成 Kotlin 异常，而不是让整个前台服务进程闪退。
- **发布完整性**：Release 资产为 APK + 同名 `2-Pyramid-Android-<版本>.apk.sha256`。请核对哈希。
- **签名说明（重要）**：官方 APK 使用仓库内公开的 **debug keystore**（`tools/ci/debug.keystore`）签名
  （便于覆盖安装与自建包互操作；CI 与本地构建共用同一把密钥，跨版本签名稳定）。
  debug keystore 是 Android 开发工具公开的标准调试密钥（口令公开），**任何人都能生成同样签名的包**，
  因此签名**不能证明来源或完整性**。完整性只由「官方 Release 页 + 同名 `.sha256`」保证；
  若需要更强保证，请按 README 从源码自行构建。

## 4. 已知边界（非漏洞，但使用时请注意）

- 转换**任意**用户提供的 zip 会解析大量 PNG / JSON / 文本；超大包（256x / 512x）在低内存设备上可能
  较慢或失败，属资源限制而非安全问题；
- **未在真机全量验证**：构建与差分验证在 PC 完成，UImage 首次解压、SAF 输入/输出、JNA 跨线程进度
  回调等路径仍需要更多真机确认；发现异常请按第 2 节报告；
- **debug keystore 签名**（见上）意味着「签名不符」不能作为拒绝安装的唯一理由——覆盖安装体验优先于
  签名一致性，请务必核对哈希；
- 从非官方渠道下载的 APK 可能被重新签名或篡改，SHA 校验链路被绕过；请只信任本仓库的 GitHub Releases。

## 5. 致谢

有效安全报告会在 Release Notes 或本文件中致谢（可要求匿名）。

---

**English summary:** Report vulnerabilities privately via GitHub Security Advisories or a `[SECURITY]` issue
without exploit details. We aim to reply within 7 business days. The app has no `INTERNET` permission (no
network capability at all), requests no broad storage permissions (SAF only), and hardens zip parsing.
Release assets ship with a `.sha256`; note that official APKs are signed with the standard public debug
keystore, which does **not** prove origin — integrity comes only from the official release page plus the
published checksum, so build from source if you need stronger guarantees.
