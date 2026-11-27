package com.example.surface

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.data.ScriptRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExternalControlReceiverTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repo = ScriptRepository.getInstance(context)

    private fun send(verb: String) =
        ExternalControlReceiver().onReceive(context, Intent(ExternalControlReceiver.action(context, verb)))

    @After fun restore() {
        ExternalControlReceiver.setEnabled(context, false)
        repo.setGloballyEnabled(true)
    }

    @Test fun ignoredUntilTheSwitchIsOn() {
        repo.setGloballyEnabled(true)
        send(ExternalControlReceiver.PAUSE)
        assertEquals(true, repo.globallyEnabled.value)
    }

    @Test fun pauseResumeToggleChangeOnlyTheGlobalState() {
        ExternalControlReceiver.setEnabled(context, true)
        repo.setGloballyEnabled(true)
        send(ExternalControlReceiver.PAUSE)
        assertEquals(false, repo.globallyEnabled.value)
        send(ExternalControlReceiver.PAUSE)
        assertEquals(false, repo.globallyEnabled.value)
        send(ExternalControlReceiver.RESUME)
        assertEquals(true, repo.globallyEnabled.value)
        send(ExternalControlReceiver.TOGGLE)
        assertEquals(false, repo.globallyEnabled.value)
        send(ExternalControlReceiver.TOGGLE)
        assertEquals(true, repo.globallyEnabled.value)
    }

    @Test fun otherActionsAreIgnored() {
        ExternalControlReceiver.setEnabled(context, true)
        repo.setGloballyEnabled(true)
        ExternalControlReceiver().onReceive(context, Intent("com.other.PAUSE"))
        ExternalControlReceiver().onReceive(context, Intent(ExternalControlReceiver.action(context, "RUN")))
        assertEquals(true, repo.globallyEnabled.value)
    }
}
