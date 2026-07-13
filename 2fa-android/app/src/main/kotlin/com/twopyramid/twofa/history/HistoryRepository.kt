package com.twopyramid.twofa.history

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * 历史记录。Done 和 Failed 的 zip 都进历史，可在 History tab 搜索/重看/复制路径/重转。
 *
 * 持久化：用 [SharedPreferences] 存 JSON 数组（MVP 简化版；规模上 1000 条都 OK）。
 * 后续可换 Room 拿 indexed search + 自动分页。
 *
 * 不放 in-memory [com.twopyramid.twofa.queue.QueueRepository] 里 —— 那个跟 Service
 * 生命周期耦合，App 进程被回收就丢。历史需要长期可查。
 */
data class HistoryRecord(
    val id: String,
    val fileName: String,
    val timestamp: Long,                  // epoch ms
    val sourceVersion: Int,
    val targetVersion: Int,
    val outputPath: String?,              // null if Failed
    val status: Status,
    val durationMs: Long,                 // 转换耗时
) {
    sealed class Status {
        object Done : Status()
        data class Failed(val reason: String) : Status()

        fun label(): String = when (this) {
            is Done -> "完成"
            is Failed -> "失败：$reason"
        }
    }
}

object HistoryRepository {

    private const val PREFS = "twofa.history"
    private const val KEY_RECORDS = "records"
    private const val MAX_RECORDS = 500

    private lateinit var prefs: SharedPreferences
    private val _records = MutableStateFlow<List<HistoryRecord>>(emptyList())
    val records: StateFlow<List<HistoryRecord>> = _records.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _records.value = loadFromDisk()
    }

    fun add(record: HistoryRecord) {
        val updated = (listOf(record) + _records.value).take(MAX_RECORDS)
        _records.value = updated
        saveToDisk(updated)
    }

    fun remove(id: String) {
        val updated = _records.value.filter { it.id != id }
        _records.value = updated
        saveToDisk(updated)
    }

    fun clear() {
        _records.value = emptyList()
        saveToDisk(emptyList())
    }

    /**
     * 模糊搜索：fileName / outputPath 包含 query（不区分大小写）。
     * 排序：最新优先。
     */
    fun search(query: String): List<HistoryRecord> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return _records.value
        return _records.value.filter { r ->
            r.fileName.lowercase().contains(q) ||
                r.outputPath?.lowercase()?.contains(q) == true ||
                r.status.label().lowercase().contains(q)
        }
    }

    // ── 序列化（org.json，零依赖） ──

    private fun loadFromDisk(): List<HistoryRecord> {
        val raw = prefs.getString(KEY_RECORDS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i -> fromJson(arr.getJSONObject(i)) }
        }.getOrDefault(emptyList())
    }

    private fun saveToDisk(records: List<HistoryRecord>) {
        val arr = JSONArray()
        records.forEach { arr.put(toJson(it)) }
        prefs.edit().putString(KEY_RECORDS, arr.toString()).apply()
    }

    private fun toJson(r: HistoryRecord): JSONObject = JSONObject().apply {
        put("id", r.id)
        put("fileName", r.fileName)
        put("timestamp", r.timestamp)
        put("sourceVersion", r.sourceVersion)
        put("targetVersion", r.targetVersion)
        put("outputPath", r.outputPath ?: JSONObject.NULL)
        put("durationMs", r.durationMs)
        put("statusKind", if (r.status is HistoryRecord.Status.Done) "done" else "failed")
        if (r.status is HistoryRecord.Status.Failed) put("failureReason", r.status.reason)
    }

    private fun fromJson(o: JSONObject): HistoryRecord {
        val status = if (o.optString("statusKind") == "done") {
            HistoryRecord.Status.Done
        } else {
            HistoryRecord.Status.Failed(o.optString("failureReason", "未知错误"))
        }
        return HistoryRecord(
            id = o.optString("id"),
            fileName = o.optString("fileName"),
            timestamp = o.optLong("timestamp"),
            sourceVersion = o.optInt("sourceVersion"),
            targetVersion = o.optInt("targetVersion"),
            outputPath = o.optString("outputPath").takeUnless { it == "null" || it.isEmpty() },
            status = status,
            durationMs = o.optLong("durationMs"),
        )
    }
}
