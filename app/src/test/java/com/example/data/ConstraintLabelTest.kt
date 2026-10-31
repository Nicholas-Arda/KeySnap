package com.example.data

import io.github.nicholasarda.keysnap.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class ConstraintLabelTest {
    @Test fun everyAvailableTypeExceptModeSupportsNegation() {
        ScriptConstraintCatalog.all.forEach {
            assertEquals(it.type,ScriptConstraintCatalog.isAvailable(it.type) && it.type != "mode_is",ScriptConstraintCatalog.supportsNegation(it.type))
        }
        assertFalse(ScriptConstraintCatalog.supportsNegation("future_type"))
    }

    @Test fun prefixIsLocalizedAndOldLabelsAreUnchanged() {
        val resources = RuntimeEnvironment.getApplication().resources
        val title = resources.getString(R.string.constraint_screen_on)
        assertEquals(title,resources.constraintLabel(ScriptConstraint("screen_on")))
        assertEquals("Not: $title",resources.constraintLabel(ScriptConstraint("screen_on",mapOf("negate" to "true"))))
        for (language in listOf("tr","de","es","fr","it","nl","ru","ar","hi","pt-BR")) {
            val configuration = android.content.res.Configuration(resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
            val localized = RuntimeEnvironment.getApplication().createConfigurationContext(configuration).resources
            val prefix = localized.getString(R.string.constraint_not_prefix)
            assertFalse(language,prefix == "Not")
            if (language == "tr") assertEquals("De\u011fil",prefix)
            assertEquals("$prefix: ${localized.getString(R.string.constraint_screen_on)}",localized.constraintLabel(ScriptConstraint("screen_on",mapOf("negate" to "true"))))
        }
    }
}
