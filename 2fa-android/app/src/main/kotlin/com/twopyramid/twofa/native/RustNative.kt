package com.twopyramid.twofa.native

import android.util.Log
import uniffi.convert_core.ConvertException
import uniffi.convert_core.convertCoreVersion as uniConvertCoreVersion
import uniffi.convert_core.convertZip as uniConvertZip
import uniffi.convert_core.getRecentLogs as uniGetRecentLogs
import uniffi.convert_core.initRuntime as uniInitRuntime
import uniffi.convert_core.isDevMode as uniIsDevMode
import uniffi.convert_core.setDevMode as uniSetDevMode
import uniffi.convert_core.ConvertRequest as UniConvertRequest
import uniffi.convert_core.ConvertReport as UniConvertReport

/**
 * 与 Rust convert-core 对接的胶水层。
 *
 * 加载流程：
 *   1. System.loadLibrary("convert_core")  — 装载 .so
 *   2. uniffi.convert_core 初始化（自动 first call 完成）
 *   3. initRuntime(logDir, appDataDir, uimageDir) 注入运行时路径
 *
 * 类型说明：
 *   - [ConvertRequest] / [ConvertReport] / [ConvertException] 都是 UniFFI 生成的真实类型
 *     （package `uniffi.convert_core`），re-export 成本地 package 方便调用方 import
 *   - `targetVersion` 在 Rust 是 `u32`，UniFFI 转成 `kotlin.UInt`；外部仍用 `Int` 简化
 *     业务层心智。
 */
object RustNative {

    private const val TAG = "RustNative"

    @Volatile
    private var initialized: Boolean = false

    fun initialize(logDir: String, appDataDir: String, uimageDir: String) {
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

            // 2. 注入运行时路径 — 失败也接住
            runCatching {
                uniInitRuntime(logDir, appDataDir, uimageDir)
            }.onFailure {
                Log.w(TAG, "initRuntime failed: $it")
            }

            initialized = true
            Log.i(TAG, "RustNative.initialize() done")
        }
    }

    /** 单 zip 资源包转换。UniFFI 抛 [ConvertException] 时向上 throw 让 ZipQueueService 接。 */
    fun convertZip(
        inputZip: String,
        outputDir: String,
        targetVersion: Int
    ): ConvertReport {
        val req = UniConvertRequest(
            inputZip = inputZip,
            outputDir = outputDir,
            targetVersion = targetVersion.toUInt()
        )
        val report = uniConvertZip(req)
        return ConvertReport(
            outputPath = report.outputPath,
            sourceVersion = report.sourceVersion.toInt(),
            targetVersion = report.targetVersion.toInt(),
            logPath = report.logPath,
        )
    }

    /** 当前批次日志（用于 UI 显示 + 导出） */
    fun recentLogs(limit: Int): List<String> = uniGetRecentLogs(limit.toUInt())

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

// ── Re-export UniFFI 类型，简化业务层 import ─────────────────────────────

/** UniFFI 生成的 [uniffi.convert_core.ConvertRequest] re-export。 */
data class ConvertRequest(
    val inputZip: String,
    val outputDir: String,
    val targetVersion: Int,
)

/** UniFFI 生成的 [uniffi.convert_core.ConvertReport] re-export。 */
data class ConvertReport(
    val outputPath: String,
    val sourceVersion: Int,
    val targetVersion: Int,
    val logPath: String,
)
