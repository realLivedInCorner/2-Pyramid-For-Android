package com.twopyramid.twofa.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.twopyramid.twofa.queue.QueueItem
import com.twopyramid.twofa.queue.QueueRepository
import com.twopyramid.twofa.service.ZipQueueService
import com.twopyramid.twofa.settings.AppSettings
import com.twopyramid.twofa.ui.components.GlassCard
import com.twopyramid.twofa.ui.components.PrimaryActionButton
import com.twopyramid.twofa.ui.components.SecondaryActionButton
import com.twopyramid.twofa.ui.theme.BlueAccent
import com.twopyramid.twofa.ui.theme.LocalTwoFATokens
import com.twopyramid.twofa.ui.theme.OnSurfaceHigh
import com.twopyramid.twofa.ui.theme.OnSurfaceLow
import com.twopyramid.twofa.ui.theme.Slate100
import com.twopyramid.twofa.ui.viewmodel.HomeViewModel

/**
 * ConvertScreen：队列 + 添加 Zip + ItemDetail + 选 target version dialog。
 *
 * 拆自 HomeScreen v1（让 Home 变介绍页 + 入口）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConvertScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val viewModel: HomeViewModel = viewModel()
    val items by viewModel.queue.collectAsState()
    val tokens = LocalTwoFATokens.current

    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var targetVersion by remember { mutableStateOf(AppSettings.defaultTargetVersion) }
    var detailItem by remember { mutableStateOf<QueueItem?>(null) }

    val pickZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            pendingUri = uri
        }
    }

    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ConvertTopBar(
                itemCount = items.size,
                hasPending = items.any { it.status is QueueItem.Status.Queued },
                onBack = onBack,
            )
            Box(modifier = Modifier.weight(1f)) {
                if (items.isEmpty()) {
                    ConvertEmpty(onAdd = { pickZipLauncher.launch(arrayOf("application/zip", "*/*")) })
                } else {
                    ConvertList(
                        items = items,
                        tokens = tokens,
                        onItemClick = { detailItem = it },
                    )
                }
            }
        }

        // FAB 不进 Column，留底右下
        ConvertFab(
            hasPending = items.any { it.status is QueueItem.Status.Queued },
            onStart = {
                val intent = Intent(ctx, ZipQueueService::class.java)
                ctx.startService(intent)
            },
            onAdd = { pickZipLauncher.launch(arrayOf("application/zip", "*/*")) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 12.dp),
        )
    }

    if (pendingUri != null) {
        ConvertVersionDialog(
            fileName = pendingUri?.lastPathSegment ?: "pack.zip",
            currentVersion = targetVersion,
            versions = AppSettings.versions,
            // (v1.2.2 ConvertVersionDialog 改用 VersionEntry，不需要 versionLabel 传参)
            onDismiss = { pendingUri = null },
            onConfirm = { chosen ->
                pendingUri?.let { viewModel.addFromSaf(it, chosen) }
                pendingUri = null
            },
            onVersionChange = { targetVersion = it },
        )
    }

    if (detailItem != null) {
        val current = items.firstOrNull { it.id == detailItem!!.id }
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { detailItem = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            if (current != null) {
                ConvertDetailSheet(
                    item = current,
                    onRemove = {
                        QueueRepository.remove(current.id)
                        detailItem = null
                    },
                    onRetry = {
                        QueueRepository.updateStatus(current.id, QueueItem.Status.Queued)
                        detailItem = null
                        ctx.startService(Intent(ctx, ZipQueueService::class.java))
                    },
                )
            }
        }
    }
}

