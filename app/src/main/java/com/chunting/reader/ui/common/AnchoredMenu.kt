package com.chunting.reader.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.min

/**
 * 贴着锚点弹出的上拉 / 下拉菜单。
 *
 * 与 Material3 的 DropdownMenu 区别：整块高度永远限制在屏幕内，
 * 各选项按可用高度等分（weight(1f)），不会在横屏车机上顶出屏幕外。
 *
 * @param anchorBounds 锚点控件在窗口坐标系中的位置（onGloballyPositioned + boundsInWindow）
 * @param itemCount    选项个数，用于预算菜单高度
 * @param itemHeight   期望的单项高度，空间不足时自动压缩
 */
@Composable
fun AnchoredMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    anchorBounds: Rect?,
    itemCount: Int,
    cardColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
    width: Dp = 150.dp,
    itemHeight: Dp = 32.dp,
    minItemHeight: Dp = 22.dp,
    cornerRadius: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded || itemCount <= 0) return

    val density = LocalDensity.current
    val root = LocalView.current.rootView
    val windowW = root.width.coerceAtLeast(1)
    val windowH = root.height.coerceAtLeast(1)

    val widthPx = with(density) { width.toPx().toInt() }
    val wantH = with(density) { (itemHeight * itemCount).toPx().toInt() }
    val minH = with(density) { (minItemHeight * itemCount).toPx().toInt() }
    val margin = with(density) { 8.dp.toPx().toInt() }
    val gap = with(density) { 6.dp.toPx().toInt() }

    val a = anchorBounds
    val anchorTop = a?.top?.toInt() ?: (windowH - wantH - gap)
    val anchorBottom = a?.bottom?.toInt() ?: windowH
    val anchorLeft = a?.left?.toInt() ?: (windowW - widthPx)

    // 上方空间更大就往上拉，否则往下拉（与播放条/播放胶囊的直觉一致）
    val spaceAbove = anchorTop - margin
    val spaceBelow = windowH - anchorBottom - margin
    val up = spaceAbove >= spaceBelow
    val avail = (if (up) spaceAbove else spaceBelow) - gap
    val heightPx = min(wantH, avail.coerceAtLeast(0)).coerceAtLeast(min(minH, windowH - margin * 2))
    val heightDp = with(density) { heightPx.toDp() }

    // 水平方向：左边缘对齐锚点，越界就往里收
    val x = anchorLeft.coerceIn(margin, (windowW - widthPx - margin).coerceAtLeast(margin))
    val y = if (up) (anchorTop - gap - heightPx) else (anchorBottom + gap)
    val clampedY = y.coerceIn(margin, (windowH - heightPx - margin).coerceAtLeast(margin))

    val provider = object : PopupPositionProvider {
        override fun calculatePosition(
            anchorBounds: IntRect,
            windowSize: IntSize,
            layoutDirection: LayoutDirection,
            popupContentSize: IntSize,
        ): IntOffset = IntOffset(x, clampedY)
    }

    Popup(
        popupPositionProvider = provider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        val shape = RoundedCornerShape(cornerRadius)
        Column(
            modifier
                .width(width)
                .height(heightDp)
                .shadow(12.dp, shape)
                .clip(shape)
                .background(cardColor)
                .border(1.dp, borderColor, shape)
                .padding(vertical = 4.dp),
            content = content,
        )
    }
}

/** 等分菜单项容器：统一点击区域与内边距，高度由 weight 平分 */
@Composable
fun ColumnScope.AnchoredMenuItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}
