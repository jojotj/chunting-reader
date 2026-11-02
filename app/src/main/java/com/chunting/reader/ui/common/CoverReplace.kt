package com.chunting.reader.ui.common

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chunting.reader.util.CoverStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 封面替换：点名一本文件夹 → 起系统相册 → 存成 2:3 封面。
 * 返回的函数参数是 folderPath，点按钮时再传，避免闭包抓错当前选中项。
 */
@Composable
fun rememberCoverReplaceLauncher(): (folderPath: String) -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf("") }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        val path = pending
        if (uri == null || path.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) { CoverStore.saveFromUri(ctx, path, uri) }
            Toast.makeText(
                ctx,
                if (ok) "封面已替换" else "这张图读不出来，换一张试试",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    return { folderPath ->
        if (folderPath.isNotEmpty()) {
            pending = folderPath
            runCatching { launcher.launch("image/*") }
        }
    }
}

/**
 * 「封面还原」：选中几本就还几本，不区分有没有换过封面（没换的删不掉，直接跳过）。
 * 返回的函数参数是 folderPath 列表，点按钮时再传，避免闭包抓错当前选中项。
 */
@Composable
fun rememberCoverResetAction(): (folderPaths: List<String>) -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    return { paths ->
        if (paths.isNotEmpty()) {
            scope.launch {
                val removed = withContext(Dispatchers.IO) { CoverStore.reset(ctx, paths) }
                Toast.makeText(
                    ctx,
                    if (removed > 0) "已还原 $removed 本封面" else "这些已经是原始封面",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}

/** 操作条上的小胶囊：描边 + 居中图标文字，禁用时整颗压暗 */
@Composable
private fun CoverPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    enabled: Boolean,
    accent: Color,
    line: Color,
    subColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tint = if (enabled) accent else subColor.copy(alpha = 0.55f)
    val stroke = if (enabled) accent.copy(alpha = 0.55f) else line
    Row(
        modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (enabled) accent.copy(alpha = 0.12f) else Color.Transparent)
            .border(1.dp, stroke, RoundedCornerShape(9.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(13.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text,
            color = tint,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * 「封面替换」按钮：与主题同色系的小胶囊。
 * 只选中 1 本时可点（描边强调色 + 强调色文字），选中多本时整颗压暗且不可点。
 */
@Composable
fun CoverReplacePill(
    enabled: Boolean,
    accent: Color,
    line: Color,
    subColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    CoverPill(
        icon = Icons.Filled.PhotoCamera,
        text = "封面替换",
        enabled = enabled,
        accent = accent,
        line = line,
        subColor = subColor,
        modifier = modifier,
        onClick = onClick
    )
}

/**
 * 「封面还原」按钮：只要选中了书就能点（可多选），还原回 App 原来的生成式封面。
 */
@Composable
fun CoverResetPill(
    enabled: Boolean,
    accent: Color,
    line: Color,
    subColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    CoverPill(
        icon = Icons.Filled.Restore,
        text = "封面还原",
        enabled = enabled,
        accent = accent,
        line = line,
        subColor = subColor,
        modifier = modifier,
        onClick = onClick
    )
}
