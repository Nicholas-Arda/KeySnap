package com.example.service.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.bridge.BridgeAuth
import com.example.bridge.BridgeProtocol
import com.example.bridge.EventServer
import com.example.data.KeyMappingConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
class BridgeHandshakeTest {

    /** The bug: the client wrote `HELLO <token>` to whatever held the port, before it proved anything. */
    @Test fun probeNeverSendsTheTokenToWhateverAnswers() = runBlocking {
        RecordingServer { _, reader, writer ->
            reader.readLine()
            writer.line("ERROR\tunauthorized")
        }.use { server ->
            assertNull(BridgeClient(port = server.port, token = TOKEN).probe())
            assertFalse(server.next().contains(TOKEN))
        }
    }

    @Test fun aNormalSessionNeverSendsTheToken() = runBlocking {
        RecordingServer { socket, reader, writer ->
            if (acceptLoginForTest(socket, reader, writer, TOKEN) && reader.readLine()?.startsWith("RUN\t") == true) {
                writer.line("OK\t1\t1\tdelay=1")
            }
        }.use { server ->
            val client = BridgeClient(port = server.port, token = TOKEN)
            assertEquals(BridgeProtocol.VERSION, client.probe()?.protocolVersion)
            assertEquals(1, client.requestRun("{\"actions\":[]}")?.ran)
            repeat(2) { assertFalse(server.next().contains(TOKEN)) }
        }
    }

    @Test fun aServerThatCannotProveTheTokenGetsNothingAfterHello2() = runBlocking {
        RecordingServer { socket, reader, writer -> acceptLoginForTest(socket, reader, writer, "some-other-token") }.use { server ->
            assertNull(BridgeClient(port = server.port, token = TOKEN).probe())
            assertTrue(server.next().matches(HELLO2_ONLY))
        }
    }

    /**
     * The relay: something holds the port while no bridge is bound, keeps the app's connection after
     * the real bridge binds, and replays each handshake line to the bridge over its own connection.
     */
    @Test fun aRelayedHandshakeAuthenticatesNeitherSide() = withRealServer { bridgePort ->
        Relay(bridgePort).use { relay ->
            val result = BridgeClient(port = relay.port, token = TOKEN).probe()
            relay.await()
            assertFalse("the relayed connection was authenticated", relay.authenticated)
            assertNull(result)
            assertFalse(relay.forwardedAuth)
        }
    }

    /** The same relay from 127.0.0.2 with the app's own port number: the address counts, not just the port. */
    @Test fun aRelayFromAnotherLoopbackAddressWithTheAppsPortFails() = withRealServer { bridgePort ->
        Relay(bridgePort) { app -> InetSocketAddress("127.0.0.2", app.port) }.use { relay ->
            val result = BridgeClient(port = relay.port, token = TOKEN).probe()
            relay.await()
            assumeTrue("cannot connect from 127.0.0.2 with that port here", relay.connected)
            assertFalse("the relayed connection was authenticated", relay.authenticated)
            assertNull(result)
            assertFalse(relay.forwardedAuth)
        }
    }

    @Test fun theRealServerAcceptsOnlyTheRightProofAndNeverTheOldHello() = withRealServer { port ->
        assertEquals(BridgeProtocol.VERSION, BridgeClient(port = port, token = TOKEN).probe()?.protocolVersion)
        assertNull(BridgeClient(port = port, token = "wrong-token").probe())

        // A wrong token, then the right token in a proof naming another endpoint than this connection's.
        for ((proofToken, ownEnd) in listOf("wrong-token" to true, TOKEN to false)) {
            raw(port) { socket, reader, writer ->
                val clientNonce = BridgeAuth.newNonce()
                writer.line("HELLO2\t$clientNonce")
                val serverNonce = reader.readLine().split('\t')[1]
                val endpoint = BridgeAuth.endpoint(socket.localAddress, socket.localPort + if (ownEnd) 0 else 1)
                writer.line("AUTH\t${BridgeAuth.proof(proofToken, BridgeAuth.ROLE_CLIENT, endpoint, clientNonce, serverNonce)}")
                assertEquals("ERROR\tunauthorized", reader.readLine())
                assertNull(reader.readLine())
            }
        }
        raw(port) { _, reader, writer ->
            writer.line("HELLO\t$TOKEN")
            assertEquals("ERROR\tunauthorized", reader.readLine())
            assertNull(reader.readLine())
        }
        raw(port) { socket, reader, writer ->
            val endpoint = BridgeAuth.endpoint(socket.localAddress, socket.localPort)
            writer.line("AUTH\t${BridgeAuth.proof(TOKEN, BridgeAuth.ROLE_CLIENT, endpoint, "", "")}")
            assertEquals("ERROR\tunauthorized", reader.readLine())
        }
    }

