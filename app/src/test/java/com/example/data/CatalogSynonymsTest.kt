package com.example.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogSynonymsTest {
    @Test fun otherNamesFindTheItem() {
        assertTrue(CatalogSynonyms.matches("toggle_mobile_data", "Cellular"))
        assertTrue(CatalogSynonyms.matches("mobile_data_on", "cellular"))
        assertTrue(CatalogSynonyms.matches("toggle_flashlight", "torch"))
        assertTrue(CatalogSynonyms.matches("flashlight_off", "Torch"))
        assertTrue(CatalogSynonyms.matches("toggle_airplane_mode", "flight mode"))
    }

    @Test fun theIdsOwnWordsWorkInAnyLanguage() {
        assertTrue(CatalogSynonyms.matches("toggle_flashlight", "flashlight"))
        assertTrue(CatalogSynonyms.matches("toggle_mobile_data", "mobile data"))
    }

    @Test fun segmentsNeverMatchInsideAnotherWord() {
        assertFalse(CatalogSynonyms.matches("discharging", "charger"))
        assertTrue(CatalogSynonyms.matches("charging_wired", "charger"))
        assertFalse(CatalogSynonyms.matches("airplane_mode_on", "profile"))
    }

    @Test fun oneLetterNeverExpands() {
        assertFalse(CatalogSynonyms.matches("toggle_bluetooth", "b"))
    }
}
