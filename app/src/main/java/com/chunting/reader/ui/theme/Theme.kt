package com.chunting.reader.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** 主题模式：跟随系统 / 明亮 / 夜晚 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** 排列方式 */
enum class ShelfLayout { LIST, GRID }

data class AppColors(
    val isDark: Boolean,
    val bg: Color,
    val card: Color,
    val text: Color,
    val sub: Color,
    val line: Color,
    val accent: Color,
    val coverBg: List<Color>,
    val coverFg: List<Color>,
    val pattern: Color,
)

val LocalAppColors = staticCompositionLocalOf {
    AppColors(
        isDark = false, bg = LightBg, card = LightCard, text = LightText, sub = LightSub,
        line = LightLine, accent = LightAccent,
        coverBg = CoverPalette.light.map { it.first },
        coverFg = CoverPalette.light.map { it.second },
        pattern = Color(0x241A1712),
    )
}

@Composable
fun TingReaderTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accentIndex: Int = 0,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val pair = AccentPalette[accentIndex.coerceIn(0, AccentPalette.lastIndex)]
    val accent = if (dark) pair.dark else pair.light
    val colors = if (dark) {
        AppColors(
            isDark = true, bg = DarkBg, card = DarkCard, text = DarkText, sub = DarkSub,
            line = DarkLine, accent = accent,
            coverBg = CoverPalette.dark.map { it.first },
            coverFg = CoverPalette.dark.map { it.second },
            pattern = Color(0x4DE8B96A),
        )
    } else {
        AppColors(
            isDark = false, bg = LightBg, card = LightCard, text = LightText, sub = LightSub,
            line = LightLine, accent = accent,
            coverBg = CoverPalette.light.map { it.first },
            coverFg = CoverPalette.light.map { it.second },
            pattern = Color(0x241A1712),
        )
    }

    val scheme = if (dark) {
        darkColorScheme(primary = accent, background = DarkBg, surface = DarkCard,
            onBackground = DarkText, onSurface = DarkText)
    } else {
        lightColorScheme(primary = accent, background = LightBg, surface = LightCard,
            onBackground = LightText, onSurface = LightText)
    }

    // 状态栏／导航栏图标明暗跟随主题，避免暗色下黑图标压在深底上
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity() ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(activity.window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
