package com.twopyramid.twofa.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.math.roundToInt

// ══ 暖纸墨设计令牌（唯一颜色来源）══════════════════════════════════════
// 规则：Screen 内不允许写死颜色，一律通过 Paper.colors 或 MaterialTheme.colorScheme 取。
//
// 浅色 = 暖白纸面 + 墨；深色 = 暖色反相（近黑偏黄 #1A1815），不是中性黑 ——
// 中性黑会让界面从「纸」变成「屏幕」，暖色发丝线也会显脏。
//
// 发丝线 / 悬停染色 / 面层叠加统一用「暖灰 RGB + alpha」表达：
// 浅色下墨色压暗、白面提亮；深色下方向反过来（发丝线用亮暖灰、面层白 alpha 弱一个量级）。
// alpha 的档位不变，只翻载体色 —— 这一点与桌面 shared.css 的 --ink-rgb / --warm-rgb 同构。

// 强调色默认值 = 墨色；用户在 设置 → 外观 里可自选（持久化 hex）。
const val DEFAULT_THEME_COLOR_HEX = "#14171A"

internal val DefaultThemeColor = Color(0xFF14171A)

// 暖灰（发丝线 / 悬停染色载体）：浅色 120,110,95；深色翻成亮暖灰 150,143,132。
private fun rgba(r: Int, g: Int, b: Int, alpha: Float): Color =
    Color(r, g, b, (alpha * 255f).roundToInt().coerceIn(0, 255))

data class PaperColors(
    val isDark: Boolean,
    // 底色与面（由浅到深的暖色阶梯）
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val surface4: Color,
    val surface5: Color,
    val surface6: Color,
    // 墨色文字阶梯：主 / 次 / 三级 / 最弱
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val ink4: Color,
    // 语义色：危险 / 警示 / 成功
    val danger: Color,
    val warn: Color,
    val ok: Color,
    val warnTint: Color,
    val warnLine: Color,
    // 强调色（浅色直接用用户色；深色 OKLab 42% 提亮）
    val accent: Color,
    val onAccent: Color,
    // 发丝线（1dp 分隔线 / 描边）与悬停、按下染色
    val hairline: Color,
    val hairlineStrong: Color,
    val hover: Color,
    val pressed: Color,
    // 面层叠加（弹窗面 / 按钮底 / 卡面）
    val panel1: Color,
    val panel2: Color,
    val panel3: Color,
    val panel4: Color,
    // 弹窗遮罩 / 弹窗投影（唯一允许投影的地方）
    val scrim: Color,
    val dialogShadow: Color,
    // 页面背景：surface → bg 的极轻暖色阶梯 + 顶部柔光
    val backdropTop: Color,
    val backdropBottom: Color,
    val backdropGlow: Color,
)

// 浅色令牌（mapping §3.1）。alpha 档位：发丝线 0.14 / 深 0.20，悬停 0.06，按下 0.09。
private fun lightPaper(themeColor: Color): PaperColors = PaperColors(
    isDark = false,
    bg = Color(0xFFF4F2EE),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF7F5F1),
    surface3 = Color(0xFFF2EFE9),
    surface4 = Color(0xFFF0EDE8),
    surface5 = Color(0xFFE8E4DC),
    surface6 = Color(0xFFDED9D0),
    ink = Color(0xFF14171A),
    ink2 = Color(0xFF5F5A51),
    ink3 = Color(0xFF6F6A61),
    ink4 = Color(0xFF8C877E),
    danger = Color(0xFFB3261E),
    warn = Color(0xFFB45309),
    ok = Color(0xFF10B981),
    warnTint = Color(0xFFFEF3C7),
    warnLine = Color(0xFFFDE68A),
    accent = themeColor,
    onAccent = onAccentFor(themeColor),
    hairline = rgba(120, 110, 95, 0.14f),
    hairlineStrong = rgba(120, 110, 95, 0.20f),
    hover = rgba(120, 110, 95, 0.06f),
    pressed = rgba(120, 110, 95, 0.09f),
    panel1 = rgba(255, 255, 255, 0.35f),
    panel2 = rgba(255, 255, 255, 0.55f),
    panel3 = rgba(255, 255, 255, 0.72f),
    panel4 = rgba(255, 255, 255, 0.90f),
    scrim = Color(0x571C1812),        // rgba(28,24,18,0.34) 暖色遮罩，不是冷灰
    dialogShadow = Color(0x241C1812), // rgba(28,24,18,0.14) 仅弹窗保留投影
    backdropTop = Color(0xFFF7F5F1),
    backdropBottom = Color(0xFFF4F2EE),
    backdropGlow = rgba(255, 255, 255, 0.55f),
)

