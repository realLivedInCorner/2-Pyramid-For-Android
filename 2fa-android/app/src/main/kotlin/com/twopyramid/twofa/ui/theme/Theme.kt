package com.twopyramid.twofa.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// ── PC 端对齐：默认蓝色主题（品牌色不随深/浅色变化）──
// 原始 / Hurricane 端：--theme-color 默认 #007bff，白底背景。
// 2FA Android 端 v1.x 误用金棕，v2 起回归 PC 端品牌。
internal val BlueAccent        = Color(0xFF007BFF)   // PC 端默认
internal val BlueAccentDeep    = Color(0xFF0056B3)
internal val BlueAccentSoft    = Color(0xFF66B2FF)

// ── 中性色原始 palette（只在构建 ColorScheme 时使用；UI 请用下方语义 token）──
private val Slate50            = Color(0xFFF8FAFC)   // 极浅背景
internal val Slate100          = Color(0xFFF1F5F9)   // 卡片底
private val Slate200           = Color(0xFFE2E8F0)   // 浅边
internal val Slate300          = Color(0xFFCBD5E1)
internal val Slate500          = Color(0xFF64748B)   // 二级文字
internal val Slate700          = Color(0xFF334155)   // 主文字
internal val Slate900          = Color(0xFF0F172A)   // 高对比

// 保留金色作为次要 accent（"Done" 状态、成功提示用）
internal val GoldPyramid       = Color(0xFFB8860B)
internal val GoldPyramidBright = Color(0xFFD4A847)
internal val GoldPyramidDeep   = Color(0xFF8B6508)

// 语义色（深浅色通用）
internal val MintAccent        = Color(0xFF22C55E)   // Done / success
internal val MintAccentSoft    = Color(0xFF86EFAC)
internal val CrimsonWarn       = Color(0xFFDC2626)   // Failed / danger
internal val CrimsonWarnSoft   = Color(0xFFFCA5A5)
internal val CyanInfo          = Color(0xFF0EA5E9)   // info
internal val AmberNotice       = Color(0xFFF59E0B)   // warning

// ── 语义文字/玻璃 token：跟随当前 ColorScheme（v2.1 起支持深色）──
// 旧实现是固定浅色常量（Slate900/700/500），深色主题下会变成深字深底不可读。
// 这里改成 @Composable getter：浅色 = 原来的效果，深色自动用 dark scheme 的 onSurface。
internal val OnSurfaceHigh: Color
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.onSurface

internal val OnSurfaceMid: Color
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.onSurfaceVariant

internal val OnSurfaceLow: Color
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)

// ── Glass / Frosted token（浅色白半透 / 深色暗半透，靠 surface + alpha 表达）──
internal val GlassSurface: Color
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)

internal val GlassSurfaceHigh: Color
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)

internal val GlassOutline: Color
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)

internal val GlassHighlight: Color = Color.White.copy(alpha = 0.35f)

private val LightScheme = lightColorScheme(
    primary            = BlueAccent,
    onPrimary          = Color.White,
    primaryContainer   = BlueAccentSoft,
    onPrimaryContainer = BlueAccentDeep,
    secondary          = MintAccent,
    onSecondary        = Color.White,
    secondaryContainer = MintAccentSoft,
    onSecondaryContainer = Color(0xFF14532D),
    tertiary           = CyanInfo,
    onTertiary         = Color.White,
    error              = CrimsonWarn,
    onError            = Color.White,
    errorContainer     = CrimsonWarnSoft,
    onErrorContainer   = Color(0xFF7F1D1D),
    surface            = Color.White,
    onSurface          = Slate900,
    surfaceVariant     = Slate100,
    onSurfaceVariant   = Slate700,
    surfaceContainer   = Slate50,
    surfaceContainerHigh = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Slate50,
    outline            = Slate200,
    outlineVariant     = Slate200.copy(alpha = 0.5f),
    background         = Color(0xFFFAFBFC),
    onBackground       = Slate900,
)

