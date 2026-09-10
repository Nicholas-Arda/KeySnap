package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test

class InstalledAppsRepositoryTest {

    @Test fun appsAreSortedByLabelIgnoringCase() {
        val sorted = sortInstalledApps(
            listOf(
                InstalledApp("c", "zebra"),
                InstalledApp("a", "Apple"),
                InstalledApp("b", "banana"),
            ),
        )
        assertEquals(listOf("Apple", "banana", "zebra"), sorted.map { it.label })
    }

    @Test fun duplicatePackagesAreCollapsedToOneEntry() {
        // A package can resolve more than one launcher activity; the picker must list it once.
        val sorted = sortInstalledApps(
            listOf(
                InstalledApp("com.example", "Example"),
                InstalledApp("com.example", "Example"),
                InstalledApp("com.other", "Other"),
            ),
        )
        assertEquals(listOf("com.example", "com.other"), sorted.map { it.packageName })
    }

    @Test fun anAppWithABlankLabelFallsBackToItsPackageName() {
        val sorted = sortInstalledApps(listOf(InstalledApp("com.example.blank", "   ")))
        assertEquals("com.example.blank", sorted.single().label)
    }
}
