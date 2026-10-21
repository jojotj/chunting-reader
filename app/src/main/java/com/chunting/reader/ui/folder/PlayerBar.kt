package com.chunting.reader.ui.folder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chunting.reader.ui.common.GeneratedCover
import com.chunting.reader.util.formatTime
import kotlin.math.abs
import kotlin.math.roundToInt

/** 可选倍速档位（播放条上拉菜单用） */
val PLAYER_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)

fun speedLabel(v: Float): String {
    val i = (v * 100).toInt()
    return if (i % 100 == 0) "%.1f×".format(i / 100f) else "%.2f×".format(v).trimEnd('0').trimEnd('.')
}

private fun rewindIcon(skip: Int): ImageVector = when (skip) {
    10 -> Icons.Filled.Replay10
    30 -> Icons.Filled.Replay30
    else -> Icons.Filled.FastRewind
}

private fun forwardIcon(skip: Int): ImageVector = when (skip) {
    10 -> Icons.Filled.Forward10
    30 -> Icons.Filled.Forward30
    else -> Icons.Filled.FastForward
}

/**
 * 列表页底部播放条：进度 + 快退/播放/快进 + 倍速，全部就地操作，不再跳独立播放页。
 */
@Composable
fun PlayerBar(
    fileName: String,
    folderName: String,
    playing: Boolean,
    positionMs: Long,
    durationMs: Long,
    speed: Float,
    skipSeconds: Int,
    accent: Color,
    isDark: Boolean,
    cardBg: Color,
    line: Color,
    textColor: Color,
    subColor: Color,
    onToggle: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onSeek: (Float) -> Unit,
    onSelectSpeed: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var slider by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var speedMenu by remember { mutableStateOf(false) }

    LaunchedEffect(positionMs, durationMs, dragging) {
        if (!dragging && durationMs > 0) {
            slider = (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        }
    }

    val onPlay = if (isDark) Color(0xFF141008) else Color(0xFFFAF8F4)
    // 与播放圆钮同色系：主题色留白胶囊 + 主题色图标文字
    val skipTint = accent
    val skipBg = accent.copy(alpha = if (isDark) 0.20f else 0.14f)

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(cardBg.copy(alpha = 0.97f))
            // 吞掉空白区域的点击，避免穿透到下方列表项（误触播放别的曲目）
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)
    ) {
        // 第一段：文件名 / 时间 / 倍速（信息行）
        Row(verticalAlignment = Alignment.CenterVertically) {
            GeneratedCover(folderName.ifEmpty { "听" }, Modifier.size(40.dp), fontSize = 16, withSpine = false)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    fileName, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${formatTime(positionMs)} / ${formatTime(durationMs)}",
                    color = subColor, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            // 倍速：点一次弹出上拉菜单，直接选档
            Box {
                Row(
                    Modifier.clip(RoundedCornerShape(50))
                        .background(if (speedMenu) accent.copy(alpha = 0.18f) else line)
                        .clickable { speedMenu = true }
                        .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        speedLabel(speed),
                        color = if (speedMenu) accent else textColor,
                        fontSize = 11.sp, fontWeight = FontWeight.Bold
                    )
                    Icon(
                        Icons.Filled.ArrowDropDown, "选择倍速",
                        tint = if (speedMenu) accent else subColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                DropdownMenu(
                    expanded = speedMenu,
                    onDismissRequest = { speedMenu = false },
                    containerColor = cardBg,
                ) {
                    PLAYER_SPEEDS.forEach { v ->
                        val sel = abs(v - speed) < 0.01f
                        DropdownMenuItem(
                            text = {
                                Text(
                                    speedLabel(v),
                                    color = if (sel) accent else textColor,
                                    fontSize = 12.sp,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            trailingIcon = {
                                if (sel) Icon(
                                    Icons.Filled.Check, null,
                                    tint = accent, modifier = Modifier.size(14.dp)
                                )
                            },
                            onClick = {
                                speedMenu = false
                                if (!sel) onSelectSpeed(v)
                            },
                        )
                    }
                }
            }
        }

        // 第二段：细进度条 + 小圆点滑块。刻意不放在最底部——贴底拖动容易触发系统手势
        Spacer(Modifier.height(6.dp))
        SeekBar(
            fraction = slider,
            accent = accent,
            trackColor = line,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            onSeekStart = { dragging = true },
            onSeek = { dragging = true; slider = it },
            onSeekEnd = { dragging = false; onSeek(it) },
        )

        // 第三段：操作键。独立一行、间距放宽，不再和文件名挤在一行
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SkipChip(
                icon = rewindIcon(skipSeconds),
                label = "−${skipSeconds}s",
                tint = skipTint,
                chipBg = skipBg,
                onClick = onRewind
            )
            Spacer(Modifier.width(36.dp))
            Box(
                Modifier.size(54.dp).clip(RoundedCornerShape(50)).background(accent)
                    .clickable { onToggle() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (playing) "暂停" else "播放",
                    tint = onPlay,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.width(36.dp))
            SkipChip(
                icon = forwardIcon(skipSeconds),
                label = "+${skipSeconds}s",
                tint = skipTint,
                chipBg = skipBg,
                onClick = onForward
            )
        }
    }
}

/**
 * 极简进度条：3dp 细轨 + 9dp 圆点，无多余阴影/描边。
 */
@Composable
private fun SeekBar(
    fraction: Float,
    accent: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    onSeekStart: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekEnd: (Float) -> Unit,
) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    val lastF = remember { mutableFloatStateOf(fraction) }
    val thumbPx = with(LocalDensity.current) { 9.dp.toPx() }

    Box(
        modifier
            .fillMaxWidth()
            .height(24.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectTapGestures { off ->
                    val f = (off.x / widthPx).coerceIn(0f, 1f)
                    lastF.floatValue = f
                    onSeekStart(); onSeek(f); onSeekEnd(f)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { onSeekStart() },
                    onDragEnd = { onSeekEnd(lastF.floatValue) },
                    onDragCancel = { onSeekEnd(lastF.floatValue) },
                ) { change, _ ->
                    change.consume()
                    val f = (change.position.x / widthPx).coerceIn(0f, 1f)
                    lastF.floatValue = f
                    onSeek(f)
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier.fillMaxWidth().height(3.dp)
                .clip(RoundedCornerShape(50)).background(trackColor)
        )
        Box(
            Modifier.fillMaxWidth(fraction).height(3.dp)
                .clip(RoundedCornerShape(50)).background(accent)
        )
        Box(
            Modifier
                .offset {
                    IntOffset(((fraction * widthPx) - thumbPx / 2f).roundToInt(), 0)
                }
                .size(9.dp)
                .clip(CircleShape)
                .background(accent)
        )
    }
}

/** 快退 / 快进胶囊按键：与播放圆钮同色系，图标 + 秒数文案 */
@Composable
private fun SkipChip(
    icon: ImageVector,
    label: String,
    tint: Color,
    chipBg: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(chipBg)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = tint, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * 该文件夹有历史、但不是当前播放所在文件夹时，底部只给一个「继续播放」入口。
 */
@Composable
fun ResumeBar(
    fileName: String,
    positionMs: Long,
    accent: Color,
    onAccent: Color,
    cardBg: Color,
    textColor: Color,
    subColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg.copy(alpha = 0.97f))
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(50)).background(accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.PlayArrow, "继续播放", tint = onAccent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("继续播放", color = textColor, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "$fileName · 上次播到 ${formatTime(positionMs)}",
                color = subColor, fontSize = 9.5.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
    }
}
