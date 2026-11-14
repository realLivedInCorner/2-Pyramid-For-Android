# 安全策略 / Security Policy

> 本文件是 **GitHub 发现入口**。完整正文在 [legal/SECURITY.md](../legal/SECURITY.md)
> （English: [legal/en/SECURITY.md](../legal/en/SECURITY.md)）—— 请以那里为准，本文件不重复内容。

## 报告漏洞 / Reporting a vulnerability

**请勿用公开 Issue 报告安全问题。** 使用 GitHub 的私密报告通道：

**Do not open a public issue for a security problem.** Use GitHub's private reporting:

<https://github.com/realLivedInCorner/2-Pyramid-For-Android/security/advisories/new>

若该入口不可用，可开一个带 `[SECURITY]` 前缀的 Issue，**只描述影响面**，维护者会引导转入私下渠道。
其它联系与支持走 [Issues](https://github.com/realLivedInCorner/2-Pyramid-For-Android/issues)。

报告请附：受影响版本与 BUILD（设置 → 关于）、Android 版本与设备型号、复现步骤、预期与实际行为、
样本包的最小公开样例（可私传）。

---

## 快速摘要 / At a glance

2-Pyramid for Android 是**本地优先**工具：资源包转换全程在设备上完成，**没有网络权限**
（APK 不声明 `INTERNET`）、无账号、无遥测、无应用内自动更新。应用只读写你通过系统文件选择器
（SAF）主动授权的一个输入包与一个输出目录。

发布产物完整性靠同名 `.sha256`：Release 资产为 APK + `2-Pyramid-Android-<版本>.apk.sha256`，
下载后请校验。官方 APK 沿用项目一贯的 **debug keystore** 签名（便于覆盖安装与自建包互操作），
**不是发行级签名密钥** —— 只从本仓库 Releases 下载并核对哈希；需要更强保证请从源码自行构建
（见 [README](../README.md)）。

支持的版本线与完整边界说明（含威胁模型、已知边界）见 [legal/SECURITY.md](../legal/SECURITY.md)。
