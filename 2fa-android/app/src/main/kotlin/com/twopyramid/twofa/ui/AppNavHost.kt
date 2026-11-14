package com.twopyramid.twofa.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.twopyramid.twofa.ui.components.HairlineDivider
import com.twopyramid.twofa.ui.screens.ConvertScreen
import com.twopyramid.twofa.ui.screens.HistoryScreen
import com.twopyramid.twofa.ui.screens.HomeScreen
import com.twopyramid.twofa.ui.screens.SettingsScreen
import com.twopyramid.twofa.ui.theme.AppText
import com.twopyramid.twofa.ui.theme.Motion
import com.twopyramid.twofa.ui.theme.Paper
import com.twopyramid.twofa.ui.theme.TwoFAShapes
import com.twopyramid.twofa.ui.theme.motion
import kotlin.math.abs

// ══ 顶层导航 + 底部 NavigationBar（mapping §4 Navigation）══════════════
//
// 路由：
//   "/"          Home
//   "/convert"   转换队列（Home 进入）
//   "/history"   历史
//   "/settings"  设置
//
// 转场：ease-out 160–240ms 的 fade + 轻微上移（不再 520ms 横滑）；减少动效时瞬时切换。
// 底栏：surface 底 + 顶部 1dp 暖发丝线，选中色 = accent（默认墨色），不用蓝色。
// 左右滑切 tab 手势保留；提示 chip 改用暖纸墨面板样式。

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val m = motion

    Scaffold(
        bottomBar = {
            WarmNavBar(
                currentRoute = currentRoute,
                onSelect = { tab ->
                    if (tab.route == currentRoute) return@WarmNavBar
                    nav.navigate(tab.route) {
                        popUpTo(nav.graph.findStartDestination().id) { inclusive = false }
                        launchSingleTop = true
                    }
                },
            )
        },
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
    ) { innerPadding ->
        val currentIndex = tabs.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)

        SwipeTabDetector(
            modifier = Modifier.padding(innerPadding),
            currentIndex = currentIndex,
            totalPages = tabs.size,
            onPrev = {
                val prev = tabs.getOrNull(currentIndex - 1) ?: return@SwipeTabDetector
                nav.navigate(prev.route) {
                    popUpTo(nav.graph.findStartDestination().id) { inclusive = false }
                    launchSingleTop = true
                }
            },
            onNext = {
                val next = tabs.getOrNull(currentIndex + 1) ?: return@SwipeTabDetector
                nav.navigate(next.route) {
                    popUpTo(nav.graph.findStartDestination().id) { inclusive = false }
                    launchSingleTop = true
                }
            },
        ) {
            NavHost(
                navController = nav,
                startDestination = Routes.HOME,
                modifier = Modifier.fillMaxSize(),
                enterTransition = {
                    if (m.reduced) {
                        EnterTransitionNone
                    } else {
                        fadeIn(
                            animationSpec = tween(m.pageEnterMs(), easing = Motion.easeOut),
                        ) + slideInVertically(
                            initialOffsetY = { it / 24 },
                            animationSpec = tween(m.pageEnterMs(), easing = Motion.easeOut),
                        )
                    }
                },
                exitTransition = {
                    if (m.reduced) {
                        ExitTransitionNone
                    } else {
                        fadeOut(animationSpec = tween(m.pageExitMs(), easing = Motion.easeIn))
                    }
                },
                popEnterTransition = {
                    if (m.reduced) {
                        EnterTransitionNone
                    } else {
                        fadeIn(
                            animationSpec = tween(m.pageEnterMs(), easing = Motion.easeOut),
                        ) + slideInVertically(
                            initialOffsetY = { it / 24 },
                            animationSpec = tween(m.pageEnterMs(), easing = Motion.easeOut),
                        )
                    }
                },
                popExitTransition = {
                    if (m.reduced) {
                        ExitTransitionNone
                    } else {
                        fadeOut(animationSpec = tween(m.pageExitMs(), easing = Motion.easeIn))
                    }
                },
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        onStartConvert = { nav.navigate(Routes.CONVERT) },
                        onOpenHistory = { nav.navigate(Routes.HISTORY) },
                        onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                    )
                }
                composable(Routes.CONVERT) {
                    ConvertScreen(onBack = { nav.popBackStack() })
                }
                composable(Routes.HISTORY) {
                    HistoryScreen()
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(onBack = { nav.popBackStack() })
                }
            }
        }
    }
}

// 减少动效时的导航转场：瞬时切换。
private val EnterTransitionNone: androidx.compose.animation.EnterTransition
    get() = androidx.compose.animation.EnterTransition.None

