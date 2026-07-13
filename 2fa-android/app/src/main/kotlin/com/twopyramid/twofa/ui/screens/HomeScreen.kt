package com.twopyramid.twofa.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.twopyramid.twofa.queue.QueueRepository
import com.twopyramid.twofa.ui.components.BrandHeader
import com.twopyramid.twofa.ui.theme.BlueAccent
import com.twopyramid.twofa.ui.theme.BlueAccentDeep
import com.twopyramid.twofa.ui.theme.BlueAccentSoft
import com.twopyramid.twofa.ui.theme.GlassOutline
import com.twopyramid.twofa.ui.theme.GlassSurface
import com.twopyramid.twofa.ui.theme.LocalTwoFATokens
import com.twopyramid.twofa.ui.theme.MintAccent
import com.twopyramid.twofa.ui.theme.OnSurfaceHigh
import com.twopyramid.twofa.ui.theme.OnSurfaceLow
import com.twopyramid.twofa.native.RustNative

/**
 * Home 介绍页（v1.2 改：Bento 布局 "左大主 + 右小次 + 蓝渐变主卡 + 优化 stagger"）。
 *
 * 布局：
 *   ┌──────────────────┬─────────┐
 *   │                  │  历史   │  ← 中（1/3 宽 × 上半）
 *   │  开始转换        │         │
 *   │  蓝渐变大格      ├─────────┤
 *   │  2/3 宽 × 全高   │  设置   │  ← 小（1/3 宽 × 下半）
 *   └──────────────────┴─────────┘
 *
 * 三张卡色调：
 *   - 主 CTA：蓝渐变（topLeft `BlueAccentSoft` → bottomRight `BlueAccentDeep` + 右上角 radial 高光）
 *   - 次要：纯白不透明 + 灰 icon
 */
@Composable
fun HomeScreen(
    onStartConvert: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val coreVersion = remember { runCatching { RustNative.convertCoreVersion() }.getOrNull() ?: "0.0.0" }
    val tokens = LocalTwoFATokens.current

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandHeader(
                productLine = "for Android",
                tagline = "The Nextgen Multi-Version Universal Resource Pack Converter",
                versionPill = "v$coreVersion",
                logoSize = 56.dp,
            )

            Spacer(Modifier.height(20.dp))

            // Bento 网格
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(340.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StaggerFade(0, tokens) {
                    PrimaryBlock(
                        modifier = Modifier.weight(2f).fillMaxHeight(),
                        icon = Icons.Filled.PlayArrow,
                        title = "开始转换",
                        subtitle = "选 .zip 文件\n转换到目标 pack_format",
                        onClick = onStartConvert,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StaggerFade(1, tokens) {
                        SecondaryBlock(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            icon = Icons.Filled.History,
                            title = "历史记录",
                            onClick = onOpenHistory,
                        )
                    }
                    StaggerFade(2, tokens) {
                        SecondaryBlock(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            icon = Icons.Filled.Settings,
                            title = "设置",
                            onClick = onOpenSettings,
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            StaggerFade(3, tokens) {
                EngineIndicator()
            }
        }
    }
}

/**
 * 主 CTA 大格（v1.2 改：Bento 大方块 + 对角蓝渐变 + 右上角高光）。
 * 占 2/3 宽 × 全高。
 */
@Composable
private fun PrimaryBlock(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        contentColor = Color.White,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 10.dp,
        tonalElevation = 0.dp,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(BlueAccentSoft, BlueAccent, BlueAccentDeep),
                        start = androidx.compose.ui.geometry.Offset.Zero,
                        end = androidx.compose.ui.geometry.Offset.Infinite,
                    ),
                ),
        ) {
            // 右上角 radial 高光（光感）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.20f),
                                Color.Transparent,
                            ),
                            center = androidx.compose.ui.geometry.Offset.Unspecified,
                            radius = 600f,
                        ),
                    ),
            )
            Column(
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.20f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
                }
                Column {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
        }
    }
}

/**
 * 次要方块（v1.2.1 改：纯白 + 1px 浅灰边 alpha 0.6 + 4dp shadow + 大字 + icon 24dp，让卡片可见）。
 */
@Composable
private fun SecondaryBlock(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = Color.White,
        contentColor = OnSurfaceHigh,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, GlassOutline.copy(alpha = 0.6f)),
        shadowElevation = 4.dp,
        tonalElevation = 0.dp,
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = OnSurfaceLow,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = OnSurfaceHigh,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun EngineIndicator() {
    val items by QueueRepository.items.collectAsState()
    val total = items.size
    val running = items.count { it.status is com.twopyramid.twofa.queue.QueueItem.Status.Running }
    val accent: Color = if (running > 0) BlueAccent else MintAccent

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(GlassSurface)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .scale(if (running > 0) 1.2f else 1.0f)
                .clip(CircleShape)
                .background(accent),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (running > 0) "Engine Running · $total in queue"
            else "Engine Ready · $total in queue",
            style = MaterialTheme.typography.labelLarge,
            color = OnSurfaceLow,
        )
    }
}

/**
 * Stagger 入场（v1.2 优化：步距 60ms、时长 300ms、slide offset 1/10，4 元素总时间 ~480ms）。
 */
@Composable
private fun StaggerFade(index: Int, tokens: com.twopyramid.twofa.ui.theme.TwoFATokens, content: @Composable () -> Unit) {
    val visibleState = remember { MutableTransitionState(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index * 60L)
        visibleState.targetState = true
    }
    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(
            animationSpec = tween(300, easing = tokens.staggerEasing)
        ) + slideInVertically(
            initialOffsetY = { it / 10 },
            animationSpec = tween(300, easing = tokens.staggerEasing),
        ),
    ) {
        content()
    }
}
