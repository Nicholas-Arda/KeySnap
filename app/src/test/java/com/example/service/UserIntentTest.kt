package com.example.service

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.data.ScriptAction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UserIntentTest {
    @Test fun oldIntentRemainsAnImplicitBroadcast() {
        val intent = userIntent(mapOf("action" to "test.ACTION"))
        assertEquals("test.ACTION", intent.action)
        assertNull(intent.`package`)
        assertEquals(0, intent.flags)
    }

    @Test fun packageActivityUriAndExtrasAreLiteral() {
        val intent = userIntent(mapOf("action" to "test.ACTION", "package" to "test.app", "target" to "activity", "uri" to "test://path?a=b", "extras" to "key=  literal  ;empty="))
        assertEquals("test.app", intent.`package`)
        assertEquals("test://path?a=b", intent.dataString)
        assertEquals("  literal  ", intent.getStringExtra("key"))
        assertEquals("", intent.getStringExtra("empty"))
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, intent.flags)
    }

    @Test fun invalidExtrasDoNotDispatchAnything() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val result = ScriptActionExecutor.android(context).runSingleActionForTest(ScriptAction("send_intent", mapOf("action" to "test.ACTION", "extras" to "key=one;broken")))
        assertFalse(result.ok)
        assertTrue(shadowOf(context as android.app.Application).broadcastIntents.none { it.action == "test.ACTION" })
    }

    @Test fun testEntryDispatchesExactlyOnePackageBroadcastWithUntrimmedExtras() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val result = ScriptActionExecutor.android(context).runSingleActionForTest(ScriptAction("send_intent", mapOf("action" to "test.ACTION", "package" to "test.app", "extras" to "key= value ")))
        assertTrue(result.ok)
        assertTrue(result.durationMs >= 0)
        val intent = shadowOf(context as android.app.Application).broadcastIntents.single { it.action == "test.ACTION" }
        assertEquals("test.app", intent.`package`)
        assertEquals(" value ", intent.getStringExtra("key"))
    }
}
