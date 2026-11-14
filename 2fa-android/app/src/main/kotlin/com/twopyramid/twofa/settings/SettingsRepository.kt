package com.twopyramid.twofa.settings

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import com.twopyramid.twofa.native.RustNative
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 全局设置持久化仓库（SharedPreferences），替换旧 `AppSettings` 的纯内存字段。
 *
 * 设计要点：
 *  - **单一真源**：UI / Service 都从这里读，写入后立刻落盘（apply）并同步 StateFlow，
 *    重启/重装后破坏性开关不会出现「看着关着实际开着」（契约 §5.3）；
 *  - **线程模型**：Service 与 UI 同进程，MutableStateFlow 线程安全；SharedPreferences 读只在
 *    [init] 时做一次，之后全部走内存 flow；
 *  - **初始化**：[init] 由 `TwoFAApp.onCreate` 调用（在 Activity setContent 之前，保证主题首帧
 *    就能拿到正确的深/浅色，避免深色启动白闪）。
 *
 * 默认值与桌面 2.203.0 完全一致（报告 §2.4），见 [ConvertOptions]。
 */
object SettingsRepository {

    private const val PREFS_NAME = "twofa.settings"

    // 外观默认值（与 ui/theme 的 DEFAULT_THEME_COLOR_HEX / Motion.SPEED_NORMAL 对齐；此处不 import UI 层，
    // 保持 settings 包对 Compose 无依赖，颜色解析在 MainActivity / Theme 做）。
    private const val DEFAULT_THEME_COLOR = "#14171A"
    private const val DEFAULT_MOTION_SCALE = 1.0f

    // ── keys（写入后不要随意改名，否则老用户设置丢失）─────────────────
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_THEME_COLOR = "theme_color"
    private const val KEY_MOTION_SCALE = "motion_scale"
    private const val KEY_OUTPUT_MODE = "output_mode"
    private const val KEY_OUTPUT_PATH = "output_path"
    private const val KEY_NAMING_PRESET = "output_naming_preset"
    private const val KEY_LAST_TARGET_VERSION = "last_target_version"
    private const val KEY_DEV_MODE = "dev_mode"
    private const val KEY_FIX_ALPHA_LAYERS = "fix_alpha_layers"
    private const val KEY_ADAPT_SHADERS = "adapt_shaders"
    private const val KEY_DELETE_SHADERS = "delete_shaders"
    private const val KEY_CONVERT_ITEM_DEFINITIONS = "convert_item_definitions"
    private const val KEY_CONVERT_CIT = "convert_cit"
    private const val KEY_DELETE_CIT = "delete_cit"
    private const val KEY_WRITE_GEN_METADATA = "write_gen_metadata"

    @Volatile
    private var prefs: SharedPreferences? = null

    @Volatile
    private var initialized: Boolean = false

    /** 是否已完成 [init]（用于日志/防御式判断，正常流程下 Application 启动即完成）。 */
    val isInitialized: Boolean get() = initialized

    // ── 响应式状态（Service 只写 / UI 只收集）────────────────────────
    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    // 强调色（mapping §5）：默认墨色；用户在 设置 → 外观 里用 HSV 选择器自选。
    // 存 #RRGGBB 原始色，深色下的派生版本由 ui.theme.Oklab 在渲染时计算（OKLab 42% 白混合）。
    private val _themeColor = MutableStateFlow(DEFAULT_THEME_COLOR)
    val themeColor: StateFlow<String> = _themeColor.asStateFlow()

    // 动画速率三档（慢 1.4 / 标准 1.0 / 快 0.7）→ MotionTokens 的时长倍数（mapping §3.4）。
    private val _motionScale = MutableStateFlow(DEFAULT_MOTION_SCALE)
    val motionScale: StateFlow<Float> = _motionScale.asStateFlow()

    private val _outputMode = MutableStateFlow(OutputMode.FOLLOW)
    val outputMode: StateFlow<OutputMode> = _outputMode.asStateFlow()

    private val _outputPath = MutableStateFlow("")
    val outputPath: StateFlow<String> = _outputPath.asStateFlow()

    private val _namingPreset = MutableStateFlow(NamingPreset.VERSION_NAME)
    val namingPreset: StateFlow<NamingPreset> = _namingPreset.asStateFlow()

    private val _options = MutableStateFlow(ConvertOptions())
    val convertOptions: StateFlow<ConvertOptions> = _options.asStateFlow()

    private val _lastTargetVersion = MutableStateFlow(AppSettings.defaultTargetVersion)
    val lastTargetVersion: StateFlow<Int> = _lastTargetVersion.asStateFlow()

    private val _devMode = MutableStateFlow(false)
    val devMode: StateFlow<Boolean> = _devMode.asStateFlow()

    /**
     * 初始化（幂等）。由 `TwoFAApp.onCreate` 在 Compose 首帧前调用。
     */
    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return

