package com.example.service.bridge

import com.example.bridge.BridgeProtocol
import com.example.bridge.EventServer
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BridgeAssistantSessionTest {
    @Test fun authenticatedRequestReturnsTheSessionOutcomeExactlyOnce() = runBlocking {
        for (shown in listOf(true, false)) {
            withServer(shown) { port, calls ->
                assertEquals(shown, BridgeClient(port = port, token = "test-token").requestShowAssistant())
                assertEquals(1, calls.get())
            }
        }
    }

    @Test fun sessionCannotBeRequestedWithoutAuthentication() = runBlocking {
        withServer(true) { port, calls ->
            Socket(BridgeProtocol.HOST, port).use { socket ->
                socket.soTimeout = 2_000
                socket.getOutputStream().write("${BridgeProtocol.CMD_SHOW_ASSISTANT}\n".toByteArray())
                assertEquals("ERROR\tunauthorized", socket.getInputStream().bufferedReader().readLine())
            }
            assertFalse(BridgeClient(port = port, token = "wrong-token").requestShowAssistant())
            assertEquals(0, calls.get())
        }
    }

    private suspend fun withServer(shown: Boolean, block: suspend (Int, AtomicInteger) -> Unit) {
        val port = ServerSocket(0).use { it.localPort }
        val calls = AtomicInteger()
        val server = EventServer(port, "test-token", object : EventServer.Listener {
            override fun onReloadRequested() {}
            override fun onShutdownRequested() {}
            override fun onRunRequested(payloadJson: String): String = error("unexpected RUN")
            override fun onPressSendRequested(payloadJson: String): String = error("unexpected PRESS_SEND")
            override fun onShowAssistantRequested(): Boolean {
                calls.incrementAndGet()
                return shown
            }
        })
        server.bind()
        val worker = thread(isDaemon = true) { server.run() }
        try {
            block(port, calls)
        } finally {
            server.stop()
            worker.join(1_000)
        }
    }
}
