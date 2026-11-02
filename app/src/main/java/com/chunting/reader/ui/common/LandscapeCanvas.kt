package com.chunting.reader.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/** 设计稿 v6 的画布基准宽度（设计稿里 px 与 dp 一一对应）。 */
private const val DESIGN_WIDTH = 1280f

/**
 * 横屏统一缩放容器。
 *
 * 设计稿 v6 的所有尺寸都按 1280×720 画布写死；而手机横屏只有 ~900dp 宽（20:9），
 * 若把这些数值直接当 dp 用，rail、36/64 分栏、5 列网格、播放条都会“按比例撑满”，
 * 表现为元素过大、比例失调。
 *
 * 这里按 availableWidth / 1280 等比缩放密度：横向尺寸（rail 96、左 36% / 右 64%、
 * 封面 2:3、各处 padding）与设计稿完全一致；纵向由实际可用高度决定，
 * 列表与网格自然滚动，播放条仍贴底。
 */
@Composable
fun LandscapeCanvas(content: @Composable BoxScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val base = LocalDensity.current
        val scale = (maxWidth.value / DESIGN_WIDTH).coerceIn(0.36f, 1.05f)
        CompositionLocalProvider(
            LocalDensity provides Density(base.density * scale, base.fontScale)
        ) {
            // 横屏三键/手势导航条常压在底部操作条上（车机无导航条时 inset 为 0，不影响布局），
            // 统一让出系统导航区，避免播放条、多选条、上拉菜单被遮住。
            Box(
                Modifier.fillMaxSize().navigationBarsPadding(),
                content = content
            )
        }
    }
}