private val ExitTransitionNone: androidx.compose.animation.ExitTransition
    get() = androidx.compose.animation.ExitTransition.None

object Routes {
    const val HOME = "/"
    const val CONVERT = "/convert"
    const val HISTORY = "/history"
    const val SETTINGS = "/settings"
}

// 横向 swipe 切 tab（保留原行为）：累积超过阈值 → 切上/下一个 tab，边界不响应。
// drag 中在屏幕边缘显示方向 chip（阻尼反馈）；chip 走暖纸墨面板样式。
@Composable
private fun SwipeTabDetector(
    currentIndex: Int,
    totalPages: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    swipeThreshold: androidx.compose.ui.unit.Dp = 48.dp,
    content: @Composable () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val m = motion
    var rawProgress by remember { mutableFloatStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = tween(
            durationMillis = m.ms(Motion.slow),
            easing = Motion.easeOut,
        ),
        label = "swipeProgress",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(currentIndex, totalPages) {
                var totalDragX = 0f
                val thresholdPx = swipeThreshold.toPx()
                detectHorizontalDragGestures(
                    onDragStart = { totalDragX = 0f; rawProgress = 0f },
                    onDragEnd = { totalDragX = 0f; rawProgress = 0f },
                    onDragCancel = { totalDragX = 0f; rawProgress = 0f },
                ) { _, dragAmount ->
                    totalDragX += dragAmount
                    rawProgress = (totalDragX / thresholdPx).coerceIn(-1f, 1f)
                    when {
                        totalDragX <= -thresholdPx && currentIndex < totalPages - 1 -> {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onNext()
                            totalDragX = 0f
                            rawProgress = 0f
                        }
                        totalDragX >= thresholdPx && currentIndex > 0 -> {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPrev()
                            totalDragX = 0f
                            rawProgress = 0f
                        }
                    }
                }
            },
    ) {
        content()

        if (abs(animatedProgress) > 0.02f) {
            SwipeHintOverlay(
                progress = animatedProgress,
                canPrev = currentIndex > 0,
                canNext = currentIndex < totalPages - 1,
            )
        }
    }
}

@Composable
private fun SwipeHintOverlay(
    progress: Float,
    canPrev: Boolean,
    canNext: Boolean,
) {
    val hintAlpha = abs(progress).coerceIn(0f, 1f) * 0.85f
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        when {
            progress < 0f && canNext -> {
                HintChip(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    label = "下一个",
                    alpha = hintAlpha,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 24.dp),
                )
            }
            progress > 0f && canPrev -> {
                HintChip(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    label = "上一个",
                    alpha = hintAlpha,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp),
                )
            }
            progress < 0f && !canNext -> {
                HintChip(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    label = "已是末页",
                    alpha = 0.3f,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 24.dp),
                )
            }
            progress > 0f && !canPrev -> {
                HintChip(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    label = "已是首页",
                    alpha = 0.3f,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp),
                )
            }
        }
    }
}

// 方向提示 chip：surface + 1dp 发丝线 + 圆形，无投影。
@Composable
private fun HintChip(
    icon: ImageVector,
    label: String,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    val c = Paper.colors
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(c.surface.copy(alpha = alpha))
            .border(1.dp, c.hairlineStrong.copy(alpha = alpha), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = c.ink2.copy(alpha = alpha),
            modifier = Modifier.size(28.dp),
        )
    }
}

private data class NavTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val tabs = listOf(
    NavTab(Routes.HOME, "首页", Icons.Filled.Home),
    NavTab(Routes.CONVERT, "转换", Icons.Filled.SwapHoriz),
    NavTab(Routes.HISTORY, "历史", Icons.Filled.History),
    NavTab(Routes.SETTINGS, "设置", Icons.Filled.Settings),
)

@Composable
private fun WarmNavBar(
    currentRoute: String?,
    onSelect: (NavTab) -> Unit,
) {
    val c = Paper.colors
    Column {
        HairlineDivider()
        NavigationBar(
            containerColor = c.surface,
            tonalElevation = 0.dp,
        ) {
            tabs.forEach { tab ->
                val selected = currentRoute == tab.route
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelect(tab) },
                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                    label = {
                        Text(
                            tab.label,
                            style = AppText.tagline.copy(
                                fontWeight = if (selected) {
                                    androidx.compose.ui.text.font.FontWeight.Medium
                                } else {
                                    androidx.compose.ui.text.font.FontWeight.Normal
                                },
                            ),
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = c.accent,
                        selectedTextColor = c.accent,
                        indicatorColor = c.accent.copy(alpha = 0.10f),
                        unselectedIconColor = c.ink3,
                        unselectedTextColor = c.ink3,
                    ),
                )
            }
        }
    }
}
