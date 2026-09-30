package com.example.data

import io.github.nicholasarda.keysnap.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ActionEditorFormTest {

    private val res = RuntimeEnvironment.getApplication().resources


    private val parameterizedAvailable = ScriptActionCatalog.all
        .filter { it.availability == ActionAvailability.AVAILABLE && it.parameters.isNotEmpty() }

    @Test fun everyAvailableActionWithParametersHasAForm() {
        val missing = parameterizedAvailable.filterNot { ActionEditorForms.isConfigurable(it.type) }
        assertEquals("Actions with params but no editor form: ${missing.map { it.type }}", emptyList<ActionDescriptor>(), missing)
    }

    @Test fun everyAvailableActionWithoutParametersHasNoForm() {
        // The one sanctioned exception is a field-less form carrying a requirement badge
        // (cycle_ringer_mode, the rotation toggles): it exists purely to surface a permission
        // that has no other in-app discovery path.
        val stray = ScriptActionCatalog.all
            .filter { it.availability == ActionAvailability.AVAILABLE && it.parameters.isEmpty() }
            .filter { ActionEditorForms.forType(it.type)?.let { form -> form.fields.isNotEmpty() || form.requirementRes == null } == true }
        assertEquals("Forms for actions that take no params: ${stray.map { it.type }}", emptyList<ActionDescriptor>(), stray)
    }

    @Test fun everyFormFieldWritesAParamItsDescriptorDeclares() {
        parameterizedAvailable.forEach { descriptor ->
            val declared = descriptor.parameters.map { it.key }.toSet()
            val written = ActionEditorForms.forType(descriptor.type)!!.fields.flatMap { it.keys }
            assertEquals(
                "${descriptor.type} form writes params its descriptor does not declare",
                emptySet<String>(),
                written.toSet() - declared,
            )
        }
    }

    @Test fun everyRequiredParamIsCoveredBySomeField() {
        parameterizedAvailable.forEach { descriptor ->
            val required = descriptor.parameters.filter { it.required }.map { it.key }.toSet()
            val written = ActionEditorForms.forType(descriptor.type)!!.fields.flatMap { it.keys }.toSet()
            assertEquals(
                "${descriptor.type} has required params no field collects",
                emptySet<String>(),
                required - written,
            )
        }
    }

    @Test fun aTwoPointFieldCountsAsFourParamKeys() {
        val field = EditorField.TwoPoints("x1", "y1", R.string.field_swipe_from, "x2", "y2", R.string.field_swipe_to)
        assertEquals(listOf("x1", "y1", "x2", "y2"), field.keys)
    }

    @Test fun thereIsNoFormForAnUnknownActionType() {
        assertNull(ActionEditorForms.forType("not_a_real_action"))
        assertFalse(ActionEditorForms.isConfigurable("not_a_real_action"))
    }

    @Test fun defaultsSeedNumberFieldsAndTheFirstChoice() {
        val defaults = ActionEditorForms.defaults(ActionEditorForms.forType("tap_screen")!!)
        assertEquals("1", defaults["count"])
        assertEquals("100", defaults["interval"])
        // A point has no sensible default; the user must pick or type it.
        assertFalse(defaults.containsKey("x"))
    }

    @Test fun httpRequestDefaultsToGetAndHidesTheBodyUntilPostOrPut() {
        val form = ActionEditorForms.forType("http_request")!!
        val defaults = ActionEditorForms.defaults(form)
        assertEquals("GET", defaults["method"])
        assertFalse(ActionEditorForms.visibleFields(form, defaults).flatMap { it.keys }.contains("body"))
        val posting = defaults + ("method" to "POST")
        assertTrue(ActionEditorForms.visibleFields(form, posting).flatMap { it.keys }.contains("body"))
    }

    @Test fun aFormIsIncompleteUntilEveryRequiredParamIsNonBlank() {
        val form = ActionEditorForms.forType("tap_screen")!!
        val defaults = ActionEditorForms.defaults(form)
        assertFalse(ActionEditorForms.isComplete(form, defaults))
        assertFalse(ActionEditorForms.isComplete(form, defaults + mapOf("x" to "100", "y" to "   ")))
        assertTrue(ActionEditorForms.isComplete(form, defaults + mapOf("x" to "100", "y" to "200")))
    }

    @Test fun completenessIgnoresAHiddenOptionalField() {
        val form = ActionEditorForms.forType("http_request")!!
        val getting = ActionEditorForms.defaults(form) + ("url" to "https://example.com")
        assertTrue(ActionEditorForms.isComplete(form, getting))
    }

    @Test fun missingRequiredNamesTheParamsAnActionStillNeeds() {
        assertEquals(listOf("x", "y"), ActionEditorForms.missingRequired(ScriptAction("tap_screen")))
        assertEquals(emptyList<String>(), ActionEditorForms.missingRequired(ScriptAction("tap_screen", mapOf("x" to "1", "y" to "2"))))
        // An action with no form can never be under-configured.
        assertEquals(emptyList<String>(), ActionEditorForms.missingRequired(ScriptAction("toggle_flashlight")))
    }

    @Test fun summariesReadAsTheChainRowsSecondLine() {
        assertEquals("540, 1180", ActionEditorForms.summarize(ScriptAction("tap_screen", mapOf("x" to "540", "y" to "1180")), res))
        assertEquals("540, 1180 · ×3", ActionEditorForms.summarize(ScriptAction("tap_screen", mapOf("x" to "540", "y" to "1180", "count" to "3")), res))
        assertEquals("GET · https://example.com", ActionEditorForms.summarize(ScriptAction("http_request", mapOf("method" to "GET", "url" to "https://example.com")), res))
        assertEquals("100, 200 → 300, 400", ActionEditorForms.summarize(ScriptAction("swipe_screen", mapOf("x1" to "100", "y1" to "200", "x2" to "300", "y2" to "400")), res))
        assertEquals("com.example.app", ActionEditorForms.summarize(ScriptAction("launch_app", mapOf("packageName" to "com.example.app")), res))
        assertNull(ActionEditorForms.summarize(ScriptAction("toggle_flashlight"), res))
    }

    @Test fun anAppPickFieldCanBeGatedOnAChoiceLikeATextField() {
        val form = ActionEditorForm(
            type = "gate_test_only",
            fields = listOf(
                EditorField.Choice("onTap", R.string.param_direction, listOf("none" to R.string.choice_pinch_in, "app" to R.string.choice_pinch_out)),
                EditorField.AppPick("packageName", R.string.param_app, visibleWhen = "onTap" to setOf("app")),
            ),
            summary = { _, _ -> "" },
        )
        assertFalse(ActionEditorForms.visibleFields(form, mapOf("onTap" to "none")).flatMap { it.keys }.contains("packageName"))
        assertTrue(ActionEditorForms.visibleFields(form, mapOf("onTap" to "app")).flatMap { it.keys }.contains("packageName"))
    }

    @Test fun showNotificationNeedsOnlyATitleUntilATapTargetIsChosen() {
        val form = ActionEditorForms.forType("show_notification")!!
        val defaults = ActionEditorForms.defaults(form)
        assertEquals("none", defaults["onTap"])
        assertFalse(ActionEditorForms.isComplete(form, defaults))
        assertTrue(ActionEditorForms.isComplete(form, defaults + ("title" to "Drink water")))
        assertFalse(ActionEditorForms.isComplete(form, defaults + mapOf("title" to "Hi", "onTap" to "url")))
        assertTrue(ActionEditorForms.isComplete(form, defaults + mapOf("title" to "Hi", "onTap" to "url", "url" to "https://example.com")))
        assertFalse(ActionEditorForms.isComplete(form, defaults + mapOf("title" to "Hi", "onTap" to "app")))
        assertTrue(ActionEditorForms.isComplete(form, defaults + mapOf("title" to "Hi", "onTap" to "app", "packageName" to "com.example.app")))
    }

    @Test fun showNotificationSummaryShowsTheLiteralUrl() {
        val url = "https://example.com/hook?token=abc"
        assertEquals("Hi · $url", ActionEditorForms.summarize(ScriptAction("show_notification", mapOf("title" to "Hi", "onTap" to "url", "url" to url)), res))
        // A URL left behind after switching the tap target back to Nothing is not shown.
        assertEquals("Hi", ActionEditorForms.summarize(ScriptAction("show_notification", mapOf("title" to "Hi", "onTap" to "none", "url" to "https://old.example")), res))
    }

    @Test fun setWallpaperIsIncompleteUntilAnImageIsPickedAndDefaultsToBothScreens() {
        val form = ActionEditorForms.forType("set_wallpaper")!!
        val defaults = ActionEditorForms.defaults(form)
        assertEquals("both", defaults["target"])
        assertFalse(ActionEditorForms.isComplete(form, defaults))
        assertTrue(ActionEditorForms.isComplete(form, defaults + ("imageUri" to "content://media/picker/0/1")))
        assertEquals("Lock screen", ActionEditorForms.summarize(ScriptAction("set_wallpaper", mapOf("imageUri" to "content://x/1", "target" to "lock")), res))
    }
}
