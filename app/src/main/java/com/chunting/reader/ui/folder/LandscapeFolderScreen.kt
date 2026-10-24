package com.chunting.reader.ui.folder

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chunting.reader.player.PlayerController
import com.chunting.reader.ui.AppViewModel
import com.chunting.reader.ui.common.AppRail
import com.chunting.reader.ui.common.GeneratedCover
import com.chunting.reader.ui.theme.LocalAppColors
import com.chunting.reader.ui.theme.ShelfLayout
import com.chunting.reader.ui.theme.ThemeMode
import com.chunting.reader.util.formatTime
import kotlin.math.abs

/**
 * 横屏（车机 / 平板 / 手机横放）文件夹页：
 * 左半屏是唱片转盘 + 播放控制，右半屏是曲目列表，一次看全，不需要底部播放条。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderScreenLandscape(vm: AppViewModel, onBack: () -> Unit) {
    val c = LocalAppColors.current
    val folder by vm.currentFolder.collectAsState()
    val tracks by vm.currentTracks.collectAsState()
    val hint by vm.resumeHint.collectAsState()
    val positions by vm.trackPositions.collectAsState()
    val current by PlayerController.currentTrack.collectAsState()
    val playing by PlayerController.isPlaying.collectAsState()
    val position by PlayerController.positionMs.collectAsState()
    val duration by PlayerController.durationMs.collectAsState()
    val speed by PlayerController.speed.collectAsState()
    val settings by vm.settingsFlow.collectAsState()
    val listState = rememberLazyListState()

    val folderPath = folder?.path.orEmpty().trimEnd('/')
    val currentPath = current?.path
    val playingHere = currentPath != null && folderPath.isNotEmpty() &&
            (currentPath == folderPath || currentPath.startsWith("$folderPath/"))
    val here = current.takeIf { playingHere }

    val shownTitle = here?.fileName ?: hint?.fileName ?: folder?.name.orEmpty()
    val totalMs = when {
        here != null -> duration.takeIf { it > 0 } ?: here.durationMs
        hint != null -> hint!!.durationMs
        else -> 0L
    }
    val basePosMs = if (here != null) position else (hint?.positionMs ?: 0L)
    val hasPlayable = playingHere || hint != null || tracks.isNotEmpty()

    // 播放中才高频刷新进度
    LaunchedEffect(playingHere) {
        if (!playingHere) return@LaunchedEffect
        while (true) {
            PlayerController.refreshPosition()
            kotlinx.coroutines.delay(500)
        }
    }

    // 切歌后把当前曲滚到可见位置
    LaunchedEffect(currentPath) {
        val p = currentPath ?: return@LaunchedEffect
        val idx = tracks.indexOfFirst { it.path == p }
        if (idx >= 0) runCatching { listState.animateScrollToItem(idx) }
    }

    // 唱片转盘：播放时匀速旋转，暂停即停在当前角度
    var rotation by remember { mutableFloatStateOf(0f) }
    val spinning = playing && playingHere
    LaunchedEffect(spinning) {
        if (!spinning) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    rotation = (rotation + (now - last) / 1_000_000_000f * 24f) % 360f
                }
                last = now
            }
        }
    }

    // 进度条拖动状态
    var slider by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val fraction = if (totalMs > 0) (basePosMs.toFloat() / totalMs).coerceIn(0f, 1f) else 0f
    LaunchedEffect(fraction, dragging) {
        if (!dragging) slider = fraction
    }

    val onAccent = if (c.isDark) Color(0xFF141008) else Color(0xFFFAF8F4)

    val discLabel = folder?.name?.ifEmpty { "听" } ?: "听"
    val idxNow = tracks.indexOfFirst { it.path == currentPath }

    Row(Modifier.fillMaxSize().background(c.bg).navigationBarsPadding()) {
        AppRail(
            layout = settings.layout,
            themeMode = settings.themeMode,
            onShelf = onBack,
            onAddFolder = {},
            onToggleLayout = {
                vm.setLayout(
                    if (settings.layout == ShelfLayout.GRID) ShelfLayout.LIST else ShelfLayout.GRID
                )
            },
            onToggleTheme = {
                vm.setTheme(
                    when (settings.themeMode) {
                        ThemeMode.SYSTEM -> ThemeMode.LIGHT
                        ThemeMode.LIGHT -> ThemeMode.DARK
                        ThemeMode.DARK -> ThemeMode.SYSTEM
                    }
                )
            },
            onSettings = {},
        )

        Row(Modifier.weight(1f).fillMaxHeight()) {
            /* ================= 左 36%：唱片 + 控制 ================= */
            Column(
                Modifier.weight(0.36f).fillMaxHeight()
                    .padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 16.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
                            .background(c.accent.copy(alpha = if (c.isDark) 0.18f else 0.12f))
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.ArrowBack, "返回",
                            tint = c.accent, modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            folder?.name ?: "",
                            color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${tracks.size} 个音频 · ${formatTime(tracks.sumOf { it.durationMs })}",
                            color = c.sub, fontSize = 10.5.sp,
                            modifier = Modifier.padding(top = 2.dp), maxLines = 1
                        )
                    }
                }

                BoxWithConstraints(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    val d = minOf(maxWidth, maxHeight) * 0.56f
                    VinylDisc(
                        diameter = d,
                        rotation = rotation,
                        label = discLabel,
                    )
                }

                Text(
                    shownTitle,
                    color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().basicMarquee(
                        iterations = Int.MAX_VALUE,
                        repeatDelayMillis = 1600,
                        velocity = 26.dp
                    )
                )
                Spacer(Modifier.height(8.dp))

                SeekBar(
                    fraction = slider,
                    accent = c.accent,
                    trackColor = c.line,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                    onSeekStart = { if (playingHere) dragging = true },
                    onSeek = { if (playingHere) { dragging = true; slider = it } },
                    onSeekEnd = {
                        if (playingHere) {
                            dragging = false
                            if (totalMs > 0) PlayerController.seekTo((it * totalMs).toLong())
                        }
                    },
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 3.dp)) {
                    val nowMs = if (dragging) (slider * totalMs).toLong() else basePosMs
                    Text(
                        formatTime(nowMs),
                        color = c.sub, fontSize = 10.sp, maxLines = 1
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "-" + formatTime((totalMs - nowMs).coerceAtLeast(0L)),
                        color = c.sub, fontSize = 10.sp, maxLines = 1
                    )
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val tint = c.accent
                    val chipBg = c.accent.copy(alpha = if (c.isDark) 0.20f else 0.14f)
                    LSkipChip(
                        icon = rewindIcon(settings.skipSeconds),
                        label = "-${settings.skipSeconds}s",
                        tint = tint, chipBg = chipBg,
                    ) { if (playingHere) PlayerController.rewind() }
                    Box(
                        Modifier.size(62.dp).clip(CircleShape).background(c.accent).clickable {
                            when {
                                playingHere -> vm.toggleOrResume()
                                hint != null -> vm.playResumeHint()
                                tracks.isNotEmpty() -> vm.play(tracks, 0)
                            }
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (playingHere && playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            if (playingHere && playing) "暂停" else "播放",
                            tint = onAccent, modifier = Modifier.size(29.dp)
                        )
                    }
                    LSkipChip(
                        icon = forwardIcon(settings.skipSeconds),
                        label = "+${settings.skipSeconds}s",
                        tint = tint, chipBg = chipBg,
                    ) { if (playingHere) PlayerController.forward() }
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SpeedPill(
                        speed = speed,
                        accent = c.accent,
                        textColor = c.text,
                        subColor = c.sub,
                        line = c.line,
                        cardBg = c.card,
                        enabled = hasPlayable,
                        onSelect = { vm.setSpeed(it) },
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (idxNow >= 0) "${idxNow + 1} / ${tracks.size}" else "- / ${tracks.size}",
                        color = c.sub, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }

            Box(Modifier.fillMaxHeight().width(1.dp).background(c.line))

            /* ================= 右 64%：曲目列表 ================= */
            Column(Modifier.weight(0.64f).fillMaxHeight()) {
                Row(
                    Modifier.fillMaxWidth().height(64.dp)
                        .padding(start = 20.dp, end = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GeneratedCover(
                        discLabel,
                        Modifier.width(28.dp).height(42.dp),
                        fontSize = 14,
                        cornerRadius = 4.dp,
                    )
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            folder?.name ?: "",
                            color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${tracks.size} 集 · ${formatTime(tracks.sumOf { it.durationMs })}" +
                                if (idxNow >= 0) " · 正在播放第 ${idxNow + 1} 集" else "",
                            color = c.sub, fontSize = 10.5.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))

                if (tracks.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("这个文件夹里没有音频文件", color = c.sub, fontSize = 12.sp)
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = 16.dp, end = 24.dp, top = 6.dp, bottom = 14.dp)
                    ) {
                        itemsIndexed(tracks, key = { _, t -> t.path }) { idx, t ->
                            TrackRow(
                                index = idx,
                                track = t,
                                isCurrent = currentPath == t.path,
                                savedPosMs = positions[t.path] ?: 0L,
                                onClick = { vm.play(tracks, idx) },
                            )
                            Box(Modifier.fillMaxWidth().height(1.dp).background(c.line.copy(alpha = 0.6f)))
                        }
                    }
                }
            }
        }
    }
}

