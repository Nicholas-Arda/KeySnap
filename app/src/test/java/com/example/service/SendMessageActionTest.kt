package com.example.service

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.data.ScriptAction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SendMessageActionTest {

    @Test fun numbersBecomeE164ForTheSimCountry() {
        assertEquals("+905321234567", normalizePhoneNumber("0532 123 45 67", "tr"))
        assertEquals("+905321234567", normalizePhoneNumber("+90 532 123 45 67", "de"))
    }

    @Test fun withoutACountryOnlyTheDigitsAndALeadingPlusStay() {
        assertEquals("+905321234567", normalizePhoneNumber(" +90 (532) 123-45-67 ", null))
        assertEquals("05321234567", normalizePhoneNumber("0532 123 45 67", ""))
        assertEquals("", normalizePhoneNumber("  ", "tr"))
    }

    /** The link apps get the text URL-encoded into a wa.me link, spaces as %20, and nothing else changed. */
    @Test fun aLinkAppGetsAWaMeLinkWithTheDigits() {
        val launch = messageLaunch("com.whatsapp", "0532 123 45 67", "On my way & 5+5=10?", "tr", opensSmsTo = true)!!
        assertEquals(Intent.ACTION_VIEW, launch.action)
        assertEquals("https://wa.me/905321234567?text=On%20my%20way%20%26%205%2B5%3D10%3F", launch.uri)
        assertNull(launch.textExtra)
        val intent = launch.toIntent()
        assertEquals("com.whatsapp", intent.`package`)
        assertEquals(launch.uri, intent.dataString)
        assertEquals("VIEW https://wa.me/905321234567?text=On%20my%20way%20%26%205%2B5%3D10%3F → com.whatsapp", launch.describe())
        assertEquals("https://wa.me/?text=Hi", messageLaunch("com.whatsapp.w4b", "", "Hi", "tr", opensSmsTo = false)!!.uri)
    }

    /** Without a SIM country a national number has no country code, and a link would read its first digits as one. */
    @Test fun aLinkAppRefusesANumberWithoutItsCountryCode() {
        assertNull(messageLaunch("com.whatsapp", "0532 123 45 67", "Hi", null, opensSmsTo = false))
        assertEquals("https://wa.me/905321234567?text=Hi", messageLaunch("com.whatsapp", "+90 532 123 45 67", "Hi", null, opensSmsTo = false)!!.uri)
    }

    @Test fun anSmsAppGetsAnSmstoChatWithTheBody() {
        val launch = messageLaunch("com.example.sms", "0532 123 45 67", "On my way", "tr", opensSmsTo = true)!!
        val intent = launch.toIntent()
        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("smsto:+905321234567", intent.dataString)
        assertEquals("On my way", intent.getStringExtra("sms_body"))
        assertEquals("com.example.sms", intent.`package`)
        assertEquals("SENDTO smsto:+905321234567 · sms_body=“On my way” → com.example.sms", launch.describe())
    }

    @Test fun anyOtherAppGetsAPlainTextShare() {
        val intent = messageLaunch("com.example.chat", "0532 123 45 67", "On my way", "tr", opensSmsTo = false)!!.toIntent()
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("text/plain", intent.type)
        assertNull(intent.data)
        assertEquals("On my way", intent.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals("com.example.chat", intent.`package`)
    }

    /** Only a launch into one person's chat has a Send button to press; a picker or share screen has none. */
    @Test fun onlyAChatWithARecipientCanBeSentAutomatically() {
        val on = mapOf("autoSend" to "true")
        assertTrue(autoSends(on, messageLaunch("com.whatsapp", "+90 532 123 45 67", "Hi", null, opensSmsTo = false)!!))
        assertTrue(autoSends(on, messageLaunch("com.example.sms", "0532 123 45 67", "Hi", "tr", opensSmsTo = true)!!))
        assertFalse(autoSends(on, messageLaunch("com.whatsapp", "", "Hi", "tr", opensSmsTo = false)!!))
        assertFalse(autoSends(on, messageLaunch("com.example.sms", "", "Hi", "tr", opensSmsTo = true)!!))
        assertFalse(autoSends(on, messageLaunch("com.example.chat", "0532 123 45 67", "Hi", "tr", opensSmsTo = false)!!))
        // Off, or saved before the switch existed.
        val chat = messageLaunch("com.whatsapp", "+90 532 123 45 67", "Hi", null, opensSmsTo = false)!!
        assertFalse(autoSends(mapOf("autoSend" to "false"), chat))
        assertFalse(autoSends(emptyMap(), chat))
        // The bridge checks the chat shows this number before it taps.
        assertEquals("+905321234567", chat.recipient)
        assertEquals("", messageLaunch("com.example.chat", "0532 123 45 67", "Hi", "tr", opensSmsTo = false)!!.recipient)
    }

    /** Advanced Mode off: the chat still opens, typed, and the action fails so the run log says why. */
    @Test fun sendingAutomaticallyWithoutTheBridgeOpensTheChatAndFails() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val executor = ScriptActionExecutor.android(context)
        val params = mapOf("packageName" to "com.whatsapp", "number" to "+90 532 123 45 67", "text" to "Hi", "autoSend" to "true")

        assertEquals(listOf(false), runBlocking { executor.execute(listOf(ScriptAction("send_message", params))) })
        assertEquals("https://wa.me/905321234567?text=Hi", shadowOf(context).nextStartedActivity.dataString)

        assertEquals(listOf(true), runBlocking { executor.execute(listOf(ScriptAction("send_message", params - "autoSend"))) })
        assertEquals("https://wa.me/905321234567?text=Hi", shadowOf(context).nextStartedActivity.dataString)
    }

    /** A plain text share has no chat to send into, so the switch is ignored rather than failing. */
    @Test fun aShareIgnoresSendAutomatically() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val params = mapOf("packageName" to "com.example.chat", "text" to "Hi", "autoSend" to "true")
        assertEquals(listOf(true), runBlocking { ScriptActionExecutor.android(context).execute(listOf(ScriptAction("send_message", params))) })
        assertEquals(Intent.ACTION_SEND, shadowOf(context).nextStartedActivity.action)
    }

    @Test fun withoutAnAppOrTextNothingRuns() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertNull(messageLaunchFor(context, mapOf("text" to "Hi")))
        assertNull(messageLaunchFor(context, mapOf("packageName" to "com.example.chat", "text" to "  ")))
        val executor = ScriptActionExecutor.android(context)
        assertEquals(listOf(false), runBlocking { executor.execute(listOf(ScriptAction("send_message", mapOf("packageName" to "com.example.chat")))) })
    }
}
