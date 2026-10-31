package com.example.data

import android.content.Context
import android.content.res.Configuration
import android.app.LocaleManager
import androidx.test.core.app.ApplicationProvider
import io.github.nicholasarda.keysnap.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * The catalogs hand out string ids rather than text, so a missing or empty translation shows up as
 * a blank row in a picker rather than as a build failure. This walks every id the catalogs and the
 * editor forms can produce, in every language the app ships, and fails on the blank.
 *
 * [AppLocale.options] is the list under test: adding a language there without adding its
 * `values-<tag>` folder is exactly the mistake this catches.
 */
@RunWith(RobolectricTestRunner::class)
class LocalizationTest {

    private val base: Context = ApplicationProvider.getApplicationContext()

    private val shippedLocales = AppLocale.options.map { it.first }.filter { it.isNotEmpty() }

    private fun contextFor(tag: String): Context {
        val config = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        return base.createConfigurationContext(config)
    }

    @Test fun everyShippedLanguageTranslatesTheProComparisonCard() {
        val labels = listOf(
            R.string.pro_card_pay_once,
            R.string.pro_table_what_you_get,
            R.string.pro_table_modes,
            R.string.pro_table_future,
            R.string.pro_table_everything_else,
            R.string.pro_card_one_time,
        )
        val english = contextFor("en").resources
        shippedLocales.forEach { tag ->
            val res = contextFor(tag).resources
            labels.forEach { label ->
                assertTrue("[$tag] ${res.getResourceEntryName(label)} is blank", res.getString(label).isNotBlank())
                if (tag != "en") {
                    assertTrue("[$tag] ${res.getResourceEntryName(label)} falls back to English", res.getString(label) != english.getString(label))
                }
            }
        }
    }

    @Test fun dutchSystemVariantsResolveDutchInsteadOfEnglishFallback() {
        listOf("nl", "nl-NL", "nl-BE").forEach { tag ->
            val res = contextFor(tag).resources
            assertEquals("[$tag] settings", "Instellingen", res.getString(R.string.settings_title))
            assertEquals("[$tag] action", "Zaklamp aan/uit", res.getString(R.string.action_toggle_flashlight))
            assertEquals("[$tag] setup", "Geavanceerde modus inschakelen", res.getString(R.string.wizard_title))
            assertEquals("[$tag] condition", "Melding ontvangen", res.getString(R.string.constraint_notification_received))
            assertEquals("[$tag] guide", "Open Instellingen", res.getStringArray(R.array.guide_pixel_steps).first())
        }
    }

    @Test fun dutchDynamicMessagesAndPluralFormsFormatCorrectly() {
        val res = contextFor("nl").resources
        assertEquals("2 van 3 acties uitgevoerd", res.getString(R.string.run_partial, 2, 3))
        assertEquals("1 actie", res.getQuantityString(R.plurals.scripts_action_count, 1, 1))
        assertEquals("2 acties", res.getQuantityString(R.plurals.scripts_action_count, 2, 2))
        assertEquals("Alle 2 acties uitgevoerd", res.getQuantityString(R.plurals.run_all, 2, 2))
    }

    @Test
    @Config(sdk = [30])
    fun dutchSelectionSurvivesContextRecreationOnAndroid11() {
        val previous = AppLocale.stored(base)
        try {
            AppLocale.set(base, "nl")
            assertEquals("nl", AppLocale.stored(base))
            assertEquals("Instellingen", AppLocale.wrap(base).getString(R.string.settings_title))
        } finally {
            AppLocale.set(base, previous)
        }
    }

    @Test
    @Config(sdk = [34])
    fun dutchSelectionMirrorsToAndroidAppLanguages() {
        val previous = AppLocale.stored(base)
        try {
            AppLocale.set(base, "nl")
            assertEquals("nl", base.getSystemService(LocaleManager::class.java).applicationLocales[0].language)
            assertEquals("Instellingen", AppLocale.wrap(base).getString(R.string.settings_title))
        } finally {
            AppLocale.set(base, previous)
        }
    }

    @Test fun everyShippedLanguageNamesEveryActionAndConstraint() {
        shippedLocales.forEach { tag ->
            val res = contextFor(tag).resources
            ScriptActionCatalog.all.forEach { descriptor ->
                assertTrue("[$tag] ${descriptor.type} has a blank title", res.getString(descriptor.titleRes).isNotBlank())
                descriptor.parameters.forEach { param ->
                    assertTrue(
                        "[$tag] ${descriptor.type}.${param.key} has a blank label",
                        res.getString(param.labelRes).isNotBlank(),
                    )
                }
            }
            ActionCategory.entries.forEach { assertTrue("[$tag] $it", res.getString(it.titleRes).isNotBlank()) }
            ScriptConstraintCatalog.all.forEach {
                assertTrue("[$tag] ${it.type} has a blank title", res.getString(it.titleRes).isNotBlank())
            }
            ConstraintCategory.entries.forEach { assertTrue("[$tag] $it", res.getString(it.titleRes).isNotBlank()) }
            TriggerPressType.entries.forEach {
                assertTrue("[$tag] $it title", res.getString(it.titleRes).isNotBlank())
                assertTrue("[$tag] $it badge", res.getString(it.shortBadgeRes).isNotBlank())
                assertTrue("[$tag] $it description", res.getString(it.descriptionRes).isNotBlank())
            }
        }
    }

