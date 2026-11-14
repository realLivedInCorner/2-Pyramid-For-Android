package com.twopyramid.twofa.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.twopyramid.twofa.ui.theme.AppText
import com.twopyramid.twofa.ui.theme.Motion
import com.twopyramid.twofa.ui.theme.Paper
import com.twopyramid.twofa.ui.theme.TwoFAShapes
import com.twopyramid.twofa.ui.theme.motion

// ══ 统一弹窗 / 底部面板壳（mapping §3.5，唯一一份）═══════════════════
//
// 全应用只允许这一套弹窗外观：
//   遮罩  暖色 rgba(28,24,18,0.34)（深色略深），不用冷灰 / 纯黑
//   面板  surface 底 + 1dp 暖发丝线 + 12dp 圆角 + 20dp 内边距 + 弹窗投影
//   标题  左标题 15sp/600，右关闭钮 26dp/圆角 6/ink3
//   正文  13sp / 行高 1.65 / ink2；区块 gap 8dp
//   按钮  右对齐、顶部暖发丝线；32dp 高 / 8dp 圆角 / 13sp/500；主按钮 accent 底
//   进场  ease-out 240ms：遮罩淡入 + 面板 opacity + 上移 10dp（不用 scale）
//   减少动效：位移瞬时到位，只保留 opacity 交叉
//
// 之前各 Screen 各写一套 AlertDialog / ModalBottomSheet，这里收敛成一个组件。

enum class DialogButtonTone { QUIET, DANGER, PRIMARY }

@Composable
fun DialogTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: DialogButtonTone = DialogButtonTone.QUIET,
    enabled: Boolean = true,
) {
    val c = Paper.colors
    val contentColor = when (tone) {
        DialogButtonTone.QUIET -> if (enabled) c.ink2 else c.ink4
        DialogButtonTone.DANGER -> if (enabled) c.danger else c.ink4
        DialogButtonTone.PRIMARY -> if (enabled) c.onAccent else c.onAccent.copy(alpha = 0.6f)
    }
    val background = when (tone) {
        DialogButtonTone.PRIMARY -> if (enabled) c.accent else c.accent.copy(alpha = 0.45f)
        else -> Color.Transparent
    }
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(TwoFAShapes.control)
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = AppText.button.copy(
                fontWeight = if (tone == DialogButtonTone.PRIMARY) {
                    androidx.compose.ui.text.font.FontWeight.SemiBold
                } else {
                    androidx.compose.ui.text.font.FontWeight.Medium
                },
            ),
            color = contentColor,
            maxLines = 1,
        )
    }
}

@Composable
fun BrandDialog(
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    maxWidth: Dp = 420.dp,
    footer: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Paper.colors
    val m = motion

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        var entered by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { entered = true }

        val scrimAlpha by animateFloatAsState(
            targetValue = if (entered) 1f else 0f,
            animationSpec = tween(durationMillis = m.fadeMs(), easing = Motion.easeOut),
            label = "brand-dialog-scrim",
        )
        val panelAlpha by animateFloatAsState(
            targetValue = if (entered) 1f else 0f,
            animationSpec = tween(durationMillis = m.fadeMs(), easing = Motion.easeOut),
            label = "brand-dialog-alpha",
        )
        val panelOffset by animateDpAsState(
            targetValue = if (entered) 0.dp else 10.dp,
            animationSpec = tween(durationMillis = m.spatialMs(), easing = Motion.easeOut),
            label = "brand-dialog-offset",
        )

        Box(modifier = Modifier.fillMaxSize()) {
            // 遮罩：暖色 scrim，点空白关闭
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = scrimAlpha }
                    .background(c.scrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                val panelMaxHeight = maxHeight * 0.86f
                Column(
                    modifier = Modifier
                        .widthIn(max = maxWidth)
                        .fillMaxWidth()
                        .heightIn(max = panelMaxHeight)
                        .graphicsLayer {
                            alpha = panelAlpha
                            translationY = panelOffset.toPx()
                        }
                        .shadow(
                            elevation = 20.dp,
                            shape = TwoFAShapes.dialog,
                            ambientColor = c.dialogShadow,
                            spotColor = c.dialogShadow,
                        )
                        .clip(TwoFAShapes.dialog)
                        .background(c.surface)
                        .border(1.dp, c.hairlineStrong, TwoFAShapes.dialog)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        )
                        .padding(20.dp),
                ) {
                    // 标题行
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(title, style = AppText.dialogTitle, color = c.ink)
                            if (!subtitle.isNullOrBlank()) {
                                Spacer(Modifier.height(3.dp))
                                Text(subtitle, style = AppText.secondary, color = c.ink3)
                            }
                        }
                        IconGlyphButton(
                            icon = Icons.Filled.Close,
                            contentDescription = "关闭",
                            onClick = onDismiss,
                            tint = c.ink4,
                            size = 26.dp,
                            iconSize = 15.dp,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    // 正文（超长时滚动）
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        content()
                    }
                    // 按钮行
                    if (footer != null) {
                        Spacer(Modifier.height(18.dp))
                        HairlineDivider()
                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                            content = footer,
                        )
                    }
                }
            }
        }
    }
}

