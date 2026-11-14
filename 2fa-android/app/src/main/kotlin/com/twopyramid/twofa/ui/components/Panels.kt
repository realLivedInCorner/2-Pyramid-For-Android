package com.twopyramid.twofa.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.twopyramid.twofa.ui.theme.AppText
import com.twopyramid.twofa.ui.theme.Paper
import com.twopyramid.twofa.ui.theme.TwoFAShapes

// 页面内「面板」原语：surface + 1dp 暖发丝线 + 圆角，无玻璃、无投影。
// 旧 GlassCard 的 blur / elevation 全部去掉 —— 层级靠面色分层与发丝线，不靠阴影。

@Composable
fun BrandPanel(
    modifier: Modifier = Modifier,
    shape: Shape = TwoFAShapes.control,
    color: Color = Paper.colors.surface,
    borderColor: Color = Paper.colors.hairlineStrong,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(color)
            .border(1.dp, borderColor, shape),
    ) {
        content()
    }
}

// 暖色发丝线（分隔线 / 弹窗页脚上边线）。不用冷灰、不用 Divider 默认色。
@Composable
fun HairlineDivider(
    modifier: Modifier = Modifier,
    color: Color = Paper.colors.hairline,
) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = color)
}

// 分组小标题：10.5sp / uppercase / 0.18em / ink3（mapping §4 Settings）。
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = AppText.label,
        color = Paper.colors.ink3,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 6.dp),
    )
}

enum class StatusTone { OK, DANGER, INK, ACCENT, WARN }

// 状态徽章：小圆点 + 文字小签（ok / danger / ink3 承担语义，不用蓝色）。
@Composable
fun StatusBadge(
    label: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    val c = Paper.colors
    val dot = when (tone) {
        StatusTone.OK -> c.ok
        StatusTone.DANGER -> c.danger
        StatusTone.INK -> c.ink3
        StatusTone.ACCENT -> c.accent
        StatusTone.WARN -> c.warn
    }
    val textColor = when (tone) {
        StatusTone.OK -> c.ink2
        StatusTone.DANGER -> c.danger
        StatusTone.INK -> c.ink3
        StatusTone.ACCENT -> c.accent
        StatusTone.WARN -> c.warn
    }
    Row(
        modifier = modifier
            .clip(TwoFAShapes.row)
            .border(1.dp, c.hairline, TwoFAShapes.row)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(4.dp))
        Text(label, style = AppText.tagline, color = textColor, maxLines = 1)
    }
}

// 版本短签（列表行 / 详情里的「→ 1.21.5」）。
@Composable
fun VersionChip(text: String, modifier: Modifier = Modifier) {
    val c = Paper.colors
    Box(
        modifier = modifier
            .clip(TwoFAShapes.row)
            .background(c.surface3)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, style = AppText.tagline, color = c.ink2, maxLines = 1)
    }
}

// 危险标记「会删文件」（destructive 开关旁）。
@Composable
fun DangerTag(text: String, modifier: Modifier = Modifier) {
    val c = Paper.colors
    Box(
        modifier = modifier
            .clip(TwoFAShapes.row)
            .background(c.danger.copy(alpha = 0.10f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(text, style = AppText.tagline.copy(fontWeight = FontWeight.SemiBold), color = c.danger)
    }
}

// 警示条（26.3 MC-311807 / Bedrock 实验性）：warn / warnTint / warnLine，不用红色。
@Composable
fun WarnBanner(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    val c = Paper.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(TwoFAShapes.control)
            .background(c.warnTint)
            .border(1.dp, c.warnLine, TwoFAShapes.control)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = c.warn, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, style = AppText.secondary.copy(fontWeight = FontWeight.SemiBold), color = c.warn)
            Spacer(Modifier.height(2.dp))
            Text(body, style = AppText.secondary, color = c.ink2)
        }
    }
}

// 弹窗 / 详情里的信息块：标签 11.5sp ink3，值 13sp ink（上下排列，与桌面 dialog 一致）。
@Composable
fun InfoLine(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val c = Paper.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Text(label, style = AppText.tagline, color = c.ink3)
        Spacer(Modifier.height(3.dp))
        Text(value, style = AppText.body, color = c.ink)
    }
}

// 横向信息行（About / 版本详情）：左标签右值。
@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val c = Paper.colors
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, style = AppText.secondary, color = c.ink3)
        Spacer(Modifier.width(16.dp))
        Text(value, style = AppText.secondary, color = c.ink2, textAlign = TextAlign.End)
    }
}