@Composable
private fun ConvertTopBar(itemCount: Int, hasPending: Boolean, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = OnSurfaceHigh)
            }
            Spacer(Modifier.width(4.dp))
            Text(
                "转换",
                style = MaterialTheme.typography.headlineLarge,
                color = OnSurfaceHigh,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.weight(1f))
            if (itemCount > 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(BlueAccent.copy(alpha = 0.10f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        if (hasPending) "$itemCount 项待转换" else "$itemCount 项",
                        style = MaterialTheme.typography.labelLarge,
                        color = BlueAccent,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConvertFab(
    hasPending: Boolean,
    onStart: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        AnimatedVisibility(
            visible = hasPending,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
        ) {
            ExtendedFloatingActionButton(
                onClick = onStart,
                icon = { Icon(Icons.Default.PlayArrow, null) },
                text = { Text("开始转换") },
                containerColor = BlueAccent,
                contentColor = Color.White,
            )
        }
        ExtendedFloatingActionButton(
            onClick = onAdd,
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("添加 Zip") },
            containerColor = OnSurfaceHigh,
            contentColor = Color.White,
        )
    }
}

@Composable
private fun ConvertEmpty(modifier: Modifier = Modifier, onAdd: () -> Unit) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + scaleIn(initialScale = 0.96f),
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.5f))
            Text(
                "还没有待转换的 zip",
                style = MaterialTheme.typography.headlineSmall,
                color = OnSurfaceHigh,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "点右下角「添加 Zip」开始",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceLow,
            )
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ConvertList(
    items: List<QueueItem>,
    tokens: com.twopyramid.twofa.ui.theme.TwoFATokens,
    onItemClick: (QueueItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
    ) {
        items(items, key = { it.id }) { item ->
            ConvertRow(
                item = item,
                index = items.indexOf(item),
                tokens = tokens,
                onClick = { onItemClick(item) },
            )
        }
    }
}

@Composable
private fun ConvertRow(
    item: QueueItem,
    index: Int,
    tokens: com.twopyramid.twofa.ui.theme.TwoFATokens,
    onClick: () -> Unit,
) {
    val visibleState = remember { androidx.compose.animation.core.MutableTransitionState(false) }
    androidx.compose.runtime.LaunchedEffect(item.id) {
        kotlinx.coroutines.delay(index * tokens.staggerDelayMs.toLong())
        visibleState.targetState = true
    }
    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = tokens.entranceDurationMs,
                easing = tokens.staggerEasing,
            ),
        ) + slideInVertically(
            initialOffsetY = { it / 4 },
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = tokens.entranceDurationMs,
                easing = tokens.staggerEasing,
            ),
        ),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 4 }),
    ) {
        ConvertRowCard(item = item, onClick = onClick)
    }
}