// 底部面板壳（详情 / 选项类内容）：同一套遮罩与发丝线，上圆角 12dp，不带多层阴影。
@Composable
fun BrandSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Paper.colors
    val m = motion

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        var entered by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { entered = true }

        val scrimAlpha by animateFloatAsState(
            targetValue = if (entered) 1f else 0f,
            animationSpec = tween(durationMillis = m.fadeMs(), easing = Motion.easeOut),
            label = "brand-sheet-scrim",
        )
        val panelAlpha by animateFloatAsState(
            targetValue = if (entered) 1f else 0f,
            animationSpec = tween(durationMillis = m.fadeMs(), easing = Motion.easeOut),
            label = "brand-sheet-alpha",
        )
        val panelOffset by animateDpAsState(
            targetValue = if (entered) 0.dp else 20.dp,
            animationSpec = tween(durationMillis = m.spatialMs(), easing = Motion.easeOut),
            label = "brand-sheet-offset",
        )

        val shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 0.dp, bottomEnd = 0.dp)

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = scrimAlpha }
                    .background(c.scrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                val panelMaxHeight = maxHeight * 0.86f
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 480.dp)
                        .heightIn(max = panelMaxHeight)
                        .graphicsLayer {
                            alpha = panelAlpha
                            translationY = panelOffset.toPx()
                        }
                        .shadow(
                            elevation = 20.dp,
                            shape = shape,
                            ambientColor = c.dialogShadow,
                            spotColor = c.dialogShadow,
                        )
                        .clip(shape)
                        .background(c.surface)
                        .border(1.dp, c.hairlineStrong, shape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        )
                        .padding(20.dp),
                ) {
                    // 拖拽把手（纯视觉线索，不承载手势）
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(36.dp)
                            .height(4.dp)
                            .clip(TwoFAShapes.dot)
                            .background(c.hairlineStrong),
                    )
                    Spacer(Modifier.height(16.dp))
                    Column(
                        modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

// 二次确认弹窗（危险 / 清空 / 删除类操作）。
@Composable
fun BrandConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmTone: DialogButtonTone = DialogButtonTone.DANGER,
    cancelLabel: String = "取消",
) {
    val c = Paper.colors
    BrandDialog(
        onDismiss = onDismiss,
        title = title,
        footer = {
            DialogTextButton(text = cancelLabel, onClick = onDismiss, tone = DialogButtonTone.QUIET)
            DialogTextButton(text = confirmLabel, onClick = onConfirm, tone = confirmTone)
        },
    ) {
        Text(body, style = AppText.body, color = c.ink2)
    }
}
