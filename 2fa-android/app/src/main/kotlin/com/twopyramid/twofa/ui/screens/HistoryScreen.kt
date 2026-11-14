package com.twopyramid.twofa.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.twopyramid.twofa.history.HistoryRecord
import com.twopyramid.twofa.history.HistoryRepository
import com.twopyramid.twofa.queue.QueueItem
import com.twopyramid.twofa.queue.QueueRepository
import com.twopyramid.twofa.settings.AppSettings
import com.twopyramid.twofa.settings.SettingsRepository
import com.twopyramid.twofa.ui.components.BrandConfirmDialog
import com.twopyramid.twofa.ui.components.BrandPanel
import com.twopyramid.twofa.ui.components.IconGlyphButton
import com.twopyramid.twofa.ui.components.PageTopBar
import com.twopyramid.twofa.ui.components.SearchField
import com.twopyramid.twofa.ui.components.StatusBadge
import com.twopyramid.twofa.ui.components.StatusTone
import com.twopyramid.twofa.ui.components.VersionChip
import com.twopyramid.twofa.ui.theme.AppText
import com.twopyramid.twofa.ui.theme.Paper
import com.twopyramid.twofa.ui.theme.motion
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ══ History（mapping §4）═══════════════════════════════════════════════
//
// 列表行：surface + 1dp 发丝线，无卡片投影 / 无玻璃；状态徽章 ok / danger / ink3。
// 搜索框 8dp 圆角 + 发丝线；清空历史改为统一弹窗壳的二次确认（不再瞬时清空）。

@Composable
fun HistoryScreen() {
    val all by HistoryRepository.records.collectAsState()
    var query by remember { mutableStateOf("") }
    var showClearConfirm by remember { mutableStateOf(false) }
    val c = Paper.colors

    val filtered by remember {
        derivedStateOf {
            if (query.isBlank()) all else HistoryRepository.search(query)
        }
    }

    val ctx = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        PageTopBar(
            title = "历史记录",
            trailing = {
                if (all.isNotEmpty()) {
                    Text(
                        "${all.size}",
                        style = AppText.numeric,
                        color = c.ink3,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    IconGlyphButton(
                        icon = Icons.Filled.DeleteSweep,
                        contentDescription = "清空历史",
                        onClick = { showClearConfirm = true },
                        tint = c.ink3,
                        size = 32.dp,
                    )
                }
            },
        )

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            SearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "搜索文件名 / 路径 / 状态…",
            )
        }

        Spacer(Modifier.height(12.dp))

        if (filtered.isEmpty()) {
            EmptyHistory(query = query)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filtered, key = { it.id }) { record ->
                    HistoryRow(
                        record = record,
                        onCopy = { record.outputPath?.let { copyToClipboard(ctx, it) } },
                        onRetry = {
                            // 重新入队：持久化历史只存 fileName，缺持久化 SAF 权限时 sourceUri 为空，
                            // Service 找不到文件会 Failed（已知遗留，待后续修）。
                            QueueRepository.enqueue(
                                QueueItem(
                                    fileName = record.fileName,
                                    sourceUri = android.net.Uri.parse(""),
                                    options = SettingsRepository.snapshotOptions(record.targetVersion),
                                ),
                            )
                        },
                    )
                }
            }
        }
    }

    if (showClearConfirm) {
        BrandConfirmDialog(
            title = "清空历史记录？",
            body = "所有转换记录（完成 + 失败）都会被删除，此操作不可撤销。",
            confirmLabel = "清空",
            onConfirm = {
                HistoryRepository.clear()
                showClearConfirm = false
            },
            onDismiss = { showClearConfirm = false },
        )
    }
}

@Composable
private fun EmptyHistory(query: String) {
    val c = Paper.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (query.isBlank()) "还没有历史记录" else "没找到匹配项",
            style = AppText.itemTitle.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
            color = c.ink2,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (query.isBlank()) "转一个 zip 试试" else "试试别的关键词，比如 .zip、1.21、失败",
            style = AppText.secondary,
            color = c.ink3,
        )
    }
}

@Composable
private fun HistoryRow(
    record: HistoryRecord,
    onCopy: () -> Unit,
    onRetry: () -> Unit,
) {
    val c = Paper.colors
    val m = motion
    val isDone = record.status is HistoryRecord.Status.Done
    val tone = if (isDone) StatusTone.OK else StatusTone.DANGER

    val visibleState = remember { androidx.compose.animation.core.MutableTransitionState(false) }
    androidx.compose.runtime.LaunchedEffect(record.id) { visibleState.targetState = true }

    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = if (m.reduced) m.fadeMs() else m.ms(com.twopyramid.twofa.ui.theme.Motion.base),
                easing = com.twopyramid.twofa.ui.theme.Motion.easeOut,
            ),
        ),
        exit = fadeOut(animationSpec = tween(durationMillis = m.fadeMs())),
    ) {
        BrandPanel(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { if (isDone) onCopy() },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            record.fileName,
                            style = AppText.itemTitle,
                            color = c.ink,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(8.dp))
                        StatusBadge(
                            label = if (isDone) "完成" else "失败",
                            tone = tone,
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VersionChip("→ ${AppSettings.versionShort(record.targetVersion)}")
                        Spacer(Modifier.width(8.dp))
                        Text(
                            formatTime(record.timestamp),
                            style = AppText.numeric,
                            color = c.ink3,
                        )
                        if (record.durationMs > 0) {
                            Text(
                                " · ${record.durationMs / 1000}s",
                                style = AppText.numeric,
                                color = c.ink3,
                            )
                        }
                    }
                    if (!isDone) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            (record.status as HistoryRecord.Status.Failed).reason,
                            style = AppText.tagline,
                            color = c.danger,
                            maxLines = 2,
                        )
                    }
                    if (record.skipped > 0 || record.notes.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            buildString {
                                if (record.skipped > 0) append("跳过 ${record.skipped} 项")
                                if (record.notes.isNotEmpty()) {
                                    if (isNotEmpty()) append(" · ")
                                    append("引擎备注 ${record.notes.size} 条")
                                }
                            },
                            style = AppText.tagline,
                            color = c.ink3,
                        )
                    }
                }
                Spacer(Modifier.width(4.dp))
                if (isDone) {
                    IconGlyphButton(
                        icon = Icons.Filled.ContentCopy,
                        contentDescription = "复制路径",
                        onClick = onCopy,
                        tint = c.ink3,
                        size = 32.dp,
                        iconSize = 16.dp,
                    )
                } else {
                    IconGlyphButton(
                        icon = Icons.Filled.Refresh,
                        contentDescription = "重新尝试",
                        onClick = onRetry,
                        tint = c.ink3,
                        size = 32.dp,
                        iconSize = 16.dp,
                    )
                }
            }
        }
    }
}

private fun formatTime(ts: Long): String {
    val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return fmt.format(Date(ts))
}

private fun copyToClipboard(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("2-Pyramid output path", text))
}
