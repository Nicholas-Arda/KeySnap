package com.example.assistant

import android.app.Application
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
class AssistantIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun selectKeySnap() {
        shadowOf(context.getSystemService(RoleManager::class.java)).addHeldRole(RoleManager.ROLE_ASSISTANT)
    }

    private fun addActivity(action: String, pkg: String, exported: Boolean = true) {
        shadowOf(context.packageManager).addResolveInfoForIntent(Intent(action), ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = pkg
                name = "$pkg.AssistActivity"
                this.exported = exported
                enabled = true
                applicationInfo = ApplicationInfo().apply { packageName = pkg; enabled = true }
            }
        })
    }

    @Test fun selectedAssistantUsesExplicitOverlayAndArmsLoopGuardBeforeLaunch() {
        selectKeySnap()
        addActivity("android.intent.action.VOICE_ASSIST", assistantPackagePriority.first())
        assertTrue(AssistantIntegration.launch(context))
        val intent = shadowOf(context).nextStartedActivity
        assertEquals("android.intent.action.VOICE_ASSIST", intent.action)
        assertEquals(ComponentName(assistantPackagePriority.first(), assistantPackagePriority.first() + ".AssistActivity"), intent.component)
        assertFalse(AssistantIntegration.invocations.accept(SystemClock.elapsedRealtime()))
    }

    @Test fun selfAndUnexportedActivitiesAreExcludedAndNoAlternativeShowsToast() {
        selectKeySnap()
        addActivity(Intent.ACTION_ASSIST, context.packageName)
        addActivity(Intent.ACTION_VOICE_COMMAND, "private.assistant", exported = false)
        assertFalse(AssistantIntegration.launch(context))
        assertNull(shadowOf(context).nextStartedActivity)
        assertEquals("No other assistant is available on this phone.", ShadowToast.getTextOfLatestToast())
    }

    @Test fun settingsOpensVoiceInputPickerInsteadOfRequestingTheRole() {
        AssistantIntegration.openSettings(context)
        assertEquals(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS, shadowOf(context).nextStartedActivity.action)
    }

    @Suppress("DEPRECATION")
    @Test fun systemComponentsAreProtectedAndNoMicrophonePermissionIsRequested() {
        val pm = context.packageManager
        listOf(KeySnapVoiceService::class.java, KeySnapSessionService::class.java).forEach { type ->
            val info = pm.getServiceInfo(ComponentName(context, type), PackageManager.GET_META_DATA)
            assertTrue(info.exported)
            assertEquals("android.permission.BIND_VOICE_INTERACTION", info.permission)
        }
        val recognition = pm.getServiceInfo(ComponentName(context, KeySnapRecognitionService::class.java), 0)
        assertEquals("android.permission.BIND_SPEECH_RECOGNITION_SERVICE", recognition.permission)
        val requested = pm.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertFalse("android.permission.RECORD_AUDIO" in requested)
        val xml = context.resources.getXml(io.github.nicholasarda.keysnap.R.xml.voice_interaction_service)
        while (xml.next() != org.xmlpull.v1.XmlPullParser.START_TAG) Unit
        assertEquals("voice-interaction-service", xml.name)
        assertEquals(KeySnapSessionService::class.java.name, xml.getAttributeValue("http://schemas.android.com/apk/res/android", "sessionService"))
        assertTrue(xml.getAttributeBooleanValue("http://schemas.android.com/apk/res/android", "supportsLaunchVoiceAssistFromKeyguard", false))
        xml.close()
    }
}
