package com.chunting.reader.ui.theme

import androidx.compose.ui.graphics.Color

/* ================= UI 色板 ================= */

// 明亮
val LightBg = Color(0xFFFAF8F4)
val LightCard = Color(0xFFFFFFFF)
val LightText = Color(0xFF1A1712)
val LightSub = Color(0xFF8A8478)
val LightLine = Color(0x141A1712)
val LightAccent = Color(0xFFA87C2C)

// 夜晚
val DarkBg = Color(0xFF0E0D0B)
val DarkCard = Color(0xFF1A1917)
val DarkText = Color(0xFFF5F1E8)
val DarkSub = Color(0xFF8A8478)
val DarkLine = Color(0x1AF5F1E8)
val DarkAccent = Color(0xFFE8B96A)

/* ================= 强调色（6 选） ================= */

data class AccentPair(val light: Color, val dark: Color)

val AccentPalette: List<AccentPair> = listOf(
    AccentPair(Color(0xFFA87C2C), Color(0xFFE8B96A)), // 1 金
    AccentPair(Color(0xFF2E6B57), Color(0xFF5FC9A8)), // 2 青
    AccentPair(Color(0xFF2F4E7E), Color(0xFF7FA8E0)), // 3 蓝
    AccentPair(Color(0xFF73406A), Color(0xFFC08FBB)), // 4 紫
    AccentPair(Color(0xFF9A4B32), Color(0xFFE09579)), // 5 砖红
    AccentPair(Color(0xFF4A5340), Color(0xFFA9BE92)), // 6 苔绿
)

/**
 * 生成式封面色板。
 * 文件夹名 hash 后在 10 色中等概率选一；浅色主题用亮板（浅底深字），
 * 深色主题用暗板（深底金字），保证 40+ 文件夹也不花。
 */
object CoverPalette {
    // index -> (浅色底, 浅色字, 深色底, 深色字)
    val light: List<Pair<Color, Color>> = listOf(
        Color(0xFFDDE4F0) to Color(0xFF24314E),
        Color(0xFFF0DBE1) to Color(0xFF4A2532),
        Color(0xFFD8E8DF) to Color(0xFF1F3B2E),
        Color(0xFFF1E7D0) to Color(0xFF4A3A1F),
        Color(0xFFE3DFEC) to Color(0xFF332F45),
        Color(0xFFF0DFCB) to Color(0xFF452C1E),
        Color(0xFFD5E5E8) to Color(0xFF1F3A40),
        Color(0xFFF0DCE3) to Color(0xFF402433),
        Color(0xFFE3EAD3) to Color(0xFF2E3B22),
        Color(0xFFF1EBD2) to Color(0xFF453C24),
    )

    val dark: List<Pair<Color, Color>> = listOf(
        Color(0xFF1E2E52) to Color(0xFFE8D9B8),
        Color(0xFF43222F) to Color(0xFFE8D9B8),
        Color(0xFF1E3F31) to Color(0xFFE8D9B8),
        Color(0xFF4A3A22) to Color(0xFFE8D9B8),
        Color(0xFF2F2B40) to Color(0xFFE8D9B8),
        Color(0xFF432C1E) to Color(0xFFE8D9B8),
        Color(0xFF1F3B40) to Color(0xFFE8D9B8),
        Color(0xFF402433) to Color(0xFFE8D9B8),
        Color(0xFF2C3B22) to Color(0xFFE8D9B8),
        Color(0xFF453C24) to Color(0xFFE8D9B8),
    )

    const val PATTERN_COUNT = 5

    /** 文件夹名 -> 稳定的 (色板索引, 图案索引) */
    fun derive(name: String): Pair<Int, Int> {
        var h = 0
        for (c in name) h = h * 31 + c.code
        val ci = (h and 0x7fffffff) % light.size
        val pi = ((h ushr 8) and 0x7fffffff) % PATTERN_COUNT
        return ci to pi
    }
}
