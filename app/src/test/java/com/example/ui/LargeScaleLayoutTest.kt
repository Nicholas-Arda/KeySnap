package com.example.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.ParagraphIntrinsics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.data.KeyMappingConfig
import com.example.data.ScriptAction
import com.example.data.ScriptTrigger
import com.example.data.ShortcutScript
import com.example.data.TriggerPressType
import com.example.data.whatsNewReleases
import com.example.ui.components.WhatsNewFeatureCard
import com.example.ui.components.WhatsNewFix
import com.example.ui.components.WhatsNewDialog
import com.example.data.SuggestedShortcuts
import com.example.ui.home.PermissionGaps
import com.example.ui.home.ProUpsellCard
import com.example.ui.home.SetupCard
import com.example.ui.home.ShortcutRow
import com.example.ui.home.SuggestedShortcutsPanel
import com.example.ui.scripts.ScriptCard
import com.example.ui.settings.SettingsGear
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsScrim
import com.example.ui.settings.SettingsSheet
import com.example.ui.settings.SheetTopGap
import com.example.ui.settings.rememberSettingsMotion
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.groundFor
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.nicholasarda.keysnap.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w290dp-h760dp-200dpi")
class LargeScaleLayoutTest(private val language: String, private val fontScale: Float) {
    companion object {
        @JvmStatic
        @Parameters(name = "{0}, font={1}, width=290, density=1.25")
        fun cases(): List<Array<Any>> = listOf("en", "tr", "de", "es", "fr", "it", "pt-BR", "ru", "ar", "hi")
            .flatMap { language -> listOf(1.3f, 2f).map { arrayOf(language, it) } }
    }

    @get:Rule val rule = createComposeRule()
    private val originalLocale = Locale.getDefault()

    @Before fun useTextLocale() {
        val qualifier = if (language == "pt-BR") "pt-rBR" else language
        RuntimeEnvironment.setQualifiers("$qualifier-w290dp-h760dp-200dpi")
        Locale.setDefault(Locale.forLanguageTag(language))
        RuntimeEnvironment.setFontScale(fontScale)
    }
    @After fun restoreTextLocale() { Locale.setDefault(originalLocale) }

