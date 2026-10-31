package com.twopyramid.twofa.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.twopyramid.twofa.queue.ItemProgress
import com.twopyramid.twofa.queue.ProgressRepository
import com.twopyramid.twofa.queue.QueueItem
import com.twopyramid.twofa.queue.QueueRepository
import com.twopyramid.twofa.service.ZipQueueService
import com.twopyramid.twofa.settings.AppSettings
import com.twopyramid.twofa.settings.ConvertOptions
import com.twopyramid.twofa.settings.NamingPreset
import com.twopyramid.twofa.settings.SettingsRepository
import com.twopyramid.twofa.ui.Texts
import com.twopyramid.twofa.ui.components.GlassCard
import com.twopyramid.twofa.ui.components.PrimaryActionButton
import com.twopyramid.twofa.ui.components.SecondaryActionButton
import com.twopyramid.twofa.ui.theme.AmberNotice
import com.twopyramid.twofa.ui.theme.BlueAccent
import com.twopyramid.twofa.ui.theme.CrimsonWarn
import com.twopyramid.twofa.ui.theme.LocalTwoFATokens
import com.twopyramid.twofa.ui.theme.OnSurfaceHigh
import com.twopyramid.twofa.ui.theme.OnSurfaceLow
import com.twopyramid.twofa.ui.viewmodel.HomeViewModel
import java.util.Locale

/**
 * ConvertScreen：队列 + 添加 Zip + ItemDetail + 「选择目标版本与转换选项」对话框。
 *
 * v2.1（桌面 2.203.0 同步，L4/L5）：
 *  - 版本表 27 项（新增 26.3/97），状态徽章对齐桌面；
 *  - 目标版本对话框内嵌**转换选项区**（6 个用户可见开关 + 命名模板预设），
 *    危险项红色「会删文件」标记 + 二次确认；26.3/Bedrock 选择即非阻塞警示；
 *  - 真实进度链路：ProgressRepository（Service 只写 / UI 只收集），不再用本地假动画；
 *  - 入队后自动启动前台服务（旧版只有"重新转换"路径启动，S24）；
 *  - Android 13+ POST_NOTIFICATIONS 运行时权限（不阻塞转换）。
 *
 * `fix_alpha_layers` 是桌面 M3 后的死开关，**不在 UI 暴露**（字段保留在 ConvertOptions）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConvertScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val viewModel: HomeViewModel = viewModel()
    val items by viewModel.queue.collectAsState()
    val progressMap by ProgressRepository.items.collectAsState()
    val tokens = LocalTwoFATokens.current
    val lastTargetVersion by SettingsRepository.lastTargetVersion.collectAsState()

    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var targetVersion by remember { mutableStateOf(lastTargetVersion) }
    var detailItem by remember { mutableStateOf<QueueItem?>(null) }

    // ── 前台服务启动（Android 12+ 后台启动限制；Android 13+ 通知权限）──
    val startQueueService: () -> Unit = {
        ContextCompat.startForegroundService(ctx, Intent(ctx, ZipQueueService::class.java))
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ ->
        // 无论是否授权都继续（未授权只是看不到前台通知，转换本身不受影响）
        startQueueService()
    }
    val startQueueWithPermission: () -> Unit = {
        val needPermission =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    ctx,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
        if (needPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startQueueService()
        }
    }

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

    val onPickZip: () -> Unit = { pickZipLauncher.launch(arrayOf("application/zip", "*/*")) }

    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(modifier = Modifier.fillMaxSize()) {
        ConvertTopBar(
            itemCount = items.size,
            hasPending = items.any { it.status is QueueItem.Status.Queued },
            onBack = onBack,
            onAdd = onPickZip,
        )
        Box(modifier = Modifier.weight(1f)) {
            if (items.isEmpty()) {
                ConvertEmpty(onAdd = onPickZip)
            } else {
                ConvertList(
                    items = items,
                    tokens = tokens,
                    progress = progressMap,
                    onItemClick = { detailItem = it },
                )
            }
        }
    }

    if (pendingUri != null) {
        ConvertVersionDialog(
            fileName = pendingUri?.lastPathSegment ?: "pack.zip",
            currentVersion = targetVersion,
            versions = AppSettings.versions,
            onDismiss = { pendingUri = null },
            onConfirm = {
                pendingUri?.let { uri ->
                    viewModel.addFromSaf(uri, targetVersion)
                    SettingsRepository.setLastTargetVersion(targetVersion)
                    startQueueWithPermission()
                }
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
                    progress = progressMap[current.id],
                    onRemove = {
                        QueueRepository.remove(current.id)
                        detailItem = null
                    },
                    onRetry = {
                        QueueRepository.updateStatus(current.id, QueueItem.Status.Queued)
                        detailItem = null
                        startQueueWithPermission()
                    },
                )
            }
        }
    }
}

