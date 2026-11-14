# 隐私说明 / Privacy Policy

**生效日期：** 2026-11-14
**适用产品：** 2-Pyramid for Android（Android 应用，含官方构建的 APK）

2-Pyramid for Android 的设计原则是：**完全本地处理、无账号、无遥测、无网络权限**。
下文说明我们实际会与不会收集什么。

---

## 一句话结论

转换资源包时，你的文件**不会离开设备**。APK **不声明 `INTERNET` 权限**，Android 系统层面就不允许本
应用联网——没有上传、没有遥测、没有崩溃自动上报，也没有应用内更新检查。转换产物默认带有一份**不含
个人信息**的转换参数记录（`2pyr-gen.json`，见第 2.1 节），可在转换选项里关闭。

---

## 1. 我们不收集的内容

- 不上传你导入的资源包、贴图、着色器或转换选项；
- 不采集崩溃堆栈自动上报、不埋点、不统计 DAU / 功能使用率；
- 不要求注册账号，不存储密码、邮箱、手机号；
- 不读取与功能无关的通讯录、短信、通话记录、位置、相机、麦克风或其它应用数据；
- 不使用广告 SDK、不集成任何第三方统计/追踪 SDK。

## 2. 权限与它们的唯一用途

应用只声明以下权限（见 `AndroidManifest.xml`）：