    /**
     * A pre-63 bridge polls [BridgeProtocol.LEGACY_CONFIG_NAME] and exits once it is gone. The first
     * payload a process builds deletes it, and nothing of ours writes that name again, in any state.
     */
    @Test fun aLegacyConfigGoesOnFirstTouchAndNeverComesBack() = withLegacyBridge(configOnDisk = false, withContext = true) { payload, server ->
        val context: Context = ApplicationProvider.getApplicationContext()
        val legacy = File(payload.payloadDir()!!, BridgeProtocol.LEGACY_CONFIG_NAME)
        // A key-less run or alarm, a place's side change, a widget or tile tap: each may be a cold process's first touch.
        val coldEntries = listOf<suspend () -> Unit>(
            { BridgeController.adoptIfInstalled(context) },
            { BridgeController.publishConfigIfInstalled(context) },
            { BridgeController.ensureAdopted(context) },
        )
        for (entry in coldEntries) {
            legacy.writeText("{}")
            payloadField().set(null, null)
            entry()
            assertFalse(legacy.exists())
            assertTrue(BridgeController.takeStaleReplacement())
        }

        assertFalse(BridgeController.start { _, _ -> false })
        for (state in BridgeState.entries) {
            stateFlow().value = state
            BridgeController.publishConfig()
            BridgeController.publishConfigIfInstalled(context)
        }
        payloadField().set(null, null)
        BridgeController.publishConfigIfInstalled(context)

        assertTrue(payload.hasConfig())
        assertFalse(legacy.exists())
        assertFalse(BridgeController.takeStaleReplacement())
        assertSentNoSecret(server.rest())
    }

    /** `ERROR unauthorized`, from a pre-63 bridge or anything posing as one, changes nothing on disk. */
    @Test fun aLegacyAnswerChangesNothingOnDisk() = withLegacyBridge(configOnDisk = true, withContext = true) { payload, server ->
        val config = payload.configFile()!!.readText()
        val script = File(payload.payloadDir()!!, "start.sh").apply { writeText("kept") }
        for (state in listOf(BridgeState.STOPPED, BridgeState.STARTING, BridgeState.RUNNING)) {
            stateFlow().value = state

            assertFalse(BridgeController.detectExistingBridge())

            assertEquals("config touched while $state", config, payload.configFile()!!.readText())
            assertTrue(script.exists())
            assertFalse(BridgeController.takeStaleReplacement())
        }
        assertSentNoSecret(server.rest())
    }

    /** A start that fails, or that the restart cap cuts off mid-spawn, leaves the config for what it may have launched. */
    @Test fun aFailedOrCancelledStartLeavesItsConfig() = withLegacyBridge(configOnDisk = false, withContext = true) { payload, server ->
        assertFalse(BridgeController.start { _, _ -> false })
        assertTrue(payload.hasConfig())

        assertTrue(payload.configFile()!!.delete())
        coroutineScope {
            val spawning = CompletableDeferred<Unit>()
            val start = launch { BridgeController.start { _, _ -> spawning.complete(Unit); awaitCancellation() } }
            spawning.await()
            start.cancelAndJoin()
        }
        assertTrue(payload.hasConfig())
        assertSentNoSecret(server.rest())
    }

    /** "Off" is not held up by whatever answers `ERROR unauthorized`, with or without a config on disk. */
    @Test fun stopIgnoresAnUnauthorizedAnswer() {
        for (configOnDisk in listOf(true, false)) {
            withLegacyBridge(configOnDisk = configOnDisk) { payload, server ->
                val other = File(payload.payloadDir()!!, "start.sh").apply { writeText("removed") }

                assertTrue(BridgeController.stop())

                assertFalse(payload.hasConfig())
                assertFalse(other.exists())
                assertEquals(BridgeState.STOPPED, BridgeController.state.value)
                assertSentNoSecret(server.rest())
            }
        }
    }