@Composable
private fun ConvertTopBar(
    itemCount: Int,
    hasPending: Boolean,
    onBack: () -> Unit,
    onAdd: () -> Unit,
) {
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
                Spacer(Modifier.width(4.dp))
            }
            // 顶部右侧 + 按钮（替代 v1.2.x 的右下 FAB，Material 3 标准模式）
            IconButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = "添加 Zip", tint = OnSurfaceHigh)
            }
        }
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
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                elevation = 2.dp,
                shadowRadius = 12.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(BlueAccent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.FolderZip,
                            contentDescription = null,
                            tint = BlueAccent,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "还没有待转换的 zip",
                        style = MaterialTheme.typography.titleLarge,
                        color = OnSurfaceHigh,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "从文件管理器里选一个 Minecraft 资源包",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceLow,
                    )
                    Spacer(Modifier.height(24.dp))
                    PrimaryActionButton(
                        text = "选择 Zip 文件",
                        onClick = onAdd,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "提示：选完文件后可以挑目标版本和转换选项",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceLow,
            )
        }
    }
}

@Composable
private fun ConvertList(
    items: List<QueueItem>,
    tokens: com.twopyramid.twofa.ui.theme.TwoFATokens,
    progress: Map<String, ItemProgress>,
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
                progress = progress[item.id],
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
    progress: ItemProgress?,
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
        ConvertRowCard(item = item, progress = progress, onClick = onClick)
    }
}

