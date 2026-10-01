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
        SuggestedShortcuts.all.filterNot { it.automation }.forEach { assertTrue(it.id, it.constraints.isEmpty()) }
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
     * CLAUDE.md: no suggested script may call an endpoint the user did not type or pre-fill anything
     * privileged. A blank HTTP request (webhooks), a blank link or a blank message is the only
     * privileged step a recipe may carry.
     */
    @Test fun noRecipeShipsAnEndpointOrAPrivilegedPayload() {
        SuggestedShortcuts.all.forEach { recipe ->
            recipe.actions.filter { it.type in ScriptActionCatalog.PRIVILEGED }.forEach { action ->
                assertTrue("${recipe.id}: only a blank HTTP request, link or message may ship", action.type in setOf("http_request", "open_url", "send_message"))
                assertTrue("${recipe.id} ships a URL", action.params["url"].isNullOrBlank())
                assertTrue("${recipe.id} ships a recipient", action.params["number"].isNullOrBlank())
                assertTrue("${recipe.id} ships a message", action.params["text"].isNullOrBlank())
            }
        }
    }

    @Test fun recipesWithABlankRequiredFieldOpenTheEditor() {
        SuggestedShortcuts.all
            .filter { recipe -> recipe.actions.any { ActionEditorForms.missingRequired(it).isNotEmpty() } }
            .forEach { assertTrue(it.id, it.needsEditor) }
    }

    @Test fun keyRecipesWithEverythingFilledStayOneTap() {
        SuggestedShortcuts.all
            .filter { !it.automation && it.actions.none { a -> ActionEditorForms.missingRequired(a).isNotEmpty() } }
            .forEach { assertFalse(it.id, it.needsEditor) }
        assertFalse(SuggestedShortcuts.all.first { it.id == "suggestion_flashlight" }.needsEditor)
    }

    @Test fun webhookShelfHoldsFiveBlankHttpRequests() {
        val webhooks = SuggestedShortcuts.all.filter { it.category == RecipeCategory.WEBHOOK }
        assertEquals(5, webhooks.size)
        webhooks.forEach {
            assertEquals(it.id, "http_request", it.actions.single().type)
            assertTrue(it.id, it.needsEditor)
        }
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