@Composable
private fun ConvertRowCard(item: QueueItem, onClick: () -> Unit) {
    val accent: Color = when (item.status) {
        is QueueItem.Status.Done    -> Color(0xFF22C55E)
        is QueueItem.Status.Failed  -> MaterialTheme.colorScheme.error
        is QueueItem.Status.Running -> BlueAccent
        is QueueItem.Status.Queued  -> OnSurfaceLow.copy(alpha = 0.6f)
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        elevation = 1.dp,
        shadowRadius = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .padding(end = 8.dp),
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
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    item.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    color = OnSurfaceHigh,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(BlueAccent.copy(alpha = 0.10f))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    ) {
                        Text(
                            "→ ${AppSettings.versionShort(item.targetVersion)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = BlueAccent,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        item.status.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceLow,
                    )
                }
                if (item.status is QueueItem.Status.Running) {
                    Spacer(Modifier.height(8.dp))
                    val animProgress by animateFloatAsState(
                        targetValue = 0.4f,
                        animationSpec = androidx.compose.animation.core.tween(1200),
                        label = "indeterminate",
                    )
                    LinearProgressIndicator(
                        progress = { animProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = BlueAccent,
                        trackColor = Slate100,
                    )
                }
            }

            if (item.status is QueueItem.Status.Done || item.status is QueueItem.Status.Failed) {
                IconButton(onClick = { QueueRepository.remove(item.id) }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "移除",
                        tint = OnSurfaceLow.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConvertVersionDialog(
    fileName: String,
    currentVersion: Int,
    versions: List<com.twopyramid.twofa.settings.AppSettings.VersionEntry>,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    onVersionChange: (Int) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, versions) {
        if (query.isBlank()) versions
        else versions.filter {
            it.packFormat.toString().contains(query) ||
                it.label.contains(query, ignoreCase = true) ||
                it.range.contains(query, ignoreCase = true) ||
                it.era.label.contains(query, ignoreCase = true)
        }
    }

    // 按 era 分组
    val grouped: List<Pair<com.twopyramid.twofa.settings.AppSettings.VersionEra, List<com.twopyramid.twofa.settings.AppSettings.VersionEntry>>> =
        remember(filtered) {
            com.twopyramid.twofa.settings.AppSettings.VersionEra.entries
                .map { era -> era to filtered.filter { it.era == era } }
                .filter { it.second.isNotEmpty() }
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择目标版本") },
        text = {
            Column {
                Text(
                    fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceLow,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                // 搜索框
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(OnSurfaceLow.copy(alpha = 0.06f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = OnSurfaceLow,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.foundation.text.BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = OnSurfaceHigh),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(BlueAccent),
                        modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                        decorationBox = { inner ->
                            if (query.isEmpty()) {
                                Text("搜索版本（1.20 / 19 / 试炼...）", color = OnSurfaceLow)
                            }
                            inner()
                        },
                    )
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { query = "" },
                            modifier = Modifier.size(20.dp),
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "清空",
                                tint = OnSurfaceLow,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                // era 分组 + cards 列表
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (grouped.isEmpty()) {
                        Text(
                            "没找到匹配 \"$query\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceLow,
                            modifier = Modifier.padding(16.dp),
                        )
                    } else {
                        grouped.forEach { (era, items) ->
                            // era 标题
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    era.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = BlueAccent,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "· ${era.desc}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceLow,
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "${items.size}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = OnSurfaceLow,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            // 该 era 的 version cards
                            items.forEach { v ->
                                val isSelected = v.packFormat == currentVersion
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isSelected) BlueAccent.copy(alpha = 0.12f)
                                            else OnSurfaceLow.copy(alpha = 0.04f)
                                        )
                                        .clickable { onVersionChange(v.packFormat) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                v.label,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = if (isSelected) BlueAccent else OnSurfaceHigh,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                            )
                                            // status badge
                                            v.status?.let { s ->
                                                Spacer(Modifier.width(6.dp))
                                                StatusBadge(s)
                                            }
                                        }
                                        Text(
                                            v.range,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = OnSurfaceLow,
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "pack_format ${v.packFormat}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected) BlueAccent else OnSurfaceLow,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    if (isSelected) {
                                        Spacer(Modifier.width(8.dp))
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = BlueAccent,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(currentVersion) }) {
                Text("开始转换", color = BlueAccent, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
private fun StatusBadge(status: com.twopyramid.twofa.settings.AppSettings.VersionStatus) {
    val (bg, fg) = when (status) {
        com.twopyramid.twofa.settings.AppSettings.VersionStatus.STABLE ->
            Color(0xFF14532D) to Color(0xFFA8FFB0)  // 绿
        com.twopyramid.twofa.settings.AppSettings.VersionStatus.LATEST ->
            BlueAccent to Color.White  // 蓝
        com.twopyramid.twofa.settings.AppSettings.VersionStatus.RECOMMENDED ->
            Color(0xFFB45309) to Color(0xFFFEF3C7)  // 琥珀
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg.copy(alpha = 0.18f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            status.label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ConvertDetailSheet(
    item: QueueItem,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
) {
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .padding(bottom = 32.dp),
    ) {
        Text(
            item.fileName,
            style = MaterialTheme.typography.headlineSmall,
            color = OnSurfaceHigh,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "目标版本：${AppSettings.versionLabel(item.targetVersion)}",
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceLow,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            item.status.label,
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceLow,
        )
        Spacer(Modifier.height(20.dp))

        when (val s = item.status) {
            is QueueItem.Status.Done -> {
                SheetInfoLine("输出路径", s.outputPath)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SecondaryActionButton(text = "移除", onClick = onRemove)
                    PrimaryActionButton(
                        text = "复制路径",
                        onClick = {
                            runCatching {
                                clipboardManager.setText(
                                    androidx.compose.ui.text.AnnotatedString(s.outputPath),
                                )
                            }
                        },
                    )
                }
            }
            is QueueItem.Status.Failed -> {
                SheetInfoLine("失败原因", s.reason)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SecondaryActionButton(text = "移除", onClick = onRemove)
                    PrimaryActionButton(text = "重新转换", onClick = onRetry)
                }
            }
            is QueueItem.Status.Running -> {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = BlueAccent,
                    trackColor = Slate100,
                )
            }
            is QueueItem.Status.Queued -> {
                Spacer(Modifier.height(16.dp))
                PrimaryActionButton(text = "开始转换", onClick = onRetry)
            }
        }
    }
}

@Composable
private fun SheetInfoLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = OnSurfaceLow)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceHigh)
    }
}
