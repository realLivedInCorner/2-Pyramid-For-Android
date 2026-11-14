package com.twopyramid.twofa.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.twopyramid.twofa.R
import com.twopyramid.twofa.ui.theme.AppText
import com.twopyramid.twofa.ui.theme.Paper

// 首页品牌块（mapping §4 Home）：brand_mark 44dp 盒 + 品牌名 + 标语 + 元信息行。
//
// brand_mark 是纯黑线条 + 透明底，深色下会隐形 —— 所以这里统一用
// painterResource + ColorFilter.tint(ink)：浅色下是墨线，深色下 ink 翻成暖白，
// 不需要额外维护 brand_mark_white 的另一份渲染路径。
//
// 44dp 盒对应新版 mark 71.5% 墨迹占比 → 可见墨迹约 31.5dp，与桌面 HomePage
// 的 .brand-mark 尺寸/留白换算一致。
// 品牌名是**唯一**允许 700 字重的标识文本（其余 UI 文本 ≤600）。
@Composable
fun BrandHeader(
    modifier: Modifier = Modifier,
    tagline: String = "Modern Universal Multi-Version Resource Pack Converter",
    meta: String? = null,
) {
    val c = Paper.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.brand_mark),
                contentDescription = null,
                colorFilter = ColorFilter.tint(c.ink),
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("2-Pyramid", style = AppText.brandName, color = c.ink)
                Spacer(Modifier.height(2.dp))
                Text(tagline, style = AppText.tagline, color = c.ink3)
            }
        }
        if (!meta.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = meta.uppercase(),
                style = AppText.label,
                color = c.ink3,
                modifier = Modifier.padding(start = 2.dp),
            )
        }
    }
}
