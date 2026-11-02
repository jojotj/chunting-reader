package com.chunting.reader.ui.folder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chunting.reader.player.PlayerController
import com.chunting.reader.ui.common.AnchoredMenu
import com.chunting.reader.ui.common.AnchoredMenuItem

/** 常用定时档位（分钟） */
private val SLEEP_PRESETS = listOf(10, 20, 30, 60)

/**
 * 定时关闭胶囊：点一下弹出上拉菜单，档位 + 自定义。
 * 与倍速胶囊同一套外观与交互，未设定时只显示图标（竖屏播放条空间紧）。
 */
@Composable
fun SleepTimerPill(
    accent: Color,
    textColor: Color,
    subColor: Color,
    line: Color,
    cardBg: Color,
    enabled: Boolean = true,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val remain by PlayerController.sleepRemainMs.collectAsState()
    val active = remain > 0L
    var open by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf<Rect?>(null) }

    // 向上取整到分钟，避免刚设定就显示 9 分
    val remainMin = ((remain + 59_999L) / 60_000L).toInt()
    val label = if (active) "${remainMin}分" else "定时"

    Row(
        modifier
            .onGloballyPositioned { anchor = it.boundsInWindow() }
            .clip(RoundedCornerShape(50))
            .background(if (open || active) accent.copy(alpha = 0.18f) else line)
            .clickable(enabled = enabled) { open = true }
            .padding(
                horizontal = if (compact && !active) 7.dp else 8.dp,
                vertical = 6.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Timer,
            "定时关闭",
            tint = if (open || active) accent else subColor,
            modifier = Modifier.size(15.dp)
        )
        if (!compact || active) {
            Spacer(Modifier.width(5.dp))
            Text(
                label,
                color = if (open || active) accent else textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    AnchoredMenu(
        expanded = open,
        onDismissRequest = { open = false },
        anchorBounds = anchor,
        itemCount = SLEEP_PRESETS.size + 1 + if (active) 1 else 0,
        cardColor = cardBg,
        borderColor = line,
        width = 158.dp,
        itemHeight = 34.dp,
    ) {
        if (active) {
            AnchoredMenuItem(onClick = {
                open = false
                PlayerController.cancelSleepTimer()
            }) {
                Text(
                    "取消定时 ${remainMin}分",
                    color = Color(0xFFD9534F),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
        SLEEP_PRESETS.forEach { m ->
            AnchoredMenuItem(onClick = {
                open = false
                PlayerController.setSleepTimer(m)
            }) {
                Text("$m 分钟", color = textColor, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                if (active && remainMin == m) SelCheck(accent)
            }
        }
        AnchoredMenuItem(onClick = {
            open = false
            custom = true
        }) {
            Text("自定义…", color = textColor, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            if (active && SLEEP_PRESETS.none { it == remainMin }) SelCheck(accent)
        }
    }

    if (custom) {
        CustomSleepDialog(
            accent = accent,
            textColor = textColor,
            subColor = subColor,
            cardBg = cardBg,
            onConfirm = { minutes ->
                custom = false
                PlayerController.setSleepTimer(minutes)
            },
            onExit = {
                // 退组回选择菜单，不设定
                custom = false
                open = true
            },
        )
    }
}

@Composable
private fun SelCheck(accent: Color) {
    Icon(Icons.Filled.Check, null, tint = accent, modifier = Modifier.size(13.dp))
}

/** 自定义分钟数：确定即设定，退出回上一层菜单 */
@Composable
private fun CustomSleepDialog(
    accent: Color,
    textColor: Color,
    subColor: Color,
    cardBg: Color,
    onConfirm: (Int) -> Unit,
    onExit: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val minutes = text.toIntOrNull()?.takeIf { it > 0 }

    AlertDialog(
        onDismissRequest = onExit,
        title = {
            Text("自定义定时关闭", color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column {
                Text(
                    "输入分钟数，到点自动暂停播放，不会关闭 App。",
                    color = subColor, fontSize = 11.5.sp, lineHeight = 17.sp
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { s -> text = s.filter { it.isDigit() }.take(3) },
                        singleLine = true,
                        placeholder = { Text("分钟数", color = subColor, fontSize = 13.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = textColor),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(130.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("分钟", color = subColor, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { minutes?.let(onConfirm) }, enabled = minutes != null) {
                Text(
                    "确定",
                    color = if (minutes != null) accent else subColor,
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onExit) { Text("退出", color = subColor, fontSize = 13.sp) }
        },
        containerColor = cardBg,
        shape = RoundedCornerShape(18.dp),
    )
}