            val p = context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs = p

            _themeMode.value = ThemeMode.fromValue(p.getString(KEY_THEME_MODE, null))
            _themeColor.value = normalizeThemeColor(p.getString(KEY_THEME_COLOR, null))
            _motionScale.value = normalizeMotionScale(p.getFloat(KEY_MOTION_SCALE, DEFAULT_MOTION_SCALE))
            _outputMode.value = OutputMode.fromValue(p.getString(KEY_OUTPUT_MODE, null))
            _outputPath.value = p.getString(KEY_OUTPUT_PATH, "") ?: ""

            val preset = NamingPreset.fromTemplate(p.getString(KEY_NAMING_PRESET, null))
            _namingPreset.value = preset

            _lastTargetVersion.value =
                p.getInt(KEY_LAST_TARGET_VERSION, AppSettings.defaultTargetVersion)

            _devMode.value = p.getBoolean(KEY_DEV_MODE, false)

            _options.value = ConvertOptions(
                targetVersion = _lastTargetVersion.value,
                fixAlphaLayers = p.getBoolean(KEY_FIX_ALPHA_LAYERS, false),
                adaptShaders = p.getBoolean(KEY_ADAPT_SHADERS, true),
                deleteShaders = p.getBoolean(KEY_DELETE_SHADERS, false),
                convertItemDefinitions = p.getBoolean(KEY_CONVERT_ITEM_DEFINITIONS, true),
                convertCit = p.getBoolean(KEY_CONVERT_CIT, false),
                deleteCit = p.getBoolean(KEY_DELETE_CIT, false),
                writeGenMetadata = p.getBoolean(KEY_WRITE_GEN_METADATA, true),
                outputNaming = preset.engineTemplate,
            )

            initialized = true

