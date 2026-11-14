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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
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
import com.twopyramid.twofa.ui.components.BrandConfirmDialog
import com.twopyramid.twofa.ui.components.BrandDialog
import com.twopyramid.twofa.ui.components.BrandPanel
import com.twopyramid.twofa.ui.components.BrandSheet
import com.twopyramid.twofa.ui.components.DangerTag
import com.twopyramid.twofa.ui.components.DialogButtonTone
import com.twopyramid.twofa.ui.components.DialogTextButton
import com.twopyramid.twofa.ui.components.HairlineDivider
import com.twopyramid.twofa.ui.components.IconGlyphButton
import com.twopyramid.twofa.ui.components.InfoLine
import com.twopyramid.twofa.ui.components.PageTopBar
import com.twopyramid.twofa.ui.components.PrimaryActionButton
import com.twopyramid.twofa.ui.components.SearchField
import com.twopyramid.twofa.ui.components.SecondaryActionButton
import com.twopyramid.twofa.ui.components.StatusBadge
import com.twopyramid.twofa.ui.components.StatusTone
import com.twopyramid.twofa.ui.components.VersionChip
import com.twopyramid.twofa.ui.components.WarnBanner
import com.twopyramid.twofa.ui.theme.AppText
import com.twopyramid.twofa.ui.theme.Motion
import com.twopyramid.twofa.ui.theme.Paper
import com.twopyramid.twofa.ui.theme.TwoFAShapes
import com.twopyramid.twofa.ui.theme.motion
import com.twopyramid.twofa.ui.viewmodel.HomeViewModel
import java.util.Locale

// ══ Convert（mapping §4 Convert）══════════════════════════════════════
//
// 保留 v2.1 全部业务逻辑：SAF 选包（takePersistableUriPermission）、入队快照、前台服务启动、
// 通知权限、7 个转换开关（fix_alpha_layers 不暴露）、危险项二次确认、26.3/Bedrock 非阻塞警示、
// 真进度（ProgressRepository）、结果面板 skipped/notes。
//
// 视觉重做点：版本选择弹窗 / 详情 sheet 全部改用统一 BrandDialog / BrandSheet 壳；
// 状态徽章用 ok/accent/ink3（不再用蓝色）；危险项 danger + 「会删文件」；26.3/Bedrock 用 warn 色。

// 危险开关（需要 danger 标记 + 二次确认的项）
private enum class DangerKind { DELETE_SHADERS, DELETE_CIT }

