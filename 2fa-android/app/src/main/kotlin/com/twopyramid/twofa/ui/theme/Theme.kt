package com.twopyramid.twofa.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// ── PC 端对齐：默认蓝色主题，light 模式为主 ──
// 原始 / Huricane 端：--theme-color 默认 #007bff，白底背景。
// 2FA Android 端 v1.x 误用金棕，v2 起回归 PC 端品牌。
internal val BlueAccent        = Color(0xFF007BFF)   // PC 端默认
internal val BlueAccentDeep   = Color(0xFF0056B3)
internal val BlueAccentSoft   = Color(0xFF66B2FF)

// ── Slate (中性冷色，比 GoldPyramid 暖色更克制) ──
internal val Slate50          = Color(0xFFF8FAFC)   // 极浅背景
internal val Slate100         = Color(0xFFF1F5F9)   // 卡片底
internal val Slate200         = Color(0xFFE2E8F0)   // 浅边
internal val Slate300         = Color(0xFFCBD5E1)
internal val Slate500         = Color(0xFF64748B)   // 二级文字
internal val Slate700         = Color(0xFF334155)   // 主文字
internal val Slate900         = Color(0xFF0F172A)   // 高对比

// 保留金色作为次要 accent（"Done" 状态、成功提示用）
internal val GoldPyramid      = Color(0xFFB8860B)
internal val GoldPyramidBright = Color(0xFFD4A847)
internal val GoldPyramidDeep  = Color(0xFF8B6508)

// 语义色
internal val MintAccent       = Color(0xFF22C55E)   // Done / success
internal val MintAccentSoft   = Color(0xFF86EFAC)
internal val CrimsonWarn      = Color(0xFFDC2626)   // Failed / danger
internal val CrimsonWarnSoft  = Color(0xFFFCA5A5)
internal val CyanInfo         = Color(0xFF0EA5E9)   // info
internal val AmberNotice      = Color(0xFFF59E0B)   // warning

// ── 文字 (light theme 配色) ──
internal val OnSurfaceHigh    = Slate900
internal val OnSurfaceMid     = Slate700
internal val OnSurfaceLow     = Slate500

// ── Glass / Frosted (Compose 1.7+ blur via RenderEffect, minSdk 31+) ──
// Light theme：白半透 + 浅边；Dark theme：dark 半透。
// v1.2 调色：GlassOutline 边从 alpha 0.8 降到 0.5（更柔和，避免颜色"怪"）。
internal val GlassSurface     = Color(0xFFFFFFFF).copy(alpha = 0.72f)
internal val GlassSurfaceHigh = Color(0xFFFFFFFF).copy(alpha = 0.85f)
internal val GlassOutline     = Slate200.copy(alpha = 0.5f)
internal val GlassHighlight   = Color(0xFFFFFFFF).copy(alpha = 0.5f)

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
    onSurface          = OnSurfaceHigh,
    surfaceVariant     = Slate100,
    onSurfaceVariant   = OnSurfaceMid,
    surfaceContainer   = Slate50,
    surfaceContainerHigh = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Slate50,
    outline            = Slate200,
    outlineVariant     = Slate200.copy(alpha = 0.5f),
    background         = Color(0xFFFAFBFC),
    onBackground       = OnSurfaceHigh,
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

@Composable
fun TwoFATheme(
    darkTheme: Boolean = false,  // 默认 light 主题（PC 端对齐）
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = when {
        // Android 12+ 用系统动态色（Material You），但允许用户后续覆盖
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !darkTheme -> dynamicLightColorScheme(context)
        darkTheme -> DarkScheme
        else      -> LightScheme
    }

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
