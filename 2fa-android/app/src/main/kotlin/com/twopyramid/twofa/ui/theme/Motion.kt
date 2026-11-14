package com.twopyramid.twofa.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import kotlin.math.roundToInt

// ══ 动效 token（mapping §3.4，来源桌面 motion-shell §2.6）══════════════
//
// 时长收敛为 7 档；缓动归一为 4 条 + spinner 用 linear。
// 设置 → 外观 → 动画速率 三档映射为 speedScale 倍数（慢 1.4 / 标准 1.0 / 快 0.7）。
//
// 减少动效语义（§3.4 / motion-shell §2.7）：
//   装饰类（入场错峰 / 极光 / 呼吸 / hover 位移）—— 组件侧直接不装动画；
//   状态反馈（spinner / 进度 / 按钮按压）—— 保留；
//   空间/位置变化（弹窗 / 抽屉 / 列表重排）—— 瞬时到位，但保留 opacity 交叉避免硬闪。
// 所以 [MotionTokens.spatialMs] 在减少动效时返回 0，而 [MotionTokens.fadeMs] 仍保留短淡化。

object Motion {
    const val micro = 160
    const val quick = 200
    const val base = 240
    const val baseAlt = 280
    const val slow = 320
    const val slowAlt = 360
    const val spin = 1000

    // 缓动：进场 / 退场 / 双向 / 位移与选中框弹簧
    val easeOut = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    val easeIn = CubicBezierEasing(0.4f, 0f, 1f, 1f)
    val easeInOut = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    val spring = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

    // 动画速率三档 → 倍数
    const val SPEED_SLOW = 1.4f
    const val SPEED_NORMAL = 1.0f
    const val SPEED_FAST = 0.7f

    fun isValidSpeed(scale: Float): Boolean =
        scale in 0.5f..2.0f
}

data class MotionTokens(
    val speedScale: Float = Motion.SPEED_NORMAL,
    val reduced: Boolean = false,
) {
    // 装饰/普通动画时长：减少动效时直接 0（调用方应跳过动画而不是硬播 0ms 动画）。
    fun ms(token: Int): Int =
        if (reduced) 0 else (token * speedScale).roundToInt().coerceAtLeast(1)

    // 状态反馈（spinner / 进度）不受减少动效影响；速率仍生效。
    fun feedbackMs(token: Int = Motion.spin): Int =
        (token * speedScale).roundToInt().coerceAtLeast(1)

    // 空间/位置变化：减少动效时瞬时到位。
    fun spatialMs(token: Int = Motion.base): Int = ms(token)

    // 透明度交叉：减少动效时保留一段短淡化，避免硬闪。
    fun fadeMs(token: Int = Motion.quick): Int =
        if (reduced) Motion.micro / 2 else (token * speedScale).roundToInt().coerceAtLeast(1)

    fun <T> tweenOut(token: Int = Motion.base, delayMs: Int = 0): TweenSpec<T> =
        tween(durationMillis = ms(token), delayMillis = ms(delayMs), easing = Motion.easeOut)

    fun <T> tweenIn(token: Int = Motion.micro): TweenSpec<T> =
        tween(durationMillis = ms(token), easing = Motion.easeIn)

    fun <T> tweenInOut(token: Int = Motion.base): TweenSpec<T> =
        tween(durationMillis = ms(token), easing = Motion.easeInOut)

    fun <T> fadeTween(token: Int = Motion.quick, easing: androidx.compose.animation.core.Easing = Motion.easeOut): TweenSpec<T> =
        tween(durationMillis = fadeMs(token), easing = easing)

    fun <T> spatialTween(token: Int = Motion.base): TweenSpec<T> =
        tween(durationMillis = spatialMs(token), easing = Motion.easeOut)

    // 页面转场：ease-out 160–240ms 的 fade + 轻微上移
    fun pageEnterMs(): Int = if (reduced) 0 else (Motion.quick * speedScale).roundToInt().coerceAtLeast(1)
    fun pageExitMs(): Int = if (reduced) 0 else (Motion.micro * speedScale).roundToInt().coerceAtLeast(1)
}

val LocalMotion = staticCompositionLocalOf { MotionTokens() }

// 组合内取当前动效 token：`val m = motion` 后 `m.fadeMs()` / `m.tweenOut(Motion.base)`。
val motion: MotionTokens
    @Composable @ReadOnlyComposable
    get() = LocalMotion.current

// 系统「移除动画」偏好（设置 → 无障碍 → 移除动画 会把 animator_duration_scale 置 0）。
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
            scale == 0f
        }.getOrDefault(false)
    }
}