@Composable
fun ConvertScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val viewModel: HomeViewModel = viewModel()
    val items by viewModel.queue.collectAsState()
    val progressMap by ProgressRepository.items.collectAsState()
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
        contract = ActivityResultContracts.OpenDocument(),
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

    Column(modifier = Modifier.fillMaxSize()) {
        PageTopBar(
            title = "转换",
            onBack = onBack,
            trailing = {
                if (items.isNotEmpty()) {
                    val hasPending = items.any { it.status is QueueItem.Status.Queued }
                    Box(
                        modifier = Modifier
                            .clip(TwoFAShapes.row)
                            .border(1.dp, Paper.colors.hairline, TwoFAShapes.row)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            if (hasPending) "${items.size} 项待转换" else "${items.size} 项",
                            style = AppText.tagline,
                            color = Paper.colors.ink3,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }
                IconGlyphButton(
                    icon = Icons.Filled.Add,
                    contentDescription = "添加 Zip",
                    onClick = onPickZip,
                    tint = Paper.colors.ink2,
                    size = 32.dp,
                    iconSize = 20.dp,
                )
            },
        )

        Box(modifier = Modifier.weight(1f)) {
            if (items.isEmpty()) {
                ConvertEmpty(onAdd = onPickZip)
            } else {
                ConvertList(
                    items = items,
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
        BrandSheet(onDismiss = { detailItem = null }) {
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
private fun ConvertEmpty(onAdd: () -> Unit) {
    val c = Paper.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandPanel(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(c.surface3),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.FolderZip,
                        contentDescription = null,
                        tint = c.ink3,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text("还没有待转换的 zip", style = AppText.itemTitle, color = c.ink)
                Spacer(Modifier.height(6.dp))
                Text(
                    "从文件管理器里选一个 Minecraft 资源包",
                    style = AppText.body,
                    color = c.ink3,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(22.dp))
                PrimaryActionButton(text = "选择 Zip 文件", onClick = onAdd)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "选完文件后可以挑目标版本和转换选项",
            style = AppText.tagline,
            color = c.ink4,
        )
    }
}

@Composable
private fun ConvertList(
    items: List<QueueItem>,
    progress: Map<String, ItemProgress>,
    onItemClick: (QueueItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
    ) {
        items(items, key = { it.id }) { item ->
            ConvertRow(
                item = item,
                progress = progress[item.id],
                onClick = { onItemClick(item) },
            )
        }
    }
}

@Composable
private fun ConvertRow(
    item: QueueItem,
    progress: ItemProgress?,
    onClick: () -> Unit,
) {
    val c = Paper.colors
    val m = motion
    val visibleState = remember { androidx.compose.animation.core.MutableTransitionState(false) }
    androidx.compose.runtime.LaunchedEffect(item.id) { visibleState.targetState = true }

    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = if (m.reduced) m.fadeMs() else m.ms(Motion.base),
                easing = Motion.easeOut,
            ),
        ),
        exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(durationMillis = m.fadeMs())),
    ) {
        ConvertRowCard(item = item, progress = progress, onClick = onClick)
    }
}

@Composable
private fun ConvertRowCard(item: QueueItem, progress: ItemProgress?, onClick: () -> Unit) {
    val c = Paper.colors
    val statusTone = when (item.status) {
        is QueueItem.Status.Done -> StatusTone.OK
        is QueueItem.Status.Failed -> StatusTone.DANGER
        is QueueItem.Status.Running -> StatusTone.ACCENT
        is QueueItem.Status.Queued -> StatusTone.INK
    }
    val statusLabel = when (item.status) {
        is QueueItem.Status.Done -> "完成"
        is QueueItem.Status.Failed -> "失败"
        is QueueItem.Status.Running -> "转换中"
        is QueueItem.Status.Queued -> "排队中"
    }

    BrandPanel(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                        item.fileName,
                        style = AppText.itemTitle,
                        color = c.ink,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    StatusBadge(label = statusLabel, tone = statusTone)
                }
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VersionChip("→ ${AppSettings.versionShort(item.targetVersion)}")
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when (val s = item.status) {
                            is QueueItem.Status.Failed -> s.reason
                            is QueueItem.Status.Done -> s.outputPath.substringAfterLast('/')
                            else -> "pack_format ${item.targetVersion}"
                        },
                        style = AppText.tagline,
                        color = c.ink3,
                        maxLines = 1,
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
                            .clip(TwoFAShapes.row),
                        color = c.accent,
                        trackColor = c.surface3,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        Texts.progressLine(progress?.phase, percent),
                        style = AppText.tagline.copy(fontFeatureSettings = "tnum"),
                        color = c.ink3,
                    )
                }
            }
            if (item.status is QueueItem.Status.Done || item.status is QueueItem.Status.Failed) {
                Spacer(Modifier.width(4.dp))
                IconGlyphButton(
                    icon = Icons.Default.Delete,
                    contentDescription = "移除",
                    onClick = { QueueRepository.remove(item.id) },
                    tint = c.ink4,
                    size = 32.dp,
                    iconSize = 16.dp,
                )
            }
        }
    }
}

