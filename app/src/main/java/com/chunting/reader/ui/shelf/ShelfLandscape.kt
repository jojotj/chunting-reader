package com.chunting.reader.ui.shelf

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chunting.reader.data.model.ShelfFolder
import com.chunting.reader.player.PlayerController
import com.chunting.reader.ui.AppViewModel
import com.chunting.reader.ui.common.AppRail
import com.chunting.reader.ui.common.DotLine
import com.chunting.reader.ui.common.GeneratedCover
import com.chunting.reader.ui.folder.TrackRow
import com.chunting.reader.ui.theme.LocalAppColors
import com.chunting.reader.ui.theme.ShelfLayout
import com.chunting.reader.util.formatDuration
import com.chunting.reader.util.formatTime

/**
 * 横屏书架（车机 / 平板 / 手机横放），对齐设计稿 v6：
 * 左侧 96dp 竖排导航栏；主区左 36% / 右 64%。
 * 陈列模式 = 5 列书封网格 + 左下角迷你播放条；
 * 列表模式 = 左书单（36%）+ 右曲目（64%），右栏底部「继续播放」与左栏播放条上沿对齐。
 */
@Composable
fun ShelfScreenLandscape(
    vm: AppViewModel,
    onOpenFolder: () -> Unit,
    onAdd: () -> Unit,
    onSettings: () -> Unit,
    onOpenCurrentFolder: () -> Unit,
) {
    val c = LocalAppColors.current
    val folders by vm.shelf.collectAsState()
    val settings by vm.settingsFlow.collectAsState()
    val current by PlayerController.currentTrack.collectAsState()
    val playing by PlayerController.isPlaying.collectAsState()
    val position by PlayerController.positionMs.collectAsState()
    val duration by PlayerController.durationMs.collectAsState()
    val speed by PlayerController.speed.collectAsState()
    val curFolder by vm.currentFolder.collectAsState()
    val curTracks by vm.currentTracks.collectAsState()
    val hint by vm.resumeHint.collectAsState()
    val positions by vm.trackPositions.collectAsState()

    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun toggleSel(path: String) {
        selected = if (path in selected) selected - path else selected + path
    }

    // 播放中每 500ms 刷新一次进度，让播放条走起来
    LaunchedEffect(current?.path) {
        if (current == null) return@LaunchedEffect
        while (true) {
            PlayerController.refreshPosition()
            kotlinx.coroutines.delay(500)
        }
    }

    val folderName = folders.firstOrNull { it.path == current?.folderPath }?.name
        ?: current?.folderPath?.substringAfterLast('/').orEmpty()

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Row(Modifier.fillMaxSize()) {
            AppRail(
                layout = settings.layout,
                themeMode = settings.themeMode,
                onShelf = { selecting = false; selected = emptySet() },
                onAddFolder = onAdd,
                onToggleLayout = {
                    vm.setLayout(
                        if (settings.layout == ShelfLayout.GRID) ShelfLayout.LIST else ShelfLayout.GRID
                    )
                },
                onToggleTheme = {
                    vm.setTheme(
                        when (settings.themeMode) {
                            com.chunting.reader.ui.theme.ThemeMode.SYSTEM -> com.chunting.reader.ui.theme.ThemeMode.LIGHT
                            com.chunting.reader.ui.theme.ThemeMode.LIGHT -> com.chunting.reader.ui.theme.ThemeMode.DARK
                            com.chunting.reader.ui.theme.ThemeMode.DARK -> com.chunting.reader.ui.theme.ThemeMode.SYSTEM
                        }
                    )
                },
                onSettings = onSettings,
            )

            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                val barWidth = maxWidth * 0.36f - 40.dp
                val resumeWidth = maxWidth * 0.64f - 60.dp

                if (folders.isEmpty()) {
                    EmptyShelfLandscape(onAdd)
                } else if (settings.layout == ShelfLayout.GRID) {
                    ShelfGridLandscape(
                        folders = folders,
                        selecting = selecting,
                        selected = selected,
                        onClick = { f ->
                            if (selecting) toggleSel(f.path)
                            else {
                                vm.openFolder(f)
                                onOpenFolder()
                            }
                        },
                        onLongClick = { f ->
                            if (!selecting) selecting = true
                            if (f.path !in selected) selected = selected + f.path
                        },
                    )
                } else {
                    ShelfSplitLandscape(
                        vm = vm,
                        folders = folders,
                        picked = curFolder,
                        tracks = curTracks,
                        positions = positions,
                        currentPath = current?.path,
                        selecting = selecting,
                        selected = selected,
                        onPick = { f -> vm.openFolder(f) },
                        onToggleSelect = { f -> toggleSel(f.path) },
                        onLongPress = { f ->
                            if (!selecting) selecting = true
                            if (f.path !in selected) selected = selected + f.path
                        },
                        onOpenFolder = onOpenFolder,
                    )
                }

                // 左下角迷你播放条（与左栏同宽，底部留 16dp）
                if (current != null && !selecting) {
                    ShelfPlayerBar(
                        trackName = current!!.fileName,
                        folderName = folderName,
                        playing = playing,
                        positionMs = position,
                        durationMs = if (duration > 0) duration else current!!.durationMs,
                        speed = speed,
                        onClick = onOpenCurrentFolder,
                        onToggle = { vm.toggleOrResume() },
                        modifier = Modifier.align(Alignment.BottomStart)
                            .padding(start = 20.dp, bottom = 16.dp)
                            .width(barWidth),
                    )
                }

                // 列表模式下右栏底部「继续播放」：上沿与左栏播放条对齐（同为 70dp 高、16dp 底距）
                if (settings.layout == ShelfLayout.LIST && !selecting && hint != null) {
                    val h = hint!!
                    val folderPath = curFolder?.path.orEmpty().trimEnd('/')
                    val sameFolder = folderPath.isNotEmpty() && h.trackPath.startsWith("$folderPath/")
                    if (sameFolder) {
                        ResumeCardLandscape(
                            fileName = h.fileName,
                            index = h.trackIndex,
                            positionMs = h.positionMs,
                            onContinue = {
                                vm.playResumeHint()
                                onOpenFolder()
                            },
                            modifier = Modifier.align(Alignment.BottomEnd)
                                .padding(end = 40.dp, bottom = 16.dp)
                                .width(resumeWidth),
                        )
                    }
                }

                // 多选操作条（占右下角，播放条与续播卡自动让位）
                if (selecting) {
                    val allSelected = folders.isNotEmpty() && selected.size == folders.size
                    Row(
                        Modifier.align(Alignment.BottomEnd)
                            .padding(end = 40.dp, bottom = 16.dp)
                            .width(resumeWidth)
                            .height(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(c.card)
                            .border(1.dp, c.line, RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.clip(RoundedCornerShape(10.dp)).background(c.line)
                                .clickable {
                                    selected = if (allSelected) emptySet()
                                    else folders.map { it.path }.toSet()
                                }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (allSelected) "取消全选" else "全选",
                                color = c.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text("已选 ${selected.size} / ${folders.size}", color = c.sub, fontSize = 10.sp)
                        Spacer(Modifier.weight(1f))
                        Box(
                            Modifier.clip(RoundedCornerShape(10.dp))
                                .background(if (selected.isEmpty()) c.line else Color(0xFFD9534F))
                                .clickable(enabled = selected.isNotEmpty()) { confirmDelete = true }
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "删除",
                                color = if (selected.isEmpty()) c.sub else Color.White,
                                fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier.clip(RoundedCornerShape(10.dp)).background(c.accent)
                                .clickable { selecting = false; selected = emptySet() }
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "完成",
                                color = if (c.isDark) Color(0xFF141008) else Color(0xFFFAF8F4),
                                fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = {
                Text(
                    "从书架移除 ${selected.size} 个文件夹？",
                    color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    "只移除书架条目和阅读进度，手机里的音频文件不会被删除。",
                    color = c.sub, fontSize = 12.sp, lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeFolders(selected.toList())
                    confirmDelete = false
                    selecting = false
                    selected = emptySet()
                }) {
                    Text("删除", color = Color(0xFFD9534F), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("取消", color = c.sub, fontSize = 13.sp)
                }
            },
            containerColor = c.card,
        )
    }
}

/* ======================= 陈列模式：5 列网格 ======================= */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfGridLandscape(
    folders: List<ShelfFolder>,
    selecting: Boolean,
    selected: Set<String>,
    onClick: (ShelfFolder) -> Unit,
    onLongClick: (ShelfFolder) -> Unit,
) {
    val c = LocalAppColors.current
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(start = 40.dp, end = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "书架",
                color = c.text, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                letterSpacing = (-0.6).sp, fontFamily = FontFamily.Serif, maxLines = 1
            )
            Spacer(Modifier.width(12.dp))
            Text(
                if (selecting) "已选 ${selected.size} 项"
                else "${folders.size} 个文件夹 · ${folders.sumOf { it.trackCount }} 段音频",
                color = c.sub, fontSize = 11.sp,
                modifier = Modifier.padding(top = 5.dp), maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 2.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(folders, key = { it.path }) { f ->
                BookCellLandscape(
                    f = f,
                    selecting = selecting,
                    checked = f.path in selected,
                    onClick = { onClick(f) },
                    onLongClick = { onLongClick(f) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCellLandscape(
    f: ShelfFolder,
    selecting: Boolean,
    checked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val c = LocalAppColors.current
    Column(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth()) {
            GeneratedCover(
                folderName = f.name,
                // v6 s1：横屏陈列的封面只占单元格宽的 36%，两行才装得下且不显臃肿
                modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.36f).aspectRatio(2f / 3f),
                fontSize = 16,
            )
            if (selecting && checked) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(top = 6.dp).size(24.dp)
                        .clip(CircleShape).background(c.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Check, "已选",
                        tint = if (c.isDark) Color(0xFF141008) else Color(0xFFFAF8F4),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
        Text(
            f.name,
            color = c.text, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 9.dp)
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${f.trackCount} 集", color = c.sub, fontSize = 10.sp, maxLines = 1)
            Spacer(Modifier.width(8.dp))
            DotLine(
                progress = f.progress,
                color = c.accent,
                trackColor = c.line,
                modifier = Modifier.weight(1f).height(3.dp),
            )
        }
    }
}

/* ======================= 列表模式：左书单 + 右曲目 ======================= */

@Composable
private fun ShelfSplitLandscape(
    vm: AppViewModel,
    folders: List<ShelfFolder>,
    picked: ShelfFolder?,
    tracks: List<com.chunting.reader.data.model.AudioTrack>,
    positions: Map<String, Long>,
    currentPath: String?,
    selecting: Boolean,
    selected: Set<String>,
    onPick: (ShelfFolder) -> Unit,
    onToggleSelect: (ShelfFolder) -> Unit,
    onLongPress: (ShelfFolder) -> Unit,
    onOpenFolder: () -> Unit,
) {
    val c = LocalAppColors.current
    val pickedPath = picked?.path.orEmpty().trimEnd('/')

    Row(Modifier.fillMaxSize()) {
        /* 左：书单 36% */
        Column(Modifier.weight(0.36f).fillMaxHeight()) {
            Row(
                Modifier.fillMaxWidth().height(64.dp).padding(start = 20.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "书架",
                    color = c.text, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.6).sp, fontFamily = FontFamily.Serif, maxLines = 1
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "· ${folders.size} 本",
                    color = c.sub, fontSize = 11.sp, maxLines = 1,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(1.dp).background(c.line))
            LazyColumn(
                contentPadding = PaddingValues(start = 12.dp, end = 14.dp, top = 10.dp, bottom = 96.dp)
            ) {
                items(folders, key = { it.path }) { f ->
                    BookRowLandscape(
                        f = f,
                        picked = f.path.trimEnd('/') == pickedPath,
                        selecting = selecting,
                        checked = f.path in selected,
                        onClick = {
                            if (selecting) onToggleSelect(f)
                            else onPick(f)
                        },
                        onLongClick = { onLongPress(f) },
                    )
                }
            }
        }
        Box(Modifier.fillMaxHeight().width(1.dp).background(c.line))

        /* 右：曲目 64% */
        Column(Modifier.weight(0.64f).fillMaxHeight()) {
            if (picked == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("点左侧书单里的书", color = c.text, fontSize = 14.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("这里会列出它的全部音频", color = c.sub, fontSize = 11.sp)
                    }
                }
            } else {
                val listened = positions.keys.count { it.startsWith("$pickedPath/") }
                Row(
                    Modifier.fillMaxWidth().height(64.dp).padding(start = 20.dp, end = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GeneratedCover(
                        picked.name,
                        Modifier.width(30.dp).height(42.dp),
                        fontSize = 15,
                        cornerRadius = 4.dp,
                    )
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            picked.name, color = c.text, fontSize = 15.sp,
                            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${picked.trackCount} 集 · ${formatDuration(picked.totalDurationMs)} · " +
                                if (listened > 0) "已听 $listened 集" else "尚未开始",
                            color = c.sub, fontSize = 10.5.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(1.dp).background(c.line))

                if (tracks.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("这个文件夹里没有音频文件", color = c.sub, fontSize = 12.sp)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 24.dp, top = 6.dp, bottom = 96.dp)
                    ) {
                        itemsIndexed(tracks, key = { _, t -> t.path }) { idx, t ->
                            TrackRow(
                                index = idx,
                                track = t,
                                isCurrent = currentPath == t.path,
                                savedPosMs = positions[t.path] ?: 0L,
                                onClick = {
                                    vm.play(tracks, idx)
                                    onOpenFolder()
                                },
                            )
                            Box(Modifier.fillMaxWidth().height(1.dp).background(c.line.copy(alpha = 0.6f)))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookRowLandscape(
    f: ShelfFolder,
    picked: Boolean,
    selecting: Boolean,
    checked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val c = LocalAppColors.current
    Box {
        Row(
            Modifier.fillMaxWidth().height(68.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (picked) c.accent.copy(alpha = 0.10f) else Color.Transparent)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GeneratedCover(
                f.name,
                Modifier.width(34.dp).height(48.dp),
                fontSize = 16,
                cornerRadius = 4.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    f.name,
                    color = if (picked) c.accent else c.text,
                    fontSize = 13.5.sp,
                    fontWeight = if (picked) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${f.trackCount} 集", color = c.sub, fontSize = 10.sp, maxLines = 1)
                    Spacer(Modifier.width(8.dp))
                    DotLine(
                        progress = f.progress,
                        color = c.accent,
                        trackColor = c.line,
                        modifier = Modifier.weight(1f).height(3.dp),
                    )
                }
            }
            if (selecting) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(22.dp).clip(CircleShape)
                        .background(if (checked) c.accent else c.line),
                    contentAlignment = Alignment.Center,
                ) {
                    if (checked) Icon(
                        Icons.Filled.Check, "已选",
                        tint = if (c.isDark) Color(0xFF141008) else Color(0xFFFAF8F4),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        if (picked) {
            Box(
                Modifier.align(Alignment.CenterStart).width(3.dp).height(30.dp)
                    .clip(RoundedCornerShape(2.dp)).background(c.accent)
            )
        }
    }
}

/* ======================= 通用小部件 ======================= */

@Composable
private fun ShelfPlayerBar(
    trackName: String,
    folderName: String,
    playing: Boolean,
    positionMs: Long,
    durationMs: Long,
    speed: Float,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalAppColors.current
    val frac = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    Row(
        modifier.height(70.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(c.card)
            .border(1.dp, c.line, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GeneratedCover(
            folderName.ifEmpty { "听" },
            Modifier.width(32.dp).height(46.dp),
            fontSize = 15,
            cornerRadius = 4.dp,
        )
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                trackName, color = c.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                "$folderName · ${formatTime(positionMs)} / ${formatTime(durationMs)}",
                color = c.sub, fontSize = 9.5.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)
            )
            DotLine(
                progress = frac,
                color = c.accent,
                trackColor = c.line,
                modifier = Modifier.fillMaxWidth().padding(top = 5.dp, end = 10.dp).height(3.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.clip(RoundedCornerShape(50)).background(c.line)
                .padding(horizontal = 9.dp, vertical = 5.dp)
        ) {
            Text(
                speedLabelText(speed),
                color = c.sub, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1
            )
        }
        Spacer(Modifier.width(9.dp))
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(c.accent).clickable { onToggle() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                if (playing) "暂停" else "播放",
                tint = if (c.isDark) Color(0xFF141008) else Color(0xFFFAF8F4),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ResumeCardLandscape(
    fileName: String,
    index: Int,
    positionMs: Long,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalAppColors.current
    Row(
        modifier.height(70.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(c.card)
            .border(1.dp, c.line, RoundedCornerShape(16.dp))
            .clickable { onContinue() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(c.accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PlayArrow, "继续播放",
                tint = if (c.isDark) Color(0xFF141008) else Color(0xFFFAF8F4),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "继续播放", color = c.text, fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                "第 ${index + 1} 集 $fileName · 上次播到 ${formatTime(positionMs)}",
                color = c.sub, fontSize = 9.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.clip(RoundedCornerShape(10.dp)).background(c.accent)
                .padding(horizontal = 13.dp, vertical = 7.dp)
        ) {
            Text(
                "继续",
                color = if (c.isDark) Color(0xFF141008) else Color(0xFFFAF8F4),
                fontSize = 11.sp, fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun EmptyShelfLandscape(onAdd: () -> Unit) {
    val c = LocalAppColors.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("书架还是空的", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("选一个存放音频的文件夹，里面的每个子文件夹都会成为一本书",
                color = c.sub, fontSize = 12.sp)
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier.clip(RoundedCornerShape(12.dp)).background(c.accent)
                    .clickable { onAdd() }.padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    "添加文件夹",
                    color = if (c.isDark) Color(0xFF141008) else Color(0xFFFAF8F4),
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/** 1.0× / 1.25× / 1.5× 这样的倍速文案 */
private fun speedLabelText(v: Float): String {
    val s = String.format("%.2f", v).trimEnd('0').trimEnd('.')
    return (if (s.contains('.')) s else "$s.0") + "×"
}
