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
import com.twopyramid.twofa.queue.QueueItem
import com.twopyramid.twofa.queue.QueueRepository
import com.twopyramid.twofa.settings.AppSettings
import com.twopyramid.twofa.ui.MainActivity
import com.twopyramid.twofa.util.SafUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * Foreground Service：消费 [QueueRepository] 中的待处理 zip，串行调 Rust 跑转换。
 *
 * 见仓库 README & AGENTS.md 的"引擎逻辑对接"段。
 */
class ZipQueueService : LifecycleService() {

    private val _progress = MutableStateFlow(-1f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _currentItemLabel = MutableStateFlow<String?>(null)
    val currentItemLabel: StateFlow<String?> = _currentItemLabel.asStateFlow()

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
            _currentItemLabel.value = "${index + 1}/${items.size} · ${item.fileName}"
            _progress.value = 0f

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
                        "${index + 1}/${items.size} · ${item.fileName} — 失败",
                        "fail",
                    )
                }
        }

        _progress.value = 1f
        _currentItemLabel.value = null
    }

    private suspend fun convertOne(item: QueueItem) {
        val ctx: Context = applicationContext
        QueueRepository.updateStatus(item.id, QueueItem.Status.Running)

        // 1. URI → cache file
        val cacheFile: File = SafUtils.copyToCache(ctx, item.sourceUri)
        _progress.value = 0.1f

        // 2. 调 RustNative.convertZip
        //    v1.2.2: FIXED + SAF → 先写到 cache staging，转换后用 DocumentFile 复制到 SAF tree
        //    FIXED + 普通路径 / FOLLOW → 写 app 私有 external dir（始终可写）
        val cfgPath = AppSettings.outputPath()
        val isSafFixed = AppSettings.outputMode() == AppSettings.OutputMode.FIXED
            && cfgPath.startsWith("content://")
        val outputDir: String = when {
            isSafFixed -> {
                SafUtils.resolveSafOutputDir(ctx, android.net.Uri.parse(cfgPath)).absolutePath
            }
            AppSettings.outputMode() == AppSettings.OutputMode.FIXED && cfgPath.isNotBlank() -> {
                File(cfgPath).apply { mkdirs() }.absolutePath
            }
            else -> {
                File(
                    ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS),
                    "2FA",
                ).apply { mkdirs() }.absolutePath
            }
        }

        updateNotification("1/${item.fileName}", "convert")

        val startedAt = System.currentTimeMillis()
        val result = RustNative.convertZip(
            inputZip = cacheFile.absolutePath,
            outputDir = outputDir,
            targetVersion = item.targetVersion,
        )
        val durationMs = System.currentTimeMillis() - startedAt
        _progress.value = 0.9f

        // v1.2.2: SAF 输出 → 把 staging 的 zip 复制到用户选的 tree 下。
        //         失败回退到原 staging path，history 仍能记下。
        val finalOutputPath: String = if (isSafFixed) {
            val resultFile = java.io.File(result.outputPath)
            val safUri = SafUtils.copyToSafTree(ctx, android.net.Uri.parse(cfgPath), resultFile)
            safUri?.toString() ?: result.outputPath
        } else {
            result.outputPath
        }

        // 推历史
        HistoryRepository.add(
            HistoryRecord(
                id = UUID.randomUUID().toString(),
                fileName = item.fileName,
                timestamp = startedAt,
                sourceVersion = result.sourceVersion,
                targetVersion = result.targetVersion,
                outputPath = finalOutputPath,
                status = HistoryRecord.Status.Done,
                durationMs = durationMs,
            )
        )

        QueueRepository.updateStatus(
            item.id,
            QueueItem.Status.Done(finalOutputPath),
        )
        _progress.value = 1f
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

    private fun updateNotification(label: String, status: String) {
        val text = when (status) {
            "convert" -> "正在转换: $label"
            "fail"    -> "失败: $label"
            else      -> label
        }
        val notification = notificationBuilder
            .setContentText(text)
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