@Composable
private fun ConvertRowCard(item: QueueItem, progress: ItemProgress?, onClick: () -> Unit) {
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
                    val percent = progress?.percent ?: 0f
                    LinearProgressIndicator(
                        progress = { percent },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = BlueAccent,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        Texts.progressLine(progress?.phase, percent),
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceLow,
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

/** 危险开关（需要红色标记 + 二次确认的项）。 */
private enum class DangerKind { DELETE_SHADERS, DELETE_CIT }

@Composable
private fun ConvertVersionDialog(
    fileName: String,
    currentVersion: Int,
    versions: List<AppSettings.VersionEntry>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onVersionChange: (Int) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val options by SettingsRepository.convertOptions.collectAsState()
    val namingPreset by SettingsRepository.namingPreset.collectAsState()
    var pendingDanger by remember { mutableStateOf<DangerKind?>(null) }

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
    val grouped: List<Pair<AppSettings.VersionEra, List<AppSettings.VersionEntry>>> =
        remember(filtered) {
            AppSettings.VersionEra.entries
                .map { era -> era to filtered.filter { it.era == era } }
                .filter { it.second.isNotEmpty() }
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择目标版本与选项") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
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

                // era 分组 + version cards
                if (grouped.isEmpty()) {
                    Text(
                        "没找到匹配 \"$query\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceLow,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    grouped.forEach { (era, items) ->
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

                // ── 选择即警示（非阻塞，S2）──
                if (AppSettings.hasKnownIssue(currentVersion)) {
                    WarningCard(
                        title = Texts.java263WarnTitle,
                        body = Texts.java263WarnBody,
                        accent = AmberNotice,
                    )
                }
                if (AppSettings.isBedrock(currentVersion)) {
                    WarningCard(
                        title = Texts.bedrockWarnTitle,
                        body = Texts.bedrockWarnBody,
                        accent = AmberNotice,
                    )
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = OnSurfaceLow.copy(alpha = 0.15f))
                Spacer(Modifier.height(8.dp))

                // ── 转换选项区（7 个开关中 fix_alpha_layers 不暴露）──
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        Texts.optionsTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = OnSurfaceHigh,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { SettingsRepository.resetConversionOptions() }) {
                        Text(Texts.optionsReset, color = BlueAccent, style = MaterialTheme.typography.labelLarge)
                    }
                }
                Text(
                    Texts.optionsHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceLow,
                )
                Spacer(Modifier.height(4.dp))

                ConversionOptionsSection(
                    options = options,
                    namingPreset = namingPreset,
                    onDangerRequest = { kind -> pendingDanger = kind },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(Texts.startConvert, color = BlueAccent, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Texts.cancel) }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )

    // 危险开关的二次确认
    pendingDanger?.let { kind ->
        DangerConfirmDialog(
            kind = kind,
            onConfirm = {
                when (kind) {
                    DangerKind.DELETE_SHADERS -> SettingsRepository.setDeleteShaders(true)
                    DangerKind.DELETE_CIT -> SettingsRepository.setDeleteCit(true)
                }
                pendingDanger = null
            },
            onDismiss = { pendingDanger = null },
        )
    }
}

/** 转换选项列表（转换对话框内使用；设置页显示摘要，不重复渲染）。 */
@Composable
private fun ConversionOptionsSection(
    options: ConvertOptions,
    namingPreset: NamingPreset,
    onDangerRequest: (DangerKind) -> Unit,
) {
    // adapt_shaders：默认 true，实验功能
    ConversionSwitchRow(
        label = Texts.adaptShadersLabel,
        hint = Texts.adaptShadersHint,
        checked = options.adaptShaders,
        onCheckedChange = { SettingsRepository.setAdaptShaders(it) },
    )
    // delete_shaders：破坏性，默认 false，红色标记 + 二次确认
    ConversionSwitchRow(
        label = Texts.deleteShadersLabel,
        hint = Texts.deleteShadersHint,
        checked = options.deleteShaders,
        destructive = true,
        onCheckedChange = { enabled ->
            if (enabled) onDangerRequest(DangerKind.DELETE_SHADERS)
            else SettingsRepository.setDeleteShaders(false)
        },
    )
    // convert_item_definitions：默认 true
    ConversionSwitchRow(
        label = Texts.convertItemDefinitionsLabel,
        hint = Texts.convertItemDefinitionsHint,
        checked = options.convertItemDefinitions,
        onCheckedChange = { SettingsRepository.setConvertItemDefinitions(it) },
    )
    // convert_cit：默认 false（止血）；delete_cit 开启时禁用（删除优先）
    ConversionSwitchRow(
        label = Texts.convertCitLabel,
        hint = if (options.deleteCit) Texts.deleteCitOverridesConvertCit else Texts.convertCitHint,
        checked = options.convertCit,
        enabled = !options.deleteCit,
        onCheckedChange = { SettingsRepository.setConvertCit(it) },
    )
    // delete_cit：破坏性，默认 false，优先级高于 convert_cit
    ConversionSwitchRow(
        label = Texts.deleteCitLabel,
        hint = Texts.deleteCitHint,
        checked = options.deleteCit,
        destructive = true,
        onCheckedChange = { enabled ->
            if (enabled) onDangerRequest(DangerKind.DELETE_CIT)
            else SettingsRepository.setDeleteCit(false)
        },
    )
    // write_gen_metadata：默认 true（仅 Java 目标写 2pyr-gen.json）
    ConversionSwitchRow(
        label = Texts.genMetadataLabel,
        hint = Texts.genMetadataHint,
        checked = options.writeGenMetadata,
        onCheckedChange = { SettingsRepository.setWriteGenMetadata(it) },
    )

    Spacer(Modifier.height(10.dp))
    // 命名模板预设
    Text(
        Texts.namingTitle,
        style = MaterialTheme.typography.titleMedium,
        color = OnSurfaceHigh,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        Texts.namingHint,
        style = MaterialTheme.typography.bodySmall,
        color = OnSurfaceLow,
    )
    Spacer(Modifier.height(6.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        NamingPreset.entries.forEach { preset ->
            val active = preset == namingPreset
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (active) BlueAccent.copy(alpha = 0.14f)
                        else OnSurfaceLow.copy(alpha = 0.06f)
                    )
                    .clickable { SettingsRepository.setNamingPreset(preset) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    preset.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) BlueAccent else OnSurfaceLow,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    Text(
        namingPreset.desc,
        style = MaterialTheme.typography.bodySmall,
        color = OnSurfaceLow,
    )
}

@Composable
private fun ConversionSwitchRow(
    label: String,
    hint: String,
    checked: Boolean,
    destructive: Boolean = false,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = OnSurfaceHigh,
                        fontWeight = FontWeight.Medium,
                    )
                    if (destructive) {
                        Spacer(Modifier.width(6.dp))
                        DangerTag()
                    }
                }
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceLow,
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
                colors = if (destructive) {
                    SwitchDefaults.colors(checkedTrackColor = CrimsonWarn)
                } else {
                    SwitchDefaults.colors()
                },
            )
        }
    }
}

