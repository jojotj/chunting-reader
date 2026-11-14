package com.chunting.reader.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.chunting.reader.ui.theme.ShelfLayout
import com.chunting.reader.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val layout: ShelfLayout = ShelfLayout.GRID,
    val skipSeconds: Int = 30,
    val defaultSpeed: Float = 1.0f,
    val rememberSpeedPerFolder: Boolean = true,
    val autoNext: Boolean = true,
    val accentIndex: Int = 0,
    /** 开头跳过：开关 + 秒数（全局，对所有音频生效） */
    val headSkipEnabled: Boolean = false,
    val headSkipSeconds: Int = 30,
    /** 结尾跳过：开关 + 秒数（全局，对所有音频生效） */
    val tailSkipEnabled: Boolean = false,
    val tailSkipSeconds: Int = 30,
)

class SettingsStore(private val ctx: Context) {

    val flow: Flow<Settings> = ctx.dataStore.data.map { p ->
        Settings(
            themeMode = runCatching {
                ThemeMode.valueOf(p[KEY_THEME] ?: ThemeMode.SYSTEM.name)
            }.getOrDefault(ThemeMode.SYSTEM),
            layout = runCatching {
                ShelfLayout.valueOf(p[KEY_LAYOUT] ?: ShelfLayout.GRID.name)
            }.getOrDefault(ShelfLayout.GRID),
            skipSeconds = p[KEY_SKIP] ?: 30,
            defaultSpeed = p[KEY_SPEED] ?: 1.0f,
            rememberSpeedPerFolder = p[KEY_REMEMBER] ?: true,
            autoNext = p[KEY_AUTONEXT] ?: true,
            accentIndex = (p[KEY_ACCENT] ?: 0).coerceIn(0, 5),
            headSkipEnabled = p[KEY_HEAD_ON] ?: false,
            headSkipSeconds = (p[KEY_HEAD_SEC] ?: 30).coerceIn(0, 600),
            tailSkipEnabled = p[KEY_TAIL_ON] ?: false,
            tailSkipSeconds = (p[KEY_TAIL_SEC] ?: 30).coerceIn(0, 600),
        )
    }

    suspend fun setTheme(mode: ThemeMode) = put { it[KEY_THEME] = mode.name }
    suspend fun setLayout(l: ShelfLayout) = put { it[KEY_LAYOUT] = l.name }
    suspend fun setSkipSeconds(s: Int) = put { it[KEY_SKIP] = s.coerceIn(5, 300) }
    suspend fun setDefaultSpeed(v: Float) = put { it[KEY_SPEED] = v }
    suspend fun setRememberSpeed(b: Boolean) = put { it[KEY_REMEMBER] = b }
    suspend fun setAutoNext(b: Boolean) = put { it[KEY_AUTONEXT] = b }
    suspend fun setAccent(i: Int) = put { it[KEY_ACCENT] = i.coerceIn(0, 5) }
    suspend fun setHeadSkipEnabled(b: Boolean) = put { it[KEY_HEAD_ON] = b }
    suspend fun setHeadSkipSeconds(s: Int) = put { it[KEY_HEAD_SEC] = s.coerceIn(0, 600) }
    suspend fun setTailSkipEnabled(b: Boolean) = put { it[KEY_TAIL_ON] = b }
    suspend fun setTailSkipSeconds(s: Int) = put { it[KEY_TAIL_SEC] = s.coerceIn(0, 600) }

    private suspend fun put(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        ctx.dataStore.edit(block)
    }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_LAYOUT = stringPreferencesKey("layout")
        val KEY_SKIP = intPreferencesKey("skip_seconds")
        val KEY_SPEED = floatPreferencesKey("default_speed")
        val KEY_REMEMBER = booleanPreferencesKey("remember_speed")
        val KEY_AUTONEXT = booleanPreferencesKey("auto_next")
        val KEY_ACCENT = intPreferencesKey("accent_index")
        val KEY_HEAD_ON = booleanPreferencesKey("head_skip_on")
        val KEY_HEAD_SEC = intPreferencesKey("head_skip_seconds")
        val KEY_TAIL_ON = booleanPreferencesKey("tail_skip_on")
        val KEY_TAIL_SEC = intPreferencesKey("tail_skip_seconds")
    }
}
