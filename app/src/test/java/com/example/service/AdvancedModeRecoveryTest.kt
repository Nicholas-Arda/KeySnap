package com.example.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.SensitiveAppsRepository
import com.example.service.RecoveryStep.Accessibility
import com.example.service.RecoveryStep.DeveloperOptions
import com.example.service.RecoveryStep.TurnOn
import com.example.service.RecoveryStep.WirelessDebugging
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AdvancedModeRecoveryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun wantAdvancedMode(wanted: Boolean) {
        context.getSharedPreferences("arda_mapper_adb_prefs", Context.MODE_PRIVATE).edit().putBoolean("advanced_mode_enabled", wanted).commit()
    }

    private val allOff = RecoverySwitches(developerOptions = false, wirelessDebugging = false, accessibility = false)

    @Test fun noCardsWithoutTheFlagOrWhileABridgeRuns() {
        assertEquals(emptyList<RecoveryStep>(), recoverySteps(ended = false, bridgeRunning = false, switches = allOff))
        assertEquals(emptyList<RecoveryStep>(), recoverySteps(ended = true, bridgeRunning = true, switches = allOff))
    }

    @Test fun oneCardPerMissingPieceInOrder() {
        assertEquals(listOf(DeveloperOptions, WirelessDebugging, Accessibility), recoverySteps(true, false, allOff))
        assertEquals(listOf(WirelessDebugging), recoverySteps(true, false, RecoverySwitches(wirelessDebugging = false)))
        assertEquals(listOf(Accessibility, TurnOn), recoverySteps(true, false, RecoverySwitches(accessibility = false)))
        // Both switches on, no bridge yet: only "Turn on" is left.
        assertEquals(listOf(TurnOn), recoverySteps(true, false, RecoverySwitches()))
    }

    @Test fun flagSetByLeavingClearedWhenTheBridgeIsBack() {
        val repo = SensitiveAppsRepository.getInstance(context)
        wantAdvancedMode(true)
        AdvancedModeRecovery.onEnded(context)
        assertTrue(repo.store.value.advancedModeEnded)
        SensitiveAppsGate.onBridgeUp(context)
        assertFalse(repo.store.value.advancedModeEnded)
    }

    @Test fun flagClearedOnceAdvancedModeIsNoLongerWanted() {
        wantAdvancedMode(true)
        AdvancedModeRecovery.onEnded(context)
        AdvancedModeRecovery.refresh(context)
        assertTrue(SensitiveAppsRepository.getInstance(context).store.value.advancedModeEnded)

        wantAdvancedMode(false)
        AdvancedModeRecovery.refresh(context)
        assertFalse(SensitiveAppsRepository.getInstance(context).store.value.advancedModeEnded)
    }
}
