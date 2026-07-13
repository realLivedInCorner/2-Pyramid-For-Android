package com.twopyramid.twofa.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.twopyramid.twofa.history.HistoryRecord
import com.twopyramid.twofa.history.HistoryRepository
import com.twopyramid.twofa.queue.QueueItem
import com.twopyramid.twofa.queue.QueueRepository
import com.twopyramid.twofa.settings.AppSettings
import com.twopyramid.twofa.ui.components.GlassCard
import com.twopyramid.twofa.ui.theme.BlueAccent
import com.twopyramid.twofa.ui.theme.CrimsonWarn
import com.twopyramid.twofa.ui.theme.GlassOutline
import com.twopyramid.twofa.ui.theme.GlassSurface
import com.twopyramid.twofa.ui.theme.GlassSurfaceHigh
import com.twopyramid.twofa.ui.theme.LocalTwoFATokens
import com.twopyramid.twofa.ui.theme.MintAccent
import com.twopyramid.twofa.ui.theme.OnSurfaceHigh
import com.twopyramid.twofa.ui.theme.OnSurfaceLow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * History tab：所有历史转换（Done + Failed），可搜索、点开复制路径或重新入队。
 *
 * 视觉：玻璃卡 group-card 风格，header 用大标题 + 玻璃搜索框。
 */
@Composable
fun HistoryScreen() {
    val all by HistoryRepository.records.collectAsState()
    var query by remember { mutableStateOf("") }
    val tokens = LocalTwoFATokens.current

    val filtered by remember {
        derivedStateOf {
            if (query.isBlank()) all
            else HistoryRepository.search(query)
        }
    }

    val ctx = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Header：标题 + 玻璃搜索框 ──
        HistoryTopBar(
            count = all.size,
            query = query,
            onQueryChange = { query = it },
            onClearQuery = { query = "" },
            onClearAll = { HistoryRepository.clear() },
        )

        Spacer(Modifier.height(8.dp))

        // ── 列表 ──
        if (filtered.isEmpty()) {
            HistoryEmpty(query = query)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(filtered, key = { it.id }) { record ->
                    HistoryRow(
                        record = record,
                        index = filtered.indexOf(record),
                        onCopy = { record.outputPath?.let { copyToClipboard(ctx, it) } },
                        onRetry = {
                            // 重新入队：缺 sourceUri（持久化历史只存 fileName），入空 URI
                            // Service 找不到文件会 Failed
                            QueueRepository.enqueue(
                                QueueItem(
                                    fileName = record.fileName,
                                    sourceUri = android.net.Uri.parse(""),
                                    targetVersion = record.targetVersion,
                                )
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryTopBar(
    count: Int,
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onClearAll: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "History",
                    style = MaterialTheme.typography.headlineLarge,
                    color = OnSurfaceLow,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "历史记录",
                    style = MaterialTheme.typography.displaySmall,
                    color = OnSurfaceHigh,
                    fontWeight = FontWeight.Black,
                )
            }
            if (count > 0) {
                IconButton(onClick = onClearAll) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "清空",
                        tint = OnSurfaceLow,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        // 玻璃风搜索框
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            elevation = 2.dp,
            shadowRadius = 12.dp,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = OnSurfaceLow,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = OnSurfaceHigh,
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(BlueAccent),
                    modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text(
                                "搜索文件名 / 路径 / 状态…",
                                style = MaterialTheme.typography.bodyLarge,
                                color = OnSurfaceLow,
                            )
                        }
                        inner()
                    },
                )
                AnimatedVisibility(
                    visible = query.isNotEmpty(),
                    enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 2 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 2 }),
                ) {
                    IconButton(onClick = onClearQuery, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "清空搜索",
                            tint = OnSurfaceLow,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryEmpty(query: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.5f))
        Text(
            if (query.isBlank()) "还没有历史记录" else "没找到匹配项",
            style = MaterialTheme.typography.titleMedium,
            color = OnSurfaceLow,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (query.isBlank()) "转一个 zip 试试"
            else "试试别的关键词，比如 .zip、1.21、失败",
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceLow.copy(alpha = 0.7f),
        )
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun HistoryRow(
    record: HistoryRecord,
    index: Int,
    onCopy: () -> Unit,
    onRetry: () -> Unit,
) {
    val tokens = LocalTwoFATokens.current
    val isDone = record.status is HistoryRecord.Status.Done
    val accent: Color = if (isDone) MintAccent else CrimsonWarn

    val visibleState = remember {
        androidx.compose.animation.core.MutableTransitionState(false)
    }
    androidx.compose.runtime.LaunchedEffect(record.id) {
        kotlinx.coroutines.delay(index * tokens.staggerDelayMs.toLong())
        visibleState.targetState = true
    }

    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(
            animationSpec = tween(tokens.entranceDurationMs, easing = tokens.staggerEasing)
        ) + slideInVertically(
            initialOffsetY = { it / 3 },
            animationSpec = tween(tokens.entranceDurationMs, easing = tokens.staggerEasing),
        ),
        exit = fadeOut(
            animationSpec = tween(tokens.exitDurationMs, easing = tokens.staggerEasing)
        ) + slideOutVertically(targetOffsetY = { it / 3 }),
    ) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { if (isDone) onCopy() },
            shape = RoundedCornerShape(20.dp),
            elevation = 2.dp,
            shadowRadius = 12.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp)
                    .padding(start = 0.dp, end = 8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxSize()
                        .background(accent),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Text(
                        record.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        color = OnSurfaceHigh,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // target version pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(BlueAccent.copy(alpha = 0.10f))
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        ) {
                            Text(
                                "→ ${AppSettings.versionShort(record.targetVersion)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = BlueAccent,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            formatTime(record.timestamp),
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceLow,
                        )
                        if (record.durationMs > 0) {
                            Text(
                                " · ${record.durationMs / 1000}s",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceLow,
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        record.status.label(),
                        style = MaterialTheme.typography.bodySmall,
                        color = accent,
                    )
                }
                if (isDone) {
                    IconButton(onClick = onCopy) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = "复制路径",
                            tint = OnSurfaceLow,
                        )
                    }
                } else {
                    IconButton(onClick = onRetry) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "重新尝试",
                            tint = OnSurfaceLow,
                        )
                    }
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
