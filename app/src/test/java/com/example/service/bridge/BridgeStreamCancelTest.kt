package com.example.service.bridge

import com.example.bridge.BridgeProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
class BridgeStreamCancelTest {

    /** A blocking read ignores cancellation, so a cancelled stream used to keep its thread and connection forever. */
    @Test fun cancellingAStreamClosesItsConnection() {
        org.robolectric.shadows.ShadowLog.stream = System.out
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val connected = CountDownLatch(1)
        val clientClosed = CountDownLatch(1)
        var accepted: Socket? = null
        thread(isDaemon = true) {
            val socket = server.accept().also { accepted = it }
            val reader = socket.getInputStream().bufferedReader()
            reader.readLine() // HELLO
            socket.getOutputStream().write("${BridgeProtocol.MSG_OK}\t${BridgeProtocol.VERSION}\t1\t0\t\n".toByteArray())
            connected.countDown()
            // A healthy bridge with no key presses: never writes again.
            if (reader.read() == -1) clientClosed.countDown()
        }
        try {
            runBlocking {
                val client = BridgeClient(host = "127.0.0.1", port = server.localPort, token = "t")
                // Outside runBlocking, so a stream that never ends fails the timeout instead of hanging the test.
                val job = CoroutineScope(Dispatchers.IO).launch { client.stream(object : BridgeClient.Listener {}) }
                assertTrue("handshake", connected.await(5, TimeUnit.SECONDS))
                Thread.sleep(200)
                assertTrue("streaming", job.isActive)

                job.cancel()
                withTimeout(3_000) { job.join() }
            }
            assertTrue("the bridge saw the connection close", clientClosed.await(3, TimeUnit.SECONDS))
        } finally {
            runCatching { accepted?.close() }
            server.close()
        }
    }
}
