package com.eastsea.badahannun.domain

import android.content.Context

/** Independent app appearance and tile override, stored across app restarts. */
enum class AppColorMode(val key: String, val label: String) {
    DARK("dark", "다크"),
    LIGHT("light", "화이트");

    companion object {
        fun fromKey(value: String?): AppColorMode =
            entries.firstOrNull { it.key == value } ?: DARK
    }
}

enum class TileColorMode(val key: String, val label: String) {
    FOLLOW_APP("follow_app", "앱 설정에 따름"),
    LIGHT("light", "화이트"),
    DARK("dark", "다크");

    companion object {
        fun fromKey(value: String?): TileColorMode =
            entries.firstOrNull { it.key == value } ?: FOLLOW_APP
    }
}

object AppearanceSettings {
    private const val PREF_NAME = "appearance"
    private const val APP_THEME = "app_theme"
    private const val TILE_THEME = "tile_theme"

    /** 'Follow app' resolves from the saved APP theme, not the system's night setting. */
    fun resolveTileTheme(tile: TileColorMode, app: AppColorMode): AppColorMode = when (tile) {
        TileColorMode.FOLLOW_APP -> app
        TileColorMode.LIGHT -> AppColorMode.LIGHT
        TileColorMode.DARK -> AppColorMode.DARK
    }

    fun readApp(context: Context): AppColorMode = AppColorMode.fromKey(
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(APP_THEME, null)
    )

    fun readTile(context: Context): TileColorMode = TileColorMode.fromKey(
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(TILE_THEME, null)
    )

    fun saveApp(context: Context, mode: AppColorMode) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(APP_THEME, mode.key).apply()
    }

    fun saveTile(context: Context, mode: TileColorMode) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(TILE_THEME, mode.key).apply()
    }
}
