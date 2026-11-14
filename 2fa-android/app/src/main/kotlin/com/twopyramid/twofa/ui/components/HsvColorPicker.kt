package com.twopyramid.twofa.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.twopyramid.twofa.ui.theme.AppText
import com.twopyramid.twofa.ui.theme.Paper
import com.twopyramid.twofa.ui.theme.TwoFAShapes
import com.twopyramid.twofa.ui.theme.toHexString
import kotlin.math.abs

// ══ HSV 强调色选择器（mapping §5，桌面 HsvColorPicker.vue 的 Android 版）═══
//
// 组成：SV 面板（可拖动）+ 色相滑条 + hex 显示。设置 → 外观 → 强调色 弹窗内使用。
// 颜色转换只在本文件内做；父级只收 Color / 存 hex（SettingsRepository.themeColor）。
// 交互细节与桌面一致：SV 面板 x = 饱和度、y = 明度（上亮下暗），色相 0..360。

private data class Hsv(val h: Float, val s: Float, val v: Float)

private fun hsvToColor(h: Float, s: Float, v: Float): Color {
    val hue = ((h % 360f) + 360f) % 360f
    val sat = s.coerceIn(0f, 1f)
    val value = v.coerceIn(0f, 1f)
    val chroma = value * sat
    val sector = hue / 60f
    val x = chroma * (1f - abs(sector % 2f - 1f))
    val (r1, g1, b1) = when {
        sector < 1f -> Triple(chroma, x, 0f)
        sector < 2f -> Triple(x, chroma, 0f)
        sector < 3f -> Triple(0f, chroma, x)
        sector < 4f -> Triple(0f, x, chroma)
        sector < 5f -> Triple(x, 0f, chroma)
        else -> Triple(chroma, 0f, x)
    }
    val m = value - chroma
    return Color(
        red = (r1 + m).coerceIn(0f, 1f),
        green = (g1 + m).coerceIn(0f, 1f),
        blue = (b1 + m).coerceIn(0f, 1f),
        alpha = 1f,
    )
}

private fun colorToHsv(color: Color): Hsv {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val hue = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }
    return Hsv(
        h = ((hue % 360f) + 360f) % 360f,
        s = if (max == 0f) 0f else delta / max,
        v = max,
    )
}

private val HueStops = listOf(
    Color(0xFFFF2B2B),
    Color(0xFFFFD12B),
    Color(0xFF2BFF6A),
    Color(0xFF2BE6FF),
    Color(0xFF2B5BFF),
    Color(0xFFB42BFF),
    Color(0xFFFF2B9A),
    Color(0xFFFF2B2B),
)

@Composable
fun HsvColorPicker(
    color: Color,
    onColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier,
    panelHeight: Dp = 140.dp,
) {
    val c = Paper.colors
    val initial = remember { colorToHsv(color) }
    var hue by remember { mutableFloatStateOf(initial.h) }
    var sat by remember { mutableFloatStateOf(initial.s) }
    var value by remember { mutableFloatStateOf(initial.v) }

    val current = hsvToColor(hue, sat, value)
    val currentHex = toHexString(current)
    val externalHex = toHexString(color)
    val onChange by rememberUpdatedState(onColorChange)

    // 外部改动（恢复默认 / 重新打开弹窗）时同步；拖动时父级回写的是同一个 hex，不会抢状态。
    LaunchedEffect(externalHex) {
        if (externalHex != currentHex) {
            val hsv = colorToHsv(color)
            hue = hsv.h
            sat = hsv.s
            value = hsv.v
        }
    }

    fun emit() {
        onChange(hsvToColor(hue, sat, value))
    }

    fun pickSv(position: Offset, size: Size) {
        sat = (position.x / size.width).coerceIn(0f, 1f)
        value = (1f - position.y / size.height).coerceIn(0f, 1f)
        emit()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // ── SV 面板 ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(panelHeight)
                .clip(TwoFAShapes.control)
                .border(1.dp, c.hairlineStrong, TwoFAShapes.control),
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { position -> pickSv(position, Size(size.width.toFloat(), size.height.toFloat())) },
                            onDragEnd = {},
                            onDragCancel = {},
                        ) { change, _ ->
                            change.consume()
                            pickSv(change.position, Size(size.width.toFloat(), size.height.toFloat()))
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { position ->
                            pickSv(position, Size(size.width.toFloat(), size.height.toFloat()))
                        }
                    },
            ) {
                val hueColor = hsvToColor(hue, 1f, 1f)
                drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                val cursor = Offset(sat * size.width, (1f - value) * size.height)
                drawCircle(color = Color.White, radius = 6.dp.toPx(), center = cursor, style = Stroke(2.dp.toPx()))
                drawCircle(
                    color = c.ink.copy(alpha = 0.45f),
                    radius = 7.dp.toPx(),
                    center = cursor,
                    style = Stroke(1.dp.toPx()),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── 色相滑条 + hex ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .pointerInput(Unit) {
                        fun pickHue(x: Float) {
                            hue = (x / size.width.toFloat() * 360f).coerceIn(0f, 359.9f)
                            emit()
                        }
                        detectDragGestures(
                            onDragStart = { position -> pickHue(position.x) },
                            onDragEnd = {},
                            onDragCancel = {},
                        ) { change, _ ->
                            change.consume()
                            pickHue(change.position.x)
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { position ->
                            hue = (position.x / size.width.toFloat() * 360f).coerceIn(0f, 359.9f)
                            emit()
                        }
                    },
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val trackHeight = 8.dp.toPx()
                    val centerY = size.height / 2f
                    drawRoundRect(
                        brush = Brush.horizontalGradient(HueStops),
                        topLeft = Offset(0f, centerY - trackHeight / 2f),
                        size = Size(size.width, trackHeight),
                        cornerRadius = CornerRadius(trackHeight / 2f),
                    )
                    val thumbX = (hue / 360f).coerceIn(0f, 1f) * size.width
                    val thumbCenter = Offset(thumbX, centerY)
                    drawCircle(color = Color.White, radius = 9.dp.toPx(), center = thumbCenter)
                    drawCircle(
                        color = hsvToColor(hue, 1f, 1f),
                        radius = 7.dp.toPx(),
                        center = thumbCenter,
                    )
                    drawCircle(
                        color = c.ink.copy(alpha = 0.35f),
                        radius = 9.dp.toPx(),
                        center = thumbCenter,
                        style = Stroke(1.dp.toPx()),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(TwoFAShapes.row)
                    .background(current)
                    .border(1.dp, c.hairlineStrong, TwoFAShapes.row),
            )
            Spacer(Modifier.width(8.dp))
            Text(currentHex, style = AppText.numeric, color = c.ink3)
        }
    }
}
