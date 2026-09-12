package com.twopyramid.twofa.settings

import com.twopyramid.twofa.native.RustNative

/**
 * 应用全局设置。
 *
 * v1.2.2 改：版本表对齐 PC 端 v1.0 ConversionPage.vue 的 22 个版本（按 era 分组）。
 * 每个版本有 label（短号）/ range（区间）/ era（年代）/ status（stable / latest）。
 */
object AppSettings {

    /**
     * 版本项（v1.2.2：对齐 PC 端 VersionEntry）。
     * - [packFormat]：传给 Rust 引擎的 pack_format 数值
     * - [label]：短号（"1.20-1.20.1"）— 状态行 / pill
     * - [range]：区间（"1.20 → 1.20.1"）— picker 显示
     * - [era]：所属年代（classic / modern / cavesCliffs / trailsTales / trickyTrials / bravery）
     * - [status]：可选 badge（stable / latest / recommended）
     */
    data class VersionEntry(
        val packFormat: Int,
        val label: String,
        val range: String,
        val era: VersionEra,
        val status: VersionStatus? = null,
    )

    enum class VersionEra(val label: String, val desc: String) {
        CLASSIC("Classic", "经典时代"),
        MODERN("Modern", "水域 / 村庄 / 下界"),
        CAVES_CLIFFS("Caves & Cliffs", "洞穴与悬崖"),
        TRAILS_TALES("Trails & Tales", "足迹与传说"),
        TRICKY_TRIALS("Tricky Trials", "试炼密室"),
        BRAVERY("Bundles of Bravery", "勇者之束"),
        BEDROCK("Bedrock", "基岩版"),
    }

    enum class VersionStatus(val label: String) {
        STABLE("稳定"),
        LATEST("最新"),
        RECOMMENDED("推荐"),
        BETA("测试"),
    }

    /** 版本列表（对齐 PC 端 ConversionPage，含 26.2 与 Bedrock） */
    val versions: List<VersionEntry> = listOf(
        // Classic（3）
        VersionEntry(1,  "1.6-1.8",       "1.6 → 1.8",        VersionEra.CLASSIC),
        VersionEntry(2,  "1.9-1.10",      "1.9 → 1.10",       VersionEra.CLASSIC),
        VersionEntry(3,  "1.11-1.12",     "1.11 → 1.12",      VersionEra.CLASSIC),
        // Modern（3）
        VersionEntry(4,  "1.13-1.14",     "1.13 → 1.14",      VersionEra.MODERN),
        VersionEntry(5,  "1.15-1.16.1",   "1.15 → 1.16.1",    VersionEra.MODERN),
        VersionEntry(6,  "1.16.2-1.16.5", "1.16.2 → 1.16.5", VersionEra.MODERN),
        // Caves & Cliffs（5）
        VersionEntry(7,  "1.17",          "1.17",             VersionEra.CAVES_CLIFFS),
        VersionEntry(8,  "1.18",          "1.18",             VersionEra.CAVES_CLIFFS),
        VersionEntry(9,  "1.19-1.19.2",   "1.19 → 1.19.2",    VersionEra.CAVES_CLIFFS),
        VersionEntry(12, "1.19.3",        "1.19.3",           VersionEra.CAVES_CLIFFS),
        VersionEntry(13, "1.19.4",        "1.19.4",           VersionEra.CAVES_CLIFFS),
        // Trails & Tales（4）
        VersionEntry(15, "1.20-1.20.1",   "1.20 → 1.20.1",    VersionEra.TRAILS_TALES),
        VersionEntry(18, "1.20.2",        "1.20.2",           VersionEra.TRAILS_TALES),
        VersionEntry(22, "1.20.3-1.20.4", "1.20.3 → 1.20.4",  VersionEra.TRAILS_TALES),
        VersionEntry(32, "1.20.5-1.20.6", "1.20.5 → 1.20.6",  VersionEra.TRAILS_TALES),
        // Tricky Trials（9）
        VersionEntry(34, "1.21-1.21.1",   "1.21 → 1.21.1",    VersionEra.TRICKY_TRIALS, VersionStatus.RECOMMENDED),
        VersionEntry(42, "1.21.2-1.21.3", "1.21.2 → 1.21.3",  VersionEra.TRICKY_TRIALS),
        VersionEntry(46, "1.21.4",        "1.21.4",           VersionEra.TRICKY_TRIALS),
        VersionEntry(55, "1.21.5",        "1.21.5",           VersionEra.TRICKY_TRIALS),
        VersionEntry(63, "1.21.6",        "1.21.6",           VersionEra.TRICKY_TRIALS),
        VersionEntry(64, "1.21.7-1.21.8", "1.21.7 → 1.21.8",  VersionEra.TRICKY_TRIALS),
        VersionEntry(69, "1.21.9-1.21.10","1.21.9 → 1.21.10", VersionEra.TRICKY_TRIALS),
        VersionEntry(75, "1.21.11",       "1.21.11",          VersionEra.TRICKY_TRIALS, VersionStatus.STABLE),
        // Bravery（2）
        VersionEntry(84, "26.1-26.1.2",   "26.1 → 26.1.2",    VersionEra.BRAVERY, VersionStatus.LATEST),
        VersionEntry(88, "26.2",          "26.2",             VersionEra.BRAVERY, VersionStatus.LATEST),
        // Bedrock（1）— 先转 Java 26.2 再 j2b
        VersionEntry(1000, "Bedrock",     "Bedrock Latest",   VersionEra.BEDROCK, VersionStatus.BETA),
    )

    /** targetVersionChoices：扁平 pack_format 列表（保留兼容 v1.1 之前的 API） */
    val targetVersionChoices: List<Int> = versions.map { it.packFormat }

    /** 默认 1.21-1.21.1（PC 端默认值） */
    val defaultTargetVersion: Int = 34

    /** 按 era 分组 */
    fun versionsByEra(): Map<VersionEra, List<VersionEntry>> = versions.groupBy { it.era }

    /** packFormat → VersionEntry（找不到返回 null） */
    fun entryByFormat(packFormat: Int): VersionEntry? = versions.firstOrNull { it.packFormat == packFormat }

    /** 状态行 / 卡片用短号（"1.20.1"） */
    fun versionShort(packFormat: Int): String =
        entryByFormat(packFormat)?.label ?: "v$packFormat"

    /** picker 显示用区间（"1.20 → 1.20.1"） */
    fun versionLabel(packFormat: Int): String =
        entryByFormat(packFormat)?.range ?: "Pack Format $packFormat"

    // ── 输出模式 + 固定路径（v1.2.1）─────────────────────
    enum class OutputMode(val label: String, val desc: String) {
        FOLLOW("跟随 zip", "输出到原 zip 同目录"),
        FIXED("固定路径", "输出到指定目录"),
    }

    @Volatile
    private var _outputMode: OutputMode = OutputMode.FOLLOW

    @Volatile
    private var _outputPath: String = ""

    fun outputMode(): OutputMode = _outputMode

    fun outputPath(): String = _outputPath

    fun setOutputMode(mode: OutputMode) {
        _outputMode = mode
    }

    fun setOutputPath(path: String) {
        _outputPath = path
    }

    // ── 开发者模式 ───────────────────────────────────────────
    @Volatile
    private var _devMode: Boolean = false

    fun isDevMode(): Boolean = _devMode

    fun setDevMode(enabled: Boolean) {
        _devMode = enabled
        RustNative.setDevMode(enabled)
    }
}
