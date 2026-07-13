package com.twopyramid.twofa.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import com.twopyramid.twofa.ui.theme.BlueAccent
import com.twopyramid.twofa.ui.theme.TwoFATheme
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TwoFATheme(darkTheme = false) {
                VortexBackground {
                    AppNavHost()
                }
            }
        }
    }
}

/**
 * PC 端 HomePage.vue .vortex-background + .blob b1..b4 + .fanhua-home::before aurora gradient 的 Android 等价物。
 *
 * 三层堆叠：
 *  - 最底：白底（MaterialTheme.colorScheme.background）
 *  - 中间：aurora-drift 线性渐变（带 phase 浮动）
 *  - 顶层：4 个 radial-gradient blob（PC 端 b1 10% 10% 280 / b2 20% 80% 360 / b3 55% 8% 300 / b4 60% 78% 220），24s 周期 phase 浮动
 *
 * 动画参考 PC 端 keyframes：
 *   0%   (0s):  translate(0, 0)         scale 1.00
 *   25%  (6s):  translate(30, -20)      scale 1.05
 *   50%  (12s): translate(-20, 25)      scale 0.97
 *   75%  (18s): translate(20, 30)       scale 1.03
 *   100% (24s): translate(0, 0)         scale 1.00
 */
@Composable
private fun VortexBackground(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme

    // 单一 24s 线性 transition，4 个 blob 共享 phase，但每个 offset delay 不同
    val transition = rememberInfiniteTransition(label = "vortex")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 24_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background),
    ) {
        // ① aurora-drift 渐变（PC 端 ::before，蓝色光感）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            BlueAccent.copy(alpha = 0.05f),
                            BlueAccent.copy(alpha = 0.01f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        // ② 4 个 blob（PC 端 .blob b1..b4），每个 delay 0/3/6/9s（24s 周期的 1/8 步距）
        Blob(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 28.dp, y = 80.dp)
                .size(280.dp),
            alpha = 0.32f,
            phase = phaseWithDelay(phase, delayMs = 0, totalMs = 24_000),
        )
        Blob(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-32).dp, y = 160.dp)
                .size(360.dp),
            alpha = 0.28f,
            phase = phaseWithDelay(phase, delayMs = 3_000, totalMs = 24_000),
        )
        Blob(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 24.dp, y = (-220).dp)
                .size(300.dp),
            alpha = 0.24f,
            phase = phaseWithDelay(phase, delayMs = 6_000, totalMs = 24_000),
        )
        Blob(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-56).dp, y = (-180).dp)
                .size(220.dp),
            alpha = 0.30f,
            phase = phaseWithDelay(phase, delayMs = 9_000, totalMs = 24_000),
        )

        // ③ 内容
        content()
    }
}

/**
 * 4-phase 关键帧：
 *   0%   -> (0, 0)        scale 1.00
 *   25%  -> (30, -20)     scale 1.05
 *   50%  -> (-20, 25)     scale 0.97
 *   75%  -> (20, 30)      scale 1.03
 *   100% -> wrap to 0
 */
private data class BlobKey(val x: Float, val y: Float, val scale: Float)

private val BlobKeys = listOf(
    BlobKey(0f, 0f, 1.00f),
    BlobKey(30f, -20f, 1.05f),
    BlobKey(-20f, 25f, 0.97f),
    BlobKey(20f, 30f, 1.03f),
)

@Composable
private fun Blob(modifier: Modifier = Modifier, alpha: Float, phase: Float) {
    // phase ∈ [0, 1) -> 4 段插值；末尾 wrap 回起点
    val pos = phase * 4f
    val idx = pos.toInt().coerceIn(0, 3)
    val frac = (pos - idx).coerceIn(0f, 1f)
    val a = BlobKeys[idx]
    val b = BlobKeys[(idx + 1) % 4]   // wrap around, 不越界
    val x = a.x + (b.x - a.x) * frac
    val y = a.y + (b.y - a.y) * frac
    val scale = a.scale + (b.scale - a.scale) * frac

    Box(
        modifier = modifier
            .offset { IntOffset(x.dp.roundToPx(), y.dp.roundToPx()) }
            .scale(scale)
            .clip(CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        BlueAccent.copy(alpha = alpha * 0.6f),
                        BlueAccent.copy(alpha = alpha * 0.1f),
                        Color.Transparent,
                    ),
                ),
            ),
    )
}

/** 同一 24s 周期内，给定 delay，返回对应的 0..1 phase。 */
private fun phaseWithDelay(phase: Float, delayMs: Int, totalMs: Int): Float {
    val delayFrac = delayMs.toFloat() / totalMs.toFloat()
    val raw = (phase - delayFrac) % 1f
    return if (raw < 0f) raw + 1f else raw
}
