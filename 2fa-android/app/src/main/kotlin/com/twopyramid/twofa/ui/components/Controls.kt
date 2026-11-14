package com.twopyramid.twofa.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.twopyramid.twofa.ui.theme.AppText
import com.twopyramid.twofa.ui.theme.Paper
import com.twopyramid.twofa.ui.theme.TwoFAShapes

// 页面 / 弹窗的轻量控件：顶栏、图标钮、分段切换、搜索框。
// 全部走暖纸墨令牌，不写死颜色；圆角 6dp（小图标钮）/ 8dp（输入框、分段容器）。

// 26dp 小图标钮（mapping §3.5 关闭钮规格：26dp / 圆角 6 / ink3）。
// 页面返回/添加用 32dp 提高可点性，弹窗关闭保持 26dp。
@Composable
fun IconGlyphButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Paper.colors.ink3,
    size: Dp = 32.dp,
    iconSize: Dp = 18.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(TwoFAShapes.row)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}

// 页面顶栏：返回（可选）+ 24sp 页面标题 + 右侧动作。
@Composable
fun PageTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val c = Paper.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = if (onBack != null) 8.dp else 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconGlyphButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                onClick = onBack,
                tint = c.ink2,
                size = 32.dp,
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(title, style = AppText.pageTitle, color = c.ink)
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

// 分段切换（主题三档 / 输出模式 / 动画速率）：容器 surface3，选中项白面 + 发丝内描边。
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    current: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Paper.colors
    Row(
        modifier = modifier
            .clip(TwoFAShapes.control)
            .background(c.surface3)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { option ->
            val active = option == current
            Box(
                modifier = Modifier
                    .clip(TwoFAShapes.row)
                    .background(if (active) c.surface else Color.Transparent)
                    .then(if (active) Modifier.border(1.dp, c.hairlineStrong, TwoFAShapes.row) else Modifier)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = labelOf(option),
                    style = AppText.secondary.copy(
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = if (active) c.ink else c.ink3,
                    maxLines = 1,
                )
            }
        }
    }
}

// 搜索框（版本选择弹窗 / 历史页）：8dp 圆角，surface2 底 + 发丝线，accent 光标。
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val c = Paper.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(TwoFAShapes.control)
            .background(c.surface2)
            .border(1.dp, c.hairlineStrong, TwoFAShapes.control)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = c.ink4, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = AppText.body.copy(color = c.ink),
            cursorBrush = SolidColor(c.accent),
            modifier = Modifier.weight(1f).padding(vertical = 10.dp),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(placeholder, style = AppText.body, color = c.ink4)
                }
                inner()
            },
        )
        if (value.isNotEmpty()) {
            IconGlyphButton(
                icon = Icons.Filled.Close,
                contentDescription = "清空",
                onClick = { onValueChange("") },
                tint = c.ink4,
                size = 26.dp,
                iconSize = 14.dp,
            )
        }
    }
}

// 一行设置项（设置页复用）：label 14sp / desc 12sp / trailing 控件；行与行之间由调用方加发丝线。
@Composable
fun SettingRow(
    label: String,
    desc: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val c = Paper.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
            Text(label, style = AppText.itemTitle, color = c.ink)
            if (desc != null) {
                Spacer(Modifier.height(2.dp))
                Text(desc, style = AppText.secondary, color = c.ink3)
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}