    /** Our bridge obeyed SHUTDOWN, then something else took its port: that is "off", not "still running". */
    @Test fun stopAfterShutdownIgnoresAListenerThatTookThePort() = withLegacyBridge(
        configOnDisk = true,
        respond = { payload ->
            var shutDown = false
            { socket, reader, writer ->
                if (shutDown) {
                    reader.readLine()
                    writer.line("ERROR\tunauthorized")
                } else if (acceptLoginForTest(socket, reader, writer, payload.controlToken) && reader.readLine() == "SHUTDOWN") {
                    writer.line("BYE")
                    shutDown = true
                }
            }
        },
    ) { payload, _ ->
        assertTrue(BridgeController.stop())

        assertFalse(payload.hasConfig())
        assertEquals(BridgeState.STOPPED, BridgeController.state.value)
        assertEquals("Advanced Mode stopped.", BridgeController.statusMessage.value)
    }

    /**
     * Runs [block] with [BridgeController] pointed at a protocol-62 bridge emulation on a port in the
     * payload's range, with the pre-63 token stored the way old versions kept it. The emulation
     * answers every login `ERROR unauthorized`, as a v62 bridge answers HELLO2, and never goes away:
     * nothing may wait for it to. [withContext] gives [BridgeController] a Context, which
     * [BridgeController.publishConfig] needs; without one, stop() skips the autostart bookkeeping.
     */
    private fun withLegacyBridge(
        configOnDisk: Boolean,
        withContext: Boolean = false,
        respond: (BridgePayload) -> (Socket, BufferedReader, BufferedWriter) -> Unit = {
            { _, reader, writer ->
                reader.readLine()
                writer.line("ERROR\tunauthorized")
            }
        },
        block: suspend (BridgePayload, RecordingServer) -> Unit,
    ) = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val socket = (38_200 until 38_700).firstNotNullOf { port ->
            runCatching { ServerSocket(port, 50, InetAddress.getLoopbackAddress()) }.getOrNull()
        }
        context.getSharedPreferences("arda_mapper_bridge_prefs", Context.MODE_PRIVATE).edit().clear()
            .putInt("control_port", socket.localPort).putString("control_token", OLD_TOKEN).commit()
        val payload = BridgePayload(context)
        payload.uninstall()
        if (configOnDisk) assertTrue(payload.writeConfig(emptyList(), true, KeyMappingConfig()))
        val server = RecordingServer(socket, respond(payload))
        val fields = listOf("payload", "appContext").map { name ->
            BridgeController::class.java.getDeclaredField(name).apply { isAccessible = true }
        }
        val previous = fields.map { it.get(null) }
        fields[0].set(null, payload)
        fields[1].set(null, if (withContext) context else null)
        BridgeController.markStopped("Advanced Mode is off.")
        try {
            server.use { block(payload, it) }
        } finally {
            fields.zip(previous).forEach { (field, value) -> field.set(null, value) }
            BridgeController.markStopped("Advanced Mode is off.")
            BridgeController.staleReplaced.set(false)
            payload.uninstall()
        }
    }

    private fun payloadField() = BridgeController::class.java.getDeclaredField("payload").apply { isAccessible = true }

    @Suppress("UNCHECKED_CAST")
    private fun stateFlow() = BridgeController::class.java.getDeclaredField("_state")
        .apply { isAccessible = true }.get(null) as MutableStateFlow<BridgeState>

    /** No token of any age, and never the old cleartext HELLO. */
    private fun assertSentNoSecret(sessions: List<String>) {
        assertTrue(sessions.isNotEmpty())
        val token = BridgePayload(ApplicationProvider.getApplicationContext()).controlToken
        for (sent in sessions) {
            assertFalse(sent.contains(token))
            assertFalse(sent.contains(OLD_TOKEN))
            assertFalse(sent.contains("HELLO\t"))
            assertTrue(sent.matches(HELLO2_ONLY))
        }
    }

    /** Runs [block] against a real [EventServer] holding [TOKEN]. */
    private fun withRealServer(block: suspend (Int) -> Unit) = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val server = EventServer(port, TOKEN, object : EventServer.Listener {
            override fun onReloadRequested() {}
            override fun onShutdownRequested() {}
            override fun onRunRequested(payloadJson: String): String = error("unexpected RUN")
            override fun onPressSendRequested(payloadJson: String): String = error("unexpected PRESS_SEND")
        })
        server.bind()
        val worker = thread(isDaemon = true) { server.run() }
        try {
            block(port)
        } finally {
            server.stop()
            worker.join(1_000)
        }
    }

    private fun raw(port: Int, block: (Socket, BufferedReader, BufferedWriter) -> Unit) {
        Socket(BridgeProtocol.HOST, port).use { socket ->
            socket.soTimeout = 2_000
            block(socket, socket.getInputStream().bufferedReader(), socket.getOutputStream().bufferedWriter())
        }
    }

    private companion object {
        const val TOKEN = "0123456789abcdef0123456789abcdef"
        const val OLD_TOKEN = "fedcba9876543210fedcba9876543210"
        val HELLO2_ONLY = Regex("HELLO2\t[0-9a-f]{32}\n")
    }
}

