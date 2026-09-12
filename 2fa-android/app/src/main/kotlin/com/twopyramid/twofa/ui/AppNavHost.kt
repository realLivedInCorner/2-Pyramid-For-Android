package com.twopyramid.twofa.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.twopyramid.twofa.ui.screens.ConvertScreen
import com.twopyramid.twofa.ui.screens.HistoryScreen
import com.twopyramid.twofa.ui.screens.HomeScreen
import com.twopyramid.twofa.ui.screens.SettingsScreen
import com.twopyramid.twofa.ui.theme.BlueAccent
import com.twopyramid.twofa.ui.theme.OnSurfaceHigh
import com.twopyramid.twofa.ui.theme.OnSurfaceLow
import kotlin.math.abs

/**
 * App 顶层导航 + 底部 NavigationBar。
 *
 * 路由：
 *   - "/"           Home (介绍 + 入口)
 *   - "/convert"    ConvertScreen (队列 + 添加 Zip)  ← 从 Home 按钮 push
 *   - "/history"    HistoryScreen (历史记录)
 *   - "/settings"   SettingsScreen (开发者模式 / 动画 / 关于)
 *
 * Tab 切换：底部 navBar 3 项（队列 history settings）。Home 内点 "开始转换" push ConvertScreen。
 * ConvertScreen / SettingsScreen 显示返回按钮，back 回 Home。
 *
 * 转场：横向 slide + fade（PC 端 cubic-bezier(0.2, 0.8, 0.2, 1) 的 Android 等价）
 */
@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            GlassNavBar(
                currentRoute = currentRoute,
                onSelect = { tab ->
                    if (tab.route == currentRoute) return@GlassNavBar
                    // 标准 tab pattern：popUpTo startDestination（inclusive=false 保留 Home）+ launchSingleTop
                    nav.navigate(tab.route) {
                        popUpTo(nav.graph.findStartDestination().id) { inclusive = false }
                        launchSingleTop = true
                    }
                },
            )
        },
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
    ) { innerPadding ->
        // 当前 tab 在底部 nav 列表里的下标（找不到时按 0 处理）
        val currentIndex = tabs.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)

        SwipeTabDetector(
            modifier = Modifier.padding(innerPadding),
            currentIndex = currentIndex,
            totalPages = tabs.size,
            onPrev = {
                // 右滑 → 切到上一个 tab（上）
                val prev = tabs.getOrNull(currentIndex - 1) ?: return@SwipeTabDetector
                nav.navigate(prev.route) {
                    popUpTo(nav.graph.findStartDestination().id) { inclusive = false }
                    launchSingleTop = true
                }
            },
            onNext = {
                // 左滑 → 切到下一个 tab（下）
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
                    // 进入：右滑 1/5 + 淡入；cubic-bezier(0.22, 1, 0.36, 1) 弹性收尾
                    slideInHorizontally(
                        initialOffsetX = { it / 5 },
                        animationSpec = tween(520, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)),
                    ) + fadeIn(
                        animationSpec = tween(380, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)),
                    )
                },
                exitTransition = {
                    // 离开：左滑 1/12 + 淡出（淡出比 slide 快，避免"内容留太久"）
                    slideOutHorizontally(
                        targetOffsetX = { -it / 12 },
                        animationSpec = tween(360, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)),
                    ) + fadeOut(
                        animationSpec = tween(220, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)),
                    )
                },
                popEnterTransition = {
                    // back 返回：左滑 1/12 + 淡入
                    slideInHorizontally(
                        initialOffsetX = { -it / 12 },
                        animationSpec = tween(520, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)),
                    ) + fadeIn(
                        animationSpec = tween(380, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)),
                    )
                },
                popExitTransition = {
                    // back 离开：右滑 1/5 + 淡出
                    slideOutHorizontally(
                        targetOffsetX = { it / 5 },
                        animationSpec = tween(360, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)),
                    ) + fadeOut(
                        animationSpec = tween(220, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)),
                    )
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

object Routes {
    const val HOME = "/"
    const val CONVERT = "/convert"
    const val HISTORY = "/history"
    const val SETTINGS = "/settings"
}

