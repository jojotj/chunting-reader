package com.chunting.reader.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chunting.reader.ui.theme.CoverPalette
import com.chunting.reader.ui.theme.LocalAppColors
import com.chunting.reader.util.monogram
import com.chunting.reader.util.pinyinHint

/**
 * 生成式封面：色板 + 几何图案 + 首字徽章。
 * 不依赖任何图片文件，同一文件夹名永远得到同一个封面。
 */
@Composable
fun GeneratedCover(
    folderName: String,
    modifier: Modifier = Modifier,
    fontSize: Int = 32,
    showHint: Boolean = false,
    cornerRadius: Dp = 6.dp,
    withSpine: Boolean = true,
) {
    val colors = LocalAppColors.current
    val (ci, pi) = CoverPalette.derive(folderName)
    val bg = colors.coverBg[ci]
    val fg = colors.coverFg[ci]
    val patternColor = colors.pattern

    Box(
        modifier = modifier
            .shadow(if (colors.isDark) 8.dp else 4.dp, RoundedCornerShape(cornerRadius))
            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = cornerRadius, bottomEnd = cornerRadius, bottomStart = 2.dp))
            .background(
                Brush.linearGradient(
                    0f to bg,
                    0.6f to bg.copy(alpha = 0.92f),
                    1f to bg.copy(alpha = 0.78f)
                )
            )
    ) {
        // 几何图案层
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawPattern(pi, patternColor, size.width, size.height)
        }

        // 书脊（左侧暗边 + 高光）
        if (withSpine) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to Color.Black.copy(alpha = 0.55f),
                            0.16f to Color.White.copy(alpha = 0.10f),
                            0.3f to Color.Transparent
                        )
                    )
            )
        }

        // 首字
        Text(
            text = monogram(folderName),
            color = fg,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Serif,
            modifier = Modifier.align(Alignment.Center)
        )

        if (showHint) {
            val hint = pinyinHint(folderName)
            if (hint.isNotEmpty()) {
                Text(
                    text = hint,
                    color = fg.copy(alpha = 0.5f),
                    fontSize = (fontSize / 4.6f).coerceAtLeast(6f).sp,
                    letterSpacing = 1.4.sp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .then(Modifier)
                )
            }
        }
    }
}

private fun DrawScope.drawPattern(index: Int, color: Color, w: Float, h: Float) {
    when (index % CoverPalette.PATTERN_COUNT) {
        0 -> { // 同心圆
            val cx = w / 2f; val cy = h / 2f
            listOf(0.26f, 0.38f, 0.52f).forEach { r ->
                drawCircle(color, radius = minOf(w, h) * r, center = Offset(cx, cy))
            }
        }
        1 -> { // 斜纹
            var x = -h; val step = w / 9f
            while (x < w + h) {
                drawLine(color, Offset(x, h), Offset(x + h, 0f), strokeWidth = 1.4f)
                x += step
            }
        }
        2 -> { // 网格
            val cols = 4; val rows = 5
            for (i in 1 until cols) {
                val x = w * i / cols
                drawLine(color, Offset(x, 0f), Offset(x, h), strokeWidth = 1.2f)
            }
            for (j in 1 until rows) {
                val y = h * j / rows
                drawLine(color, Offset(0f, y), Offset(w, y), strokeWidth = 1.2f)
            }
        }
        3 -> { // 弧线
            for (k in 0..2) {
                val r = minOf(w, h) * (0.45f + k * 0.16f)
                drawCircle(
                    color,
                    radius = r,
                    center = Offset(w / 2f, h * 1.35f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.4f)
                )
            }
        }
        else -> { // 散点
            listOf(
                Offset(0.18f, 0.22f) to 0.05f,
                Offset(0.78f, 0.16f) to 0.07f,
                Offset(0.30f, 0.76f) to 0.09f,
                Offset(0.82f, 0.72f) to 0.04f,
            ).forEach { (p, r) ->
                drawCircle(color, radius = minOf(w, h) * r, center = Offset(w * p.x, h * p.y))
            }
        }
    }
}