/**
 * Accepts one app connection and replays its handshake to the bridge on [target] over a second
 * connection, made from [from] when given, forwarding each answer back.
 */
private class Relay(private val target: Int, private val from: (Socket) -> InetSocketAddress? = { null }) : AutoCloseable {
    private val listener = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
    private val done = java.util.concurrent.CountDownLatch(1)
    val port: Int = listener.localPort

    /** The relay's own connection to the bridge is up. */
    @Volatile var connected = false

    /** The app sent AUTH, and the relay passed it on. */
    @Volatile var forwardedAuth = false

    /** The bridge answered OK on the relay's own connection. */
    @Volatile var authenticated = false

    init {
        thread(isDaemon = true) {
            runCatching {
                listener.accept().use { app ->
                    Socket().use { bridge ->
                        from(app)?.let(bridge::bind)
                        bridge.connect(InetSocketAddress(BridgeProtocol.HOST, target))
                        connected = true
                        app.soTimeout = 2_000
                        bridge.soTimeout = 2_000
                        val appIn = app.getInputStream().bufferedReader()
                        val appOut = app.getOutputStream().bufferedWriter()
                        val bridgeIn = bridge.getInputStream().bufferedReader()
                        val bridgeOut = bridge.getOutputStream().bufferedWriter()
                        bridgeOut.line(appIn.readLine()) // HELLO2
                        appOut.line(bridgeIn.readLine()) // CHALLENGE
                        val auth = appIn.readLine()
                        if (auth != null) {
                            forwardedAuth = true
                            bridgeOut.line(auth)
                            val reply = bridgeIn.readLine().orEmpty()
                            authenticated = reply.startsWith("OK\t")
                            appOut.line(reply)
                        }
                    }
                }
            }
            done.countDown()
        }
    }

    fun await() = assertTrue(done.await(5, TimeUnit.SECONDS))

    override fun close() = listener.close()
}

private fun BufferedWriter.line(text: String) {
    write(text)
    write("\n")
    flush()
}

/** Loopback server that serves one connection at a time and keeps every byte each client sent. */
private class RecordingServer(
    private val server: ServerSocket = ServerSocket(0, 50, InetAddress.getLoopbackAddress()),
    private val respond: (Socket, BufferedReader, BufferedWriter) -> Unit,
) : AutoCloseable {
    private val transcripts = LinkedBlockingQueue<String>()
    val port: Int = server.localPort

    init {
        thread(isDaemon = true) {
            while (true) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                socket.use {
                    it.soTimeout = 2_000
                    val sink = ByteArrayOutputStream()
                    val input = Recording(it.getInputStream(), sink)
                    runCatching {
                        respond(it, input.bufferedReader(), it.getOutputStream().bufferedWriter())
                        // Whatever the client sends after the scripted exchange counts too.
                        it.soTimeout = 500
                        while (input.read() >= 0) Unit
                    }
                    transcripts.put(synchronized(sink) { sink.toString(Charsets.UTF_8.name()) })
                }
            }
        }
    }

    /** Everything the next finished connection sent. */
    fun next(): String = transcripts.poll(5, TimeUnit.SECONDS) ?: error("no connection finished")

    /** Every connection still to finish, once none has for a second. */
    fun rest(): List<String> = generateSequence { transcripts.poll(1, TimeUnit.SECONDS) }.toList()

    override fun close() = server.close()

    private class Recording(input: InputStream, private val sink: ByteArrayOutputStream) : FilterInputStream(input) {
        override fun read(): Int = super.read().also { if (it >= 0) synchronized(sink) { sink.write(it) } }

        override fun read(b: ByteArray, off: Int, len: Int): Int =
            super.read(b, off, len).also { if (it > 0) synchronized(sink) { sink.write(b, off, it) } }
    }
}
