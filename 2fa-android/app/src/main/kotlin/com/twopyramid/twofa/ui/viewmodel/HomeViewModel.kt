package com.twopyramid.twofa.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.twopyramid.twofa.queue.QueueItem
import com.twopyramid.twofa.queue.QueueRepository
import com.twopyramid.twofa.settings.SettingsRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * 主页 ViewModel：把 SAF 选中的 zip 加入 [QueueRepository]，UI 订阅 queue 显示列表。
 *
 * Service 通过 [QueueRepository.snapshotPending] 拉队列消费；
 * Service 完成时改 status，UI 通过 `repository.items.collectAsState()` 自动刷新。
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    val queue: StateFlow<List<QueueItem>> = QueueRepository.items

    /**
     * 用户在 ConvertScreen 选完 target version 后调用，把 zip 入队。
     *
     * v2.1：入队时**冻结**一份 [com.twopyramid.twofa.settings.ConvertOptions] 快照
     * （7 个开关 + 输出命名 + 目标版本），之后用户改设置不影响已排队任务（S18）。
     */
    fun addFromSaf(uri: android.net.Uri, targetVersion: Int) {
        val ctx = getApplication<Application>().applicationContext

        val fileName = ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        } ?: uri.lastPathSegment
            ?: "pack.zip"

        QueueRepository.enqueue(
            QueueItem(
                fileName = fileName,
                sourceUri = uri,
                options = SettingsRepository.snapshotOptions(targetVersion),
            )
        )
    }

    fun removeItem(id: String) {
        QueueRepository.update(id) { it.copy(status = QueueItem.Status.Failed("用户取消")) }
        // 注：当前是软删除（改 Failed），如果要硬删除，可以加 `removeIf` API
    }
}
