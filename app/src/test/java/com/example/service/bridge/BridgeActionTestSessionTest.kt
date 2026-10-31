package com.example.service.bridge

import com.example.bridge.ActionTestResult
import com.example.bridge.BridgeProtocol
import com.example.bridge.EventServer
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BridgeActionTestSessionTest {
    @Test fun privateResultReachesOnlyTheAuthenticatedRequesterOnce() = runBlocking {
        withServer { port, calls ->
            Socket(BridgeProtocol.HOST, port).use { observer ->
                observer.soTimeout = 250
                val reader = observer.getInputStream().bufferedReader()
                val writer = observer.getOutputStream().bufferedWriter()
                writer.write("HELLO\ttest-token\nWATCH\n")
                writer.flush()
                assertTrue(reader.readLine().startsWith("OK\t"))
                assertEquals("OK\twatching", reader.readLine())
                val result = BridgeClient(port = port, token = "test-token").requestTestAction("{\"actions\":[]}")!!
                assertEquals(listOf("private\toutput", "last line"), result.output)
                assertEquals(7, result.exitCode)
                assertEquals(1, calls.get())
                assertThrows(SocketTimeoutException::class.java) { reader.readLine() }
            }
        }
    }

    @Test fun unauthenticatedTestRequestsNeverExecute() = runBlocking {
        withServer { port, calls ->
            Socket(BridgeProtocol.HOST, port).use { socket ->
                socket.soTimeout = 2_000
                socket.getOutputStream().write("TEST_ACTION\t{}\n".toByteArray())
                assertEquals("ERROR\tunauthorized", socket.getInputStream().bufferedReader().readLine())
            }
            assertNull(BridgeClient(port = port, token = "wrong-token").requestTestAction("{}"))
            assertEquals(0, calls.get())
        }
    }

    private suspend fun withServer(block: suspend (Int, AtomicInteger) -> Unit) {
        val port = ServerSocket(0).use { it.localPort }
        val calls = AtomicInteger()
        val server = EventServer(port, "test-token", object : EventServer.Listener {
            override fun onReloadRequested() {}
            override fun onShutdownRequested() {}
            override fun onRunRequested(payloadJson: String): String = error("Must not use RUN or its telemetry")
            override fun onPressSendRequested(payloadJson: String): String = error("Unexpected PRESS_SEND")
            override fun onTestActionRequested(payloadJson: String): String {
                calls.incrementAndGet()
                return ActionTestResult(false, 7, null, 20, false, listOf("private\toutput", "last line")).toWire()
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
