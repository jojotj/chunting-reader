package com.chunting.reader.ui.shelf

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chunting.reader.data.model.ShelfFolder
import com.chunting.reader.ui.AppViewModel
import com.chunting.reader.ui.common.GeneratedCover
import com.chunting.reader.ui.theme.LocalAppColors
import com.chunting.reader.ui.theme.ShelfLayout
import com.chunting.reader.ui.theme.ThemeMode
import com.chunting.reader.util.formatDuration

/**
 * 书架：竖屏沿用原来的三列网格 + 底部播放条；横屏（车机 / 平板 / 手机横放）
 * 切换为设计稿 v6 的「96dp 导航栏 + 左 36% / 右 64%」布局。
 */
@Composable
fun ShelfScreen(
    vm: AppViewModel,
    onOpenFolder: () -> Unit,
    onAdd: () -> Unit,
    onSettings: () -> Unit,
    onOpenCurrentFolder: () -> Unit,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(
        androidx.compose.ui.Modifier.fillMaxSize()
    ) {
        if (maxWidth > maxHeight) {
            com.chunting.reader.ui.common.LandscapeCanvas {
                ShelfScreenLandscape(
                    vm = vm,
                    onOpenFolder = onOpenFolder,
                    onAdd = onAdd,
                    onSettings = onSettings,
                    onOpenCurrentFolder = onOpenCurrentFolder,
                )
            }
        } else {
            ShelfScreenPortrait(
                vm = vm,
                onOpenFolder = onOpenFolder,
                onAdd = onAdd,
                onSettings = onSettings,
                onOpenCurrentFolder = onOpenCurrentFolder,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShelfScreenPortrait(
    vm: AppViewModel,
    onOpenFolder: () -> Unit,
    onAdd: () -> Unit,
    onSettings: () -> Unit,
    onOpenCurrentFolder: () -> Unit,
) {
    val c = LocalAppColors.current
    val folders by vm.shelf.collectAsState()
    val settings by vm.settingsFlow.collectAsState()
    val current by com.chunting.reader.player.PlayerController.currentTrack.collectAsState()
    val playing by com.chunting.reader.player.PlayerController.isPlaying.collectAsState()

    // 书架多选（长按进入：封面中心勾选 + 底部全选/删除 + 右上完成）
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    fun toggleSel(path: String) {
        selected = if (path in selected) selected - path else selected + path
    }
    // 底部浮层（多选条 / 迷你播放条）实测高度，列表底部留白据此计算
    var barAreaH by remember { mutableIntStateOf(0) }
    val barAreaDp = with(LocalDensity.current) { barAreaH.toDp() }
    val hasBottomBar = selecting || current != null
    val shelfBottomPad = if (hasBottomBar) barAreaDp + 16.dp else 100.dp

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            // 标题
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (selecting) "已选 ${selected.size} 项" else "书架",
                        color = c.text,
                        fontSize = if (selecting) 21.sp else 26.sp,
                        lineHeight = if (selecting) 26.sp else 32.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.6).sp,
                        // 字体与主题无关：衬线/无衬线行高不同，切换明暗时整页会上下跳
                        fontFamily = FontFamily.Serif,
                    )
                    Text(
                        if (selecting) "点按继续勾选"
                        else if (folders.isEmpty()) "还没有添加文件夹"
                        else "${folders.size} 个文件夹 · ${folders.sumOf { it.trackCount }} 段音频",
                        color = c.sub, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp)
                    )
                }
                if (!selecting) {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(c.line)
                        .clickable {
                            vm.setLayout(
                                if (settings.layout == ShelfLayout.GRID) ShelfLayout.LIST else ShelfLayout.GRID
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (settings.layout == ShelfLayout.GRID) Icons.Filled.GridView
                        else Icons.AutoMirrored.Filled.ViewList,
                        contentDescription = "切换排列",
                        tint = c.sub, modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(c.line)
                        .clickable {
                            vm.setTheme(
                                when (settings.themeMode) {
                                    ThemeMode.SYSTEM -> ThemeMode.LIGHT
                                    ThemeMode.LIGHT -> ThemeMode.DARK
                                    ThemeMode.DARK -> ThemeMode.SYSTEM
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        when (settings.themeMode) {
                            ThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
                            ThemeMode.LIGHT -> Icons.Filled.LightMode
                            ThemeMode.DARK -> Icons.Filled.DarkMode
                        },
                        contentDescription = "切换主题",
                        tint = c.sub, modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                // 设置入口（原底部「设置」Tab，收成右上角小图标）
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(c.line)
                        .clickable { onSettings() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Settings, contentDescription = "设置",
                        tint = c.sub, modifier = Modifier.size(16.dp)
                    )
                }
                }
                if (selecting) {
                    Box(
                        Modifier.clip(RoundedCornerShape(9.dp)).background(c.accent)
                            .clickable { selecting = false; selected = emptySet() }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "完成",
                            color = if (c.isDark) Color(0xFF141008) else Color(0xFFFAF8F4),
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (folders.isEmpty()) {
                EmptyShelf(onAdd)
            } else {
                when (settings.layout) {
                    ShelfLayout.GRID -> ShelfGrid(
                        folders, vm, onOpenFolder,
                        selecting = selecting,
                        selected = selected,
                        onToggleSelect = { f -> toggleSel(f.path) },
                        onLongPress = { f ->
                            if (!selecting) selecting = true
                            if (f.path !in selected) selected = selected + f.path
                        },
                        bottomPad = shelfBottomPad,
                    )
                    ShelfLayout.LIST -> ShelfList(
                        folders, vm, onOpenFolder,
                        selecting = selecting,
                        selected = selected,
                        onToggleSelect = { f -> toggleSel(f.path) },
                        onLongPress = { f ->
                            if (!selecting) selecting = true
                            if (f.path !in selected) selected = selected + f.path
                        },
                        bottomPad = shelfBottomPad,
                    )
                }
            }
        }

        // 添加按钮：有迷你播放条时自动抬到播放条上方，避免被遮挡
        if (!selecting) {
        FloatingActionButton(
            onClick = onAdd,
            containerColor = c.accent,
            contentColor = if (c.isDark) Color(0xFF141008) else Color(0xFFFAF8F4),
            modifier = Modifier.align(Alignment.BottomEnd)
                .then(if (current != null) Modifier else Modifier.navigationBarsPadding())
                .padding(
                    end = 18.dp,
                    bottom = if (current != null) {
                        (if (barAreaDp > 86.dp) barAreaDp else 86.dp) + 16.dp
                    } else 18.dp
                )
        ) {
            Icon(Icons.Filled.Add, contentDescription = "添加文件夹")
        }
        }

        // 迷你播放条（多选时让位给操作条）：保持圆角浮窗原貌，不加渐变/实心遮罩
        if (current != null && !selecting) {
            MiniPlayer(
                folderName = folders.firstOrNull { it.path == current!!.folderPath }?.name
                    ?: current!!.folderPath.substringAfterLast('/'),
                fileName = current!!.fileName,
                playing = playing,
                accent = c.accent,
                cardBg = c.card,
                line = c.line,
                textColor = c.text,
                subColor = c.sub,
                onClick = { onOpenCurrentFolder() },
                onToggle = { vm.toggleOrResume() },
                modifier = Modifier.align(Alignment.BottomCenter)
                    .onSizeChanged { barAreaH = it.height }
                    .navigationBarsPadding()
                    .padding(bottom = 10.dp, start = 11.dp, end = 11.dp)
            )
        }

        // 多选操作条：全选 / 删除
        if (selecting) {
            val allSelected = folders.isNotEmpty() && selected.size == folders.size
            Row(
                Modifier.align(Alignment.BottomCenter)
                    .onSizeChanged { barAreaH = it.height }
                    .navigationBarsPadding()
                    .padding(bottom = 10.dp, start = 11.dp, end = 11.dp)
                    .fillMaxWidth().clip(RoundedCornerShape(15.dp))
                    .background(c.card)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(c.line)
                        .clickable {
                            selected = if (allSelected) emptySet() else folders.map { it.path }.toSet()
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (allSelected) "取消全选" else "全选",
                        color = c.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.weight(1f))
                Text("共 ${folders.size} 个", color = c.sub, fontSize = 10.sp)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp))
                        .background(if (selected.isEmpty()) c.line else Color(0xFFD9534F))
                        .clickable(enabled = selected.isNotEmpty()) { confirmDelete = true }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "删除",
                        color = if (selected.isEmpty()) c.sub else Color.White,
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    // 删除前确认：只动书架条目，不动手机里的音频文件
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfGrid(
    folders: List<ShelfFolder>,
    vm: AppViewModel,
    onOpenFolder: () -> Unit,
    selecting: Boolean = false,
    selected: Set<String> = emptySet(),
    onToggleSelect: (ShelfFolder) -> Unit = {},
    onLongPress: (ShelfFolder) -> Unit = {},
    bottomPad: androidx.compose.ui.unit.Dp = 100.dp,
) {
    val c = LocalAppColors.current
    // 横屏 / 大屏（车机、平板）按宽度自适应列数，竖屏手机仍是 3 列
    val cfg = LocalConfiguration.current
    val cols = when {
        cfg.screenWidthDp >= 1000 -> 6
        cfg.screenWidthDp >= 760 -> 5
        cfg.screenWidthDp >= 600 -> 4
        else -> 3
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(cols),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 14.dp, bottom = bottomPad),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(folders, key = { it.path }) { f ->
            Box(Modifier.fillMaxWidth().combinedClickable(
                onClick = {
                    if (selecting) onToggleSelect(f) else { vm.openFolder(f); onOpenFolder() }
                },
                onLongClick = { onLongPress(f) },
            )) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // v6 竖屏 s6：封面取单元格宽的 59%、2:3 比例，比老版整行铺满瘦一号
                Box(Modifier.fillMaxWidth(0.59f).aspectRatio(2f / 3f)) {
                    GeneratedCover(
                        folderName = f.name,
                        modifier = Modifier.fillMaxSize(),
                        fontSize = 20,
                    )
                    if (f.progress > 0f) {
                        LinearProgressIndicator(
                            progress = { f.progress },
                            color = c.accent,
                            trackColor = Color.Black.copy(alpha = 0.25f),
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.5.dp)
                        )
                    }
                    // 多选蒙层 + 中心复选框
                    if (selecting) {
                        val isSel = f.path in selected
                        Box(
                            Modifier.matchParentSize()
                                .background(Color.Black.copy(alpha = if (isSel) 0.34f else 0.10f))
                        )
                        Icon(
                            if (isSel) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                            contentDescription = if (isSel) "已选" else "未选",
                            tint = if (isSel) c.accent else Color.White.copy(alpha = 0.92f),
                            modifier = Modifier.align(Alignment.Center).size(if (isSel) 30.dp else 26.dp)
                        )
                    }
                }
                Text(
                    f.name,
                    color = c.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 7.dp)
                )
                Text(
                    "${f.trackCount} 讲 · ${(f.progress * 100).toInt()}%",
                    color = c.sub, fontSize = 9.sp, maxLines = 1,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 3.dp)
                )
            }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfList(
    folders: List<ShelfFolder>,
    vm: AppViewModel,
    onOpenFolder: () -> Unit,
    selecting: Boolean = false,
    selected: Set<String> = emptySet(),
    onToggleSelect: (ShelfFolder) -> Unit = {},
    onLongPress: (ShelfFolder) -> Unit = {},
    bottomPad: androidx.compose.ui.unit.Dp = 100.dp,
) {
    val c = LocalAppColors.current
    androidx.compose.foundation.lazy.LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = bottomPad),
    ) {
        items(folders, key = { it.path }) { f ->
            Row(
                Modifier.fillMaxWidth().combinedClickable(
                    onClick = {
                        if (selecting) onToggleSelect(f) else { vm.openFolder(f); onOpenFolder() }
                    },
                    onLongClick = { onLongPress(f) },
                ).padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    GeneratedCover(f.name, Modifier.size(42.dp), fontSize = 17)
                    // 多选蒙层 + 中心复选框
                    if (selecting) {
                        val isSel = f.path in selected
                        Box(
                            Modifier.matchParentSize().clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = if (isSel) 0.34f else 0.10f))
                        )
                        Icon(
                            if (isSel) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                            contentDescription = if (isSel) "已选" else "未选",
                            tint = if (isSel) c.accent else Color.White.copy(alpha = 0.92f),
                            modifier = Modifier.align(Alignment.Center).size(if (isSel) 21.dp else 18.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    // 书架列表统一单行省略，不做跑马灯：整页各条同时滚动会让版面发乱
                    Text(
                        f.name, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${f.trackCount} 讲 · ${formatDuration(f.totalDurationMs)}",
                        color = c.sub, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Text("${(f.progress * 100).toInt()}%", color = c.sub, fontSize = 11.sp)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        }
    }
}

@Composable
private fun EmptyShelf(onAdd: () -> Unit) {
    val c = LocalAppColors.current
    Column(
        Modifier.fillMaxSize().padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GeneratedCover("书架", Modifier.size(96.dp), fontSize = 40)
        Spacer(Modifier.height(22.dp))
        Text("书架是空的", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "添加文件夹后才会出现在这里\n书架不会自动导入，由你手动挑选",
            color = c.sub, fontSize = 12.sp, lineHeight = 19.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).background(c.accent)
                .clickable { onAdd() }.padding(horizontal = 20.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Add, null, tint = if (c.isDark) Color(0xFF141008) else Color(0xFFFAF8F4), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "添加文件夹",
                color = if (c.isDark) Color(0xFF141008) else Color(0xFFFAF8F4),
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun MiniPlayer(
    folderName: String,
    fileName: String,
    playing: Boolean,
    accent: Color,
    cardBg: Color,
    line: Color,
    textColor: Color,
    subColor: Color,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(cardBg)
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GeneratedCover(folderName, Modifier.size(34.dp), fontSize = 14, withSpine = false)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                fileName, color = textColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                folderName, color = subColor, fontSize = 9.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
        Box(
            Modifier.size(29.dp).clip(RoundedCornerShape(50)).background(accent)
                .clickable { onToggle() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                if (playing) "暂停" else "播放",
                tint = if (cardBg == Color(0xFF1A1917)) Color(0xFF141008) else Color(0xFFFAF8F4),
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