    private fun content(body: @Composable BoxScope.() -> Unit) {
        rule.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                setLocale(Locale.forLanguageTag(language))
                this.fontScale = this@LargeScaleLayoutTest.fontScale
            }
            val context = LocalContext.current.createConfigurationContext(configuration)
            // The configuration context is no longer the activity, so the setup card's permission
            // launcher would not find a registry through it.
            val registryOwner = LocalActivityResultRegistryOwner.current!!
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides registryOwner,
                LocalContext provides context,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(1.25f, fontScale),
                LocalLayoutDirection provides if (language == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                MyApplicationTheme(themeMode = ThemeMode.DARK) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                        Box(
                            Modifier.width(290.dp).height(760.dp)
                                .background(MaterialTheme.colorScheme.background).testTag("review"),
                        ) { body() }
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun capture(name: String) {
        rule.onNodeWithTag("review").captureRoboImage(
            "build/outputs/largescale-review/$name-$language-$fontScale.png",
        )
    }

    private fun layouts(): List<TextLayoutResult> {
        val results = mutableListOf<TextLayoutResult>()
        rule.onAllNodes(hasText("", substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes().forEach { node ->
                node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            }
        assertTrue("text was measured", results.isNotEmpty())
        return results
    }

    private fun assertWholeWords(layout: TextLayoutResult) {
        assertEquals(fontScale, layout.layoutInput.density.fontScale, 0f)
        // didOverflowWidth compares rounded layout width with unrounded intrinsic width, which
        // flags even a short single line on this renderer. Check the actual glyph span instead.
        assertTrue("text height fits: ${layout.layoutInput.text}", layout.multiParagraph.height <= layout.size.height + 1f)
        for (line in 0 until layout.lineCount) {
            assertFalse("text must not ellipsize: ${layout.layoutInput.text}", layout.isLineEllipsized(line))
            assertTrue("glyphs fit: ${layout.layoutInput.text}", layout.getLineRight(line) - layout.getLineLeft(line) <= layout.size.width + 1f)
        }
        val text = layout.layoutInput.text.text
        for (line in 0 until layout.lineCount - 1) {
            val end = layout.getLineEnd(line)
            if (end == 0 || end == text.length || text[end - 1].isWhitespace() || text[end].isWhitespace()) continue
            // A word wider than the whole column has to break somewhere; on a device Hyphens.Auto
            // marks it with a hyphen (this renderer has no patterns for some languages).
            var wordStart = end
            while (wordStart > 0 && !text[wordStart - 1].isWhitespace()) wordStart--
            var wordEnd = end
            while (wordEnd < text.length && !text[wordEnd].isWhitespace()) wordEnd++
            val wordWidth = ParagraphIntrinsics(
                text = text.substring(wordStart, wordEnd),
                style = layout.layoutInput.style,
                density = layout.layoutInput.density,
                fontFamilyResolver = layout.layoutInput.fontFamilyResolver,
            ).maxIntrinsicWidth
            val longerThanTheColumn = wordWidth > layout.size.width &&
                layout.layoutInput.style.hyphens == Hyphens.Auto
            assertTrue(
                "mid-word break in '$text' at $end (word ${wordWidth.toInt()}px, column ${layout.size.width}px)",
                text[end - 1] in "-‐‑–­​/" || longerThanTheColumn || hasRenderedHyphen(layout, line),
            )
        }
    }

    private fun hasRenderedHyphen(result: TextLayoutResult, line: Int): Boolean {
        // Compose omits inserted hyphens from TextLayoutResult.text. Inspect Android's actual
        // line-end hyphen edit so an arbitrary emergency word break cannot pass as hyphenation.
        fun field(owner: Any, name: String): Any = owner.javaClass.getDeclaredField(name).run {
            isAccessible = true
            get(owner)!!
        }
        val infos = field(result.multiParagraph, "paragraphInfoList") as List<*>
        var localLine = line
        for (info in infos) {
            val paragraph = field(info!!, "paragraph") as androidx.compose.ui.text.Paragraph
            if (localLine < paragraph.lineCount) {
                val nativeLayout = field(field(paragraph, "layout"), "layout")
                val edit = nativeLayout.javaClass.getMethod("getEndHyphenEdit", Int::class.javaPrimitiveType)
                    .invoke(nativeLayout, localLine) as Int
                return edit != 0
            }
            localLine -= paragraph.lineCount
        }
        return false
    }

    @Test fun settingsCloseClearsInteractiveRowsAtEveryScrollPosition() {
        content {
            val motion = rememberSettingsMotion(true)
            SettingsSheet(motion, Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 80.dp)) {
                SettingsScreen(KeyMappingConfig(), {}, {}, themeMode = ThemeMode.DARK)
            }
            SettingsGear(motion, true, {}, Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 14.dp))
        }
        val titleLayouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithTag("settings_title", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(titleLayouts) }
        capture("settings-title")
        titleLayouts.forEach(::assertWholeWords)
        for (index in listOf(2, 3, 4, 6, 7, 9, 10, 11)) {
            rule.onNodeWithTag("settings_list").performScrollToIndex(index)
            val close = rule.onNodeWithTag("settings_gear").fetchSemanticsNode().boundsInRoot
            val list = rule.onNodeWithTag("settings_list").fetchSemanticsNode().boundsInRoot
            assertTrue("scroll viewport is below X", list.top >= close.bottom)
            rule.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes()
                .filter { it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag) != "settings_gear" }
                .forEach { assertFalse("X overlaps interactive content at item $index", close.overlaps(it.boundsInRoot)) }
            if (index == 9) capture("settings-appearance")
            if (index == 10) {
                val languageLayouts = mutableListOf<TextLayoutResult>()
                rule.onNodeWithTag("settings_language_label", useUnmergedTree = true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(languageLayouts) }
                languageLayouts.forEach(::assertWholeWords)
                capture("settings-language")
            }
            if (index == 11) {
                capture("settings-about")
                for (tag in listOf("about_app_button_label", "privacy_policy_button_label", "erase_all_data_button_label")) {
                    val aboutLayouts = mutableListOf<TextLayoutResult>()
                    rule.onNodeWithTag(tag, useUnmergedTree = true)
                        .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(aboutLayouts) }
                    aboutLayouts.forEach(::assertWholeWords)
                }
            }
        }
    }

    @Test fun proCardKeepsWholeWordsAndFullPurchaseLabel() {
        content {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                ProUpsellCard(activeCount = 1, limit = 3, proPrice = null, onOpenProScreen = {})
            }
        }
        capture("pro")
        layouts().forEach(::assertWholeWords)
    }

    @Test fun cappedProCardKeepsThePlayPriceAndWholeWords() {
        content {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                ProUpsellCard(activeCount = 3, limit = 3, proPrice = "€ 12,99", onOpenProScreen = {})
            }
        }
        capture("pro-price")
        layouts().forEach(::assertWholeWords)
    }

    @Test fun whatsNewBodiesUseFullWidthAndParagraphHyphenation() {
        content {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                val release = whatsNewReleases.getValue(13L)
                release.features.forEach { WhatsNewFeatureCard(it) {} }
                release.fixes.forEach { WhatsNewFix(it, compact = true) }
            }
        }
        capture("whats-new")
        layouts().forEach { layout ->
            assertEquals(fontScale, layout.layoutInput.density.fontScale, 0f)
            assertEquals(Hyphens.Auto, layout.layoutInput.style.hyphens)
            assertEquals(LineBreak.Paragraph, layout.layoutInput.style.lineBreak)
            assertWholeWords(layout)
        }
        capture("whats-new")
        rule.onNodeWithTag("whats_new_fix_${whatsNewReleases.getValue(13L).fixes.last()}").performScrollTo()
        capture("whats-new-fixes")
    }

    @Test fun whatsNewDialogKeepsItsDismissButtonBelowScrollableContent() {
        content { WhatsNewDialog(whatsNewReleases.getValue(13L), ThemeMode.DARK, {}, {}) }
        val resources = RuntimeEnvironment.getApplication().resources
        assertEquals(Locale.forLanguageTag(language).language, resources.configuration.locales[0].language)
        // Italian uses "Novità" for both the dialog title and its New section.
        assertTrue(rule.onAllNodes(hasText(resources.getString(R.string.whats_new_title)), useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty())
        val dialog = rule.onNodeWithTag("whats_new_dialog").fetchSemanticsNode().boundsInRoot
        val button = rule.onNodeWithTag("whats_new_got_it").fetchSemanticsNode().boundsInRoot
        assertTrue("dismiss button fits dialog", button.bottom <= dialog.bottom && button.top >= dialog.top)
        layouts().forEach {
            assertEquals("dialog retains display density", 1.25f, it.layoutInput.density.density, 0f)
            assertEquals("dialog retains font scale", fontScale, it.layoutInput.density.fontScale, 0f)
            assertWholeWords(it)
        }
        rule.onNodeWithTag("whats_new_dialog").captureRoboImage(
            "build/outputs/largescale-review/dialog-$language-$fontScale.png",
        )
        rule.onNodeWithTag("whats_new_fix_${whatsNewReleases.getValue(13L).fixes.last()}").performScrollTo()
        rule.onNodeWithTag("whats_new_dialog").captureRoboImage(
            "build/outputs/largescale-review/dialog-fixes-$language-$fontScale.png",
        )
    }

    @Test fun shortcutNamesAndSubtitlesAllowTwoLinesOnBothPages() {
        val script = ShortcutScript(
            id = "large", name = "Assistant shortcut example", actions = listOf(ScriptAction.ToggleFlashlight),
            triggers = listOf(ScriptTrigger(listOf(24, 25), TriggerPressType.LONG_PRESS)),
        )
        content {
            Column(Modifier.padding(16.dp)) {
                ScriptCard(script, true, false, { _, _ -> }, {}, {})
                ShortcutRow(script) {}
            }
        }
        listOf("script_name_large", "script_subtitle_large").forEach { tag ->
            val results = mutableListOf<TextLayoutResult>()
            rule.onNodeWithTag(tag, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            assertEquals(2, results.single().layoutInput.maxLines)
            assertTrue(results.single().lineCount in 1..2)
        }
        layouts().filter { it.layoutInput.text.text == script.name }.forEach {
            assertEquals(2, it.layoutInput.maxLines)
            assertEquals(2, it.lineCount)
        }
        capture("shortcuts")
    }

    private fun captureR2(name: String) {
        rule.onNodeWithTag("review").captureRoboImage(
            "build/outputs/largescale-review-r2/$name-$language-$fontScale.png",
        )
    }

    private fun layoutOf(tag: String): TextLayoutResult = layoutsOf(tag).first()

    private fun layoutsOf(tag: String): List<TextLayoutResult> {
        val results = mutableListOf<TextLayoutResult>()
        rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        }
        assertTrue("text measured for $tag", results.isNotEmpty())
        return results
    }

    private fun tagged(prefix: String) = rule.onAllNodes(
        SemanticsMatcher("tag starts with $prefix") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
        },
        useUnmergedTree = true,
    ).fetchSemanticsNodes().map { it.config[SemanticsProperties.TestTag] }

    @Test fun suggestionTitlesKeepHalfTheRowAndWholeWords() {
        content {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                SuggestedShortcutsPanel(isPro = false, scripts = emptyList(), onPick = {})
            }
        }
        captureR2("suggestions")
        val titles = tagged("suggestion_title_")
        assertTrue("suggestions rendered", titles.isNotEmpty())
        for (tag in titles) {
            val id = tag.removePrefix("suggestion_title_")
            val row = rule.onNodeWithTag("suggestion_$id", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val column = layoutOf(tag).layoutInput.constraints.maxWidth
            assertTrue("title of $id gets ${column}px of ${row.width}px", column >= row.width / 2)
            assertWholeWords(layoutOf(tag))
        }
        (tagged("suggestion_badge_") + tagged("recipe_advanced_") + "ad_slot_tag").forEach { layoutsOf(it).forEach(::assertWholeWords) }
        layoutsOf("ad_slot_email").forEach { email ->
            assertEquals("business@ardasenn.com", email.layoutInput.text.text.replace("​", ""))
            assertWholeWords(email)
        }
        rule.onAllNodesWithTag("ad_slot", useUnmergedTree = true).onFirst().performScrollTo()
        captureR2("suggestions-adslot")
        val automation = SuggestedShortcuts.ordered.first { it.automation }
        rule.onNodeWithTag("suggestion_${automation.id}").performScrollTo()
        captureR2("suggestions-automation")
    }

    @Test fun setupCardKeepsWholeWordsInEveryStep() {
        content {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                SetupCard(PermissionGaps(notifications = false, battery = false), isServiceActive = false, onOpenAccessibility = {})
                SetupCard(PermissionGaps(notifications = false, battery = false), isServiceActive = true, onOpenAccessibility = {})
                SetupCard(PermissionGaps(notifications = false, battery = true), isServiceActive = true, onOpenAccessibility = {})
            }
        }
        captureR2("setup")
        // The step markers' digits sit in a fixed 26 dp bead; they are not text the user reads as words.
        // The facsimile's one-line app label may ellipsize; that is a picture of a list row, not instructions.
        val appName = RuntimeEnvironment.getApplication().getString(R.string.app_name)
        layouts().filterNot { it.layoutInput.text.text.all(Char::isDigit) || it.layoutInput.text.text == appName }
            .forEach(::assertWholeWords)
        rule.onAllNodesWithTag("permission_alerts")[1].performScrollTo()
        captureR2("setup-step2")
        rule.onAllNodesWithTag("permission_alerts")[2].performScrollTo()
        captureR2("setup-step3")
    }

    @Test fun settingsPanelHidesThePageAboveIt() {
        var pageShown by mutableStateOf(true)
        content {
            if (pageShown) {
                Column(Modifier.fillMaxSize()) {
                    repeat(40) { Text("paused, so none of them run", style = MaterialTheme.typography.bodyLarge) }
                }
            }
            val motion = rememberSettingsMotion(true)
            SettingsScrim(motion, true, {}, ground = groundFor(ThemeMode.DARK).stops.first().color)
            SettingsSheet(motion, Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = SheetTopGap, bottom = 80.dp)) {
                SettingsScreen(KeyMappingConfig(), {}, {}, themeMode = ThemeMode.DARK)
            }
        }
        val panelTop = rule.onNodeWithTag("settings_panel").fetchSemanticsNode().boundsInRoot.top.toInt()
        assertTrue("panel sits below a strip", panelTop > 0)
        // captureToImage never settles here, so read the PNGs Roborazzi writes anyway.
        fun strip(name: String): IntArray {
            captureR2(name)
            val image = ImageIO.read(File("build/outputs/largescale-review-r2/$name-$language-$fontScale.png"))
            return image.getRGB(0, 0, image.width, panelTop, null, 0, image.width)
        }
        val withPage = strip("settings-top")
        pageShown = false
        rule.waitForIdle()
        assertTrue("page text shows above the Settings panel", withPage.contentEquals(strip("settings-top-empty")))
    }
}
