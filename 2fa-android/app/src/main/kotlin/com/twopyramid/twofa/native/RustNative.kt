package com.twopyramid.twofa.native

import android.util.Log
import com.twopyramid.twofa.settings.ConvertOptions as AppConvertOptions
import uniffi.convert_core.ConvertException
import uniffi.convert_core.ConvertOptions as UniConvertOptions
import uniffi.convert_core.ConvertProgress as UniConvertProgress
import uniffi.convert_core.ConvertReport as UniConvertReport
import uniffi.convert_core.ConvertRequest as UniConvertRequest
import uniffi.convert_core.convertCoreVersion as uniConvertCoreVersion
import uniffi.convert_core.convertZip as uniConvertZip
import uniffi.convert_core.getRecentLogs as uniGetRecentLogs
import uniffi.convert_core.initRuntime as uniInitRuntime
import uniffi.convert_core.isDevMode as uniIsDevMode
import uniffi.convert_core.setDevMode as uniSetDevMode

/**
 * 与 Rust convert-core 对接的胶水层（新引擎 2.203.0 facade）。
 *
 * 加载流程：
 *   1. System.loadLibrary("convert_core")  — 装载 .so
 *   2. uniffi.convert_core 初始化（首次调用自动完成）
 *   3. initRuntime(logDir, appDataDir, uimageDir, tempDir) 注入运行时路径
 *      → tempDir = app cacheDir（Android 没有可写 /tmp；Rust 侧同时 set TMPDIR）
 *
 * 类型说明：
 *   - UniFFI 生成的 [uniffi.convert_core.ConvertOptions] / [ConvertRequest] / [ConvertReport] /
 *     [ConvertProgress] 直接映射为业务层类型（见下方本地 DTO 与 [AppConvertOptions]）。
 *   - Rust u32 → Kotlin **UInt**、u64 → **ULong**，这里在胶水层做显式转换，业务层继续用
 *     Int/Long，避免 UInt 扩散到 UI。
 *   - [ConvertProgress.onProgress] 由 **Rust worker 线程**回调（rayon/JNA），实现里只写
 *     StateFlow/线程安全状态，绝不直接碰 Compose/UI。
 */
object RustNative {

    private const val TAG = "RustNative"

    @Volatile
    private var initialized: Boolean = false

    fun initialize(
        logDir: String,
        appDataDir: String,
        uimageDir: String,
        tempDir: String,
    ) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return

            // 1. 装载 .so — 主动 loadLibrary 喂 JNA 让它能找到符号。
            // 失败 log 详细 stack trace（不再吞），让 TwoFAApp 的 runCatching 决定要不要继续。
            try {
                System.loadLibrary("convert_core")
                Log.i(TAG, "convert_core.so loaded OK")
            } catch (e: Throwable) {
                Log.e(TAG, "convert_core.so FAILED to load", e)
                throw e
            }

            // 2. 注入运行时路径（4 参：含 temp_dir）— 失败也接住，让 App 继续跑
            runCatching {
                uniInitRuntime(logDir, appDataDir, uimageDir, tempDir)
            }.onFailure {
                Log.w(TAG, "initRuntime failed: $it")
            }

            initialized = true
            Log.i(TAG, "RustNative.initialize() done")
        }
    }

    /**
     * 单 zip 资源包转换（新 facade）。
     *
     * @param inputZip 输入 zip 的**文件系统绝对路径**（SAF URI 由 Service 先复制到 cache）
     * @param outputDir 输出目录
     * @param options 入队时冻结的选项快照（7 开关 + output_naming + 目标版本）
     * @param onProgress 进度回调：progress ∈ [0,1]，phase 为 extract / 任务名 / output。
     *        **回调来自 Rust worker 线程**，实现方必须线程安全（只写 StateFlow 等）。
     * @throws ConvertException UniFFI 错误（InputNotFound / Conversion；BedrockNotSupported 保留不用）
     */
    fun convertZip(
        inputZip: String,
        outputDir: String,
        options: AppConvertOptions,
        onProgress: (Double, String) -> Unit,
    ): ConvertReport {
        val req = UniConvertRequest(
            inputZip = inputZip,
            outputDir = outputDir,
            options = options.toUni(),
        )

        // 本地 val 避免与 override 方法名 onProgress 混淆
        val progressCallback = onProgress
        val progressSink = object : UniConvertProgress {
            override fun onProgress(progress: Double, phase: String) {
                progressCallback(progress, phase)
            }
        }

        val report: UniConvertReport = uniConvertZip(req, progressSink)
        return ConvertReport(
            outputPath = report.outputPath,
            sourceVersion = report.sourceVersion.toInt(),
            targetVersion = report.targetVersion.toInt(),
            logPath = report.logPath,
            skipped = report.skipped.toInt(),
            notes = report.notes,
            elapsedMs = report.elapsedMs.toLong(),
        )
    }

    /** 当前批次日志（用于 UI 显示） */
    fun recentLogs(limit: Int): List<String> =
        runCatching { uniGetRecentLogs(limit.coerceAtLeast(0).toUInt()) }
            .getOrDefault(emptyList())

    /** 开发者模式开关 */
    fun setDevMode(enabled: Boolean) = runCatching { uniSetDevMode(enabled) }.let { }

    /** 开发者模式查询 */
    fun isDevMode(): Boolean = runCatching { uniIsDevMode() }.getOrDefault(false)

    /**
     * 转换核心版本号。**永不抛** — 失败时返回 "(version unavailable)"，
     * 因为这个函数在 Composable 渲染时直接调，没法 try/catch 包住。
     */
    fun convertCoreVersion(): String =
        runCatching { uniConvertCoreVersion() }.getOrDefault("(version unavailable)")
}

// ── 本地 DTO（业务层使用，避免 UInt/ULong 扩散）───────────────────────────

/**
 * 业务层转换报告。
 *
 * 与 UniFFI 生成的 [UniConvertReport] 一一对应（7 字段），只是把 u32/u64 转成 Int/Long。
 */
data class ConvertReport(
    val outputPath: String,
    val sourceVersion: Int,
    val targetVersion: Int,
    val logPath: String,
    /** 引擎跳过总数（MixedRunReport.skipped） */
    val skipped: Int = 0,
    /** 引擎 notes：既有跳过原因也有普通进展，UI 不能统一标「跳过」 */
    val notes: List<String> = emptyList(),
    /** 转换耗时毫秒（ConversionTiming.total_s * 1000，含 IO） */
    val elapsedMs: Long = 0L,
)

// ── 映射：业务层 ConvertOptions → UniFFI 生成类型 ──────────────────────────

private fun AppConvertOptions.toUni(): UniConvertOptions = UniConvertOptions(
    targetVersion = targetVersion.coerceAtLeast(0).toUInt(),
    fixAlphaLayers = fixAlphaLayers,
    adaptShaders = adaptShaders,
    deleteShaders = deleteShaders,
    convertItemDefinitions = convertItemDefinitions,
    convertCit = convertCit,
    deleteCit = deleteCit,
    writeGenMetadata = writeGenMetadata,
    outputNaming = outputNaming,
)