// 深色令牌（mapping §3.1）：暖色反相，面层逐级提亮并保持同一色相。
private fun darkPaper(themeColor: Color): PaperColors {
    val derivedAccent = deriveDarkAccent(themeColor)
    return PaperColors(
        isDark = true,
        bg = Color(0xFF1A1815),
        surface = Color(0xFF232019),
        surface2 = Color(0xFF201D1A),
        surface3 = Color(0xFF262320),
        surface4 = Color(0xFF2B2723),
        surface5 = Color(0xFF322D28),
        surface6 = Color(0xFF3A342E),
        ink = Color(0xFFF2EFE9),
        ink2 = Color(0xFFB8B2A8),
        ink3 = Color(0xFF8E887D),
        ink4 = Color(0xFF6F6A60),
        danger = Color(0xFFE5534B),
        warn = Color(0xFFD29922),
        ok = Color(0xFF3FB950),
        warnTint = rgba(210, 153, 34, 0.14f),
        warnLine = rgba(210, 153, 34, 0.32f),
        accent = derivedAccent,
        onAccent = onAccentFor(derivedAccent),
        // 深色下不翻成纯白：亮暖灰「勉强看得出来」即可，纯白一排按钮会糊成一片。
        hairline = rgba(150, 143, 132, 0.10f),
        hairlineStrong = rgba(150, 143, 132, 0.16f),
        hover = rgba(150, 143, 132, 0.045f),
        pressed = rgba(150, 143, 132, 0.07f),
        panel1 = rgba(255, 252, 245, 0.03f),
        panel2 = rgba(255, 252, 245, 0.045f),
        panel3 = rgba(255, 252, 245, 0.06f),
        panel4 = Color(0xFF232019),
        scrim = Color(0x70100E0B),       // 深色遮罩略深（暖黑）
        dialogShadow = Color(0x73000000),
        backdropTop = Color(0xFF201D1A),
        backdropBottom = Color(0xFF1A1815),
        backdropGlow = rgba(255, 252, 245, 0.03f),
    )
}

fun lightPaperColors(themeColor: Color = DefaultThemeColor): PaperColors = lightPaper(themeColor)

fun darkPaperColors(themeColor: Color = DefaultThemeColor): PaperColors = darkPaper(themeColor)

// 强调色上的文字色：取与强调色对比度更高的那一方（亮强调色 → 墨字，暗强调色 → 白字）。
// 深色派生后的默认强调色约 #929496（浅灰），这里会正确选墨字而不是白字。
internal fun onAccentFor(accent: Color): Color {
    val ink = Color(0xFF14171A)
    val white = Color(0xFFFFFFFF)
    return if (contrastRatio(white, accent) >= contrastRatio(ink, accent)) white else ink
}

private fun contrastRatio(a: Color, b: Color): Float {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    val lighter = if (la > lb) la else lb
    val darker = if (la > lb) lb else la
    return (lighter + 0.05f) / (darker + 0.05f)
}

private fun relativeLuminance(color: Color): Float {
    fun channel(c: Float): Float =
        if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
    return 0.2126f * channel(color.red) + 0.7152f * channel(color.green) + 0.0722f * channel(color.blue)
}

// 十六进制颜色文本 → Color（宽松解析；非法输入回退 fallback）。
fun parseHexColor(hex: String, fallback: Color = DefaultThemeColor): Color {
    val raw = hex.trim().removePrefix("#")
    if (raw.length != 6) return fallback
    return runCatching {
        val value = raw.toLong(16)
        Color(
            red = ((value shr 16) and 0xFF).toInt(),
            green = ((value shr 8) and 0xFF).toInt(),
            blue = (value and 0xFF).toInt(),
            alpha = 0xFF,
        )
    }.getOrDefault(fallback)
}

// Color → 大写 #RRGGBB（设置页 hex 显示 / 持久化）。
fun toHexString(color: Color): String {
    val r = (color.red * 255f).roundToInt().coerceIn(0, 255)
    val g = (color.green * 255f).roundToInt().coerceIn(0, 255)
    val b = (color.blue * 255f).roundToInt().coerceIn(0, 255)
    return "#" + r.toString(16).padStart(2, '0').uppercase() +
        g.toString(16).padStart(2, '0').uppercase() +
        b.toString(16).padStart(2, '0').uppercase()
}

val LocalPaperColors = staticCompositionLocalOf { lightPaper(DefaultThemeColor) }

// 组合内取当前主题令牌：`val c = Paper.colors` 后 `c.ink` / `c.hairline` ...
object Paper {
    val colors: PaperColors
        @Composable @ReadOnlyComposable
        get() = LocalPaperColors.current
}
