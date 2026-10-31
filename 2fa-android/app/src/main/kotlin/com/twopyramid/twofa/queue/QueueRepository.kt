package com.twopyramid.twofa.queue

import android.net.Uri
import com.twopyramid.twofa.settings.ConvertOptions
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
    /**
     * 入队瞬间冻结的转换选项快照（含 targetVersion，S18）。
     *
     * 为什么快照：转换选项是全局设置，但队列可能跨会话/跨设置修改存在；如果 Service 在
     * 执行时才读全局设置，用户会得到「和点击时看到的不一致」的结果。Service 消费时只读这份。
     */
    val options: ConvertOptions = ConvertOptions(),
    val status: Status = Status.Queued,
) {
    /** 目标 pack_format 便捷读取（真源是 [options] 里的快照）。 */
    val targetVersion: Int get() = options.targetVersion

    sealed class Status(val label: String) {
        data object Queued : Status("排队中…")
        data object Running : Status("转换中…")
        data class Done(
            val outputPath: String,
            /** 源 → 目标版本（Rust ConvertReport.sourceVersion；4a 阶段可能为 0） */
            val sourceVersion: Int = 0,
            /** 引擎跳过的任务/条目数（Rust ConvertReport.skipped） */
            val skipped: Int = 0,
            /** 引擎 notes：既有跳过原因也有普通进展，UI 不能统一标「跳过」 */
            val notes: List<String> = emptyList(),
            /** 转换耗时（Rust ConvertReport.elapsed_ms） */
            val elapsedMs: Long = 0L,
        ) : Status("完成 → ${outputPath.substringAfterLast('/')}")
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
        ProgressRepository.finish(id)
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
        ProgressRepository.clear()
    }
}
