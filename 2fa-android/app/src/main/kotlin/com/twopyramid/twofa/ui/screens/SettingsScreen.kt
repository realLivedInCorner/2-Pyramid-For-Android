package com.twopyramid.twofa.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.twopyramid.twofa.BuildConfig
import com.twopyramid.twofa.R
import com.twopyramid.twofa.history.HistoryRepository
import com.twopyramid.twofa.native.RustNative
import com.twopyramid.twofa.settings.ConvertOptions
import com.twopyramid.twofa.settings.NamingPreset
import com.twopyramid.twofa.settings.OutputMode
import com.twopyramid.twofa.settings.SettingsRepository
import com.twopyramid.twofa.settings.ThemeMode
import com.twopyramid.twofa.ui.components.BrandConfirmDialog
import com.twopyramid.twofa.ui.components.BrandDialog
import com.twopyramid.twofa.ui.components.BrandPanel
import com.twopyramid.twofa.ui.components.DialogButtonTone
import com.twopyramid.twofa.ui.components.DialogTextButton
import com.twopyramid.twofa.ui.components.HairlineDivider
import com.twopyramid.twofa.ui.components.HsvColorPicker
import com.twopyramid.twofa.ui.components.InfoRow
import com.twopyramid.twofa.ui.components.PageTopBar
import com.twopyramid.twofa.ui.components.SectionLabel
import com.twopyramid.twofa.ui.components.SegmentedControl
import com.twopyramid.twofa.ui.components.SettingRow
import com.twopyramid.twofa.ui.theme.AppText
import com.twopyramid.twofa.ui.theme.DEFAULT_THEME_COLOR_HEX
import com.twopyramid.twofa.ui.theme.Motion
import com.twopyramid.twofa.ui.theme.Paper
import com.twopyramid.twofa.ui.theme.TwoFAShapes
import com.twopyramid.twofa.ui.theme.parseHexColor
import com.twopyramid.twofa.ui.theme.toHexString
import java.io.File
import kotlin.math.abs

