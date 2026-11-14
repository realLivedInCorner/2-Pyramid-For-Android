package com.twopyramid.twofa.ui

import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.twopyramid.twofa.settings.SettingsRepository
import com.twopyramid.twofa.settings.ThemeMode
import com.twopyramid.twofa.ui.theme.Paper
import com.twopyramid.twofa.ui.theme.TwoFATheme
import com.twopyramid.twofa.ui.theme.parseHexColor

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 首帧前同步读设置（TwoFAApp.onCreate 已完成 SettingsRepository.init，这里幂等兜底）：
        // 提前把 window 背景设成目标主题的暖纸墨底色，避免深色/浅色用户启动时先闪一帧反色。
        SettingsRepository.init(this)
        val initialDark = SettingsRepository.resolveDarkTheme(this)
        window.setBackgroundDrawable(
            ColorDrawable(if (initialDark) DARK_WINDOW_BG else LIGHT_WINDOW_BG),
        )
        applySystemBars(initialDark)

        setContent {
            val themeMode by SettingsRepository.themeMode.collectAsState()
            val themeColorHex by SettingsRepository.themeColor.collectAsState()
            val motionScale by SettingsRepository.motionScale.collectAsState()
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            val themeColor = remember(themeColorHex) { parseHexColor(themeColorHex) }

            // 主题切换后同步系统栏图标明暗（深色背景 → 浅色图标）
            LaunchedEffect(darkTheme) { applySystemBars(darkTheme) }

            TwoFATheme(
                darkTheme = darkTheme,
                themeColor = themeColor,
                motionScale = motionScale,
            ) {
                PaperBackdrop {
                    AppNavHost()
                }
            }
        }
    }

    private fun applySystemBars(dark: Boolean) {
        val style = if (dark) {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        } else {
            SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    private companion object {
        // 与 ui/theme/Color.kt 的 bg 令牌 / res/values 的 launch_background_* 保持一致（暖纸墨）。
        private const val LIGHT_WINDOW_BG = 0xFFF4F2EE.toInt()
        private const val DARK_WINDOW_BG = 0xFF1A1815.toInt()
    }
}

// 全应用背景：暖色阶梯（surface → bg）+ 顶部极轻柔光，静态不跑动画。
// 取代旧的 VortexBackground（4 个蓝色 blob + 24s 极光漂移）—— 冷色极光和常驻装饰动画
// 都是规范明确要去掉的内容（mapping §4 Home / motion-shell §2.7 装饰类）。
@Composable
private fun PaperBackdrop(content: @Composable () -> Unit) {
    val c = Paper.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(c.backdropTop, c.backdropBottom),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.radialGradient(
                        colors = listOf(c.backdropGlow, Color.Transparent),
                    ),
                ),
        )
        content()
    }
}
