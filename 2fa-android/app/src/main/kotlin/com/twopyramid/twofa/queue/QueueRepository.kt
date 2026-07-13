package com.twopyramid.twofa.queue

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * 队列状态仓库：跨进程 / 跨组件共享队列数据。
 *
 * - [HomeViewModel] 添加 zip 时 push 新 QueueItem
 * - [ZipQueueService] 启动消费时按入队顺序处理
 * - 处理结果（Running → Done/Failed）由 Service 写回，所有订阅者（UI）自动更新
 *
 * 用 MutableStateFlow 而不是 LiveData：
 *   1. Compose 的 collectAsState 原生支持
 *   2. Service 用 `lifecycleScope.launch { snapshot.first { ... } }` 也能消费
 */
data class QueueItem(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val sourceUri: Uri,
    /** 目标 pack_format；添加 zip 时由用户在 HomeScreen 选择（不再是 Settings 的全局默认）。 */
    val targetVersion: Int,
    val status: Status = Status.Queued,
) {
    sealed class Status(val label: String) {
        data object Queued : Status("排队中…")
        data object Running : Status("转换中…")
        data class Done(val outputPath: String) : Status("完成 → ${outputPath.substringAfterLast('/')}")
        data class Failed(val reason: String) : Status("失败：$reason")
    }
}

object QueueRepository {

    private val _items = MutableStateFlow<List<QueueItem>>(emptyList())
    val items: StateFlow<List<QueueItem>> = _items.asStateFlow()

    private val _outputDir = MutableStateFlow<String?>(null)
    val outputDir: StateFlow<String?> = _outputDir.asStateFlow()

    fun enqueue(item: QueueItem) {
        _items.value = _items.value + item
    }

    fun snapshotPending(): List<QueueItem> =
        _items.value.filter {
            it.status is QueueItem.Status.Queued || it.status is QueueItem.Status.Running
        }

    fun update(id: String, transform: (QueueItem) -> QueueItem) {
        _items.value = _items.value.map { if (it.id == id) transform(it) else it }
    }

    fun remove(id: String) {
        _items.value = _items.value.filter { it.id != id }
    }

    fun updateStatus(id: String, status: QueueItem.Status) {
        update(id) { it.copy(status = status) }
    }

    fun setOutputDir(dir: String) {
        _outputDir.value = dir
    }

    fun currentOutputDir(): String =
        _outputDir.value ?: error("Output directory not set — call setOutputDir() first")

    fun clear() {
        _items.value = emptyList()
    }
}
