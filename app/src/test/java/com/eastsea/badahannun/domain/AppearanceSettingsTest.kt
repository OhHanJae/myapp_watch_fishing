package com.eastsea.badahannun.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AppearanceSettingsTest {
    @Test fun tileCanFollowAppThemeOrOverrideIt() {
        assertEquals(AppColorMode.DARK,
            AppearanceSettings.resolveTileTheme(TileColorMode.FOLLOW_APP, AppColorMode.DARK))
        assertEquals(AppColorMode.LIGHT,
            AppearanceSettings.resolveTileTheme(TileColorMode.FOLLOW_APP, AppColorMode.LIGHT))
        assertEquals(AppColorMode.LIGHT,
            AppearanceSettings.resolveTileTheme(TileColorMode.LIGHT, AppColorMode.DARK))
        assertEquals(AppColorMode.DARK,
            AppearanceSettings.resolveTileTheme(TileColorMode.DARK, AppColorMode.LIGHT))
    }

    @Test fun unknownPreferencesFallBackSafely() {
        assertEquals(AppColorMode.DARK, AppColorMode.fromKey(null))
        assertEquals(AppColorMode.DARK, AppColorMode.fromKey("unknown"))
        assertEquals(TileColorMode.FOLLOW_APP, TileColorMode.fromKey(null))
        assertEquals(TileColorMode.FOLLOW_APP, TileColorMode.fromKey("unknown"))
    }
}
