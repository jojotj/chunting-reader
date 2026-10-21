package com.chunting.reader.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chunting.reader.ui.AppViewModel
import com.chunting.reader.ui.theme.AccentPalette
import com.chunting.reader.ui.theme.LocalAppColors
import com.chunting.reader.ui.theme.ShelfLayout
import com.chunting.reader.ui.theme.ThemeMode

@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit = {}) {
    val c = LocalAppColors.current
    val s by vm.settingsFlow.collectAsState()

    Column(
        Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(c.line)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = c.sub, modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                "设置", color = c.text, fontSize = 24.sp, fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(18.dp))

        SectionTitle("外观")
        SettingRow("主题", "明亮 / 夜晚 / 跟随系统") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SegChip("明亮", s.themeMode == ThemeMode.LIGHT, c) { vm.setTheme(ThemeMode.LIGHT) }
                SegChip("夜晚", s.themeMode == ThemeMode.DARK, c) { vm.setTheme(ThemeMode.DARK) }
                SegChip("跟随", s.themeMode == ThemeMode.SYSTEM, c) { vm.setTheme(ThemeMode.SYSTEM) }
            }
        }
        SettingRow("书架排列", "一行 3 本的网格，或单列列表") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SegChip("网格", s.layout == ShelfLayout.GRID, c) { vm.setLayout(ShelfLayout.GRID) }
                SegChip("列表", s.layout == ShelfLayout.LIST, c) { vm.setLayout(ShelfLayout.LIST) }
            }
        }

        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
            Text("强调色", color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                "按钮、进度条、选中态的颜色，共 6 种",
                color = c.sub, fontSize = 9.5.sp, modifier = Modifier.padding(top = 2.dp)
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                AccentPalette.forEachIndexed { i, pair ->
                    val on = s.accentIndex == i
                    Box(
                        Modifier.size(28.dp).clip(RoundedCornerShape(50))
                            .background(if (c.isDark) pair.dark else pair.light)
                            .then(
                                if (on) Modifier.border(2.dp, c.text.copy(alpha = 0.55f), RoundedCornerShape(50))
                                else Modifier
                            )
                            .clickable { vm.setAccent(i) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (on) Icon(
                            Icons.Filled.Check, null,
                            tint = if (c.isDark) Color(0xFF141008) else Color(0xFFFAF8F4),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.line).padding(top = 10.dp))
        }

        SectionTitle("播放")
        SettingRow("快进 / 后退秒数", "播放页两翼按钮的跳转幅度") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(10, 15, 30, 60).forEach { v ->
                    SegChip("$v", s.skipSeconds == v, c) { vm.setSkipSeconds(v) }
                }
            }
        }
        SettingRow("默认倍速", "新文件夹首次播放的速度") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0.5f, 1f, 1.25f, 1.5f, 2f).forEach { v ->
                    SegChip(fmt(v), kotlin.math.abs(s.defaultSpeed - v) < 0.01f, c) { vm.setDefaultSpeed(v) }
                }
            }
        }
        SettingRow("记住每个文件夹的倍速", "不同内容用不同速度") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SegChip("开", s.rememberSpeedPerFolder, c) { vm.setRememberSpeed(true) }
                SegChip("关", !s.rememberSpeedPerFolder, c) { vm.setRememberSpeed(false) }
            }
        }
        SettingRow("播放结束自动下一曲", "文件夹内连续播放") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SegChip("开", s.autoNext, c) { vm.setAutoNext(true) }
                SegChip("关", !s.autoNext, c) { vm.setAutoNext(false) }
            }
        }

        SectionTitle("关于")
        Text(
            "本地听书 · 纯离线\n不申请网络权限，没有任何在线功能。\n封面由文件夹名自动生成，不读取任何图片。",
            color = c.sub, fontSize = 11.sp, lineHeight = 18.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun SectionTitle(t: String) {
    val c = LocalAppColors.current
    Text(
        t, color = c.sub, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingRow(title: String, sub: String, control: @Composable () -> Unit) {
    val c = LocalAppColors.current
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(sub, color = c.sub, fontSize = 9.5.sp, modifier = Modifier.padding(top = 2.dp))
            }
            control()
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line).padding(top = 10.dp))
    }
}

@Composable
private fun SegChip(label: String, on: Boolean, c: com.chunting.reader.ui.theme.AppColors, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (on) c.accent else c.line)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            color = if (on) {
                if (c.isDark) Color(0xFF141008) else Color(0xFFFAF8F4)
            } else c.sub,
            fontSize = 10.5.sp,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

private fun fmt(v: Float): String {
    val i = (v * 100).toInt()
    return if (i % 100 == 0) "%d×".format(i / 100) else "%.2f×".format(v).trimEnd('0')
}