// ══ Settings（mapping §4 Settings）═════════════════════════════════════
//
// 分组结构：10.5sp uppercase ink3 分组标题 + 行 + 1dp 暖发丝线（不再用 glass group-card）。
// 新增「外观」：主题三档（已有）+ 强调色（HSV 选择器，持久化 themeColor）
//   + 动画速率三档（映射 MotionTokens 时长倍数）。
// 关于：brand_mark 72dp + versionName + BUILD（BuildConfig.BUILD_NUMBER）+ 引擎版本。
// 全部弹窗（强调色 / 清空历史 / 清空缓存 / 日志 / 版本详情）走统一 BrandDialog 壳。

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val c = Paper.colors

    val themeMode by SettingsRepository.themeMode.collectAsState()
    val themeColorHex by SettingsRepository.themeColor.collectAsState()
    val motionScale by SettingsRepository.motionScale.collectAsState()
    val devMode by SettingsRepository.devMode.collectAsState()
    val outputMode by SettingsRepository.outputMode.collectAsState()
    val outputPath by SettingsRepository.outputPath.collectAsState()
    val namingPreset by SettingsRepository.namingPreset.collectAsState()
    val convertOptions by SettingsRepository.convertOptions.collectAsState()

    var showAccentDialog by remember { mutableStateOf(false) }
    var showVersionDialog by remember { mutableStateOf(false) }
    var showClearHistoryConfirm by remember { mutableStateOf(false) }
    var showClearCacheConfirm by remember { mutableStateOf(false) }
    var showLogDialog by remember { mutableStateOf(false) }

    val appVersion = remember { BuildConfig.VERSION_NAME }
    val buildNumber = remember { BuildConfig.BUILD_NUMBER }
    val coreVersion = remember {
        runCatching { RustNative.convertCoreVersion() }.getOrDefault("(unavailable)")
    }

    val pickOutputDir = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            // S19：存原始 content:// tree URI（Service 据此走 SAF 分支）
            SettingsRepository.setOutputPath(uri.toString())
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item {
            PageTopBar(title = "设置", onBack = onBack)
            Spacer(Modifier.height(4.dp))
        }

        // ── APPEARANCE：主题 + 强调色 + 动画速率 ──
        item {
            SettingsGroup(title = "APPEARANCE") {
                SettingRow(
                    label = "主题",
                    desc = "浅色 / 深色 / 跟随系统（默认跟随系统）",
                ) {
                    SegmentedControl(
                        options = ThemeMode.entries,
                        current = themeMode,
                        labelOf = {
                            when (it) {
                                ThemeMode.LIGHT -> "浅色"
                                ThemeMode.DARK -> "深色"
                                ThemeMode.SYSTEM -> "系统"
                            }
                        },
                        onSelect = { SettingsRepository.setThemeMode(it) },
                    )
                }
                HairlineDivider(Modifier.padding(start = 16.dp))
                SettingRow(
                    label = "强调色",
                    desc = "默认墨色；深色下自动在 OKLab 空间提亮（保色相）",
                    modifier = Modifier.clickable { showAccentDialog = true },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(TwoFAShapes.row)
                                .background(c.accent)
                                .border(1.dp, c.hairlineStrong, TwoFAShapes.row),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(themeColorHex.uppercase(), style = AppText.numeric, color = c.ink3)
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = c.ink4,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                HairlineDivider(Modifier.padding(start = 16.dp))
                SettingRow(
                    label = "动画速率",
                    desc = "缩放全局动效时长（装饰类入场在减少动效时整体移除）",
                ) {
                    SegmentedControl(
                        options = SpeedPresets,
                        current = SpeedPresets.minByOrNull { abs(it.scale - motionScale) } ?: SpeedPresets[1],
                        labelOf = { it.label },
                        onSelect = { SettingsRepository.setMotionScale(it.scale) },
                    )
                }
            }
        }

        // ── CONVERT ──
        item {
            SettingsGroup(title = "CONVERT") {
                SettingRow(
                    label = "输出模式",
                    desc = when (outputMode) {
                        OutputMode.FOLLOW -> "跟随 zip 同目录"
                        OutputMode.FIXED -> "输出到指定目录"
                    },
                ) {
                    SegmentedControl(
                        options = OutputMode.entries,
                        current = outputMode,
                        labelOf = { if (it == OutputMode.FOLLOW) "跟随" else "固定" },
                        onSelect = { SettingsRepository.setOutputMode(it) },
                    )
                }
                if (outputMode == OutputMode.FIXED) {
                    HairlineDivider(Modifier.padding(start = 16.dp))
                    SettingRow(
                        label = "输出路径",
                        desc = displayOutputPath(ctx, outputPath),
                    ) {
                        Text(
                            text = if (outputPath.isBlank()) "选择" else "更换",
                            style = AppText.button,
                            color = c.accent,
                            modifier = Modifier
                                .clip(TwoFAShapes.row)
                                .clickable { pickOutputDir.launch(null) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                HairlineDivider(Modifier.padding(start = 16.dp))
                SettingRow(
                    label = "输出命名模板",
                    desc = "默认 [Ver][Name]；同名产物追加 (n) 不覆盖",
                ) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        NamingPreset.entries.forEach { preset ->
                            val active = preset == namingPreset
                            Box(
                                modifier = Modifier
                                    .clip(TwoFAShapes.row)
                                    .background(if (active) c.accent.copy(alpha = 0.12f) else c.surface3)
                                    .clickable { SettingsRepository.setNamingPreset(preset) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    preset.template,
                                    style = AppText.tagline.copy(
                                        fontWeight = if (active) androidx.compose.ui.text.font.FontWeight.SemiBold else null,
                                    ),
                                    color = if (active) c.accent else c.ink3,
                                )
                            }
                        }
                    }
                }
                HairlineDivider(Modifier.padding(start = 16.dp))
                SettingRow(
                    label = "转换选项",
                    desc = optionsSummary(convertOptions),
                ) {
                    Text(
                        text = "恢复默认",
                        style = AppText.button,
                        color = c.accent,
                        modifier = Modifier
                            .clip(TwoFAShapes.row)
                            .clickable { SettingsRepository.resetConversionOptions() }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }

        // ── DATA ──
        item {
            SettingsGroup(title = "DATA") {
                SettingRow(
                    label = "清空历史记录",
                    desc = "删除全部转换记录（不可恢复）",
                ) {
                    DangerRowAction(text = "清空") { showClearHistoryConfirm = true }
                }
                HairlineDivider(Modifier.padding(start = 16.dp))
                SettingRow(
                    label = "清空缓存",
                    desc = "删除 filesDir/converted 下的输出文件",
                ) {
                    DangerRowAction(text = "清空") { showClearCacheConfirm = true }
                }
            }
        }

        // ── DEVELOPER ──
        item {
            SettingsGroup(title = "DEVELOPER") {
                SettingRow(
                    label = "开发者模式",
                    desc = "开启后运行时会输出详细日志",
                ) {
                    Switch(
                        checked = devMode,
                        onCheckedChange = { SettingsRepository.setDevMode(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = c.onAccent,
                            checkedTrackColor = c.accent,
                            uncheckedThumbColor = c.ink4,
                            uncheckedTrackColor = c.surface5,
                            uncheckedBorderColor = c.hairlineStrong,
                        ),
                    )
                }
                if (devMode) {
                    HairlineDivider(Modifier.padding(start = 16.dp))
                    SettingRow(
                        label = "查看日志",
                        desc = "显示最近 200 行 Rust 端日志",
                    ) {
                        Text(
                            text = "查看",
                            style = AppText.button,
                            color = c.accent,
                            modifier = Modifier
                                .clip(TwoFAShapes.row)
                                .clickable { showLogDialog = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }

        // ── ABOUT ──
        item {
            SettingsGroup(title = "ABOUT") {
                AboutBlock(
                    appVersion = appVersion,
                    buildNumber = buildNumber,
                    coreVersion = coreVersion,
                    onClick = { showVersionDialog = true },
                )
            }
        }
    }

    if (showAccentDialog) {
        AccentColorDialog(
            currentHex = themeColorHex,
            onDismiss = { showAccentDialog = false },
        )
    }
    if (showVersionDialog) {
        VersionInfoDialog(
            appVersion = appVersion,
            buildNumber = buildNumber,
            coreVersion = coreVersion,
            onDismiss = { showVersionDialog = false },
        )
    }
    if (showClearHistoryConfirm) {
        BrandConfirmDialog(
            title = "清空历史记录？",
            body = "所有转换记录（完成 + 失败）都会被删除，此操作不可撤销。",
            confirmLabel = "清空",
            onConfirm = {
                HistoryRepository.clear()
                showClearHistoryConfirm = false
            },
            onDismiss = { showClearHistoryConfirm = false },
        )
    }
    if (showClearCacheConfirm) {
        BrandConfirmDialog(
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

// ── 小组件 ──────────────────────────────────────────────────────────────

private data class SpeedPreset(val label: String, val scale: Float)

private val SpeedPresets = listOf(
    SpeedPreset("慢", Motion.SPEED_SLOW),
    SpeedPreset("标准", Motion.SPEED_NORMAL),
    SpeedPreset("快", Motion.SPEED_FAST),
)

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(20.dp))
        SectionLabel(title)
        Spacer(Modifier.height(6.dp))
        BrandPanel(modifier = Modifier.fillMaxWidth()) {
            Column(content = content)
        }
    }
}

@Composable
private fun DangerRowAction(text: String, onClick: () -> Unit) {
    val c = Paper.colors
    Text(
        text = text,
        style = AppText.button,
        color = c.danger,
        modifier = Modifier
            .clip(TwoFAShapes.row)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

// 关于块：brand_mark 72dp 盒（tint(ink) 适配深浅主题）+ versionName + BUILD + 引擎版本。
@Composable
private fun AboutBlock(
    appVersion: String,
    buildNumber: Int,
    coreVersion: String,
    onClick: () -> Unit,
) {
    val c = Paper.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(R.drawable.brand_mark),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(c.ink),
                    modifier = Modifier.size(72.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("2-Pyramid for Android", style = AppText.itemTitle, color = c.ink)
                Spacer(Modifier.height(3.dp))
                Text(
                    "v$appVersion · BUILD $buildNumber",
                    style = AppText.numeric,
                    color = c.ink3,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "convert-core $coreVersion",
                    style = AppText.tagline,
                    color = c.ink4,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = c.ink4,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        HairlineDivider()
        Spacer(Modifier.height(10.dp))
        InfoRow(label = "目标平台", value = "Android 12+")
        InfoRow(label = "转换引擎", value = "桌面 2.203.0 M3 引擎")
        InfoRow(label = "UI 框架", value = "Jetpack Compose Material 3")
    }
}

@Composable
private fun AccentColorDialog(
    currentHex: String,
    onDismiss: () -> Unit,
) {
    val c = Paper.colors
    var draftColor by remember(currentHex) { mutableStateOf(parseHexColor(currentHex)) }

    BrandDialog(
        onDismiss = onDismiss,
        title = "强调色",
        subtitle = "默认墨色；选择后全应用生效",
        maxWidth = 480.dp,
        footer = {
            DialogTextButton(
                text = "恢复默认",
                onClick = { draftColor = parseHexColor(DEFAULT_THEME_COLOR_HEX) },
                tone = DialogButtonTone.QUIET,
            )
            Spacer(Modifier.weight(1f))
            DialogTextButton(text = "取消", onClick = onDismiss, tone = DialogButtonTone.QUIET)
            DialogTextButton(
                text = "确认",
                onClick = {
                    SettingsRepository.setThemeColor(toHexString(draftColor))
                    onDismiss()
                },
                tone = DialogButtonTone.PRIMARY,
            )
        },
    ) {
        // 预览：色块 + 应用名（与桌面 ThemeDialog 的 preview 同构）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(TwoFAShapes.control)
                .background(c.surface3)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(TwoFAShapes.control)
                    .background(draftColor)
                    .border(1.dp, c.hairlineStrong, TwoFAShapes.control),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("2-Pyramid Theme", style = AppText.itemTitle, color = c.ink)
                Spacer(Modifier.height(2.dp))
                Text(toHexString(draftColor), style = AppText.numeric, color = c.ink3)
            }
        }
        HsvColorPicker(
            color = draftColor,
            onColorChange = { draftColor = it },
        )
        Text(
            "深色主题下强调色会在 OKLab 空间与白色混合提亮（保留色相），保证深底上的可读性。",
            style = AppText.tagline,
            color = c.ink3,
        )
    }
}

@Composable
private fun VersionInfoDialog(
    appVersion: String,
    buildNumber: Int,
    coreVersion: String,
    onDismiss: () -> Unit,
) {
    val c = Paper.colors
    BrandDialog(
        onDismiss = onDismiss,
        title = "版本信息",
        footer = {
            DialogTextButton(text = "关闭", onClick = onDismiss, tone = DialogButtonTone.QUIET)
        },
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(R.drawable.brand_mark),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(c.ink),
                    modifier = Modifier.size(72.dp),
                )
            }
        }
        Text("2-Pyramid for Android", style = AppText.itemTitle, color = c.ink)
        Text("v$appVersion · BUILD $buildNumber", style = AppText.numeric, color = c.ink3)
        Text("转换引擎 convert-core $coreVersion", style = AppText.secondary, color = c.ink3)
        Text("同步基准：桌面版 2.203.0（M3 引擎）", style = AppText.tagline, color = c.ink4)
        Spacer(Modifier.height(4.dp))
        HairlineDivider()
        Spacer(Modifier.height(6.dp))
        InfoRow(label = "目标平台", value = "Android 12+")
        InfoRow(label = "构建号", value = "BUILD $buildNumber")
        InfoRow(label = "UI 框架", value = "Jetpack Compose Material 3")
    }
}

@Composable
private fun LogDialog(onDismiss: () -> Unit) {
    val c = Paper.colors
    var logs by remember { mutableStateOf("加载中…") }
    LaunchedEffect(Unit) {
        logs = runCatching { RustNative.recentLogs(200).joinToString("\n") }
            .getOrDefault("无法读取日志")
    }
    BrandDialog(
        onDismiss = onDismiss,
        title = "运行日志",
        subtitle = "最近 200 行",
        maxWidth = 480.dp,
        footer = {
            DialogTextButton(text = "关闭", onClick = onDismiss, tone = DialogButtonTone.QUIET)
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(TwoFAShapes.control)
                .background(c.surface3)
                .border(1.dp, c.hairline, TwoFAShapes.control)
                .padding(12.dp),
        ) {
            Text(
                logs,
                style = AppText.tagline.copy(fontFeatureSettings = "tnum"),
                color = c.ink2,
            )
        }
    }
}

// ── 文本辅助 ────────────────────────────────────────────────────────────

private fun optionsSummary(options: ConvertOptions): String = buildString {
    append("着色器适配"); append(if (options.adaptShaders) "开" else "关")
    append(" · 物品定义"); append(if (options.convertItemDefinitions) "开" else "关")
    append(" · CIT 转换"); append(if (options.convertCit) "开" else "关")
    append(" · 参数文件"); append(if (options.writeGenMetadata) "开" else "关")
    if (options.deleteShaders) append(" · 删除 shaders")
    if (options.deleteCit) append(" · 删除 CIT")
}

// SAF tree URI / 普通路径 → 可读文案。
private fun displayOutputPath(ctx: android.content.Context, path: String): String = when {
    path.isBlank() -> "未选择"
    path.startsWith("content://") -> runCatching {
        DocumentFile.fromTreeUri(ctx, Uri.parse(path))?.name
    }.getOrNull() ?: "已选择 SAF 目录"
    else -> path
}