    @Test fun everyShippedLanguageExplainsEveryRunFailure() {
        shippedLocales.forEach { tag ->
            val res = contextFor(tag).resources
            RunFailure.entries.forEach { assertTrue("[$tag] $it", res.getString(it.labelRes).isNotBlank()) }
        }
    }

    @Test fun everyShippedLanguageLabelsEveryEditorField() {
        shippedLocales.forEach { tag ->
            val res = contextFor(tag).resources
            val actionForms = ScriptActionCatalog.all.mapNotNull { ActionEditorForms.forType(it.type) }
            val constraintForms = ScriptConstraintCatalog.all.mapNotNull { ConstraintEditorForms.form(it.type) }
            actionForms.forEach { form ->
                form.helperRes?.let { assertTrue("[$tag] ${form.type} helper", res.getString(it).isNotBlank()) }
                form.requirementRes?.let { assertTrue("[$tag] ${form.type} badge", res.getString(it).isNotBlank()) }
            }
            constraintForms.forEach { form ->
                form.helperRes?.let { assertTrue("[$tag] ${form.type} helper", res.getString(it).isNotBlank()) }
            }
            (actionForms.map { it.type to it.fields } + constraintForms.map { it.type to it.fields }).forEach { (type, fields) ->
                fields.forEach { field ->
                    val labels = when (field) {
                        is EditorField.Text -> listOfNotNull(field.labelRes, field.hintRes, field.helperRes)
                        is EditorField.Number -> listOf(field.labelRes)
                        is EditorField.Choice -> listOf(field.labelRes) + field.options.map { it.second }
                        is EditorField.KeyCodePick -> listOf(field.labelRes)
                        is EditorField.AppPick -> listOf(field.labelRes)
                        is EditorField.Point -> listOf(field.labelRes)
                        is EditorField.TwoPoints -> listOf(field.labelARes, field.labelBRes)
                        is EditorField.BluetoothDevicePick -> listOf(field.labelRes)
                        is EditorField.TimeRange -> listOf(field.startLabelRes, field.endLabelRes)
                        is EditorField.DaySet -> listOf(field.labelRes)
                        is EditorField.Time -> listOf(field.labelRes)
                        is EditorField.Interval -> listOf(field.labelRes)
                        is EditorField.ImagePick -> listOf(field.labelRes)
                        is EditorField.ContactPick -> listOf(field.labelRes)
                        is EditorField.Switch -> listOf(field.labelRes)
                    }
                    labels.forEach { assertTrue("[$tag] $type field label", res.getString(it).isNotBlank()) }
                }
            }
        }
    }

    /** Each guide's steps must survive translation too — a short list is a dropped step. */
    @Test fun everyShippedLanguageKeepsEveryDeveloperOptionsStep() {
        val expected = DeveloperOptionsGuides.all.associate {
            it.id to base.resources.getStringArray(it.stepsRes).size
        }
        shippedLocales.forEach { tag ->
            val res = contextFor(tag).resources
            DeveloperOptionsGuides.all.forEach { guide ->
                val steps = res.getStringArray(guide.stepsRes)
                assertEquals("[$tag] ${guide.id} step count", expected.getValue(guide.id), steps.size)
                steps.forEach { assertTrue("[$tag] ${guide.id} has a blank step", it.isNotBlank()) }
            }
        }
    }

    /**
     * CLAUDE.md: a ready-made command may name the service it targets, but no phone-automation or
     * key-mapping app is ever named in a recipe, in any language or payload.
     */
    @Test fun noRecipeNamesAnotherAutomationOrKeyMappingApp() {
        val banned = Regex("""(?i)\b(macro ?droid|tasker|automate|automagic|key ?mapper|button ?mapper|ifttt)\b""")
        (shippedLocales + "en").distinct().forEach { tag ->
            val res = contextFor(tag).resources
            val texts = RecipeCategory.entries.map { it.titleRes } +
                SuggestedShortcuts.all.flatMap { listOfNotNull(it.titleRes, it.subtitleRes, it.noteRes) }
            texts.map(res::getString).forEach { text ->
                assertTrue("[$tag] blank recipe string", text.isNotBlank())
                assertTrue("[$tag] names another app: $text", !banned.containsMatchIn(text))
            }
        }
        SuggestedShortcuts.all.flatMap { it.actions }.flatMap { it.params.values }.forEach {
            assertTrue("payload names another app: $it", !banned.containsMatchIn(it))
        }
    }

    @Test fun localeConfigListsEveryLanguageThePickerOffers() {
        // The picker and res/xml/locales_config.xml have to agree, or Android 13's per-app
        // language screen offers a different set than the in-app one.
        val declared = base.resources.getXml(R.xml.locales_config).let { parser ->
            buildList {
                while (parser.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "locale") {
                        add(parser.getAttributeValue("http://schemas.android.com/apk/res/android", "name"))
                    }
                }
            }
        }
        assertEquals(shippedLocales.sorted(), declared.sorted())
    }
}
