package com.twopyramid.twofa.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ══ 字阶 token（mapping §3.2，来源桌面 dialog-spec §2.2）════════════════
//
// 八档：24 / 18 / 15 / 14 / 13 / 12 / 11.5 / 10.5sp
//   24   页面主标题（仅页面级）
//   18   阶段标题 / 首页 CTA
//   15   弹窗标题
//   14   卡片 / 条目标题
//   13   正文与按钮（正文行高 1.65）
//   12   次要说明
//   11.5 标语
//   10.5 小字标签（uppercase，letter-spacing 0.18em）
//
// 字重上限 600：只用 Normal(400) / Medium(500) / SemiBold(600)。
// 唯一例外：首页品牌名（它是标识，不是 UI 文本），见 BrandHeader。
// 中文标题 letter-spacing 一律 0（不要负字距）；数字用 tnum 等宽。

private val Sans = FontFamily.SansSerif

object AppText {
    // 24sp / 600 —— 页面主标题
    val pageTitle = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.sp,
    )

    // 18sp / 600 —— 阶段标题、首页 CTA
    val sectionTitle = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    )

    // 15sp / 600 —— 弹窗标题
    val dialogTitle = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    )

    // 14sp / 600 —— 卡片 / 条目标题
    val itemTitle = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    )

    // 13sp / 400 / 行高 1.65 —— 正文
    val body = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = (13f * 1.65f).sp,
        letterSpacing = 0.sp,
    )

    // 12sp / 400 —— 次要说明
    val secondary = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
    )

    // 11.5sp / 400 —— 标语
    val tagline = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 11.5.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
    )

    // 10.5sp / 500 / 0.18em —— 小字标签（配合 .uppercase() 使用）
    val label = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
        letterSpacing = (10.5f * 0.18f).sp,
    )

    // 13sp / 500 —— 按钮
    val button = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
    )

    // 13sp / 600 —— 主操作按钮
    val buttonStrong = button.copy(fontWeight = FontWeight.SemiBold)

    // 数字等宽（耗时 / BUILD / 版本号）
    val numeric = secondary.copy(fontFeatureSettings = "tnum")

    // 首页品牌名（唯一允许 600 以上的标识文字：700）
    val brandName = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp,
    )
}

// M3 Typography 槽位全部映射到 ≤600 的字重，避免 Material 组件（Switch 标签、
// BottomSheet 标题等）回落到 M3 默认字重。未显式使用的槽位也一并收敛。
val TwoFATypography = Typography(
    displayLarge = AppText.pageTitle,
    displayMedium = AppText.sectionTitle,
    displaySmall = AppText.dialogTitle,
    headlineLarge = AppText.pageTitle,
    headlineMedium = AppText.sectionTitle,
    headlineSmall = AppText.dialogTitle,
    titleLarge = AppText.itemTitle,
    titleMedium = AppText.itemTitle.copy(fontWeight = FontWeight.Medium),
    titleSmall = AppText.buttonStrong,
    bodyLarge = AppText.body,
    bodyMedium = AppText.body,
    bodySmall = AppText.secondary,
    labelLarge = AppText.button,
    labelMedium = AppText.secondary.copy(fontWeight = FontWeight.Medium),
    labelSmall = AppText.label,
)
