package com.example.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.KeyMappingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class KeyRecordingPreviewTest {
    private val coordinator = HardwareKeyTriggerCoordinator

    @Test fun heldKeysAreLiveAcrossSourcesAndFirstReleaseEmitsTheWholeCombo() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val context = ApplicationProvider.getApplicationContext<Context>()
        try {
            coordinator.initialize(context, KeyMappingRepository.getInstance(context))
            val results = mutableListOf<List<Int>>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                coordinator.recordedKey.collect { results += it }
            }
            ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
            coordinator.startRecordingKey()
            assertTrue(coordinator.dispatchKeyEvent(25, true, "Activity"))
            assertEquals(listOf(25), coordinator.recordingKeys.value)
            coordinator.dispatchKeyEvent(25, true, "Accessibility")
            assertEquals(listOf(25), coordinator.recordingKeys.value)
            coordinator.dispatchKeyEvent(26, true, "Advanced Mode")
            assertEquals(listOf(25, 26), coordinator.recordingKeys.value)
            coordinator.dispatchKeyEvent(25, false, "Activity")
            assertEquals(listOf(listOf(25, 26)), results)
            assertEquals(emptyList<Int>(), coordinator.recordingKeys.value)
            assertFalse(coordinator.isRecordingKey.value)
        } finally {
            coordinator.cancelRecordingKey()
            Dispatchers.resetMain()
        }
    }

    @Test fun cancelAndRestartClearThePreviewWithoutPublishingARecordedKey() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val context = ApplicationProvider.getApplicationContext<Context>()
        try {
            coordinator.initialize(context, KeyMappingRepository.getInstance(context))
            val results = mutableListOf<List<Int>>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                coordinator.recordedKey.collect { results += it }
            }
            ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
            coordinator.startRecordingKey()
            coordinator.dispatchKeyEvent(24, true, "Activity")
            assertEquals(listOf(24), coordinator.recordingKeys.value)
            coordinator.cancelRecordingKey()
            assertTrue(coordinator.recordingKeys.value.isEmpty())
            assertFalse(coordinator.isRecordingKey.value)
            coordinator.startRecordingKey()
            assertTrue(coordinator.recordingKeys.value.isEmpty())
            assertTrue(results.isEmpty())
        } finally {
            coordinator.cancelRecordingKey()
            Dispatchers.resetMain()
        }
    }
}