| 权限 | 用途 | 行为说明 |
|---|---|---|
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_DATA_SYNC` | 转换在后台队列中继续执行（前台服务，类型为 `dataSync`） | 转换期间通知栏显示进度；离开界面不会中断转换 |
| `POST_NOTIFICATIONS`（Android 13+） | 显示上述前台服务通知与队列进度 | 拒绝授权时转换仍可进行，只是看不到通知 |

**不申请** `INTERNET`、`READ/WRITE_EXTERNAL_STORAGE`、`MANAGE_EXTERNAL_STORAGE` 等权限。

文件访问一律通过系统 **Storage Access Framework（SAF）**：你主动选择一个输入包文件与一个输出目录，
系统文件选择器把这两处授权给应用（持久化授权只覆盖你选中的位置）。应用**只**读取你选择的那一个输入
包、**只**写入你选择的输出目录；没有选择器授权时无法访问其它文件。

## 2.2 本机存储（全部在应用私有目录内）

| 内容 | 实际位置 | 说明 |
|---|---|---|
| 设置（转换开关、输出模式/目录、命名预设、主题、强调色、上次目标版本、开发者模式） | 应用私有 `SharedPreferences` | 仅本机；卸载即删除 |
| 转换历史 | 应用私有 `SharedPreferences` | 仅本机；可在历史页清空 |
| 运行日志 | 应用私有 `filesDir/logs/2_pyramid_<日期>.log` | 日期滚动；release 默认只写 Warn/Error，打开开发者模式后写 Info；可在设置里查看最近日志 |
| UImage 参照素材 | 应用私有 `filesDir/UImage` | 随 APK 分发的素材，首次启动解压得到（见第 4 节） |
| 临时文件 | 应用私有 `cacheDir`（含 SAF 输入包的副本 `cacheDir/zips`） | 转换用临时目录；系统清理缓存或卸载时删除 |
| 转换输出 | **你通过 SAF 选择的输出目录** | 由你决定位置；应用不额外复制 |

- Android 的系统备份：应用声明 `android:allowBackup="false"`，**不参与**云备份/设备迁移，
  数据不会被同步到 Google 账号或其他设备。
- 卸载应用会删除上述**全部应用私有数据**（设置 / 历史 / 日志 / 素材 / 缓存）；你通过 SAF 输出到
  自有目录的转换产物**不受影响**，需要你自行管理。

日志可能包含**资源包文件名与输出路径**，便于排障。日志只保存在应用私有目录，不会自动发送给任何人；
如果你把日志内容复制粘贴到公开 Issue，其中的路径/文件名同样会公开——请自行检查后再分享。

## 2.1 转换产物中的元数据文件（2pyr-gen）

**Java 版**资源包转换成功后，产物**包根目录**会写入 `2pyr-gen.json`（**默认开启**，可在转换选项里关闭；
关闭后不生成该文件）。它只属于**本地产物**：不会上传，也不会发送给任何人；只有你把转换后的包分享出去时，
它才随包一起被分发。

记录内容**仅限转换参数**：

- 生成器名称、版本号与构建号（BUILD）
- 生成时间（UTC，RFC 3339 秒级）
- 源与目标的 `pack_format`、目标版本标签（能可靠取得时）
- 本次转换的六个开关（图层修复、着色器适配、彻底删除 shaders、物品定义升级、OptiFine CIT 转换、
  彻底删除 CIT）

**不包含**文件路径、用户名、设备名、硬件 ID、UUID、IP 地址或网络信息。文件另含一行**中性说明**
（告知接收方该文件由 2-Pyramid 生成、Minecraft 不读取、可在转换页关闭），不属于个人信息。用途是让
转换产物可追溯、便于排障；Minecraft 客户端**不读取**该文件，它不参与资源包加载。

如果分享产物时不想带上这些转换参数，请在转换选项里关闭「写入转换参数」，或转换后手动删除该文件
（删除不影响资源包使用）。

## 3. 网络访问

**没有任何网络访问。**

- APK **不声明 `INTERNET` 权限**，应用无法建立网络连接；
- **没有自动更新/更新检查**：升级由你从 GitHub Releases 手动下载并覆盖安装；
- 没有远程配置、没有在线素材下载、没有第三方 API 调用。

因此也不存在「数据出境」「第三方处理者」：除你主动通过 GitHub（浏览器/客户端）下载 APK 或提交 Issue
之外，应用本身不参与任何网络传输。

## 4. 随包分发的素材（UImage）

APK 内置 80 张 `UImage` 参照素材（约 0.56 MB），用于「用原版风格素材生成/叠加贴图」类转换任务
（tipped arrow、smithing overlay、snow bucket 等）。这些素材来自**维护者桌面项目 2-Pyramid** 的对应
目录，随 APK 分发、首次启动解压到本机私有目录：

- 它们是**参考素材**，不是你的数据，也不会被上传；
- 相关内容（Minecraft 材质）的权利归其权利人所有，本项目不主张著作权，仅作转换参照；
- 若权利人认为分发方式不妥，可通过 Issues 联系维护者，我们会评估移除。

素材清单与来源见仓库 [`brand/`](../brand/) 与 [`legal/THIRD-PARTY-NOTICES.md`](./THIRD-PARTY-NOTICES.md)。

## 5. 第三方处理者

无。应用不向任何第三方发送数据（它没有联网能力）。GitHub 作为托管与分发平台，其自身处理 Issues / PR /
Release 下载的元数据，适用 GitHub 自己的隐私政策——那部分是**平台行为**，与应用无关。

## 6. 未成年人

本应用不面向 13 岁以下儿童设计，也不会在知情情况下收集儿童个人信息（事实上不收集任何个人信息）。

## 7. 你的权利

- 可随时在应用内清除转换历史 / 重置设置；也可在系统设置中清除缓存；
- 可卸载应用以删除全部应用私有数据；
- 你通过 SAF 输出的转换产物在你自己的目录里，由你完全掌控；
- 对隐私有疑问，请在仓库 Issues 提问。

## 8. 变更

政策更新会修改文首日期并在仓库说明。重大变更将通过 Release Notes 与 `CHANGELOG.md` 提示。

---

**English summary:** 2-Pyramid for Android processes resource packs entirely on your device. The APK does
**not** declare the `INTERNET` permission, so there is no upload, no telemetry, no crash reporting and no
in-app update check. Files are accessed only through the system Storage Access Framework: you pick one input
pack and one output folder. Settings, history and logs live in app-private storage; `allowBackup="false"`
keeps them out of cloud backup, and uninstalling removes them. Java-target outputs gain a
`2pyr-gen.json` parameter record (no personal data, ignored by Minecraft, can be disabled). The app ships 80
`UImage` reference assets from the maintainer's desktop project — they are neither collected nor uploaded,
and no ownership is claimed over the underlying Minecraft-derived content.

**联系方式：** GitHub Issues — https://github.com/realLivedInCorner/2-Pyramid-For-Android/issues
