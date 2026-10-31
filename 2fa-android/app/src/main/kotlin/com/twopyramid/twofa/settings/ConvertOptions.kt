package com.twopyramid.twofa.settings

/**
 * 转换选项快照（本地 DTO）。
 *
 * 字段与 Rust convert-core 的 UniFFI `ConvertOptions` 一一对应（CONTRACT §4.1）；
 * binding 重新生成后由 [com.twopyramid.twofa.native.RustNative] 映射成生成类型，
 * **不手改生成代码**。
 *
 * 默认值严格对齐桌面 2.203.0（报告 §2.4）：
 *  - [fixAlphaLayers] = false：M3 后桌面实现已删除（死开关），UI 不暴露，字段保留给
 *    ConvertOptions / 2pyr-gen 契约，默认 false；
 *  - [adaptShaders] = true（计划级过滤）；
 *  - [deleteShaders] = false（破坏性）；
 *  - [convertItemDefinitions] = true；
 *  - [convertCit] = false（2026-10 止血默认关；关闭不删源文件）；
 *  - [deleteCit] = false（破坏性，优先于 convertCit）；
 *  - [writeGenMetadata] = true（仅 Java 目标写 2pyr-gen.json）。
 *
 * [outputNaming] = null 表示交给引擎默认模板 `[Ver][Name]`（与契约 `Option<String>` 一致）。
 */
data class ConvertOptions(
    /** 目标 pack_format（Bedrock = 1000）。入队时冻结进 QueueItem。 */
    val targetVersion: Int = AppSettings.defaultTargetVersion,
    val fixAlphaLayers: Boolean = false,
    val adaptShaders: Boolean = true,
    val deleteShaders: Boolean = false,
    val convertItemDefinitions: Boolean = true,
    val convertCit: Boolean = false,
    val deleteCit: Boolean = false,
    val writeGenMetadata: Boolean = true,
    val outputNaming: String? = null,
) {
    /** 危险/破坏性开关是否全部关闭（用于设置页摘要与二次确认逻辑）。 */
    val hasDestructive: Boolean get() = deleteShaders || deleteCit
}

/**
 * 输出命名模板预设（桌面 UI 是自由模板 + 占位符按钮，Android 先做 3 个等价预设）。
 *
 * 引擎侧占位符语义（桌面 `pack/version_converter.rs:473-485`）：
 *  - `[Ver]` → `[Java 1.20-1.20.1]` 之类的版本前缀
 *  - `[Name]` → 源包名
 *  - `[Time]` → `YYYYMMDD-HHMMSS`
 * 默认 `[Ver][Name]`；冲突不覆盖、追加 `(n)`。
 */
enum class NamingPreset(val template: String, val label: String, val desc: String) {
    VERSION_NAME("[Ver][Name]", "版本 + 名称", "例：[Java 1.21.5]MyPack.zip"),
    VERSION_TIME("[Ver][Time]", "版本 + 时间", "例：[Java 1.21.5]20261024-153000.zip"),
    NAME_ONLY("[Name]", "仅名称", "例：MyPack.zip"),
    ;

    /**
     * 传给引擎的 output_naming：默认预设传 null，让引擎用自己的默认模板，
     * 其余传模板字符串（契约 §4.1：None => "[Ver][Name]"）。
     */
    val engineTemplate: String? get() = if (this == VERSION_NAME) null else template

    companion object {
        /** 从持久化值 / 引擎模板还原预设（兼容旧预设名 default/timestamp/overwrite）。 */
        fun fromTemplate(template: String?): NamingPreset = when (template) {
            null, "", "[Ver][Name]", "default" -> VERSION_NAME
            "[Ver][Time]", "timestamp" -> VERSION_TIME
            "[Name]", "overwrite" -> NAME_ONLY
            else -> VERSION_NAME
        }
    }
}

/** 主题模式（契约 §5.3 / 报告 E3：App 比桌面多一个三档选择器）。 */
enum class ThemeMode(val label: String, val value: String) {
    LIGHT("浅色", "light"),
    DARK("深色", "dark"),
    SYSTEM("跟随系统", "system"),
    ;

    companion object {
        fun fromValue(value: String?): ThemeMode =
            entries.firstOrNull { it.value == value } ?: SYSTEM
    }
}

/** 输出位置模式（沿用旧 AppSettings.OutputMode 的语义，迁移到持久化仓库）。 */
enum class OutputMode(val label: String, val value: String) {
    FOLLOW("跟随 zip", "follow"),
    FIXED("固定路径", "fixed"),
    ;

    companion object {
        fun fromValue(value: String?): OutputMode =
            entries.firstOrNull { it.value == value } ?: FOLLOW
    }
}
