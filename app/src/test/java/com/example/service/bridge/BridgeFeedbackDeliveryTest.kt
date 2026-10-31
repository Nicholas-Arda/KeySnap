package com.example.service.bridge

import com.example.bridge.BridgeProtocol
import com.example.bridge.EventServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
class BridgeFeedbackDeliveryTest {
    @Test fun probesDoNotClaimDeliveryAndColdBurstsReplayOnlyNewestAsDisplayFeedback() {
        withServer { server, port, wakes ->
            connect(port).use { probe ->
                server.broadcastTrigger(100, 24, "SINGLE_PRESS", "first", true, "toggle_flashlight=1")
                server.broadcastTrigger(101, 25, "SINGLE_PRESS", "last", true, "cycle_ringer_mode=1")
                awaitWakes(wakes, 2)
                probe.send(BridgeProtocol.CMD_WATCH_FEEDBACK)
                assertEquals("OK\twatching", probe.read())
                val replay = probe.read()
                assertTrue(replay.startsWith("FEEDBACK\t101\t25\tSINGLE_PRESS\tlast\t"))
                // A foreground stream taking over must not replay the same report a second time.
                connect(port).use { foreground ->
                    foreground.send(BridgeProtocol.CMD_WATCH)
                    assertEquals("OK\twatching", foreground.read())
                    server.broadcastTrigger(102, 24, "SINGLE_PRESS", "live", true, "toggle_flashlight=1")
                    assertTrue(foreground.read().startsWith("TRIGGER\t102\t"))
                    assertEquals(2, wakes.get())
                    // Feedback subscriber receives no second copy when the foreground owns execution.
                    probe.socket.soTimeout = 100
                    assertThrows(java.net.SocketTimeoutException::class.java) { probe.read() }
                }
            }
        }
    }

    @Test fun foregroundColdAdoptionReplaysDisplayOnlyThenReceivesLiveTriggers() {
        withServer { server, port, wakes ->
            server.broadcastTrigger(100, 24, "SINGLE_PRESS", "cold", true, "toggle_flashlight=1")
            awaitWakes(wakes, 1)
            connect(port).use { client ->
                client.send(BridgeProtocol.CMD_WATCH)
                assertEquals("OK\twatching", client.read())
                assertTrue(client.read().startsWith("FEEDBACK\t100\t"))
                server.broadcastTrigger(101, 24, "SINGLE_PRESS", "live", true, "toggle_flashlight=1")
                assertTrue(client.read().startsWith("TRIGGER\t101\t"))
            }
        }
    }

    @Test fun modeFeedbackIsLiveAndNeverReplayedOnReconnect() {
        withServer { server, port, wakes ->
            server.broadcastMode(2)
            connect(port).use { client ->
                client.send(BridgeProtocol.CMD_WATCH)
                assertEquals("OK\twatching", client.read())
                server.broadcastMode(3)
                assertEquals("MODE\t3", client.read())
                client.socket.soTimeout = 100
                assertThrows(java.net.SocketTimeoutException::class.java) { client.read() }
            }
            assertEquals(0, wakes.get())
        }
    }

    private fun withServer(block: (EventServer, Int, AtomicInteger) -> Unit) {
        val port = ServerSocket(0).use { it.localPort }
        val wakes = AtomicInteger()
        val server = EventServer(port, "test-token", object : EventServer.Listener {
            override fun onReloadRequested() {}
            override fun onShutdownRequested() {}
            override fun onRunRequested(payloadJson: String) = "0\t0"
            override fun onPressSendRequested(payloadJson: String) = "not-found"
            override fun onFeedbackWakeRequested() { wakes.incrementAndGet() }
        })
        server.bind()
        val worker = thread(isDaemon = true) { server.run() }
        try { block(server, port, wakes) } finally { server.stop(); worker.join(1000) }
    }

    @Test fun pendingIsDisplayOnlyAndPrecedesTheSingleExecutionReport() {
        withServer { server, port, wakes ->
            connect(port).use { client ->
                client.send(BridgeProtocol.CMD_WATCH)
                assertEquals("OK\twatching", client.read())
                server.broadcastPending(100, 24, "SINGLE_PRESS", "run", "delay=0", 42)
                assertEquals("FEEDBACK\t100\t24\tSINGLE_PRESS\trun\t0\tdelay=0\t42\t1", client.read())
                // Execution has not finished, but the UI already has everything it needs.
                server.broadcastTrigger(1100, 24, "SINGLE_PRESS", "run", true, "delay=1", 42)
                assertEquals("TRIGGER\t1100\t24\tSINGLE_PRESS\trun\t1\tdelay=1\t42\t0", client.read())
                assertEquals(0, wakes.get())
            }
        }
    }

    @Test fun coldPendingWakesBeforeCompletionAndFeedbackSubscriberReceivesBothPhases() {
        withServer { server, port, wakes ->
            server.broadcastPending(100, 24, "SINGLE_PRESS", "run", "delay=0", 42)
            awaitWakes(wakes, 1)
            connect(port).use { client ->
                client.send(BridgeProtocol.CMD_WATCH_FEEDBACK)
                assertEquals("OK\twatching", client.read())
                assertTrue(client.read().endsWith("\t42\t1"))
                server.broadcastTrigger(1100, 24, "SINGLE_PRESS", "run", true, "delay=1", 42)
                assertTrue(client.read().endsWith("\t42\t0"))
            }
        }
    }

    @Test fun clientDecodesPendingIdentityWithoutCallingExecutionListener() {
        val client = BridgeClient(port = 1, token = "test-token")
        val dispatch = client.javaClass.getDeclaredMethod("dispatch", String::class.java, BridgeClient.Listener::class.java)
            .apply { isAccessible = true }
        val feedback = mutableListOf<BridgeTrigger>()
        val triggers = mutableListOf<BridgeTrigger>()
        val listener = object : BridgeClient.Listener {
            override fun onFeedback(trigger: BridgeTrigger) { feedback += trigger }
            override fun onTrigger(trigger: BridgeTrigger) { triggers += trigger }
        }
        dispatch.invoke(client, "FEEDBACK\t100\t24\tSINGLE_PRESS\trun\t0\tdelay=0\t42\t1", listener)
        assertTrue(triggers.isEmpty())
        assertTrue(feedback.single().pending)
        assertEquals(42L, feedback.single().capsuleRunId)
        dispatch.invoke(client, "TRIGGER\t1100\t24\tSINGLE_PRESS\trun\t1\tdelay=1\t42\t0", listener)
        assertFalse(triggers.single().pending)
        assertEquals(feedback.single().capsuleRunId, triggers.single().capsuleRunId)
    }

    private fun awaitWakes(wakes: AtomicInteger, count: Int) {
        val deadline = System.nanoTime() + 2_000_000_000L
        while (wakes.get() < count && System.nanoTime() < deadline) Thread.sleep(1)
        assertEquals(count, wakes.get())
    }

    private fun connect(port: Int): Client = Client(Socket("127.0.0.1", port)).also {
        it.send("HELLO\ttest-token")
        assertTrue(it.read().startsWith("OK\t${BridgeProtocol.VERSION}\t"))
    }

    private class Client(val socket: Socket) : AutoCloseable {
        private val reader = socket.getInputStream().bufferedReader()
        private val writer = socket.getOutputStream().bufferedWriter()
        init { socket.soTimeout = 2000 }
        fun send(line: String) { writer.write(line); writer.newLine(); writer.flush() }
        fun read(): String = reader.readLine()
        override fun close() = socket.close()
    }
}
