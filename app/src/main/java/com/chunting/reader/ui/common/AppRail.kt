package com.chunting.reader.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chunting.reader.ui.theme.LocalAppColors
import com.chunting.reader.ui.theme.ShelfLayout
import com.chunting.reader.ui.theme.ThemeMode

/**
 * 横屏左侧竖排导航栏（96dp）：听 logo / 书架 / 添加文件夹 / 弹性留白 / 排列 / 明暗 / 设置。
 * 竖屏页面没有这条导航栏，只有横屏（车机、平板、手机横放）使用。
 */
@Composable
fun AppRail(
    layout: ShelfLayout,
    themeMode: ThemeMode,
    onShelf: () -> Unit,
    onAddFolder: () -> Unit,
    onToggleLayout: () -> Unit,
    onToggleTheme: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalAppColors.current
    val railBg = if (c.isDark) Color(0xFF141311) else Color(0xFFFFFFFF)

    Box(modifier.width(96.dp).fillMaxHeight().background(railBg)) {
        Column(
            Modifier.fillMaxSize().padding(top = 18.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            // logo
            Box(
                Modifier.size(46.dp)
                    .shadow(8.dp, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFFC99A45), Color(0xFF8E6320)),
                            start = Offset.Zero,
                            end = Offset(46f, 46f),
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "听",
                    color = Color(0xFFFBF3E2),
                    fontSize = 23.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Serif,
                )
            }
            Spacer(Modifier.height(22.dp))

            // 书架（当前页，高亮）
            Column(
                Modifier.width(72.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.accent.copy(alpha = 0.13f))
                    .clickable { onShelf() }
                    .padding(vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Filled.LibraryBooks, "书架",
                    tint = c.accent, modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "书架",
                    color = c.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(10.dp))

            RailSquare(Icons.Filled.CreateNewFolder, "添加文件夹", c.sub, onAddFolder)
            Spacer(Modifier.weight(1f))
            // 与竖屏一致：图标显示"当前排列方式"（陈列=网格 / 列表=列表）
            RailSquare(
                if (layout == ShelfLayout.GRID) Icons.Filled.GridView
                else Icons.Filled.ViewList,
                "切换排列", c.sub, onToggleLayout,
            )
            Spacer(Modifier.height(8.dp))
            // 图标语义与竖屏一致：显示"当前所在模式"（明亮=太阳 / 夜晚=月亮 / 跟随=自动）
            RailSquare(
                when (themeMode) {
                    ThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
                    ThemeMode.LIGHT -> Icons.Filled.LightMode
                    ThemeMode.DARK -> Icons.Filled.DarkMode
                },
                "切换主题", c.sub, onToggleTheme,
            )
            Spacer(Modifier.height(8.dp))
            RailSquare(Icons.Filled.Settings, "设置", c.sub, onSettings)
        }
        // 右边界线
        Box(
            Modifier.fillMaxHeight().width(1.dp).background(c.line)
                .align(Alignment.CenterEnd)
        )
    }
}

@Composable
private fun RailSquare(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier.width(56.dp).height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(23.dp))
    }
}
