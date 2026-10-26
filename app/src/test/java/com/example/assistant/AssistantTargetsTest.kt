package com.example.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantTargetsTest {
    private fun target(pkg: String, kind: AssistantTarget.Kind = AssistantTarget.Kind.ASSIST) =
        AssistantTarget(pkg, "$pkg.Activity", "action", kind)

    @Test fun preferredPackagesWinRegardlessOfDiscoveryOrderAndSelfIsNeverReturned() {
        val all = assistantPackagePriority.map { target(it) } + target("other.assistant") + target("keysnap")
        assertEquals(
            assistantPackagePriority + "other.assistant",
            orderedAssistantTargets(all.reversed(), "keysnap").map { it.packageName },
        )
    }

    @Test fun overlayWinsOverFullAppAndDiscoveryDuplicatesAreRemoved() {
        val pkg = assistantPackagePriority.first()
        val overlay = target(pkg, AssistantTarget.Kind.OVERLAY)
        val full = target(pkg, AssistantTarget.Kind.LAUNCHER)
        assertEquals(listOf(overlay, full), orderedAssistantTargets(listOf(full, overlay, overlay), "keysnap"))
    }

    @Test fun noAlternativeProducesNoSelfFallback() {
        assertTrue(orderedAssistantTargets(listOf(target("keysnap")), "keysnap").isEmpty())
    }

    @Test fun geminiAppBeatsTheGoogleAppsAssistOverlay() {
        // The Google app's overlay opens the old Assistant while KeySnap holds the role.
        val gemini = target("com.google.android.apps.bard", AssistantTarget.Kind.LAUNCHER)
        val google = target("com.google.android.googlequicksearchbox", AssistantTarget.Kind.OVERLAY)
        assertEquals(listOf(gemini, google), orderedAssistantTargets(listOf(google, gemini), "keysnap"))
    }
}
