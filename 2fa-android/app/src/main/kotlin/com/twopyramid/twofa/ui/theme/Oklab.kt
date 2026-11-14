package com.twopyramid.twofa.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.pow

// ══ OKLab 色彩工具（深色强调色派生）═══════════════════════════════════
//
// 深色下强调色要「保色相、提明度」：用户挑的是颜色不是明度，直接反转/压暗等于
// 让这个设置失效。做法与桌面 shared.css 完全一致：
//     color-mix(in oklab, themeColor 42%, #ffffff)
// 即 themeColor 权重 42%、白色权重 58%，在 OKLab 空间线性插值。
//
// 为什么用 OKLab 而不是 sRGB 混白：sRGB 里往白里混会明显偏色（蓝发紫、黄发绿）。
// 为什么是 42%：桌面实测这是还能过 WCAG AA（文本对比 >= 4.5）的最大保留比例，
// 48% 时降到 4.40 不合格。注意方向：42% 是**用户色**的占比，越大越暗。
//
// 深色默认色 #14171A 的派生结果约为 #9D9C9A（桌面同源规则，见报告/令牌说明）。

private data class Oklab(val l: Double, val a: Double, val b: Double)

private const val USER_WEIGHT_IN_DARK = 0.42f

// 深色强调色：用户色 42% + 白 58%（OKLab 插值）。
fun deriveDarkAccent(themeColor: Color): Color =
    mixOklab(themeColor, Color.White, weightA = USER_WEIGHT_IN_DARK)

// 两色在 OKLab 空间线性插值，weightA ∈ [0,1] 为 colorA 的权重。
fun mixOklab(colorA: Color, colorB: Color, weightA: Float): Color {
    val w = weightA.coerceIn(0f, 1f)
    val la = toOklab(colorA)
    val lb = toOklab(colorB)
    val mixed = Oklab(
        l = la.l * w + lb.l * (1f - w),
        a = la.a * w + lb.a * (1f - w),
        b = la.b * w + lb.b * (1f - w),
    )
    return fromOklab(mixed)
}

// sRGB(0..1) → linear RGB
private fun srgbToLinear(c: Double): Double =
    if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

// linear RGB → sRGB(0..1)
private fun linearToSrgb(c: Double): Double =
    if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1.0 / 2.4) - 0.055

private fun toOklab(color: Color): Oklab {
    val r = srgbToLinear(color.red.toDouble())
    val g = srgbToLinear(color.green.toDouble())
    val b = srgbToLinear(color.blue.toDouble())

    val l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b
    val m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b
    val s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b

    val l_ = cbrt(l)
    val m_ = cbrt(m)
    val s_ = cbrt(s)

    return Oklab(
        l = 0.2104542553 * l_ + 0.7936177850 * m_ - 0.0040720468 * s_,
        a = 1.9779984951 * l_ - 2.4285922050 * m_ + 0.4505937099 * s_,
        b = 0.0259040371 * l_ + 0.7827717662 * m_ - 0.8086757660 * s_,
    )
}

private fun fromOklab(lab: Oklab): Color {
    val l_ = lab.l + 0.3963377774 * lab.a + 0.2158037573 * lab.b
    val m_ = lab.l - 0.1055613458 * lab.a - 0.0638541728 * lab.b
    val s_ = lab.l - 0.0894841775 * lab.a - 1.2914855480 * lab.b

    val l = l_ * l_ * l_
    val m = m_ * m_ * m_
    val s = s_ * s_ * s_

    val rLin = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
    val gLin = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
    val bLin = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s

    fun encode(v: Double): Float =
        linearToSrgb(v.coerceIn(0.0, 1.0)).toFloat().coerceIn(0f, 1f)

    return Color(encode(rLin), encode(gLin), encode(bLin), 1f)
}

// 三次方根（负数保留符号；OKLab 的 LMS 在合法输入下非负，这里仍防御）。
private fun cbrt(x: Double): Double =
    if (x < 0.0) -((-x).pow(1.0 / 3.0)) else x.pow(1.0 / 3.0)

// 供调试/日志使用：两个 OKLab 色的感知距离（欧氏近似）。
fun oklabDistance(colorA: Color, colorB: Color): Float {
    val a = toOklab(colorA)
    val b = toOklab(colorB)
    val dl = a.l - b.l
    val da = a.a - b.a
    val db = a.b - b.b
    return abs(dl * dl + da * da + db * db).toFloat()
}
