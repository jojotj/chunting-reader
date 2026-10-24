package com.chunting.reader.ui.folder

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chunting.reader.data.model.AudioTrack
import com.chunting.reader.player.PlayerController
import com.chunting.reader.ui.AppViewModel
import com.chunting.reader.ui.common.GeneratedCover
import com.chunting.reader.ui.theme.LocalAppColors
import com.chunting.reader.util.formatTime

/**
 * 竖屏走底部播放条布局；横屏（车机 / 平板 / 手机横放）走「唱片转盘 + 右侧列表」布局。
 * 用实际测量到的宽高判断，旋转屏幕或改变窗口大小时自动切换。
 */
@Composable
fun FolderScreen(vm: AppViewModel, onBack: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            com.chunting.reader.ui.common.LandscapeCanvas {
                FolderScreenLandscape(vm = vm, onBack = onBack)
            }
        } else {
            FolderScreenPortrait(vm = vm, onBack = onBack)
        }
    }
}

@Composable
private fun FolderScreenPortrait(vm: AppViewModel, onBack: () -> Unit) {
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

    // 当前播放曲是否属于这个文件夹
    val folderPath = folder?.path.orEmpty().trimEnd('/')
    val playingHere = current != null && folderPath.isNotEmpty() &&
            (current!!.path == folderPath || current!!.path.startsWith("$folderPath/"))

    // 底部遮罩（渐变 + 播放条）实际高度，用于把列表底部留白收到刚好
    var barAreaH by remember { mutableIntStateOf(0) }
    val hasBar = playingHere || hint != null
    val bottomPad = if (hasBar) with(LocalDensity.current) { barAreaH.toDp() } + 8.dp else 16.dp

    // 回到文件夹时自动定位到上次播放的那一首
    LaunchedEffect(hint, tracks.size) {
        val h = hint ?: return@LaunchedEffect
        if (h.trackIndex in tracks.indices) {
            runCatching { listState.scrollToItem(h.trackIndex) }
        }
    }

    // 播放条可见时高频刷新进度
    LaunchedEffect(playingHere) {
        if (!playingHere) return@LaunchedEffect
        while (true) {
            PlayerController.refreshPosition()
            kotlinx.coroutines.delay(500)
        }
    }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            // 顶部：返回 + 文件夹名
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(c.line)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.ArrowBack, null, tint = c.text, modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        folder?.name ?: "",
                        color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${tracks.size} 个音频 · ${formatTime(tracks.sumOf { it.durationMs })}",
                        color = c.sub, fontSize = 10.sp, modifier = Modifier.padding(top = 1.dp)
                    )
                }
            }

            if (tracks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("这个文件夹里没有音频文件", color = c.sub, fontSize = 12.sp)
                }
                return@Column
            }

            // 点任一文件即从该条开始播放，后续自动续播，不再需要「播放全部」
            Text(
                "按文件名排序 · 点任意一条开始播放",
                color = c.sub, fontSize = 9.5.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomPad)
            ) {
                itemsIndexed(tracks, key = { _, t -> t.path }) { idx, t ->
                    val isCurrent = current?.path == t.path
                    TrackRow(
                        index = idx,
                        track = t,
                        isCurrent = isCurrent,
                        savedPosMs = positions[t.path] ?: 0L,
                        onClick = { vm.play(tracks, idx) },
                    )
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                }
            }
        }

        // 底部：本条已播过才给「继续播放」；正在放本文件夹的曲目才给播放条
        val ct = current
        val h = hint
        if (hasBar) Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .onSizeChanged { barAreaH = it.height }
        ) {
            // 渐变淡出，避免列表在播放条上沿被硬切
            Box(
                Modifier.fillMaxWidth().height(18.dp).background(
                    Brush.verticalGradient(listOf(c.bg.copy(alpha = 0f), c.bg))
                )
            )
            // 实心底：铺到屏幕最底（含导航栏区域），列表内容不再从缝隙透出
            Box(
                Modifier.fillMaxWidth().background(c.bg)
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp, start = 12.dp, end = 12.dp)
            ) {
            when {
                ct != null && playingHere -> PlayerBar(
                    fileName = ct.fileName,
                    folderName = folder?.name ?: ct.folderPath.substringAfterLast('/'),
                    playing = playing,
                    positionMs = position,
                    durationMs = duration.takeIf { it > 0 } ?: ct.durationMs,
                    speed = speed,
                    skipSeconds = settings.skipSeconds,
                    accent = c.accent,
                    isDark = c.isDark,
                    cardBg = c.card,
                    line = c.line,
                    textColor = c.text,
                    subColor = c.sub,
                    onToggle = { vm.toggleOrResume() },
                    onRewind = { PlayerController.rewind() },
                    onForward = { PlayerController.forward() },
                    onSeek = { p ->
                        val d = duration.takeIf { it > 0 } ?: ct.durationMs
                        if (d > 0) PlayerController.seekTo((p * d).toLong())
                    },
                    onSelectSpeed = { vm.setSpeed(it) },
                )

                h != null -> ResumeBar(
                    fileName = h.fileName,
                    positionMs = h.positionMs,
                    accent = c.accent,
                    onAccent = if (c.isDark) Color(0xFF141008) else Color(0xFFFAF8F4),
                    cardBg = c.card,
                    textColor = c.text,
                    subColor = c.sub,
                    onClick = { vm.playResumeHint() },
                )
            }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TrackRow(
    index: Int,
    track: AudioTrack,
    isCurrent: Boolean,
    savedPosMs: Long,
    onClick: () -> Unit,
) {
    val c = LocalAppColors.current
    val highlight = if (isCurrent) c.accent.copy(alpha = 0.12f) else Color.Transparent

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(highlight)
            .clickable { onClick() }.padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "%02d".format(index + 1),
            color = c.sub, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(18.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            // 长文件名滚动显示
            val nameMod = if (isCurrent) {
                Modifier.fillMaxWidth().basicMarquee(
                    iterations = Int.MAX_VALUE,
                    repeatDelayMillis = 1400,
                    velocity = 28.dp
                )
            } else {
                Modifier.fillMaxWidth()
            }
            Text(
                track.fileName,
                color = if (isCurrent) c.accent else c.text,
                fontSize = 13.5.sp,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = if (isCurrent) TextOverflow.Clip else TextOverflow.Ellipsis,
                modifier = nameMod
            )
            // 每一条曲目各自的续播位置，跳到别的课后回来仍从这里继续
            if (!isCurrent && savedPosMs > 0L) {
                Text(
                    "上次听到 ${formatTime(savedPosMs)}",
                    color = c.accent.copy(alpha = 0.85f),
                    fontSize = 9.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        if (isCurrent) {
            EqualizerBars(c.accent)
        } else {
            Text(
                formatTime(track.durationMs),
                color = c.sub, fontSize = 9.5.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun EqualizerBars(color: Color) {
    Row(
        Modifier.height(13.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        listOf(5, 11, 8, 12).forEach { h ->
            Box(
                Modifier.width(2.5.dp).height(h.dp).clip(RoundedCornerShape(1.dp)).background(color)
            )
        }
    }
}