@Composable
private fun ConvertVersionDialog(
    fileName: String,
    currentVersion: Int,
    versions: List<AppSettings.VersionEntry>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onVersionChange: (Int) -> Unit,
) {
    val c = Paper.colors
    var query by remember { mutableStateOf("") }
    val options by SettingsRepository.convertOptions.collectAsState()
    val namingPreset by SettingsRepository.namingPreset.collectAsState()
    var pendingDanger by remember { mutableStateOf<DangerKind?>(null) }

    val filtered = remember(query, versions) {
        if (query.isBlank()) {
            versions
        } else {
            versions.filter {
                it.packFormat.toString().contains(query) ||
                    it.label.contains(query, ignoreCase = true) ||
                    it.range.contains(query, ignoreCase = true) ||
                    it.era.label.contains(query, ignoreCase = true)
            }
        }
    }

    val grouped: List<Pair<AppSettings.VersionEra, List<AppSettings.VersionEntry>>> =
        remember(filtered) {
            AppSettings.VersionEra.entries
                .map { era -> era to filtered.filter { it.era == era } }
                .filter { it.second.isNotEmpty() }
        }

    BrandDialog(
        onDismiss = onDismiss,
        title = "选择目标版本",
        subtitle = fileName,
        maxWidth = 480.dp,
        footer = {
            DialogTextButton(text = Texts.cancel, onClick = onDismiss, tone = DialogButtonTone.QUIET)
            DialogTextButton(text = Texts.startConvert, onClick = onConfirm, tone = DialogButtonTone.PRIMARY)
        },
    ) {
        SearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "搜索版本（1.20 / 19 / 试炼…）",
        )

        if (grouped.isEmpty()) {
            Text(
                "没找到匹配「$query」",
                style = AppText.body,
                color = c.ink3,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        } else {
            grouped.forEach { (era, eraItems) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(era.label, style = AppText.itemTitle.copy(fontWeight = FontWeight.Medium), color = c.ink2)
                    Spacer(Modifier.width(6.dp))
                    Text("· ${era.desc}", style = AppText.tagline, color = c.ink4)
                    Spacer(Modifier.weight(1f))
                    Text("${eraItems.size}", style = AppText.numeric, color = c.ink4)
                }
                eraItems.forEach { v ->
                    VersionPickRow(
                        entry = v,
                        selected = v.packFormat == currentVersion,
                        onClick = { onVersionChange(v.packFormat) },
                    )
                }
            }
        }

        // 选择即警示（非阻塞）：26.3 已知问题 / Bedrock 实验性，用 warn 色不用红色
        if (AppSettings.hasKnownIssue(currentVersion)) {
            WarnBanner(title = Texts.java263WarnTitle, body = Texts.java263WarnBody)
        }
        if (AppSettings.isBedrock(currentVersion)) {
            WarnBanner(title = Texts.bedrockWarnTitle, body = Texts.bedrockWarnBody)
        }

        HairlineDivider(modifier = Modifier.padding(top = 8.dp))

        // 转换选项区
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Texts.optionsTitle, style = AppText.itemTitle, color = c.ink)
            Spacer(Modifier.weight(1f))
            DialogTextButton(
                text = Texts.optionsReset,
                onClick = { SettingsRepository.resetConversionOptions() },
                tone = DialogButtonTone.QUIET,
            )
        }
        Text(Texts.optionsHint, style = AppText.tagline, color = c.ink3)

        ConversionOptionsSection(
            options = options,
            namingPreset = namingPreset,
            onDangerRequest = { kind -> pendingDanger = kind },
        )
    }

    // 危险开关的二次确认
    pendingDanger?.let { kind ->
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
        BrandConfirmDialog(
            title = title,
            body = body,
            confirmLabel = Texts.confirmEnable,
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

@Composable
private fun VersionPickRow(
    entry: AppSettings.VersionEntry,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val c = Paper.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TwoFAShapes.row)
            .background(if (selected) c.hover else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.label,
                    style = AppText.itemTitle.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                    color = if (selected) c.accent else c.ink,
                )
                entry.status?.let { status ->
                    Spacer(Modifier.width(6.dp))
                    VersionStatusBadge(status)
                }
            }
            Text(entry.range, style = AppText.tagline, color = c.ink3)
        }
        Text(
            "pack_format ${entry.packFormat}",
            style = AppText.tagline.copy(fontFeatureSettings = "tnum"),
            color = c.ink4,
        )
        if (selected) {
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.Check, contentDescription = null, tint = c.accent, modifier = Modifier.size(16.dp))
        }
    }
}

