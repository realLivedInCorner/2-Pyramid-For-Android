package com.twopyramid.twofa.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.twopyramid.twofa.ui.theme.GlassOutline
import com.twopyramid.twofa.ui.theme.GlassSurface
import com.twopyramid.twofa.ui.theme.LocalTwoFAElevations

/**
 * 公共玻璃卡：白半透 + 浅边 + 微阴影 + 大圆角。
 * PC 端 .group-card 的 Android 等价物（24px 圆角 / blur 20 / 边框 1px / 阴影 0 4 20 rgba(0,0,0,0.02)）。
 *
 * 实际 blur 由 Material You / RenderEffect 父级提供；这里用 `Color.copy(alpha)` 模拟半透效果，
 * 配合父级 AuroraBackground 的渐变底，视觉等价 PC 端 backdrop-filter。
 *
 * v1.2 减 shadow 默认值：elevation 4→2，shadowRadius 20→10。
 * 原因：Compose 一次性 mount 多张卡时 shadow 合成压力大（切 Settings 卡）。
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(24.dp),
    elevation: Dp = 2.dp,
    shadowRadius: Dp = 10.dp,
    background: Color = GlassSurface,
    borderColor: Color = GlassOutline,
    borderWidth: Dp = 1.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.04f),
                spotColor = Color.Black.copy(alpha = 0.04f),
            )
            .clip(shape)
            .background(background)
            .border(borderWidth, borderColor, shape),
    ) {
        content()
    }
}
