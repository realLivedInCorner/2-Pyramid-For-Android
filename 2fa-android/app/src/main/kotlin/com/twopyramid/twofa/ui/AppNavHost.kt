package com.twopyramid.twofa.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding),
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

object Routes {
    const val HOME = "/"
    const val CONVERT = "/convert"
    const val HISTORY = "/history"
    const val SETTINGS = "/settings"
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
