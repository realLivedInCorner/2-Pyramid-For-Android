package com.twopyramid.twofa.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.twopyramid.twofa.R
import com.twopyramid.twofa.TwoFAApp
import com.twopyramid.twofa.history.HistoryRecord
import com.twopyramid.twofa.history.HistoryRepository
import com.twopyramid.twofa.native.RustNative
import com.twopyramid.twofa.queue.ProgressRepository
import com.twopyramid.twofa.queue.QueueItem
import com.twopyramid.twofa.queue.QueueRepository
import com.twopyramid.twofa.settings.OutputMode
import com.twopyramid.twofa.settings.SettingsRepository
import com.twopyramid.twofa.ui.MainActivity
import com.twopyramid.twofa.util.SafUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Foreground Service：消费 [QueueRepository] 中的待处理 zip，串行调 Rust 跑转换。
 *
 * v2.1（桌面 2.203.0 同步，task-6）：
 *  - 转换选项来自 [QueueItem.options] 入队快照（S18），不读全局最新设置；
 *  - **真进度**：Rust `ConvertProgress.onProgress(progress, phase)` 回调（来自 rayon/JNA
 *    worker 线程）→ [ProgressRepository] 全局 StateFlow（percent 单调 + phase），UI 收集展示；
 *    删除了旧的 0.1/0.9 假值；通知做节流（整数百分比变化才刷新，避免 4000+ 条目回调打爆通知）；
 *  - 结果写 Queue/History：source_version / skipped / notes / elapsed_ms 全部是引擎真值
 *    （notes ≠ 跳过，UI 侧不统一标「跳过」）。
 */
class ZipQueueService : LifecycleService() {

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForegroundCompat()

        val pending = QueueRepository.snapshotPending()
        if (pending.isEmpty()) {
            stopForegroundAndSelf()
            return Service.START_NOT_STICKY
        }

        lifecycleScope.launch(Dispatchers.IO) {
            processQueue(pending)
            stopForegroundAndSelf()
        }

