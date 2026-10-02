package com.example.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Headphones
import com.example.ui.home.iconForScript
import com.example.ui.home.matches
import com.example.ui.scripts.actionIconFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestedShortcutsTest {

    @Test fun everyIdIsUnique() {
        val ids = SuggestedShortcuts.all.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test fun everyReferencedActionExistsInTheCatalog() {
        SuggestedShortcuts.all.forEach { suggestion ->
            suggestion.actions.forEach { action ->
                assertNotNull(
                    "${suggestion.id} references unknown action ${action.type}",
                    ScriptActionCatalog.descriptor(action.type),
                )
            }
            assertNotNull(
                "${suggestion.id} has an unknown icon type ${suggestion.iconType}",
                ScriptActionCatalog.descriptor(suggestion.iconType),
            )
        }
    }

    /** A tappable tile must be fully runnable; a partly-implemented chain would fail halfway. */
    @Test fun availabilityFollowsTheCatalog() {
        SuggestedShortcuts.all.forEach { suggestion ->
            val allRunnable = suggestion.actions.all { ScriptActionCatalog.isAvailable(it.type) }
            assertEquals(suggestion.id, allRunnable, suggestion.available)
        }
    }

    @Test fun everySuggestionIsCurrentlyAvailable() {
        SuggestedShortcuts.all.forEach { assertTrue("${it.id} advertises something unimplemented", it.available) }
    }

    /** An automation must name at least one condition that can wake it, and every one must be complete. */
    @Test fun automationsHaveAWakeableCompleteCondition() {
        SuggestedShortcuts.all.filter { it.automation }.forEach { suggestion ->
            assertTrue(suggestion.id, suggestion.constraints.any { ScriptConstraintCatalog.isAutomation(it.type) })
            suggestion.constraints.forEach { constraint ->
                assertNotNull("${suggestion.id}: unknown constraint ${constraint.type}", ScriptConstraintCatalog.descriptor(constraint.type))
            }
        }
        // A key recipe may carry an Only-if (the car ones), but never one only an automation can use.
        SuggestedShortcuts.all.filterNot { it.automation }.flatMap { r -> r.constraints.map { r.id to it.type } }.forEach { (id, type) ->
            assertNotNull("$id: unknown constraint $type", ScriptConstraintCatalog.descriptor(type))
            assertFalse("$id: $type needs an automation", type in ScriptConstraintCatalog.AUTOMATION_ONLY)
        }
    }

    @Test fun everySuggestionHasAtLeastOneAction() {
        SuggestedShortcuts.all.forEach { assertTrue(it.id, it.actions.isNotEmpty()) }
    }

    @Test fun delayActionsCarryANumericDuration() {
        SuggestedShortcuts.all.flatMap { it.actions }.filter { it.type == "delay" }.forEach { action ->
            assertNotNull("delay without duration", action.params["duration"]?.toLongOrNull())
        }
    }

    /**
     * Two suggestions can share an action list and differ only in their conditions (Headphones
     * connected vs. Car Bluetooth, both "play"). Building one used to hide the other, because the
     * panel compared actions alone.
     */
    @Test fun buildingOneSuggestionDoesNotHideAnotherWithDifferentConstraints() {
        val headphones = SuggestedShortcuts.all.first { it.id == "suggestion_headphones_play" }
        val car = SuggestedShortcuts.all.first { it.id == "suggestion_car_bluetooth" }
        assertEquals("the regression needs two suggestions with the same actions", headphones.actions, car.actions)
        val built = scriptFor(headphones)
        assertTrue(headphones.matches(built))
        assertFalse(car.matches(built))
    }

    /** The editor fills the Bluetooth device in after the suggestion is picked, so a built car
     *  shortcut carries a parameter the suggestion does not — it must still count as built. */
    @Test fun aConstraintParameterFilledInByTheEditorStillMatches() {
        val car = SuggestedShortcuts.all.first { it.id == "suggestion_car_bluetooth" }
        val built = scriptFor(car).copy(
            constraints = listOf(ScriptConstraint("bluetooth_device_connected", mapOf("address" to "00:11:22:33:44:55"))),
        )
        assertTrue(car.matches(built))
    }

    /** A built suggestion keeps the panel's icon; anything else falls back to its first action's. */
    @Test fun iconForScriptPrefersTheSuggestionIcon() {
        val headphones = SuggestedShortcuts.all.first { it.id == "suggestion_headphones_play" }
        val built = scriptFor(headphones)
        assertEquals(Icons.Outlined.Headphones, iconForScript(built))
        assertEquals(actionIconFor("media_play"), iconForScript(built.copy(automation = false, constraints = emptyList())))
    }

    private fun scriptFor(suggestion: SuggestedShortcut) = ShortcutScript(
        id = suggestion.id,
        name = "built",
        triggers = emptyList(),
        actions = suggestion.actions,
        constraints = suggestion.constraints,
        automation = suggestion.automation,
    )

    @Test fun orderedPutsAvailableSuggestionsFirst() {
        val availability = SuggestedShortcuts.ordered.map { it.available }
        assertEquals(SuggestedShortcuts.all.size, SuggestedShortcuts.ordered.size)
        assertFalse("A coming-soon tile precedes an available one", availability.zipWithNext().any { !it.first && it.second })
    }

    /**
     * CLAUDE.md "Privileged actions must stay legible": a recipe pre-fills a privileged action only
     * from the reviewed allow-list, never with an HTTP endpoint, links only to non-web URIs, and
     * always shows the command in the editor before it can run.
     */
    @Test fun privilegedPayloadsComeFromTheAllowListAndOpenTheEditor() {
        val privileged = SuggestedShortcuts.all.flatMap { r -> r.actions.filter { it.type in ScriptActionCatalog.PRIVILEGED }.map { r to it } }
        assertTrue("the package ships privileged recipes", privileged.isNotEmpty())
        privileged.forEach { (recipe, action) ->
            // method is a choice, not a payload.
            action.params.filterKeys { it != "method" }.values.filter { it.isNotBlank() }.forEach { payload ->
                assertTrue("${recipe.id} pre-fills an unlisted payload: $payload", payload in SuggestedShortcuts.PREFILLED_PAYLOADS)
            }
            if (action.type == "http_request") assertTrue("${recipe.id} ships a URL", action.params["url"].isNullOrBlank())
            if (action.type == "open_url") {
                val url = action.params["url"].orEmpty()
                assertTrue("${recipe.id} links to $url", url.isBlank() || listOf("geo:", "mailto:", "tel:", "content:").any(url::startsWith))
            }
            assertTrue("${recipe.id} carries ${action.type} but skips the editor", recipe.needsEditor)
        }
    }

    @Test fun recipesWithABlankRequiredFieldOpenTheEditor() {
        SuggestedShortcuts.all
            .filter { recipe -> recipe.actions.any { ActionEditorForms.missingRequired(it).isNotEmpty() } }
            .forEach { assertTrue(it.id, it.needsEditor) }
    }

    @Test fun keyRecipesWithEverythingFilledStayOneTap() {
        SuggestedShortcuts.all
            .filter { !it.automation && it.actions.none { a -> a.type in ScriptActionCatalog.PRIVILEGED || ActionEditorForms.missingRequired(a).isNotEmpty() } }
            .forEach { assertFalse(it.id, it.needsEditor) }
        assertFalse(SuggestedShortcuts.all.first { it.id == "suggestion_flashlight" }.needsEditor)
    }

    /** Renamed service recipes keep their ids: a user may already have built one. */
    @Test fun commandsShelfHoldsThePackage() {
        val shelf = SuggestedShortcuts.all.filter { it.category == RecipeCategory.COMMANDS }
        assertEquals(24, shelf.size)
        shelf.forEach {
            assertEquals(it.id, 1, it.actions.size)
            assertTrue(it.id, it.actions.single().type in setOf("shell_command", "open_url", "http_request"))
            assertTrue(it.id, it.needsEditor)
        }
        assertEquals(
            setOf(
                "recipe_web_address", "recipe_smart_home", "recipe_ha_arrived_home", "recipe_chat_channel",
                "recipe_phone_notification", "recipe_spreadsheet", "recipe_web_post",
            ),
            shelf.filter { it.actions.single().type == "http_request" }.map { it.id }.toSet(),
        )
    }

    @Test fun routinesShelfHoldsTheEverydayAutomations() {
        assertEquals(
            setOf(
                "suggestion_car_bluetooth", "suggestion_night_charge_dnd", "recipe_wifi_vibrate",
                "recipe_wifi_ring", "recipe_morning_dnd_off",
            ),
            SuggestedShortcuts.all.filter { it.category == RecipeCategory.ROUTINES }.map { it.id }.toSet(),
        )
    }

    /** matches() hides a built recipe by shape; two recipes with one shape would hide each other. */
    @Test fun noTwoRecipesShareAShape() {
        val shapes = SuggestedShortcuts.all.map { Triple(it.automation, it.actions, it.constraints.map { c -> c.type }) }
        assertEquals(shapes.size, shapes.distinct().size)
    }

    @Test fun shapeDecidesTheCategoryUnlessARecipeNamesOne() {
        assertEquals(RecipeCategory.SINGLE, SuggestedShortcuts.all.first { it.id == "suggestion_flashlight" }.category)
        assertEquals(RecipeCategory.MULTI, SuggestedShortcuts.all.first { it.id == "suggestion_sos" }.category)
        assertEquals(RecipeCategory.AUTOMATION, SuggestedShortcuts.all.first { it.id == "suggestion_low_battery" }.category)
    }

    /**
     * Contact, app and text all ship blank: the user picks them on their own phone, so the recipe
     * names no app, and its note says the user taps Send.
     */
    @Test fun sendMessageRecipeIsOneBlankSendMessageAction() {
        val recipe = SuggestedShortcuts.all.first { it.id == "recipe_send_message" }
        assertEquals(listOf(ScriptAction("send_message")), recipe.actions)
        assertTrue(recipe.available)
        assertTrue(recipe.needsEditor)
        assertNotNull(recipe.noteRes)
        assertEquals(RecipeCategory.SINGLE, recipe.category)
    }
}
