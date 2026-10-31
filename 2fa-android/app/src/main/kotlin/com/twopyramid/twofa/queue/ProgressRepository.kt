package com.twopyramid.twofa.queue

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 单个队列项的实时进度。
 *
 * @param percent 0..1，**单调不回退**（并行任务乱序回调也不会让进度倒退）
 * @param phase Rust 回调的 phase 短串：`extract` / `convert` / `output` / 任务名；
 *              UI 层负责翻译成中文文案（见 `ui/Texts.phaseLabel`）
 */
data class ItemProgress(
    val percent: Float = 0f,
    val phase: String = "",
)

/**
 * 全局进度仓库（进度链路骨架，S24）。
 *
 * 背景：旧实现把进度藏在 `ZipQueueService` 实例内的 StateFlow 里，而 `onBind` 返回 null，
 * UI **根本拿不到**（ConvertScreen 只能用本地假动画）。这里改成进程级单例：
 *  - **Service 只写**：转换开始/回调/结束调用 [start] / [update] / [finish]；
 *  - **UI 只收集**：`ConvertScreen` 通过 `collectAsState()` 订阅 [items]，按 queue item id 取进度；
 *  - 单调：同一项内 percent 只增不减，phase 可随时更新（任务名变化）。
 *
 * 4a 阶段 Service 写入的仍是占位值（0.1 / 0.9）；4b 接 Rust ConvertProgress 回调后
 * 只需替换写入来源，UI/仓库链路不变。
 */
object ProgressRepository {

    // phase 约定（与 CONTRACT §4.1 的短串一致）
    const val PHASE_EXTRACT = "extract"
    const val PHASE_CONVERT = "convert"
    const val PHASE_OUTPUT = "output"

    private val _items = MutableStateFlow<Map<String, ItemProgress>>(emptyMap())
    val items: StateFlow<Map<String, ItemProgress>> = _items.asStateFlow()

    /** 转换开始：登记该项，从 0% 起步。 */
    fun start(itemId: String) {
        _items.update { it + (itemId to ItemProgress(0f, "")) }
    }

    /**
     * 写进度。percent 会被 coerce 到 [0,1] 并与已有值取 max（单调）；
     * phase 为空时保留旧值（回调偶发空串不丢文案）。
     */
    fun update(itemId: String, percent: Double, phase: String) {
        val clamped = percent.coerceIn(0.0, 1.0).toFloat()
        _items.update { map ->
            val prev = map[itemId] ?: ItemProgress()
            val merged = ItemProgress(
                percent = maxOf(prev.percent, clamped),
                phase = phase.ifBlank { prev.phase },
            )
            map + (itemId to merged)
        }
    }

    /** 当前项的 phase（无则空串）。 */
    fun phase(itemId: String): String = _items.value[itemId]?.phase ?: ""

    /** 当前项的 percent（无则 0）。 */
    fun percent(itemId: String): Float = _items.value[itemId]?.percent ?: 0f

    /** 转换结束（成功/失败/取消都调）：从 map 移除，避免 UI 残留 Running 进度。 */
    fun finish(itemId: String) {
        _items.update { it - itemId }
    }

    /** 全部清空（例如队列被清空）。 */
    fun clear() {
        _items.value = emptyMap()
    }
}
