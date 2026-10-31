package com.twopyramid.twofa.settings

/**
 * 应用全局设置（**只保留版本目录**）。
 *
 * v2.1（桌面 2.203.0 同步）：输出模式 / 固定路径 / 开发者模式 / 主题 / 转换开关等
 * 可变设置已迁移到 [SettingsRepository]（SharedPreferences 持久化）。
 * 本对象现在只负责「静态版本表 + 标签辅助函数」，不再持有任何运行时状态。
 *
 * 版本表与桌面 `src/data/minecraftVersions.ts`（2.203.0）逐条对齐：
 * 27 项 = 26 个 Java pack_format + Bedrock 1000。
 *  - 26.3 / 97 = latest（新增）
 *  - 26.2 / 88 = stable
 *  - 1.21.11 / 75 = stable
 *  - 26.1-26.1.2 / 84 = 无 status（对齐桌面 26.1 无 status）
 *  - 1.21-1.21.1 / 34 = 无 status（严格对齐桌面 `minecraftVersions.ts`；默认目标仍是 34）
 *  - Bedrock Latest / 1000 = beta（label 统一为 "Bedrock Latest"）
 */
object AppSettings {

    /**
     * 版本项（对齐桌面 VersionEntry）。
     * - [packFormat]：传给 Rust 引擎的 pack_format 数值
     * - [label]：短号（"1.20-1.20.1"）— 状态行 / pill
     * - [range]：区间（"1.20 → 1.20.1"）— picker 显示
     * - [era]：所属年代（classic / modern / cavesCliffs / trailsTales / trickyTrials / bravery）
     * - [status]：可选 badge（stable / latest / recommended / beta）
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

    /** 版本列表（27 项：26 Java + Bedrock 1000，对齐桌面 2.203.0） */
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
        VersionEntry(34, "1.21-1.21.1",   "1.21 → 1.21.1",    VersionEra.TRICKY_TRIALS),
        VersionEntry(42, "1.21.2-1.21.3", "1.21.2 → 1.21.3",  VersionEra.TRICKY_TRIALS),
        VersionEntry(46, "1.21.4",        "1.21.4",           VersionEra.TRICKY_TRIALS),
        VersionEntry(55, "1.21.5",        "1.21.5",           VersionEra.TRICKY_TRIALS),
        VersionEntry(63, "1.21.6",        "1.21.6",           VersionEra.TRICKY_TRIALS),
        VersionEntry(64, "1.21.7-1.21.8", "1.21.7 → 1.21.8",  VersionEra.TRICKY_TRIALS),
        VersionEntry(69, "1.21.9-1.21.10","1.21.9 → 1.21.10", VersionEra.TRICKY_TRIALS),
        VersionEntry(75, "1.21.11",       "1.21.11",          VersionEra.TRICKY_TRIALS, VersionStatus.STABLE),
        // Bravery（3）— 26.3 为本次新增，88 改 stable，84 无 status（对齐桌面）
        VersionEntry(84, "26.1-26.1.2",   "26.1 → 26.1.2",    VersionEra.BRAVERY),
        VersionEntry(88, "26.2",          "26.2",             VersionEra.BRAVERY, VersionStatus.STABLE),
        VersionEntry(97, "26.3",          "26.3",             VersionEra.BRAVERY, VersionStatus.LATEST),
        // Bedrock（1）— 先转 Java 26.3（pack_format 97）再 j2b
        VersionEntry(1000, "Bedrock Latest", "Bedrock",       VersionEra.BEDROCK, VersionStatus.BETA),
    )

    /** targetVersionChoices：扁平 pack_format 列表（保留兼容旧 API）。 */
    val targetVersionChoices: List<Int> = versions.map { it.packFormat }

    /** 默认目标 1.21-1.21.1（pack_format 34，对齐桌面默认值）。 */
    val defaultTargetVersion: Int = 34

    /** 按 era 分组 */
    fun versionsByEra(): Map<VersionEra, List<VersionEntry>> = versions.groupBy { it.era }

    /** packFormat → VersionEntry（找不到返回 null） */
    fun entryByFormat(packFormat: Int): VersionEntry? =
        versions.firstOrNull { it.packFormat == packFormat }

    /** 是否本次新增/有已知游戏缺陷的 26.3（pack_format 97）目标。 */
    fun hasKnownIssue(packFormat: Int): Boolean = packFormat == 97

    /** 是否 Bedrock 目标（转换实验性警示用）。 */
    fun isBedrock(packFormat: Int): Boolean = packFormat == 1000

    /** 状态行 / 卡片用短号（"1.20.1"） */
    fun versionShort(packFormat: Int): String =
        entryByFormat(packFormat)?.label ?: "v$packFormat"

    /** picker 显示用区间（"1.20 → 1.20.1"） */
    fun versionLabel(packFormat: Int): String =
        entryByFormat(packFormat)?.range ?: "Pack Format $packFormat"
}