/**
 * 横向 swipe 切 tab 的手势检测器。
 *
 * 行为：
 *   - 右滑累积超过 [swipeThreshold] → 调 onPrev（切到上一个 tab / "上"）
 *   - 左滑累积超过 [swipeThreshold] → 调 onNext（切到下一个 tab / "下"）
 *   - 第一个 tab 右滑 / 最后一个 tab 左滑不响应（边界保护）
 *   - drag 触发后会给一次 haptic 反馈（LongPress 触觉）
 *
 * v1.3 阻尼反馈（damping feedback）：
 *   - drag 中实时显示屏幕中央箭头 overlay（← 指示上一个 / → 指示下一个）
 *   - 箭头 alpha 跟 drag 距离/阈值比例 (0..1) 联动
 *   - 箭头带 cubic-bezier(0.22, 1, 0.36, 1) 弹性入场,符合项目"慢 + 弹性"动画风格
 *   - 拖到位切 tab 时触发 spring 回弹 + 短暂淡出
 *   - 给"系统在响应你的手势"明确的视觉信号,避免 swipe 不可见
 *
 * 实现细节：
 *   - 用 detectHorizontalDragGestures 而非 Modifier.draggable，因为前者不与 LazyColumn 垂直
 *     滚动争抢（detectHorizontalDragGestures 只对水平 drag 回调，垂直 drag 透传）。
 *   - 累积 drag 距离，到阈值触发一次；触发后 reset 0，避免一次拖动连续切两个 tab。
 *   - 触发后不 consume 下层 change,允许 NavHost 内可能的水平 scrollable 区域仍能响应（虽然
 *     当前 4 个 tab 都没有水平 scrollable,这是预留保险）。
 *   - pointerInput key = currentIndex,tab 切换时重新订阅,totalDragX / dragProgress 自动重置。
 */
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
    // drag progress: -1..1，< 0 = 左滑（下一个），> 0 = 右滑（上一个）。animateFloatAsState 走
    // 弹性 cubic-bezier 缓动，让 overlay 跟手有"惯性"感
    var rawProgress by remember { mutableFloatStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = tween(
            durationMillis = 320,
            easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f),
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
                    // 不 consume change,下层水平 scrollable 仍能响应（当前 4 tab 都没用水平 scroll）
                    totalDragX += dragAmount
                    // 归一化进度:0..1。clamp 让 hint 在触到阈值后不再继续放大
                    rawProgress = (totalDragX / thresholdPx).coerceIn(-1f, 1f)
                    when {
                        // 左滑（负 dragAmount 累积到负值）→ 下一个 tab
                        totalDragX <= -thresholdPx && currentIndex < totalPages - 1 -> {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onNext()
                            totalDragX = 0f
                            rawProgress = 0f
                        }
                        // 右滑（正 dragAmount 累积到正值）→ 上一个 tab
                        totalDragX >= thresholdPx && currentIndex > 0 -> {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPrev()
                            totalDragX = 0f
                            rawProgress = 0f
                        }
                    }
                }
            }
    ) {
        content()

        // 阻尼反馈 overlay:左/右滑分别在对应边缘显示箭头
        // 仅在 drag 进度有值时显示,松手后弹回 0 顺便淡出
        if (abs(animatedProgress) > 0.02f) {
            SwipeHintOverlay(
                progress = animatedProgress,
                canPrev = currentIndex > 0,
                canNext = currentIndex < totalPages - 1,
            )
        }
    }
}

/**
 * swipe hint overlay:屏幕左/右边缘的"←/→"半透明圆形按钮
 *
 * - 进度 < 0（左滑）:右侧显示 → 箭头
 * - 进度 > 0（右滑）:左侧显示 ← 箭头
 * - 边界时(currentIndex 在首/尾):对应方向 hint 提示弱化(alpha 更低)
 * - alpha 跟 |progress| 联动(0..1),progress = 0.5 时 hint 半透
 */
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
            // 左滑（progress < 0）→ 右侧显示 →（下一个）
            progress < 0f && canNext -> {
                HintChip(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    label = "下一个",
                    alpha = hintAlpha,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 24.dp),
                )
            }
            // 右滑（progress > 0）→ 左侧显示 ←（上一个）
            progress > 0f && canPrev -> {
                HintChip(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    label = "上一个",
                    alpha = hintAlpha,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 24.dp),
                )
            }
            // 边界方向:显示更弱的提示(alpha 0.3)告诉用户"到头了"
            progress < 0f && !canNext -> {
                HintChip(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    label = "已是末页",
                    alpha = 0.3f,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 24.dp),
                )
            }
            progress > 0f && !canPrev -> {
                HintChip(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    label = "已是首页",
                    alpha = 0.3f,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 24.dp),
                )
            }
        }
    }
}

/**
 * 圆形 hint chip:半透磨砂背景 + 大箭头 + 副标签
 * alpha 0..1 由 caller 决定
 */
@Composable
private fun HintChip(
    icon: ImageVector,
    label: String,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Surface(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.CircleShape,
        color = BlueAccent.copy(alpha = alpha * 0.18f),
        contentColor = OnSurfaceHigh,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = BlueAccent.copy(alpha = alpha),
                modifier = Modifier.size(36.dp),
            )
        }
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
private fun GlassNavBar(
    currentRoute: String?,
    onSelect: (NavTab) -> Unit,
) {
    NavigationBar(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        tonalElevation = 0.dp,
    ) {
        tabs.forEach { tab ->
            val selected = currentRoute == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = BlueAccent,
                    selectedTextColor = BlueAccent,
                    indicatorColor = BlueAccent.copy(alpha = 0.18f),
                    unselectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
