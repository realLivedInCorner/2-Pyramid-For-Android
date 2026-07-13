package com.twopyramid.twofa

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.twopyramid.twofa.history.HistoryRepository
import com.twopyramid.twofa.native.RustNative
import java.io.File

/**
 * Application 类：启动时初始化
 *  - Native 库（Rust → JNI → UniFFI）—— 失败也不能让 App 崩
 *  - 全局运行时路径（log / app data / UImage）
 *  - Foreground Service 通知 Channel
 */
class TwoFAApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 防御式：native lib 还没编译 / 占位 binding 抛异常都不能让 App 死
        // （首次跑 AS 时没装 .so 是常见情况，不能一开就崩）
        runCatching { initializeRust() }
            .onFailure { Log.e(TAG, "Rust init failed (App continues with placeholder binding)", it) }

        // History 持久化（SharedPreferences）— 必须 init
        HistoryRepository.init(this)

        createNotificationChannel()
    }

    private fun initializeRust() {
        val appFilesDir: File = filesDir
        val uimageDir: File = File(appFilesDir, "UImage").apply { mkdirs() }
        val logDir: File = File(appFilesDir, "logs").apply { mkdirs() }

        RustNative.initialize(
            logDir = logDir.absolutePath,
            appDataDir = appFilesDir.absolutePath,
            uimageDir = uimageDir.absolutePath
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                FOREGROUND_CHANNEL_ID,
                "资源包转换进度",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示当前正在转换的资源包进度"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        const val FOREGROUND_CHANNEL_ID = "twofa.queue"
        private const val TAG = "TwoFA"
        lateinit var instance: TwoFAApp
            private set
    }
}
