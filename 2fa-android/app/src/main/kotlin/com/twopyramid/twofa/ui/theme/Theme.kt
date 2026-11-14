package com.twopyramid.twofa.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// ══ 形状 token（mapping §3.3）══════════════════════════════════════════
// 12dp 弹窗；8dp 按钮 / 输入框 / 页内块；6dp 行 / 小图标钮；50% 圆点。
// 投影默认无：只保留弹窗、下拉/浮层、主操作按钮（轻微）。卡片/列表行一律无投影。
object TwoFAShapes {
    val dialog = RoundedCornerShape(12.dp)
    val control = RoundedCornerShape(8.dp)
    val row = RoundedCornerShape(6.dp)
    val dot = RoundedCornerShape(percent = 50)
    val panel = control

    val material = Shapes(
        extraSmall = row,
        small = control,
        medium = dialog,
        large = dialog,
        extraLarge = dialog,
    )
}

// M3 ColorScheme 只是 Material 组件（Switch / ripple / 系统栏等）的桥接层；
// UI 自己的取色走 Paper.colors（本地 token 更细：surface2..6 / ink2..4 / 发丝线 / 遮罩）。
private fun lightScheme(c: PaperColors) = lightColorScheme(
    primary = c.accent,
    onPrimary = c.onAccent,
    primaryContainer = c.surface3,
    onPrimaryContainer = c.ink,
    inversePrimary = c.accent,
    secondary = c.ink,
    onSecondary = c.surface,
    secondaryContainer = c.surface3,
    onSecondaryContainer = c.ink,
    tertiary = c.ink2,
    onTertiary = c.surface,
    tertiaryContainer = c.surface3,
    onTertiaryContainer = c.ink,
    background = c.bg,
    onBackground = c.ink,
    surface = c.surface,
    onSurface = c.ink,
    surfaceVariant = c.surface3,
    onSurfaceVariant = c.ink2,
    surfaceTint = c.accent,
    inverseSurface = c.ink,
    inverseOnSurface = c.bg,
    error = c.danger,
    onError = Color.White,
    errorContainer = c.warnTint,
    onErrorContainer = c.danger,
    outline = c.hairlineStrong,
    outlineVariant = c.hairline,
    scrim = c.scrim,
    surfaceBright = c.surface,
    surfaceDim = c.bg,
    surfaceContainerLowest = c.surface,
    surfaceContainerLow = c.surface2,
    surfaceContainer = c.surface3,
    surfaceContainerHigh = c.surface4,
    surfaceContainerHighest = c.surface5,
)

private fun darkScheme(c: PaperColors) = darkColorScheme(
    primary = c.accent,
    onPrimary = c.onAccent,
    primaryContainer = c.surface4,
    onPrimaryContainer = c.ink,
    inversePrimary = c.accent,
    secondary = c.ink,
    onSecondary = c.surface,
    secondaryContainer = c.surface4,
    onSecondaryContainer = c.ink,
    tertiary = c.ink2,
    onTertiary = c.surface,
    tertiaryContainer = c.surface4,
    onTertiaryContainer = c.ink,
    background = c.bg,
    onBackground = c.ink,
    surface = c.surface,
    onSurface = c.ink,
    surfaceVariant = c.surface3,
    onSurfaceVariant = c.ink2,
    surfaceTint = c.accent,
    inverseSurface = c.ink,
    inverseOnSurface = c.bg,
    error = c.danger,
    onError = Color.White,
    errorContainer = c.warnTint,
    onErrorContainer = c.danger,
    outline = c.hairlineStrong,
    outlineVariant = c.hairline,
    scrim = c.scrim,
    surfaceBright = c.surface5,
    surfaceDim = c.bg,
    surfaceContainerLowest = c.surface,
    surfaceContainerLow = c.surface2,
    surfaceContainer = c.surface3,
    surfaceContainerHigh = c.surface4,
    surfaceContainerHighest = c.surface5,
)

// 应用主题。
//
// - darkTheme 由 MainActivity 按 ThemeMode（light/dark/system）解析后传入；
// - themeColor 是用户在 设置 → 外观 里挑的原始强调色（默认墨色 #14171A），
//   深色下由 OKLab 42% 白混合派生可用版本（deriveDarkAccent）；
// - motionScale 是「动画速率」三档的倍数，reducedMotion 读系统「移除动画」；
// - 关闭 dynamic color（Material You）：品牌色/暖纸墨不随壁纸漂移（报告 E3）。
@Composable
fun TwoFATheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeColor: Color = DefaultThemeColor,
    motionScale: Float = Motion.SPEED_NORMAL,
    content: @Composable () -> Unit,
) {
    val paper = remember(darkTheme, themeColor) {
        if (darkTheme) darkPaperColors(themeColor) else lightPaperColors(themeColor)
    }
    val scheme = remember(paper) { if (paper.isDark) darkScheme(paper) else lightScheme(paper) }
    val reducedMotion = rememberReducedMotion()
    val tokens = remember(motionScale, reducedMotion) {
        MotionTokens(
            speedScale = if (Motion.isValidSpeed(motionScale)) motionScale else Motion.SPEED_NORMAL,
            reduced = reducedMotion,
        )
    }

    CompositionLocalProvider(
        LocalPaperColors provides paper,
        LocalMotion provides tokens,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = TwoFATypography,
            shapes = TwoFAShapes.material,
            content = content,
        )
    }
}