/**
 * 黑胶唱片：外层纹路盘随播放旋转，中心封面固定不转，中间留一个轴孔。
 */
@Composable
private fun VinylDisc(diameter: Dp, rotation: Float, label: String) {
    val c = LocalAppColors.current
    val disc = if (c.isDark) Color(0xFF14120E) else Color(0xFF1E1B16)
    val groove = if (c.isDark) Color(0xFF3B352A) else Color(0xFF3E382C)

    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxSize().rotate(rotation).clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(disc, groove.copy(alpha = 0.55f), disc, disc)
                    )
                )
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxR = size.minDimension / 2f
                var i = 1
                while (true) {
                    val r = maxR * (0.56f + i * 0.028f)
                    if (r >= maxR - 1f) break
                    drawCircle(
                        color = groove.copy(alpha = 0.35f),
                        radius = r,
                        center = center,
                        style = Stroke(width = 1f)
                    )
                    i++
                }
            }
        }
        // 中心圆形封面（不随盘旋转，文字始终正着）
        Box(Modifier.size(diameter * 0.5f).clip(CircleShape)) {
            GeneratedCover(
                label.ifEmpty { "听" },
                Modifier.fillMaxSize(),
                fontSize = 30,
                withSpine = false,
                cornerRadius = 0.dp,
            )
        }
        // 轴孔
        Box(
            Modifier.size((diameter * 0.04f).coerceAtLeast(4.dp))
                .clip(CircleShape).background(c.bg)
        )
        Box(Modifier.fillMaxSize().clip(CircleShape).border(1.dp, c.line, CircleShape))
    }
}

/** 横屏用的快退 / 快进胶囊 */
@Composable
private fun LSkipChip(
    icon: ImageVector,
    label: String,
    tint: Color,
    chipBg: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(chipBg).clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, color = tint, fontSize = 10.5.sp, fontWeight = FontWeight.Medium)
    }
}

/** 倍速胶囊 + 下拉选档 */
@Composable
private fun SpeedPill(
    speed: Float,
    accent: Color,
    textColor: Color,
    subColor: Color,
    line: Color,
    cardBg: Color,
    enabled: Boolean,
    onSelect: (Float) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(50))
                .background(if (open) accent.copy(alpha = 0.18f) else line)
                .clickable(enabled = enabled) { open = true }
                .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                speedLabel(speed),
                color = if (open) accent else textColor,
                fontSize = 11.sp, fontWeight = FontWeight.Bold
            )
            Icon(
                Icons.Filled.ArrowDropDown, "选择倍速",
                tint = if (open) accent else subColor,
                modifier = Modifier.size(16.dp)
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
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
                        open = false
                        if (!sel) onSelect(v)
                    },
                )
            }
        }
    }
}
