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

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
class ThemePrefsTest {
    private val context = org.robolectric.RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("arda_mapper_appearance", android.content.Context.MODE_PRIVATE)

    @Test
    fun systemResolvesByPhoneSettingAndOtherChoicesIgnoreIt() {
        assertEquals(ThemeMode.DARK, resolveThemeMode(ThemeMode.SEPIA, followSystem = true, systemDark = true))
        assertEquals(ThemeMode.LIGHT, resolveThemeMode(ThemeMode.SEPIA, followSystem = true, systemDark = false))
        assertEquals(ThemeMode.SEPIA, resolveThemeMode(ThemeMode.SEPIA, followSystem = false, systemDark = true))
    }

    @Test
    fun oledOnlyAppliesToDark() {
        assertEquals(true, isOledActive(ThemeMode.DARK, true))
        assertEquals(false, isOledActive(ThemeMode.LIGHT, true))
        assertEquals(false, isOledActive(ThemeMode.DARK, false))
        assertEquals(androidx.compose.ui.graphics.Color.Black, groundFor(ThemeMode.DARK, oled = true).stops.first().color)
        assertNotEquals(groundFor(ThemeMode.DARK).stops.first().color, groundFor(ThemeMode.DARK, oled = true).stops.first().color)
    }

    @Test
    fun systemIsTheDefaultOnlyForAFreshInstallWithNoStoredChoice() {
        assertEquals(true, defaultFollowSystem(hasStoredChoice = false, freshInstall = true))
        assertEquals(false, defaultFollowSystem(hasStoredChoice = true, freshInstall = true))
        assertEquals(false, defaultFollowSystem(hasStoredChoice = false, freshInstall = false))
    }

    @Test
    fun existingChoiceSurvivesAndIsNotSwitchedToSystem() {
        prefs.edit().clear().putString("theme_mode", "SEPIA").commit()
        val loaded = storedThemePrefs(context)
        assertEquals(ThemePrefs(ThemeMode.SEPIA, followSystem = false, oled = false), loaded)
    }

    @Test
    fun legacyDarkFlagStillMapsAndRoundTripsSavedChoices() {
        prefs.edit().clear().putBoolean("dark_theme", false).commit()
        assertEquals(ThemeMode.LIGHT, storedThemePrefs(context).mode)

        saveThemeSystem(context)
        saveOled(context, true)
        assertEquals(true, storedThemePrefs(context).followSystem)
        assertEquals(true, storedThemePrefs(context).oled)

        saveThemeMode(context, ThemeMode.DARK)
        assertEquals(ThemePrefs(ThemeMode.DARK, followSystem = false, oled = true), storedThemePrefs(context))
    }
}
