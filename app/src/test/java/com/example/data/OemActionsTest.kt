package com.example.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OemActionsTest {
    private val everything: (String) -> Boolean = { true }

    @Test fun onlyTheThreeBrandsMatch() {
        listOf("OPPO", "OnePlus", "realme").forEach { assertTrue(it, OemActions.brandMatches(it)) }
        listOf("samsung", "Google", "Xiaomi", "").forEach { assertFalse(it, OemActions.brandMatches(it)) }
    }

    @Test fun mindSpaceNeedsTheBrandTheAppAndTheSideKey() {
        val type = OemActions.MIND_SPACE_CAPTURE
        assertTrue(OemActions.offered(type, brand = true, installed = everything, hasSideKey = { true }))
        assertFalse(OemActions.offered(type, brand = false, installed = everything, hasSideKey = { true }))
        assertFalse(OemActions.offered(type, brand = true, installed = { false }, hasSideKey = { true }))
        assertFalse(OemActions.offered(OemActions.OPEN_MIND_SPACE, brand = true, installed = everything, hasSideKey = { false }))
    }

    @Test fun theAssistantNeedsOnlyTheBrandAndItsApp() {
        val type = OemActions.BUILT_IN_ASSISTANT
        assertTrue(OemActions.offered(type, brand = true, installed = { it == OemActions.ASSISTANT_PACKAGE }, hasSideKey = { false }))
        assertFalse(OemActions.offered(type, brand = true, installed = { it == OemActions.MIND_SPACE_PACKAGE }, hasSideKey = { true }))
    }

    @Test fun sideKeyHoldJobsNeedTheKeyAndTheirOwnApp() {
        val type = OemActions.TRANSLATE_SCREEN
        val translator = { pkg: String -> pkg == "com.coloros.translate" }
        assertTrue(OemActions.offered(type, brand = true, installed = translator, hasSideKey = { true }))
        assertFalse(OemActions.offered(type, brand = true, installed = translator, hasSideKey = { false }))
        assertFalse(OemActions.offered(OemActions.RECORD_AUDIO, brand = true, installed = translator, hasSideKey = { true }))
    }

    @Test fun oseeNeedsOnlyTheBrandAndItsApp() {
        val type = OemActions.OPEN_OSEE
        assertTrue(OemActions.offered(type, brand = true, installed = { it == OemActions.OSEE_PACKAGE }, hasSideKey = { false }))
        assertFalse(OemActions.offered(type, brand = false, installed = { it == OemActions.OSEE_PACKAGE }, hasSideKey = { true }))
    }

    @Test fun everyOtherActionIsAlwaysOffered() {
        assertTrue(OemActions.offered("toggle_flashlight", brand = false, installed = { false }, hasSideKey = { false }))
    }
}