// 版本状态徽章：latest → accent；stable → ok；recommended → warn；beta → ink3（不用蓝）。
@Composable
private fun VersionStatusBadge(status: AppSettings.VersionStatus) {
    val tone = when (status) {
        AppSettings.VersionStatus.LATEST -> StatusTone.ACCENT
        AppSettings.VersionStatus.STABLE -> StatusTone.OK
        AppSettings.VersionStatus.RECOMMENDED -> StatusTone.WARN
        AppSettings.VersionStatus.BETA -> StatusTone.INK
    }
    StatusBadge(label = status.label, tone = tone)
}

@Composable
private fun ConversionOptionsSection(
    options: ConvertOptions,
    namingPreset: NamingPreset,
    onDangerRequest: (DangerKind) -> Unit,
) {
    val c = Paper.colors

    // adapt_shaders：默认 true，实验功能
    ConversionSwitchRow(
        label = Texts.adaptShadersLabel,
        hint = Texts.adaptShadersHint,
        checked = options.adaptShaders,
        onCheckedChange = { SettingsRepository.setAdaptShaders(it) },
    )
    // delete_shaders：破坏性，默认 false，danger 标记 + 二次确认
    ConversionSwitchRow(
        label = Texts.deleteShadersLabel,
        hint = Texts.deleteShadersHint,
        checked = options.deleteShaders,
        destructive = true,
        onCheckedChange = { enabled ->
            if (enabled) onDangerRequest(DangerKind.DELETE_SHADERS) else SettingsRepository.setDeleteShaders(false)
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
            if (enabled) onDangerRequest(DangerKind.DELETE_CIT) else SettingsRepository.setDeleteCit(false)
        },
    )
    // write_gen_metadata：默认 true（仅 Java 目标写 2pyr-gen.json）
    ConversionSwitchRow(
        label = Texts.genMetadataLabel,
        hint = Texts.genMetadataHint,
        checked = options.writeGenMetadata,
        onCheckedChange = { SettingsRepository.setWriteGenMetadata(it) },
    )

    Spacer(Modifier.height(6.dp))
    Text(Texts.namingTitle, style = AppText.itemTitle, color = c.ink)
    Text(Texts.namingHint, style = AppText.tagline, color = c.ink3)
    Spacer(Modifier.height(6.dp))
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        NamingPreset.entries.forEach { preset ->
            val active = preset == namingPreset
            Box(
                modifier = Modifier
                    .clip(TwoFAShapes.row)
                    .background(if (active) c.accent.copy(alpha = 0.12f) else c.surface3)
                    .clickable { SettingsRepository.setNamingPreset(preset) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    preset.label,
                    style = AppText.tagline.copy(
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = if (active) c.accent else c.ink3,
                )
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    Text(namingPreset.desc, style = AppText.tagline, color = c.ink4)
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
    val c = Paper.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = AppText.body.copy(fontWeight = FontWeight.Medium),
                    color = if (destructive) c.danger else c.ink,
                )
                if (destructive) {
                    Spacer(Modifier.width(6.dp))
                    DangerTag(Texts.dangerTag)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                hint,
                style = AppText.tagline,
                color = if (enabled) c.ink3 else c.ink4,
            )
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = if (destructive) {
                SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = c.danger,
                    uncheckedThumbColor = c.ink4,
                    uncheckedTrackColor = c.surface5,
                    uncheckedBorderColor = c.hairlineStrong,
                )
            } else {
                SwitchDefaults.colors(
                    checkedThumbColor = c.onAccent,
                    checkedTrackColor = c.accent,
                    uncheckedThumbColor = c.ink4,
                    uncheckedTrackColor = c.surface5,
                    uncheckedBorderColor = c.hairlineStrong,
                )
            },
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
    val c = Paper.colors
    val clipboardManager = LocalClipboardManager.current
    var notesExpanded by remember(item.id) { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(item.fileName, style = AppText.sectionTitle, color = c.ink)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            VersionChip("→ ${AppSettings.versionLabel(item.targetVersion)}")
            Spacer(Modifier.width(8.dp))
            StatusBadge(
                label = when (item.status) {
                    is QueueItem.Status.Done -> "完成"
                    is QueueItem.Status.Failed -> "失败"
                    is QueueItem.Status.Running -> "转换中"
                    is QueueItem.Status.Queued -> "排队中"
                },
                tone = when (item.status) {
                    is QueueItem.Status.Done -> StatusTone.OK
                    is QueueItem.Status.Failed -> StatusTone.DANGER
                    is QueueItem.Status.Running -> StatusTone.ACCENT
                    is QueueItem.Status.Queued -> StatusTone.INK
                },
            )
        }
        Spacer(Modifier.height(16.dp))

        when (val s = item.status) {
            is QueueItem.Status.Done -> {
                if (s.sourceVersion > 0) {
                    InfoLine(
                        label = "版本",
                        value = "${AppSettings.versionShort(s.sourceVersion)} → " +
                            AppSettings.versionShort(item.targetVersion),
                    )
                    Spacer(Modifier.height(10.dp))
                }
                InfoLine(label = "输出路径", value = s.outputPath)
                if (s.elapsedMs > 0) {
                    Spacer(Modifier.height(10.dp))
                    InfoLine(label = "耗时", value = formatElapsed(s.elapsedMs))
                }
                if (s.skipped > 0) {
                    Spacer(Modifier.height(10.dp))
                    InfoLine(label = "跳过的任务数", value = "${s.skipped}")
                }
                if (s.notes.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    NotesSection(
                        notes = s.notes,
                        expanded = notesExpanded,
                        onToggle = { notesExpanded = !notesExpanded },
                    )
                }
                Spacer(Modifier.height(18.dp))
                HairlineDivider()
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    SecondaryActionButton(text = "移除", onClick = onRemove, height = 32.dp)
                    PrimaryActionButton(
                        text = "复制路径",
                        onClick = {
                            runCatching {
                                clipboardManager.setText(AnnotatedString(s.outputPath))
                            }
                        },
                        height = 32.dp,
                    )
                }
            }
            is QueueItem.Status.Failed -> {
                InfoLine(label = "失败原因", value = s.reason)
                Spacer(Modifier.height(18.dp))
                HairlineDivider()
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    SecondaryActionButton(text = "移除", onClick = onRemove, height = 32.dp)
                    PrimaryActionButton(text = "重新转换", onClick = onRetry, height = 32.dp)
                }
            }
            is QueueItem.Status.Running -> {
                val percent = progress?.percent ?: 0f
                LinearProgressIndicator(
                    progress = { percent },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(TwoFAShapes.row),
                    color = c.accent,
                    trackColor = c.surface3,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    Texts.progressLine(progress?.phase, percent),
                    style = AppText.body.copy(fontFeatureSettings = "tnum"),
                    color = c.ink3,
                )
            }
            is QueueItem.Status.Queued -> {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    PrimaryActionButton(text = "开始转换", onClick = onRetry, height = 32.dp)
                }
            }
        }
    }
}

// 引擎备注（notes）：既有跳过原因也有普通进展（如「写出 12 个物品定义」），
// 所以标题用「引擎备注」而不是「跳过原因」。
@Composable
private fun NotesSection(notes: List<String>, expanded: Boolean, onToggle: () -> Unit) {
    val c = Paper.colors
    Column {
        Text("引擎备注（含跳过原因与普通进展）", style = AppText.tagline, color = c.ink3)
        Spacer(Modifier.height(4.dp))
        val visible = if (expanded) notes else notes.take(3)
        visible.forEach { note ->
            Text("· $note", style = AppText.tagline, color = c.ink2)
        }
        if (notes.size > 3) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (expanded) "收起" else "展开全部（${notes.size} 条）",
                style = AppText.tagline.copy(fontWeight = FontWeight.Medium),
                color = c.accent,
                modifier = Modifier
                    .clip(TwoFAShapes.row)
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

private fun formatElapsed(ms: Long): String =
    String.format(Locale.US, "%.1fs", ms / 1000.0)
