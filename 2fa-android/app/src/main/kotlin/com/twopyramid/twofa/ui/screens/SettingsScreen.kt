package com.twopyramid.twofa.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.twopyramid.twofa.BuildConfig
import com.twopyramid.twofa.history.HistoryRepository
import com.twopyramid.twofa.native.RustNative
import com.twopyramid.twofa.settings.ConvertOptions
import com.twopyramid.twofa.settings.NamingPreset
import com.twopyramid.twofa.settings.OutputMode
import com.twopyramid.twofa.settings.SettingsRepository
import com.twopyramid.twofa.settings.ThemeMode
import com.twopyramid.twofa.ui.components.GlassCard
import com.twopyramid.twofa.ui.theme.BlueAccent
import com.twopyramid.twofa.ui.theme.CrimsonWarn
import com.twopyramid.twofa.ui.theme.LocalTwoFATokens
import com.twopyramid.twofa.ui.theme.OnSurfaceHigh
import com.twopyramid.twofa.ui.theme.OnSurfaceLow
import java.io.File

/**
 * 设置页。
 *
 * v2.1（桌面 2.203.0 同步）：
 *  - 全部设置持久化（SharedPreferences → [SettingsRepository]），重启保持；
 *  - **主题三档** light / dark / system（比桌面现状完整，报告 E3），切换即时生效；
 *  - CONVERT 分组：输出模式/固定路径 + 命名模板预设 + 转换选项摘要（详细开关在转换对话框内调整）；
 *  - SAF 固定输出目录修复（S19）：存原始 `content://` tree URI，Service 据此判断分支；
 *  - ABOUT：App versionName（BuildConfig）+ convertCoreVersion 两行，移除旧写死更新文案。
 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val tokens = LocalTwoFATokens.current
    val ctx = LocalContext.current

    val themeMode by SettingsRepository.themeMode.collectAsState()
    val devMode by SettingsRepository.devMode.collectAsState()
    val outputMode by SettingsRepository.outputMode.collectAsState()
    val outputPath by SettingsRepository.outputPath.collectAsState()
    val namingPreset by SettingsRepository.namingPreset.collectAsState()
    val convertOptions by SettingsRepository.convertOptions.collectAsState()

    var animationSpeed by remember { mutableStateOf<AnimationSpeed>(AnimationSpeed.NORMAL) }
    var searchQuery by remember { mutableStateOf("") }

    var showVersionDialog by remember { mutableStateOf(false) }
    var showClearHistoryConfirm by remember { mutableStateOf(false) }
    var showClearCacheConfirm by remember { mutableStateOf(false) }
    var showLogDialog by remember { mutableStateOf(false) }

    val appVersion = remember { BuildConfig.VERSION_NAME }
    val coreVersion = remember {
        runCatching { RustNative.convertCoreVersion() }.getOrDefault("(unavailable)")
    }

    val pickOutputDir = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            // S19 修复：保存原始 content:// tree URI（旧实现存 "tree/primary:..." 伪路径，
            // 导致 ZipQueueService 的 startsWith("content://") 判断永远走不到 SAF 分支）。
            SettingsRepository.setOutputPath(uri.toString())
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 48.dp),
        ) {
            item {
                SettingsTopBar(
                    onBack = onBack,
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                )
                Spacer(Modifier.height(8.dp))
            }

            // ── THEME（三档，持久化；首帧前由 MainActivity 读取）──
            item {
                SettingsGroup(title = "THEME") {
                    StaggerItem(0, tokens) {
                        SettingItem(
                            icon = Icons.Default.AutoAwesome,
                            label = "主题",
                            desc = "浅色 / 深色 / 跟随系统（默认跟随系统）",
                            trailing = {
                                SegmentedChips(
                                    options = ThemeMode.entries,
                                    current = themeMode,
                                    labelOf = { it.label },
                                    onChange = { SettingsRepository.setThemeMode(it) },
                                )
                            },
                        )
                    }
                }
            }

            // ── CONVERT ──
            item {
                SettingsGroup(title = "CONVERT") {
                    StaggerItem(1, tokens) {
                        SettingItem(
                            icon = Icons.Default.Folder,
                            label = "输出模式",
                            desc = when (outputMode) {
                                OutputMode.FOLLOW -> "跟随 zip 同目录"
                                OutputMode.FIXED  -> "输出到指定目录"
                            },
                            trailing = {
                                SegmentedChips(
                                    options = OutputMode.entries,
                                    current = outputMode,
                                    labelOf = { it.label },
                                    onChange = { SettingsRepository.setOutputMode(it) },
                                )
                            },
                        )
                    }
                    if (outputMode == OutputMode.FIXED) {
                        StaggerItem(2, tokens) {
                            SettingItem(
                                icon = Icons.Default.Storage,
                                label = "输出路径",
                                desc = displayOutputPath(ctx, outputPath),
                                trailing = {
                                    TextButton(onClick = { pickOutputDir.launch(null) }) {
                                        Text(
                                            if (outputPath.isBlank()) "选择" else "更换",
                                            color = BlueAccent,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                },
                            )
                        }
                    }
                    StaggerItem(3, tokens) {
                        SettingItem(
                            icon = Icons.Default.Folder,
                            label = "输出命名模板",
                            desc = "默认 [Ver][Name]；同名产物追加 (n) 不覆盖",
                            trailing = {
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    NamingPreset.entries.forEach { preset ->
                                        Chip(
                                            text = preset.template,
                                            active = preset == namingPreset,
                                            onClick = { SettingsRepository.setNamingPreset(preset) },
                                        )
                                    }
                                }
                            },
                        )
                    }
                    StaggerItem(4, tokens) {
                        SettingItem(
                            icon = Icons.Default.AutoAwesome,
                            label = "转换选项",
                            desc = optionsSummary(convertOptions),
                            trailing = {
                                TextButton(onClick = { SettingsRepository.resetConversionOptions() }) {
                                    Text(
                                        "恢复默认",
                                        color = BlueAccent,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            },
                        )
                    }
                }
            }

            // ── DATA ──
            item {
                SettingsGroup(title = "DATA") {
                    StaggerItem(5, tokens) {
                        SettingItem(
                            icon = Icons.Default.History,
                            label = "清空历史记录",
                            desc = "删除全部转换记录（不可恢复）",
                            trailing = {
                                TextButton(onClick = { showClearHistoryConfirm = true }) {
                                    Text("清空", color = CrimsonWarn, fontWeight = FontWeight.SemiBold)
                                }
                            },
                        )
                    }
                    StaggerItem(6, tokens) {
                        SettingItem(
                            icon = Icons.Default.Storage,
                            label = "清空缓存",
                            desc = "删除 filesDir/converted 下的输出文件",
                            trailing = {
                                TextButton(onClick = { showClearCacheConfirm = true }) {
                                    Text("清空", color = CrimsonWarn, fontWeight = FontWeight.SemiBold)
                                }
                            },
                        )
                    }
                }
            }

            // ── DEVELOPER ──
            item {
                SettingsGroup(title = "DEVELOPER") {
                    StaggerItem(7, tokens) {
                        SettingItem(
                            icon = Icons.Default.Code,
                            label = "开发者模式",
                            desc = "开启后运行时会输出详细日志",
                            trailing = {
                                Switch(
                                    checked = devMode,
                                    onCheckedChange = { SettingsRepository.setDevMode(it) },
                                )
                            },
                        )
                    }
                }
            }

            // ── ANIMATION ──
            item {
                SettingsGroup(title = "ANIMATION") {
                    StaggerItem(8, tokens) {
                        SettingItem(
                            icon = Icons.Default.Speed,
                            label = "动画速率",
                            desc = "控制列表入场和转场时长",
                            trailing = {
                                SegmentedSpeedPicker(
                                    current = animationSpeed,
                                    onChange = { animationSpeed = it },
                                )
                            },
                        )
                    }
                }
            }

            // ── DEV (only when dev mode 启用) ──
            if (devMode) {
                item {
                    SettingsGroup(title = "DEV TOOLS") {
                        StaggerItem(9, tokens) {
                            SettingItem(
                                icon = Icons.Default.Terminal,
                                label = "查看日志",
                                desc = "显示最近 200 行 Rust 端日志",
                                trailing = {
                                    TextButton(onClick = { showLogDialog = true }) {
                                        Text("查看", color = BlueAccent, fontWeight = FontWeight.SemiBold)
                                    }
                                },
                            )
                        }
                    }
                }
            }

            // ── ABOUT ──
            item {
                SettingsGroup(title = "ABOUT") {
                    StaggerItem(10, tokens) {
                        AboutBlock(
                            appVersion = appVersion,
                            coreVersion = coreVersion,
                            onClick = { showVersionDialog = true },
                        )
                    }
                }
            }
        }
    }

    if (showVersionDialog) {
        VersionInfoDialog(
            appVersion = appVersion,
            coreVersion = coreVersion,
            onDismiss = { showVersionDialog = false },
        )
    }
    if (showClearHistoryConfirm) {
        ConfirmActionDialog(
            title = "清空历史记录？",
            body = "所有转换记录（Done + Failed）会被删除，此操作不可撤销。",
            confirmLabel = "清空",
            onConfirm = {
                HistoryRepository.clear()
                showClearHistoryConfirm = false
            },
            onDismiss = { showClearHistoryConfirm = false },
        )
    }
    if (showClearCacheConfirm) {
        ConfirmActionDialog(
            title = "清空缓存？",
            body = "filesDir/converted 下的所有输出文件会被删除。",
            confirmLabel = "清空",
            onConfirm = {
                runCatching {
                    val dir = File(ctx.filesDir, "converted")
                    if (dir.exists()) dir.listFiles()?.forEach { it.delete() }
                }
                showClearCacheConfirm = false
            },
            onDismiss = { showClearCacheConfirm = false },
        )
    }
    if (showLogDialog) {
        LogDialog(onDismiss = { showLogDialog = false })
    }
}

/** 转换选项摘要（设置页只显示摘要；详细开关在转换对话框内）。 */
private fun optionsSummary(options: ConvertOptions): String = buildString {
    append("着色器适配"); append(if (options.adaptShaders) "开" else "关")
    append(" · 物品定义"); append(if (options.convertItemDefinitions) "开" else "关")
    append(" · CIT 转换"); append(if (options.convertCit) "开" else "关")
    append(" · 参数文件"); append(if (options.writeGenMetadata) "开" else "关")
    if (options.deleteShaders) append(" · 删除 shaders")
    if (options.deleteCit) append(" · 删除 CIT")
}

