package com.example.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ThemeModeTest {
    @Test
    fun themeModesCycleThroughAllAvailableModes() {
        assertEquals(ThemeMode.DARK, ThemeMode.LIGHT.next())
        assertEquals(ThemeMode.SEPIA, ThemeMode.DARK.next())
        assertEquals(ThemeMode.LIGHT, ThemeMode.SEPIA.next())
    }

    @Test
    fun themeModesUseDistinctBackgrounds() {
        val backgrounds = ThemeMode.entries.map(::backgroundFor)

        assertEquals(ThemeMode.entries.size, backgrounds.distinct().size)
        assertNotEquals(backgroundFor(ThemeMode.DARK), backgroundFor(ThemeMode.LIGHT))
    }
}
