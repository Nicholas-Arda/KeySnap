package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DeveloperOptionsGuideTest {

    private val res = RuntimeEnvironment.getApplication().resources

    @Test fun everyIdIsUnique() {
        val ids = DeveloperOptionsGuides.all.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test fun everyGuideHasNonEmptySteps() {
        DeveloperOptionsGuides.all.forEach { guide ->
            assertTrue("${guide.id} has no steps", res.getStringArray(guide.stepsRes).isNotEmpty())
            assertTrue("${guide.id} label is blank", res.getString(guide.labelRes).isNotBlank())
        }
    }

    @Test fun genericIsIncludedInAll() {
        assertTrue(DeveloperOptionsGuides.all.any { it.id == DeveloperOptionsGuides.GENERIC.id })
    }

    @Test fun detectForDeviceMatchesKnownManufacturers() {
        assertEquals("samsung", DeveloperOptionsGuides.detectForDevice(manufacturer = "samsung", brand = "samsung").id)
        assertEquals("xiaomi", DeveloperOptionsGuides.detectForDevice(manufacturer = "Xiaomi", brand = "Redmi").id)
        assertEquals("huawei", DeveloperOptionsGuides.detectForDevice(manufacturer = "HUAWEI", brand = "HONOR").id)
        assertEquals("coloros", DeveloperOptionsGuides.detectForDevice(manufacturer = "OnePlus", brand = "OnePlus").id)
        assertEquals("vivo", DeveloperOptionsGuides.detectForDevice(manufacturer = "vivo", brand = "iQOO").id)
        assertEquals("pixel", DeveloperOptionsGuides.detectForDevice(manufacturer = "Google", brand = "google").id)
    }

    @Test fun detectForDeviceFallsBackToGenericForUnknownManufacturer() {
        assertEquals(DeveloperOptionsGuides.GENERIC.id, DeveloperOptionsGuides.detectForDevice(manufacturer = "Sony", brand = "Sony").id)
    }

    @Test fun byIdLooksUpGuide() {
        assertEquals("samsung", DeveloperOptionsGuides.byId("samsung").id)
    }
}
