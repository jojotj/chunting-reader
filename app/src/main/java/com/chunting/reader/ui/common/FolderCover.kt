package com.chunting.reader.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chunting.reader.ui.theme.LocalAppColors
import com.chunting.reader.util.CoverStore

/**
 * 文件夹封面：用户换过图就显示照片，否则回落到原来的生成式封面。
 * 两者共用同一套书脊、圆角、阴影参数，换图后视觉不掉层。
 */
@Composable
fun FolderCover(
    folderPath: String,
    folderName: String,
    modifier: Modifier = Modifier,
    fontSize: Int = 32,
    showHint: Boolean = false,
    cornerRadius: Dp = 6.dp,
    withSpine: Boolean = true,
) {
    val ctx = LocalContext.current
    val version by CoverStore.version.collectAsState()
    val bitmap = remember(folderPath, version) {
        if (folderPath.isEmpty()) null else CoverStore.load(ctx, folderPath)
    }
    val bmp = bitmap
    if (bmp == null) {
        // 生成式封面：配色与首字都由文件夹名哈希产生，还原后自动补回这个字
        GeneratedCover(folderName, modifier, fontSize, showHint, cornerRadius, withSpine)
    } else {
        PhotoCover(
            bitmap = bmp,
            modifier = modifier,
            cornerRadius = cornerRadius,
            withSpine = withSpine,
        )
    }
}

/**
 * 照片封面：圆角/书脊/阴影与 GeneratedCover 完全一致，只加一层极弱的顶部高光提质感。
 * 照片上不叠任何文字（专辑图本身常带标题），只在还原成生成式封面时才重新显示首字。
 */
@Composable
private fun PhotoCover(
    bitmap: android.graphics.Bitmap,
    modifier: Modifier,
    cornerRadius: Dp,
    withSpine: Boolean,
) {
    val colors = LocalAppColors.current
    val outer = RoundedCornerShape(cornerRadius)
    val inner = RoundedCornerShape(
        topStart = 2.dp, topEnd = cornerRadius, bottomEnd = cornerRadius, bottomStart = 2.dp
    )
    Box(
        modifier
            .shadow(if (colors.isDark) 8.dp else 4.dp, outer)
            .clip(inner)
            .background(Color(0xFF1C1913))
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // 顶部高光（与宣传卡片同款，弱到几乎不可见，只负责提质感）
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.16f),
                    0.42f to Color.Transparent
                )
            )
        )
        // 书脊暗边（与生成式封面同一条渐变）
        if (withSpine) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.55f),
                        0.16f to Color.White.copy(alpha = 0.10f),
                        0.3f to Color.Transparent
                    )
                )
            )
        }
        // 1px 细白边，暗色照片不会糊进背景
        Box(
            Modifier.fillMaxSize().border(
                0.9.dp, Color.White.copy(alpha = if (colors.isDark) 0.16f else 0.26f), inner
            )
        )
    }
}
