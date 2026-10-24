package com.chunting.reader.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 细进度条：底槽 + 按比例填充，用于书封下方、播放条里的“听到哪了” */
@Composable
fun DotLine(
    progress: Float,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
) {
    val p = progress.coerceIn(0f, 1f)
    Box(modifier.clip(RoundedCornerShape(2.dp)).background(trackColor)) {
        if (p > 0f) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(p).background(color))
        }
    }
}
