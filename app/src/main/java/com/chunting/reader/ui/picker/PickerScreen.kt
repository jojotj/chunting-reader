package com.chunting.reader.ui.picker

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chunting.reader.data.repo.DirNode
import com.chunting.reader.ui.AppViewModel
import com.chunting.reader.ui.common.GeneratedCover
import com.chunting.reader.ui.theme.LocalAppColors

/**
 * 目录浏览器：像文件管理器一样逐级进入手机目录，左侧复选框多选文件夹后批量加入书架。
 * 目录列表直接读文件系统（内部存储 / 存储卡 / 任意子目录），不依赖媒体库索引；
 * 每个目录的「音频数量」由后台限深探测异步补全，先从 0 变「统计中」，再变实际值。
 */
@Composable
fun PickerScreen(vm: AppViewModel, onBack: () -> Unit) {
    val c = LocalAppColors.current
    val entries by vm.browseEntries.collectAsState()
    val crumbs by vm.breadcrumb.collectAsState()
    val checked by vm.checkedDirs.collectAsState()
    val loading by vm.pickerLoading.collectAsState()
    val adding by vm.addingFolders.collectAsState()
    val shelf by vm.shelf.collectAsState()
    val curNode = crumbs.lastOrNull()
    val inShelf = shelf.map { it.path.trimEnd('/') }.toSet()

    val ctx = androidx.compose.ui.platform.LocalContext.current
    var needAllFiles by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        needAllFiles = Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()
    }

    val onFg = if (c.isDark) Color(0xFF141008) else Color(0xFFFAF8F4)

    Column(Modifier.fillMaxSize().background(c.bg)) {
        // 顶部标题栏
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(c.line)
                    .clickable { if (!vm.browseUp()) onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.ArrowBack, "返回", tint = c.text, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("选择文件夹", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (checked.isEmpty()) "点目录进去，左侧方框可多选"
                    else "已选 ${checked.size} 个文件夹",
                    color = if (checked.isEmpty()) c.sub else c.accent,
                    fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 面包屑
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CrumbChip("存储位置", crumbs.isEmpty()) { vm.browseInto(null) }
            crumbs.forEachIndexed { idx, n ->
                Text("›", color = c.sub, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 4.dp))
                CrumbChip(n.name, idx == crumbs.lastIndex) { vm.browseInto(n.path) }
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))

        if (needAllFiles) {
            Row(
                Modifier.fillMaxWidth()
                    .clickable {
                        runCatching {
                            ctx.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:${ctx.packageName}")
                                )
                            )
                        }
                    }
                    .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "目录列表不全？点此开启「所有文件访问」",
                    color = c.accent, fontSize = 11.sp, modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Filled.KeyboardArrowRight, null,
                    tint = c.accent, modifier = Modifier.size(16.dp)
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.accent)
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 14.dp)
        ) {
            if (entries.isEmpty() && curNode == null) {
                item(key = "__empty__") {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("没有读到手机存储", color = c.text, fontSize = 14.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "请到系统设置里给本应用授予「音乐和音频」或「所有文件访问」权限后重进",
                                color = c.sub, fontSize = 11.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
            if (entries.isEmpty() && curNode != null) {
                item(key = "__nochild__") {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp)) {
                        Text(
                            "这个目录下没有子文件夹；若音频就在当前目录，勾选上方「当前文件夹」即可",
                            color = c.sub, fontSize = 11.sp
                        )
                    }
                }
            }
            if (curNode != null) {
                item(key = "__current__") {
                    DirRow(
                        node = curNode,
                        checked = curNode.path.trimEnd('/') in checked,
                        inShelf = curNode.path.trimEnd('/') in inShelf,
                        label = "当前文件夹",
                        onCheck = { vm.toggleChecked(curNode.path) },
                        onOpen = {},
                    )
                }
                item(key = "__divider__") {
                    Box(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)
                            .height(1.dp).background(c.line)
                    )
                }
            }
            items(entries, key = { it.path }) { node ->
                DirRow(
                    node = node,
                    checked = node.path.trimEnd('/') in checked,
                    inShelf = node.path.trimEnd('/') in inShelf,
                    onCheck = { vm.toggleChecked(node.path) },
                    onOpen = { vm.browseInto(node.path) },
                )
            }
        }

        // 底部操作条
        Row(
            Modifier.fillMaxWidth().background(c.line.copy(alpha = 0.55f))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (checked.isEmpty()) "尚未选择" else "已选 ${checked.size} 个",
                    color = if (checked.isEmpty()) c.sub else c.text,
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (checked.isEmpty()) "勾选左侧方框可多选" else "点右侧「取消所选」可全部清空",
                    color = c.sub, fontSize = 9.5.sp
                )
            }
            // 取消所选
            Box(
                Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(c.line)
                    .clickable(enabled = checked.isNotEmpty() && !adding) { vm.clearChecked() }
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "取消所选",
                    color = if (checked.isEmpty()) c.sub.copy(alpha = 0.5f) else c.text,
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (checked.isEmpty() || adding) c.line else c.accent)
                    .clickable(enabled = checked.isNotEmpty() && !adding) { vm.addCheckedToShelf() }
                    .padding(horizontal = 18.dp, vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                if (adding) {
                    CircularProgressIndicator(
                        color = c.accent, strokeWidth = 2.dp, modifier = Modifier.size(14.dp)
                    )
                } else {
                    Text(
                        "加入书架" + if (checked.isEmpty()) "" else " (${checked.size})",
                        color = if (checked.isEmpty()) c.sub else onFg,
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun CrumbChip(text: String, active: Boolean, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Text(
        text,
        color = if (active) c.text else c.sub,
        fontSize = 11.sp,
        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 3.dp)
    )
}

@Composable
private fun DirRow(
    node: DirNode,
    checked: Boolean,
    inShelf: Boolean,
    label: String? = null,
    onCheck: () -> Unit,
    onOpen: () -> Unit,
) {
    val c = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onOpen() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onCheck() },
            enabled = !inShelf,
            colors = CheckboxDefaults.colors(
                checkedColor = c.accent,
                uncheckedColor = if (inShelf) c.line else c.sub,
                checkmarkColor = if (c.isDark) Color(0xFF141008) else Color(0xFFFAF8F4),
            ),
            modifier = Modifier.size(38.dp)
        )
        GeneratedCover(node.name, Modifier.size(40.dp), fontSize = 17)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    node.name,
                    color = if (inShelf) c.sub else c.text,
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (label != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        label,
                        color = c.accent, fontSize = 8.5.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(c.accent.copy(alpha = 0.14f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
                if (inShelf) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "已在书架",
                        color = c.sub, fontSize = 8.5.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(c.line)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
            Text(
                if (label != null) "当前所在目录 · 可直接勾选加入书架" else "文件夹 · 点进去继续选",
                color = c.sub, fontSize = 9.5.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        if (label == null) {
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Filled.KeyboardArrowRight, "进入",
                tint = c.sub, modifier = Modifier.size(18.dp)
            )
        }
    }
}
