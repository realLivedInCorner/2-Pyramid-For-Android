package com.twopyramid.twofa.ui

import com.twopyramid.twofa.queue.ProgressRepository

/**
 * 新增文案集中处（本次不引入 i18n 框架，先集中成常量对象，方便后续迁移 strings.xml）。
 *
 * 语义直接复用桌面 `src/locales/zh-CN.json` 的 `conversion.*`（报告 §2.6 / 前端调研 §2.6）：
 *  - 危险开关用 «会删文件» 红色标记 + 说明「这是删除，不是优化」；
 *  - CIT 转换默认关闭（2026-10 止血），文案说明「关闭不删源文件」；
 *  - 26.3 已知问题 MC-311807 只做**非阻塞**提示（S2：Android 无桌面按钮路径，风险更低）；
 *  - Bedrock 为测试中功能，选择即提示（不阻塞）。
 */
object Texts {

    // ── 转换选项 ────────────────────────────────────────────────────
    const val optionsTitle = "转换选项"
    const val optionsReset = "恢复默认"
    const val optionsHint = "选项在点击「开始转换」时冻结；之后改设置不影响已排队的任务"

    const val adaptShadersLabel = "着色器适配"
    const val adaptShadersHint = "实验功能：按目标版本改写 shaders，建议先用小体积带 shader 的包试转"

    const val deleteShadersLabel = "彻底删除 shaders"
    const val deleteShadersHint = "删掉后自定义光影不再生效，游戏用原版渲染；只在着色器导致加载失败时使用"

    const val convertItemDefinitionsLabel = "物品定义升级"
    const val convertItemDefinitionsHint = "只影响 1.21.4+ 目标；更低版本没有物品定义"

    const val convertCitLabel = "OptiFine CIT 转换"
    const val convertCitHint = "默认关闭；只在实机验证过目标客户端后打开；关闭不删源文件"

    const val deleteCitLabel = "彻底删除 CIT"
    const val deleteCitHint = "删掉后 OptiFine 与新物品定义都不生效；只在 CIT 导致游戏加载失败（紫黑格）时使用"

    const val genMetadataLabel = "写入转换参数文件"
    const val genMetadataHint = "默认开启；产物根目录写 2pyr-gen.json（不含个人信息），关闭后不包含该文件"

    const val deleteCitOverridesConvertCit = "已开启「彻底删除 CIT」：删除优先，CIT 转换会被跳过"

    const val dangerTag = "会删文件"

    const val namingTitle = "输出命名"
    const val namingHint = "默认模板 [Ver][Name]；同名产物不覆盖，自动追加 (n)"

    // ── 危险开关二次确认（S2/契约 §5.2）────────────────────────────
    const val deleteShadersConfirmTitle = "彻底删除 shaders？"
    const val deleteShadersConfirmBody =
        "转换完成后，产物里的 shaders/ 目录与 assets/minecraft/shaders/ 会被整个删除，" +
            "目录和里面的文件都不保留。代价：包里的自定义光影不再加载，游戏改用原版渲染。这是删除，不是优化。"
    const val deleteCitConfirmTitle = "彻底删除 CIT？"
    const val deleteCitConfirmBody =
        "转换完成后，assets/minecraft/optifine/cit 与 assets/minecraft/mcpatcher/cit 会被整个删除。" +
            "代价：OptiFine 与新版物品定义都不会生效，这些自定义外观全部消失。这是删除，不是优化。"
    const val confirmEnable = "确认开启"
    const val cancel = "取消"

    // ── 版本选择警示（非阻塞）──────────────────────────────────────
    const val java263WarnTitle = "Minecraft 26.3 已知问题"
    const val java263WarnBody =
        "经测试，Java 版 26.3 在材质包选择页点击「打开材质包目录」无反应，后续可能出现未响应。" +
            "官方缺陷号 MC-311807。仍可选择 26.3 作为目标，此提示不阻塞转换。"
    const val bedrockWarnTitle = "基岩版转换（测试中）"
    const val bedrockWarnBody =
        "基岩版转换尚未完善，可能存在显示或兼容问题，建议先用测试包验证。"

    // ── 进度 phase 文案（UI 翻译 Rust 回调短串）────────────────────
    const val phasePreparing = "准备中"
    const val phaseExtract = "解压中"
    const val phaseConvert = "转换中"
    const val phaseOutput = "打包中"

    /** Rust phase 短串 → 中文文案（对齐桌面遗留语义：extract/convert/output）。 */
    fun phaseLabel(phase: String?): String = when (phase) {
        null, "" -> phasePreparing
        ProgressRepository.PHASE_EXTRACT -> phaseExtract
        ProgressRepository.PHASE_CONVERT -> phaseConvert
        ProgressRepository.PHASE_OUTPUT -> phaseOutput
        // 任务级 phase（如 adapt_shaders）→ "转换中（任务名）"
        else -> "$phaseConvert（$phase）"
    }

    /** 进度条右侧文案：`转换中（任务名） 42%`。 */
    fun progressLine(phase: String?, percent: Float): String {
        val p = (percent.coerceIn(0f, 1f) * 100).toInt()
        return "${phaseLabel(phase)} $p%"
    }

    // ── 其他 ────────────────────────────────────────────────────────
    const val manyFilesWarning = "文件较多，可能需要等待较长时间"
    const val startConvert = "开始转换"
    const val notificationPermissionHint = "未授予通知权限：转换仍会继续进行，只是看不到前台通知"
}
