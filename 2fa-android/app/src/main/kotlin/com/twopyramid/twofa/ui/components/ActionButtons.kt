package com.twopyramid.twofa.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.twopyramid.twofa.ui.theme.AppText
import com.twopyramid.twofa.ui.theme.Paper
import com.twopyramid.twofa.ui.theme.TwoFAShapes

// 页面级操作按钮（mapping §4 组件：8dp 圆角、32/40dp 高、ink/accent）。
// 投影默认无 —— 只有弹窗浮层保留投影，按钮的「主」靠墨/强调色底而不是靠抬升。
// 弹窗页脚用 BrandDialogButton（32dp），这里只放页面/面板内的 40dp 按钮。

@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 40.dp,
) {
    val c = Paper.colors
    CompositionLocalProvider(LocalContentColor provides c.onAccent) {
        Box(
            modifier = modifier
                .height(height)
                .clip(TwoFAShapes.control)
                .background(if (enabled) c.accent else c.accent.copy(alpha = 0.45f))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leadingIcon != null) {
                    Icon(leadingIcon, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(7.dp))
                }
                Text(text, style = AppText.buttonStrong, color = c.onAccent, maxLines = 1)
            }
        }
    }
}

@Composable
fun SecondaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 40.dp,
) {
    val c = Paper.colors
    val contentColor = if (enabled) c.ink2 else c.ink4
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Box(
            modifier = modifier
                .height(height)
                .clip(TwoFAShapes.control)
                .background(Color.Transparent)
                .border(1.dp, c.hairlineStrong, TwoFAShapes.control)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leadingIcon != null) {
                    Icon(leadingIcon, contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(text, style = AppText.button, color = contentColor, maxLines = 1)
            }
        }
    }
}