/** SAF tree URI / 普通路径 → 可读文案。 */
private fun displayOutputPath(ctx: android.content.Context, path: String): String = when {
    path.isBlank() -> "未选择"
    path.startsWith("content://") -> runCatching {
        DocumentFile.fromTreeUri(ctx, Uri.parse(path))?.name
    }.getOrNull() ?: "已选择 SAF 目录"
    else -> path
}

/** 通用单选 chip（主题/输出模式/命名模板共用）。 */
@Composable
private fun <T> SegmentedChips(
    options: List<T>,
    current: T,
    labelOf: (T) -> String,
    onChange: (T) -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(OnSurfaceLow.copy(alpha = 0.08f))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { option ->
            val active = option == current
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (active) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onChange(option) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    labelOf(option),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) OnSurfaceHigh else OnSurfaceLow,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

/** 小型选择 chip（命名模板）。 */
@Composable
private fun Chip(text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (active) BlueAccent.copy(alpha = 0.14f) else OnSurfaceLow.copy(alpha = 0.06f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (active) BlueAccent else OnSurfaceLow,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
private fun SettingsTopBar(
    onBack: () -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "返回",
                    tint = OnSurfaceHigh,
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineLarge,
                color = OnSurfaceLow,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            "设置",
            style = MaterialTheme.typography.displaySmall,
            color = OnSurfaceHigh,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(12.dp))
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(14.dp),
            elevation = 2.dp,
            shadowRadius = 8.dp,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = OnSurfaceLow,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = OnSurfaceHigh),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(BlueAccent),
                    modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text(
                                "搜索设置项…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = OnSurfaceLow,
                            )
                        }
                        inner()
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(top = 24.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = OnSurfaceLow,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 6.dp),
        )
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(20.dp),
            elevation = 2.dp,
            shadowRadius = 8.dp,
        ) {
            Column {
                content()
            }
        }
    }
}

