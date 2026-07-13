package com.twopyramid.twofa.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.twopyramid.twofa.ui.theme.BlueAccent

/**
 * Primary / Secondary 两组按钮〟 *
 * 主要用于 BottomSheet、Card 内的操作行。点击有轻微按下缩放反馈〟 */

@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    leadingIcon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    FilledPill(
        text = text,
        background = MaterialTheme.colorScheme.primary,
        foreground = MaterialTheme.colorScheme.onPrimary,
        onClick = onClick,
        leadingIcon = leadingIcon,
        modifier = modifier,
    )
}

@Composable
fun SecondaryActionButton(
    text: String,
    onClick: () -> Unit,
    leadingIcon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    FilledPill(
        text = text,
        background = MaterialTheme.colorScheme.surface,
        foreground = MaterialTheme.colorScheme.onSurface,
        onClick = onClick,
        leadingIcon = leadingIcon,
        modifier = modifier
            .then(
                Modifier.background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(14.dp),
                ),
            ),
    )
}

@Composable
private fun FilledPill(
    text: String,
    background: androidx.compose.ui.graphics.Color,
    foreground: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    leadingIcon: ImageVector?,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    val padX by animateDpAsState(
        targetValue = if (pressed) 12.dp else 16.dp,
        animationSpec = tween(120),
        label = "press-pad",
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .clickable {
                pressed = true
                onClick()
                pressed = false
            }
            .padding(horizontal = padX, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null) {
                Icon(
                    leadingIcon,
                    contentDescription = null,
                    tint = foreground,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = foreground,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