private val DarkScheme = darkColorScheme(
    primary            = BlueAccent,
    onPrimary          = Color.White,
    primaryContainer   = BlueAccentDeep,
    onPrimaryContainer = Color.White,
    secondary          = MintAccent,
    onSecondary        = Color(0xFF0A1F12),
    tertiary           = CyanInfo,
    onTertiary         = Color(0xFF002130),
    error              = CrimsonWarn,
    onError            = Color.White,
    surface            = Color(0xFF0F1419),
    onSurface          = Color(0xFFE6E9EF),
    surfaceVariant     = Color(0xFF1B1F25),
    onSurfaceVariant   = Color(0xFF9AA1AC),
    surfaceContainer   = Color(0xFF161B21),
    surfaceContainerHigh = Color(0xFF1F242A),
    surfaceContainerLowest = Color(0xFF0A0D11),
    surfaceContainerLow = Color(0xFF0F1419),
    outline            = Color(0xFF2A2F37),
    outlineVariant     = Color(0xFF1F242A),
    background         = Color(0xFF0A0D11),
    onBackground       = Color(0xFFE6E9EF),
)

/**
 * Shape token：PC 端 .group-card 用 24px；pill/segmented 用 999px；button/small card 16-20px。
 */
data class TwoFAShapes(
    val cardLarge: androidx.compose.ui.graphics.Shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    val cardMedium: androidx.compose.ui.graphics.Shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    val cardSmall: androidx.compose.ui.graphics.Shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    val pill: androidx.compose.ui.graphics.Shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
    val chip: androidx.compose.ui.graphics.Shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
)

val LocalTwoFAShapes = staticCompositionLocalOf { TwoFAShapes() }

/**
 * Elevation token：PC 端 .group-card 用 0 4 20 rgba(0,0,0,0.02)，dock 用 0 12 30 rgba(0,0,0,0.12)。
 */
data class TwoFAElevations(
    val card: androidx.compose.ui.unit.Dp = 4.dp,           // group-card 微浮
    val cardShadow: androidx.compose.ui.unit.Dp = 20.dp,
    val dock: androidx.compose.ui.unit.Dp = 12.dp,         // dock 浮起
    val dockShadow: androidx.compose.ui.unit.Dp = 30.dp,
    val popup: androidx.compose.ui.unit.Dp = 20.dp,        // dialog
    val popupShadow: androidx.compose.ui.unit.Dp = 50.dp,
)

val LocalTwoFAElevations = staticCompositionLocalOf { TwoFAElevations() }

/**
 * 额外的设计 token：动画时长 / 步距 / 缓动集中放。
 *
 * 用户偏好（"慢 + 弹性 stagger"）编码在这里。
 *
 * v1.2 减负：80ms→40ms、500ms→360ms、380ms→280ms，shadow 同时减薄，
 * 解决"切 Settings 卡卡"（Compose 一次性 mount 多个 GlassCard + shadow 的合成压力）。
 */
data class TwoFATokens(
    val staggerDelayMs: Int = 40,
    val entranceDurationMs: Int = 360,
    val exitDurationMs: Int = 280,
    val staggerEasing: androidx.compose.animation.core.CubicBezierEasing =
        // PC 端 .page-shell 用 cubic-bezier(0.2, 0.8, 0.2, 1) — 弹性收尾，慢出
        androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f),
)

val LocalTwoFATokens = staticCompositionLocalOf { TwoFATokens() }

/**
 * 应用主题。
 *
 * v2.1（桌面同步）：由 [com.twopyramid.twofa.settings.ThemeMode] 驱动（light/dark/system），
 * 由 `MainActivity` 从 [com.twopyramid.twofa.settings.SettingsRepository] 读取后传入。
 *
 * **关闭 dynamic color（Material You）**：Android 12+ 的系统取色会让品牌蓝随壁纸漂移，
 * 与桌面「固定 #007BFF」的品牌口径冲突；这里统一用品牌 Light/DarkScheme（报告 §4.3 E3）。
 */
@Composable
fun TwoFATheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme

    CompositionLocalProvider(
        LocalTwoFATokens provides TwoFATokens(),
        LocalTwoFAShapes provides TwoFAShapes(),
        LocalTwoFAElevations provides TwoFAElevations(),
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = TwoFATypography,
            content = content,
        )
    }
}
