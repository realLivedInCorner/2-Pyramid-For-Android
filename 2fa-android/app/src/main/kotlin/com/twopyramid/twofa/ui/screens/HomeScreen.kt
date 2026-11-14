package com.twopyramid.twofa.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.twopyramid.twofa.BuildConfig
import com.twopyramid.twofa.native.RustNative
import com.twopyramid.twofa.queue.QueueItem
import com.twopyramid.twofa.queue.QueueRepository
import com.twopyramid.twofa.ui.components.BrandHeader
import com.twopyramid.twofa.ui.components.BrandPanel
import com.twopyramid.twofa.ui.theme.AppText
import com.twopyramid.twofa.ui.theme.Motion
import com.twopyramid.twofa.ui.theme.Paper
import com.twopyramid.twofa.ui.theme.TwoFAShapes
import com.twopyramid.twofa.ui.theme.motion

// ══ Home（mapping §4）══════════════════════════════════════════════════
//
// 品牌块：brand_mark 44dp 盒 + 品牌名（唯一 700 字重）+ 11.5sp 标语 + 10.5sp 元信息。
// Bento：主 CTA 一个「块」（accent 底），历史/设置两个「行块」（surface + 发丝线）。
// 去玻璃 / 去投影 / 去冷色极光；背景由 MainActivity 的暖色阶梯承担。
// 入场错峰属装饰类：减少动效时整个跳过（不是压到 0ms）。

@Composable
fun HomeScreen(
    onStartConvert: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val c = Paper.colors
    val appVersion = remember { BuildConfig.VERSION_NAME }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(24.dp))

        StaggerIn(index = 0) {
            BrandHeader(
                tagline = "Modern Universal Multi-Version Resource Pack Converter",
                meta = "for Android · v$appVersion",
            )
        }

        Spacer(Modifier.height(32.dp))

        StaggerIn(index = 1) {
            PrimaryCta(onClick = onStartConvert)
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StaggerIn(index = 2, modifier = Modifier.weight(1f)) {
                NavBlock(
                    icon = Icons.Filled.History,
                    title = "历史记录",
                    desc = "全部转换记录",
                    onClick = onOpenHistory,
                )
            }
            StaggerIn(index = 3, modifier = Modifier.weight(1f)) {
                NavBlock(
                    icon = Icons.Filled.Settings,
                    title = "设置",
                    desc = "外观 · 转换 · 关于",
                    onClick = onOpenSettings,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        StaggerIn(index = 4) {
            EngineFooter()
        }
        Spacer(Modifier.height(12.dp))
    }
}

// 主 CTA 块：accent 底（默认 = 墨色 #14171A），无渐变、无投影。
@Composable
private fun PrimaryCta(onClick: () -> Unit) {
    val c = Paper.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(148.dp)
            .clip(TwoFAShapes.dialog)
            .background(c.accent)
            .clickable(onClick = onClick)
            .padding(20.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(c.onAccent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = c.onAccent,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text("开始转换", style = AppText.sectionTitle, color = c.onAccent)
            }
            Column {
                Text(
                    "选择 .zip 资源包",
                    style = AppText.body,
                    color = c.onAccent.copy(alpha = 0.92f),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "转换到目标 pack_format，支持 Java 与 Bedrock",
                    style = AppText.secondary,
                    color = c.onAccent.copy(alpha = 0.72f),
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = c.onAccent.copy(alpha = 0.9f),
            modifier = Modifier.align(Alignment.BottomEnd).size(18.dp),
        )
    }
}

// 次级入口「行块」：surface + 1dp 发丝线，无玻璃 / 无投影。
@Composable
private fun NavBlock(
    icon: ImageVector,
    title: String,
    desc: String,
    onClick: () -> Unit,
) {
    val c = Paper.colors
    BrandPanel(
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(icon, contentDescription = null, tint = c.ink3, modifier = Modifier.size(20.dp))
            Column {
                Text(title, style = AppText.itemTitle, color = c.ink)
                Spacer(Modifier.height(2.dp))
                Text(desc, style = AppText.tagline, color = c.ink3)
            }
        }
    }
}

// 引擎状态行：状态点 + 队列数 + convert-core 版本（10.5/11.5sp 弱文本，不再用玻璃 pill）。
@Composable
private fun EngineFooter() {
    val c = Paper.colors
    val items by QueueRepository.items.collectAsState()
    val running = items.count { it.status is QueueItem.Status.Running }
    val coreVersion = remember {
        runCatching { RustNative.convertCoreVersion() }.getOrNull()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (running > 0) c.accent else c.ok),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (running > 0) "转换中 · 队列 ${items.size}" else "引擎就绪 · 队列 ${items.size}",
            style = AppText.tagline,
            color = c.ink3,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "convert-core ${coreVersion ?: "—"}",
            style = AppText.tagline.copy(fontFeatureSettings = "tnum"),
            color = c.ink4,
        )
    }
}

// 首页入场错峰（装饰类）：每块 fade + 轻微上移，延迟差 55ms。
// 减少动效时直接渲染内容（不挂 AnimatedVisibility），符合 §3.4 语义。
@Composable
private fun StaggerIn(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val m = motion
    if (m.reduced) {
        Box(modifier = modifier) { content() }
        return
    }
    val visibleState = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(((index * 55) * m.speedScale).toLong())
        visibleState.targetState = true
    }
    AnimatedVisibility(
        visibleState = visibleState,
        modifier = modifier,
        enter = fadeIn(
            animationSpec = tween(durationMillis = m.ms(Motion.base), easing = Motion.easeOut),
        ) + slideInVertically(
            initialOffsetY = { it / 12 },
            animationSpec = tween(durationMillis = m.ms(Motion.slow), easing = Motion.easeOut),
        ),
    ) {
        content()
    }
}