@Composable
private fun DangerTag() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(CrimsonWarn.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            Texts.dangerTag,
            style = MaterialTheme.typography.labelSmall,
            color = CrimsonWarn,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun WarningCard(title: String, body: String, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceLow,
            )
        }
    }
}

@Composable
private fun DangerConfirmDialog(
    kind: DangerKind,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title: String
    val body: String
    when (kind) {
        DangerKind.DELETE_SHADERS -> {
            title = Texts.deleteShadersConfirmTitle
            body = Texts.deleteShadersConfirmBody
        }
        DangerKind.DELETE_CIT -> {
            title = Texts.deleteCitConfirmTitle
            body = Texts.deleteCitConfirmBody
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body, color = OnSurfaceLow) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(Texts.confirmEnable, color = CrimsonWarn, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Texts.cancel) } },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
private fun StatusBadge(status: AppSettings.VersionStatus) {
    val (bg, fg) = when (status) {
        AppSettings.VersionStatus.STABLE ->
            Color(0xFF14532D) to Color(0xFFA8FFB0)  // 绿
        AppSettings.VersionStatus.LATEST ->
            BlueAccent to Color.White  // 蓝
        AppSettings.VersionStatus.RECOMMENDED ->
            Color(0xFFB45309) to Color(0xFFFEF3C7)  // 琥珀
        AppSettings.VersionStatus.BETA ->
            Color(0xFF4C1D95) to Color(0xFFE9D5FF)  // 紫
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
    progress: ItemProgress?,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    var notesExpanded by remember(item.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
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
                // 结果区（4b 真数据；4a 阶段 sourceVersion/skipped/notes 为 DTO 默认值）
                if (s.sourceVersion > 0) {
                    SheetInfoLine(
                        "版本",
                        "${AppSettings.versionShort(s.sourceVersion)} → " +
                            AppSettings.versionShort(item.targetVersion),
                    )
                    Spacer(Modifier.height(10.dp))
                }
                SheetInfoLine("输出路径", s.outputPath)
                if (s.elapsedMs > 0) {
                    Spacer(Modifier.height(10.dp))
                    SheetInfoLine("耗时", formatElapsed(s.elapsedMs))
                }
                if (s.skipped > 0) {
                    Spacer(Modifier.height(10.dp))
                    SheetInfoLine("跳过的任务数", "${s.skipped}")
                }
                if (s.notes.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    NotesSection(
                        notes = s.notes,
                        expanded = notesExpanded,
                        onToggle = { notesExpanded = !notesExpanded },
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SecondaryActionButton(text = "移除", onClick = onRemove)
                    PrimaryActionButton(
                        text = "复制路径",
                        onClick = {
                            runCatching {
                                clipboardManager.setText(AnnotatedString(s.outputPath))
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
                val percent = progress?.percent ?: 0f
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { percent },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = BlueAccent,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    Texts.progressLine(progress?.phase, percent),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceLow,
                )
            }
            is QueueItem.Status.Queued -> {
                Spacer(Modifier.height(16.dp))
                PrimaryActionButton(text = "开始转换", onClick = onRetry)
            }
        }
    }
}

/**
 * 引擎备注（notes）：**既有跳过原因也有普通进展**（如"写出 12 个物品定义"），
 * 所以标题用「引擎备注」而不是「跳过原因」（S17 的语义要求）。
 */
@Composable
private fun NotesSection(notes: List<String>, expanded: Boolean, onToggle: () -> Unit) {
    Column {
        Text(
            "引擎备注（含跳过原因与普通进展）",
            style = MaterialTheme.typography.labelLarge,
            color = OnSurfaceLow,
        )
        Spacer(Modifier.height(4.dp))
        val visible = if (expanded) notes else notes.take(3)
        visible.forEach { note ->
            Text(
                "· $note",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceLow,
            )
        }
        if (notes.size > 3) {
            TextButton(onClick = onToggle) {
                Text(
                    if (expanded) "收起" else "展开全部（${notes.size} 条）",
                    color = BlueAccent,
                    style = MaterialTheme.typography.labelLarge,
                )
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

private fun formatElapsed(ms: Long): String =
    String.format(Locale.US, "%.1fs", ms / 1000.0)