        return Service.START_NOT_STICKY
    }

    private suspend fun processQueue(items: List<QueueItem>) {
        for ((index, item) in items.withIndex()) {
            ProgressRepository.start(item.id)
            updateNotification(
                label = "${index + 1}/${items.size} · ${item.fileName}",
                status = "convert",
                percent = 0f,
            )

            runCatching { convertOne(item) }
                .onFailure { e ->
                    // 用 e.toString() 而不是 e.message — JNA 异常 message 经常 null，
                    // 但 toString() 至少给 "com.sun.jna.Native$..." 之外的具体 cause。
                    val reason = e.toString()
                    Log.e("ZipQueueService", "convertOne failed for ${item.fileName}", e)

                    // 推历史（Failed 也算历史，方便看错误）
                    HistoryRepository.add(
                        HistoryRecord(
                            id = UUID.randomUUID().toString(),
                            fileName = item.fileName,
                            timestamp = System.currentTimeMillis(),
                            sourceVersion = 0,
                            targetVersion = item.targetVersion,
                            outputPath = null,
                            status = HistoryRecord.Status.Failed(reason),
                            durationMs = 0,
                        )
                    )

                    QueueRepository.updateStatus(
                        item.id,
                        QueueItem.Status.Failed(reason),
                    )
                    updateNotification(
                        label = "${index + 1}/${items.size} · ${item.fileName}",
                        status = "fail",
                        percent = ProgressRepository.percent(item.id),
                    )
                }
                .also {
                    // 成功/失败都清理该项进度，避免 UI 残留 Running 进度
                    ProgressRepository.finish(item.id)
                }
        }

        ProgressRepository.clear()
    }

    private suspend fun convertOne(item: QueueItem) {
        val ctx: Context = applicationContext
        QueueRepository.updateStatus(item.id, QueueItem.Status.Running)

        // 1. Kotlin 侧准备阶段（SAF URI → cache 真实文件）—— 记为解压阶段起点
        ProgressRepository.update(item.id, 0.0, ProgressRepository.PHASE_EXTRACT)
        updateNotification(item.fileName, "convert", 0f)
        val cacheFile: File = SafUtils.copyToCache(ctx, item.sourceUri)

        // 2. 输出目录（FIXED + SAF → 先写 cache staging，转换后复制进用户选的 tree）
        //    S19 修复后：SettingsScreen 存原始 content:// tree URI，这里用 startsWith 判断分支。
        val cfgPath = SettingsRepository.currentOutputPath()
        val isSafFixed = SettingsRepository.currentOutputMode() == OutputMode.FIXED &&
            cfgPath.startsWith("content://")
        val outputDir: String = when {
            isSafFixed -> {
                SafUtils.resolveSafOutputDir(ctx, android.net.Uri.parse(cfgPath)).absolutePath
            }
            SettingsRepository.currentOutputMode() == OutputMode.FIXED && cfgPath.isNotBlank() -> {
                File(cfgPath).apply { mkdirs() }.absolutePath
            }
            else -> {
                File(
                    ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS),
                    "2FA",
                ).apply { mkdirs() }.absolutePath
            }
        }

        // 3. 真进度回调：来自 Rust worker 线程（JNA），只写线程安全状态，不碰 UI。
        //    通知节流：整数百分比变化才刷新（避免 4000+ 条目回调打爆 NotificationManager）。
        ProgressRepository.update(item.id, 0.0, ProgressRepository.PHASE_CONVERT)
        val lastNotifiedPercent = AtomicInteger(-1)
        val startedAt = System.currentTimeMillis()
        val progressCallback: (Double, String) -> Unit = { fraction, phase ->
            val safeFraction = fraction.coerceIn(0.0, 1.0)
            ProgressRepository.update(item.id, safeFraction, phase)
            val pct = (safeFraction * 100).toInt()
            if (lastNotifiedPercent.getAndSet(pct) != pct) {
                updateNotification(item.fileName, "convert", safeFraction.toFloat())
            }
        }

        val result = RustNative.convertZip(
            inputZip = cacheFile.absolutePath,
            outputDir = outputDir,
            options = item.options,
            onProgress = progressCallback,
        )
        val wallMs = System.currentTimeMillis() - startedAt
        ProgressRepository.update(item.id, 1.0, ProgressRepository.PHASE_OUTPUT)

        // 4. SAF 输出 → 把 staging 的 zip 复制到用户选的 tree 下。失败回退到原 staging path。
        val finalOutputPath: String = if (isSafFixed) {
            val resultFile = File(result.outputPath)
            val safUri = SafUtils.copyToSafTree(ctx, android.net.Uri.parse(cfgPath), resultFile)
            safUri?.toString() ?: result.outputPath
        } else {
            result.outputPath
        }

        // 引擎耗时优先（ConversionTiming.total_s）；引擎没给（0）时回退 Kotlin 墙钟
        val elapsedMs = if (result.elapsedMs > 0) result.elapsedMs else wallMs

        // 5. 推历史（含 source_version / skipped / notes）
        HistoryRepository.add(
            HistoryRecord(
                id = UUID.randomUUID().toString(),
                fileName = item.fileName,
                timestamp = startedAt,
                sourceVersion = result.sourceVersion,
                targetVersion = result.targetVersion,
                outputPath = finalOutputPath,
                status = HistoryRecord.Status.Done,
                durationMs = elapsedMs,
                skipped = result.skipped,
                notes = result.notes,
            )
        )

        QueueRepository.updateStatus(
            item.id,
            QueueItem.Status.Done(
                outputPath = finalOutputPath,
                sourceVersion = result.sourceVersion,
                skipped = result.skipped,
                notes = result.notes,
                elapsedMs = elapsedMs,
            ),
        )
        updateNotification(item.fileName, "done", 1f)
    }

    // ── 通知相关 ───────────────────────────────────────────────

    private val notificationBuilder: NotificationCompat.Builder by lazy {
        NotificationCompat.Builder(applicationContext, TwoFAApp.FOREGROUND_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(
                PendingIntent.getActivity(
                    applicationContext, 0,
                    Intent(applicationContext, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
    }

    private fun startForegroundCompat() {
        val notification: Notification = notificationBuilder
            .setContentTitle("2-Pyramid 队列")
            .setContentText("正在准备...")
            .build()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun updateNotification(label: String, status: String, percent: Float) {
        val pct = (percent.coerceIn(0f, 1f) * 100).toInt()
        val text = when (status) {
            "convert" -> "正在转换 $pct%: $label"
            "fail"    -> "失败: $label"
            "done"    -> "完成: $label"
            else      -> label
        }
        val notification = notificationBuilder
            .setContentText(text)
            .setProgress(100, pct, false)
            .build()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun stopForegroundAndSelf() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        @Suppress("DEPRECATION")
        stopSelf()
    }

    companion object {
        const val NOTIFICATION_ID = 0x2FA
    }
}
