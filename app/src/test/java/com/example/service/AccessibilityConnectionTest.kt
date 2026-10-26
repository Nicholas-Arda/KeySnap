package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowAccessibilityService

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [ConnectionAccessibilityShadow::class])
class AccessibilityConnectionTest {
    private val services = mutableListOf<KeyMapperAccessibilityService>()

    @Before fun setUp() { ConnectionAccessibilityShadow.connectionInfo = AccessibilityServiceInfo() }
    @After fun tearDown() {
        services.asReversed().forEach { it.onDestroy() }
        ConnectionAccessibilityShadow.connectionInfo = null
    }

    private fun connect(): KeyMapperAccessibilityService =
        Robolectric.buildService(KeyMapperAccessibilityService::class.java).create().get().also {
            services += it
            KeyMapperAccessibilityService::class.java.getDeclaredMethod("onServiceConnected").apply {
                isAccessible = true
            }.invoke(it)
        }

    @Test fun connectedCallbackWithoutAFrameworkConnectionIsNotActive() {
        ConnectionAccessibilityShadow.connectionInfo = null
        connect()
        assertFalse(KeyMapperAccessibilityService.isServiceActive.value)
    }

    @Test fun staleLocalInstanceCannotMaskADeadFrameworkConnection() {
        connect()
        assertTrue(KeyMapperAccessibilityService.isServiceActive.value)
        ConnectionAccessibilityShadow.connectionInfo = null
        assertFalse(KeyMapperAccessibilityService.refreshConnectionStatus())
        assertFalse(KeyMapperAccessibilityService.isServiceActive.value)
    }

    @Test fun feedbackInterruptionDoesNotDisconnectALiveService() {
        val service = connect()
        service.onInterrupt()
        assertTrue(KeyMapperAccessibilityService.refreshConnectionStatus())
    }

    @Test fun unbindingClearsTheInstanceEvenIfFrameworkInfoIsStillAvailable() {
        val service = connect()
        service.onUnbind(Intent())
        assertFalse(KeyMapperAccessibilityService.refreshConnectionStatus())
    }

    @Test fun destroyingAnOldInstanceDoesNotClearItsReplacement() {
        val old = connect()
        connect()
        old.onUnbind(Intent())
        old.onDestroy()
        assertTrue(KeyMapperAccessibilityService.refreshConnectionStatus())
    }
}

@Implements(AccessibilityService::class)
class ConnectionAccessibilityShadow : ShadowAccessibilityService() {
    companion object { var connectionInfo: AccessibilityServiceInfo? = null }
    @Implementation protected fun getServiceInfo(): AccessibilityServiceInfo? = connectionInfo
}