            // devMode 在 Rust 初始化之后才有效；失败也让 App 继续跑
            runCatching { RustNative.setDevMode(_devMode.value) }
        }
    }

    private fun edit(block: SharedPreferences.Editor.() -> Unit) {
        val p = prefs ?: return
        val editor = p.edit()
        editor.block()
        editor.apply()
    }

    // ── 主题 ────────────────────────────────────────────────────────
    fun setThemeMode(mode: ThemeMode) {
        if (_themeMode.value == mode) return
        _themeMode.value = mode
        edit { putString(KEY_THEME_MODE, mode.value) }
    }

    // ── 强调色 / 动画速率（外观）──────────────────────────────────
    /**
     * 写入强调色（#RRGGBB）。非法输入回退默认墨色；返回实际生效的值，
     * 便于 UI 在确认弹窗后显示「当前颜色」。
     */
    fun setThemeColor(hex: String): String {
        val normalized = normalizeThemeColor(hex)
        if (_themeColor.value == normalized) return normalized
        _themeColor.value = normalized
        edit { putString(KEY_THEME_COLOR, normalized) }
        return normalized
    }

    /** 恢复默认强调色（墨色 #14171A）。 */
    fun resetThemeColor(): String = setThemeColor(DEFAULT_THEME_COLOR)

    fun setMotionScale(scale: Float) {
        val normalized = normalizeMotionScale(scale)
        if (_motionScale.value == normalized) return
        _motionScale.value = normalized
        edit { putFloat(KEY_MOTION_SCALE, normalized) }
    }

    fun currentThemeColor(): String = _themeColor.value

    fun currentMotionScale(): Float = _motionScale.value

    private fun normalizeThemeColor(value: String?): String {
        val raw = value?.trim().orEmpty()
        val body = if (raw.startsWith("#")) raw.substring(1) else raw
        if (body.length != 6 || body.any { it.digitToIntOrNull(16) == null }) {
            return DEFAULT_THEME_COLOR
        }
        return "#" + body.uppercase()
    }

    private fun normalizeMotionScale(scale: Float): Float =
        if (scale in 0.5f..2.0f) scale else DEFAULT_MOTION_SCALE

    /**
     * 把主题模式解析成实际是否深色。
     * 同步、不依赖 Compose，用于 `MainActivity` 首帧前设置窗口背景 / 系统栏。
     */
    fun resolveDarkTheme(context: Context, mode: ThemeMode = _themeMode.value): Boolean =
        when (mode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> {
                val uiMode = context.resources.configuration.uiMode
                (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            }
        }

    // ── 输出模式 / 路径 ─────────────────────────────────────────────
    fun setOutputMode(mode: OutputMode) {
        if (_outputMode.value == mode) return
        _outputMode.value = mode
        edit { putString(KEY_OUTPUT_MODE, mode.value) }
    }

    fun setOutputPath(path: String) {
        if (_outputPath.value == path) return
        _outputPath.value = path
        edit { putString(KEY_OUTPUT_PATH, path) }
    }

    // ── 命名模板 ────────────────────────────────────────────────────
    fun setNamingPreset(preset: NamingPreset) {
        if (_namingPreset.value == preset) return
        _namingPreset.value = preset
        edit { putString(KEY_NAMING_PRESET, preset.template) }
        updateOptions { it.copy(outputNaming = preset.engineTemplate) }
    }

    // ── 转换开关（7 项全持久化；fix_alpha_layers 保留字段但 UI 不暴露）──
    fun setAdaptShaders(enabled: Boolean) {
        if (_options.value.adaptShaders == enabled) return
        edit { putBoolean(KEY_ADAPT_SHADERS, enabled) }
        updateOptions { it.copy(adaptShaders = enabled) }
    }

    fun setDeleteShaders(enabled: Boolean) {
        if (_options.value.deleteShaders == enabled) return
        edit { putBoolean(KEY_DELETE_SHADERS, enabled) }
        updateOptions { it.copy(deleteShaders = enabled) }
    }

    fun setConvertItemDefinitions(enabled: Boolean) {
        if (_options.value.convertItemDefinitions == enabled) return
        edit { putBoolean(KEY_CONVERT_ITEM_DEFINITIONS, enabled) }
        updateOptions { it.copy(convertItemDefinitions = enabled) }
    }

    fun setConvertCit(enabled: Boolean) {
        if (_options.value.convertCit == enabled) return
        edit { putBoolean(KEY_CONVERT_CIT, enabled) }
        updateOptions { it.copy(convertCit = enabled) }
    }

    fun setDeleteCit(enabled: Boolean) {
        if (_options.value.deleteCit == enabled) return
        edit { putBoolean(KEY_DELETE_CIT, enabled) }
        updateOptions { it.copy(deleteCit = enabled) }
    }

    fun setWriteGenMetadata(enabled: Boolean) {
        if (_options.value.writeGenMetadata == enabled) return
        edit { putBoolean(KEY_WRITE_GEN_METADATA, enabled) }
        updateOptions { it.copy(writeGenMetadata = enabled) }
    }

    // fix_alpha_layers：桌面 M3 后已是死开关（实现已删，字段仅存于契约），UI 不暴露。
    // 保留 setter 给 Reset/未来契约用，默认 false 并持久化。
    fun setFixAlphaLayers(enabled: Boolean) {
        if (_options.value.fixAlphaLayers == enabled) return
        edit { putBoolean(KEY_FIX_ALPHA_LAYERS, enabled) }
        updateOptions { it.copy(fixAlphaLayers = enabled) }
    }

    /** 恢复转换选项默认值（不影响主题/输出路径/命名模板之外的设置）。 */
    fun resetConversionOptions() {
        val defaults = ConvertOptions(
            targetVersion = _lastTargetVersion.value,
            outputNaming = _namingPreset.value.engineTemplate,
        )
        _options.value = defaults
        edit {
            putBoolean(KEY_FIX_ALPHA_LAYERS, defaults.fixAlphaLayers)
            putBoolean(KEY_ADAPT_SHADERS, defaults.adaptShaders)
            putBoolean(KEY_DELETE_SHADERS, defaults.deleteShaders)
            putBoolean(KEY_CONVERT_ITEM_DEFINITIONS, defaults.convertItemDefinitions)
            putBoolean(KEY_CONVERT_CIT, defaults.convertCit)
            putBoolean(KEY_DELETE_CIT, defaults.deleteCit)
            putBoolean(KEY_WRITE_GEN_METADATA, defaults.writeGenMetadata)
        }
    }

    // ── 上次目标版本 ────────────────────────────────────────────────
    fun setLastTargetVersion(version: Int) {
        _lastTargetVersion.value = version
        edit { putInt(KEY_LAST_TARGET_VERSION, version) }
        updateOptions { it.copy(targetVersion = version) }
    }

    // ── 开发者模式 ──────────────────────────────────────────────────
    fun setDevMode(enabled: Boolean) {
        if (_devMode.value == enabled) return
        _devMode.value = enabled
        edit { putBoolean(KEY_DEV_MODE, enabled) }
        runCatching { RustNative.setDevMode(enabled) }
    }

    // ── 快照 / 便捷读取 ─────────────────────────────────────────────
    /**
     * 入队瞬间冻结的转换选项快照（S18）。之后用户改设置不影响已排队/正在转换的任务。
     */
    fun snapshotOptions(targetVersion: Int): ConvertOptions =
        _options.value.copy(targetVersion = targetVersion)

    fun currentOptions(): ConvertOptions = _options.value

    fun currentOutputMode(): OutputMode = _outputMode.value

    fun currentOutputPath(): String = _outputPath.value

    private fun updateOptions(transform: (ConvertOptions) -> ConvertOptions) {
        _options.value = transform(_options.value)
    }
}