@Composable
private fun SettingItem(
    icon: ImageVector,
    label: String,
    desc: String,
    trailing: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(BlueAccent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = BlueAccent,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                color = OnSurfaceHigh,
                fontWeight = FontWeight.Medium,
            )
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceLow,
            )
        }
        trailing()
    }
}

@Composable
private fun AboutBlock(appVersion: String, coreVersion: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            listOf(
                                BlueAccent.copy(alpha = 0.4f),
                                BlueAccent.copy(alpha = 0.05f),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = androidx.compose.ui.res.painterResource(
                        com.twopyramid.twofa.R.drawable.ic_pyramid_logo
                    ),
                    contentDescription = null,
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(BlueAccent),
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "2-Pyramid For Android",
                    style = MaterialTheme.typography.titleMedium,
                    color = OnSurfaceHigh,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "v$appVersion · convert-core $coreVersion",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceLow,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = OnSurfaceLow.copy(alpha = 0.18f))
        Spacer(Modifier.height(14.dp))
        InfoLine("目标平台", "Android 12+")
        InfoLine("转换引擎", "桌面 2.203.0 M3 引擎")
        InfoLine("UI 框架", "Jetpack Compose Material 3")
        Spacer(Modifier.height(8.dp))
        Text(
            "点击查看版本详情 →",
            style = MaterialTheme.typography.bodySmall,
            color = BlueAccent,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceLow,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceHigh,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SegmentedSpeedPicker(
    current: AnimationSpeed,
    onChange: (AnimationSpeed) -> Unit,
) {
    SegmentedChips(
        options = AnimationSpeed.entries,
        current = current,
        labelOf = { it.shortLabel },
        onChange = onChange,
    )
}

private enum class AnimationSpeed(val shortLabel: String) {
    SLOW("慢"),
    NORMAL("标准"),
    FAST("快"),
}

@Composable
private fun VersionInfoDialog(appVersion: String, coreVersion: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("版本信息") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                listOf(BlueAccent.copy(alpha = 0.4f), BlueAccent.copy(alpha = 0.05f)),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = androidx.compose.ui.res.painterResource(
                            com.twopyramid.twofa.R.drawable.ic_pyramid_logo
                        ),
                        contentDescription = null,
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(BlueAccent),
                        modifier = Modifier.size(46.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "2-Pyramid For Android v$appVersion",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "转换引擎 convert-core $coreVersion",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceLow,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "同步基准：桌面版 2.203.0（M3 引擎）",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceLow,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭", color = BlueAccent) } },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
private fun ConfirmActionDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body, color = OnSurfaceLow) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = CrimsonWarn, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
private fun LogDialog(onDismiss: () -> Unit) {
    var logs by remember { mutableStateOf("加载中…") }
    LaunchedEffect(Unit) {
        logs = runCatching { RustNative.recentLogs(200).joinToString("\n") }
            .getOrDefault("无法读取日志")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("运行日志（最近 200 行）") },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 400.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0B1020))
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    logs,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFA8FFB0),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭", color = BlueAccent) } },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
private fun StaggerItem(
    index: Int,
    tokens: com.twopyramid.twofa.ui.theme.TwoFATokens,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index * tokens.staggerDelayMs.toLong())
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(tokens.entranceDurationMs, easing = tokens.staggerEasing)
        ) + slideInVertically(
            initialOffsetY = { it / 3 },
            animationSpec = tween(tokens.entranceDurationMs, easing = tokens.staggerEasing),
        ),
    ) {
        content()
    }
}
