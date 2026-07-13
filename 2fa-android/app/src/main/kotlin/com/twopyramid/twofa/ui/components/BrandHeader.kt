package com.twopyramid.twofa.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.twopyramid.twofa.R
import com.twopyramid.twofa.ui.theme.BlueAccent
import com.twopyramid.twofa.ui.theme.OnSurfaceLow
import com.twopyramid.twofa.ui.theme.OnSurfaceHigh

/**
 * 2-Pyramid 品牌头（PC 端 HomePage.vue .brand-block 等价）。
 *
 * 排版从大到小：
 *   ① Logo（圆形 + 浅蓝径向 + Image tint，可选 showLogo=false 隐藏）
 *   ② 主标 2-Pyramid          56sp / 900 / 紧 tracking / 居中
 *   ③ 副标 for Android  + version pill    同行
 *   ④ 长描述 tagline          12sp / 0.6 tracking / 浅灰 / 居中
 */
@Composable
fun BrandHeader(
    productLine: String = "for Android",
    tagline: String = "The Nextgen Multi-Version Universal Resource Pack Converter",
    versionPill: String? = null,
    logoSize: Dp = 80.dp,
    showLogo: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (showLogo) {
            // ① Logo：圆形 + 浅蓝径向 + Image tint
            Box(
                modifier = Modifier
                    .size(logoSize)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                BlueAccent.copy(alpha = 0.30f),
                                BlueAccent.copy(alpha = 0.10f),
                                Color.Transparent,
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_pyramid_logo),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(BlueAccent),
                    modifier = Modifier.size(logoSize * 0.55f),
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        // ② 主标（独立一行，避免被副标挤成竖排）
        Text(
            text = "2-Pyramid",
            style = MaterialTheme.typography.displayLarge,
            color = OnSurfaceHigh,
            textAlign = TextAlign.Center,
        )

        // ③ 副标 for Android + version pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = productLine,
                style = MaterialTheme.typography.titleMedium,
                color = OnSurfaceLow,
            )
            if (versionPill != null) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(BlueAccent.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = versionPill,
                        style = MaterialTheme.typography.labelLarge,
                        color = BlueAccent,
                    )
                }
            }
        }

        // ④ 长描述副标
        Text(
            text = tagline,
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceLow,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
