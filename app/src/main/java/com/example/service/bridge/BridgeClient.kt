package com.example.service.bridge

import android.util.Log
import com.example.bridge.BridgeProtocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

/** A live key transition observed by the bridge. */
data class BridgeKeyEvent(
    val timestamp: Long,
    val keyCode: Int,
    val isDown: Boolean,
    val isMapped: Boolean,
    val source: String,
)

/** A script the bridge matched and executed. */
data class BridgeTrigger(
    val timestamp: Long,
    val keyCode: Int,
    val pressType: String,
    val scriptId: String,
    val success: Boolean,
    /** The bridge's per-action results in script order; null from a bridge older than protocol v11. */
    val actionResults: List<Pair<String, Boolean>>? = null,
)

/** The bridge's answer to a RUN: [ran] of [total] actions succeeded. */
data class BridgeRunResult(
    val ran: Int,
    val total: Int,
    /** Per-action results in chain order; null from a bridge older than protocol v11. */
    val actionResults: List<Pair<String, Boolean>>?,
)

/**
 * Parses the bridge's RUN reply; null unless it is an OK for a non-empty chain. The bridge answers
 * `0 0` when it could not parse the payload, and a chain always has an action, so that reads as
 * "not run" and the caller runs the chain itself.
 */
internal fun parseRunReply(line: String?): BridgeRunResult? {
    val fields = line?.split('\t').orEmpty()
    if (fields.firstOrNull() != BridgeProtocol.MSG_OK || fields.size < 3) return null
    val total = fields[2].toIntOrNull() ?: 0
    if (total == 0) return null
    return BridgeRunResult(
        ran = fields[1].toIntOrNull() ?: 0,
        total = total,
        actionResults = decodeActionResults(fields.getOrNull(3)),
    )
}

/**
 * The reply to the command just sent. Every authenticated connection also receives the EVENT, TRIGGER
 * and DEVICE lines the bridge pushes, and a key press during a long command lands one first; those are
 * skipped. Even the handshake can be preceded by one, since the bridge marks a connection logged in
 * just before it answers HELLO. Null when the stream ends.
 */
internal fun readReply(nextLine: () -> String?): String? {
    while (true) {
        val line = nextLine() ?: return null
        if (line.substringBefore('\t') !in PUSHED) return line
    }
}

private val PUSHED = setOf(BridgeProtocol.MSG_EVENT, BridgeProtocol.MSG_TRIGGER, BridgeProtocol.MSG_DEVICE)

/** The bridge's identity from its HELLO answer, or null unless it is an OK carrying one. */
internal fun parseHandshake(line: String?): BridgeStatus? {
    val fields = line?.split('\t').orEmpty()
    if (fields.firstOrNull() != BridgeProtocol.MSG_OK || fields.size < 4) return null
    return BridgeStatus(
        protocolVersion = fields[1].toIntOrNull() ?: 0,
        pid = fields[2].toIntOrNull() ?: 0,
        startedAtMillis = fields[3].toLongOrNull() ?: 0L,
        status = fields.getOrNull(4).orEmpty(),
    )
}

/**
 * Whether a SHUTDOWN was acknowledged: BYE, or the bridge closing the connection as it exits. Only a
 * request; [BridgeController.stop] still checks that the port stopped answering.
 */
internal fun isShutdownAck(line: String?): Boolean = line == null || line.startsWith(BridgeProtocol.MSG_BYE)

/** The outcome word of a PRESS_SEND reply, or null for anything but an OK. */
internal fun parsePressSendReply(line: String?): String? {
    val fields = line?.split('\t').orEmpty()
    return if (fields.firstOrNull() == BridgeProtocol.MSG_OK) fields.getOrNull(1) else null
}

private fun decodeActionResults(field: String?): List<Pair<String, Boolean>>? =
    field?.let { BridgeProtocol.decodeActionResults(it) }?.map { it.key to it.value }

/** The bridge's identity, returned by a successful handshake. */
data class BridgeStatus(
    val protocolVersion: Int,
    val pid: Int,
    val startedAtMillis: Long,
    val status: String,
)

/**
 * Loopback client for the detached bridge.
 *
 * A successful handshake is the app's definition of "Advanced Mode is running": the bridge owns
 * the port, so answering it proves the privileged process is alive. Nothing here keeps the
 * bridge running — dropping the connection, or the whole app dying, does not affect it.
 */
class BridgeClient(
    private val host: String = BridgeProtocol.HOST,
    private val port: Int,
    private val token: String,
) {

    companion object {
        private const val TAG = "BridgeClient"
        private const val CONNECT_TIMEOUT_MS = 1_500
        private const val HANDSHAKE_TIMEOUT_MS = 2_000
        /** The bridge answers BYE at once; the caller then confirms the port really stopped answering. */
        private const val SHUTDOWN_REPLY_TIMEOUT_MS = 3_000
        // The bridge waits up to 30 s for the chat to come to the front (an OEM may hold it behind an
        // "allow opening" dialog), then up to 8 s to press; one uiautomator dump can itself take
        // seconds, and it dumps once more after tapping.
        private const val PRESS_SEND_TIMEOUT_MS = 60_000
        private const val SEP = '\t'
    }

    /** Messages pushed by the bridge over an authenticated connection. */
    interface Listener {
        fun onKeyEvent(event: BridgeKeyEvent) {}
        fun onTrigger(trigger: BridgeTrigger) {}
        fun onDevice(path: String, name: String) {}
    }

    private class Session(
        val socket: Socket,
        val reader: BufferedReader,
        val writer: BufferedWriter,
        val status: BridgeStatus,
    ) : AutoCloseable {
        override fun close() {
            runCatching { socket.close() }
        }
    }

    /** One-shot liveness probe. Returns null when no authenticated bridge answers. */
    suspend fun probe(): BridgeStatus? = withContext(Dispatchers.IO) {
        openSession()?.use { it.status }
    }

    /** Asks the bridge to re-read its config file immediately instead of waiting for the poll. */
    suspend fun requestReload(): Boolean = withContext(Dispatchers.IO) {
        openSession()?.use { session ->
            runCatching {
                session.writer.writeLine(BridgeProtocol.CMD_RELOAD)
                session.reader.readLine() != null
            }.getOrDefault(false)
        } ?: false
    }

    /**
     * Runs one action chain in the bridge now. Null when no bridge answered or the reply was not
     * an OK, which leaves the caller to run the chain itself.
     */
    // ponytail: a reply lost after the RUN was sent (the bridge dying mid-chain) also reads as null,
    // so the caller may repeat actions the bridge already ran; return a "sent, outcome unknown"
    // result instead if that ever shows up.
    suspend fun requestRun(payloadJson: String): BridgeRunResult? = withContext(Dispatchers.IO) {
        openSession()?.use { session ->
            runCatching {
                session.writer.writeLine("${BridgeProtocol.CMD_RUN}$SEP$payloadJson")
                parseRunReply(readReply(session.reader::readLine))
            }.getOrNull()
        }
    }

    /**
     * Asks the bridge to tap Send in the chat just opened. Its outcome word (see
     * [BridgeProtocol.PRESS_SEND_OK]), or null when no answer came. The payload carries the message
     * and the recipient: never log it.
     */
    suspend fun requestPressSend(payloadJson: String): String? = withContext(Dispatchers.IO) {
        openSession()?.use { session ->
            runCatching {
                // The bridge gives up after its own deadline; this only guards a bridge that hangs.
                session.socket.soTimeout = PRESS_SEND_TIMEOUT_MS
                session.writer.writeLine("${BridgeProtocol.CMD_PRESS_SEND}$SEP$payloadJson")
                parsePressSendReply(readReply(session.reader::readLine))
            }.getOrNull()
        }
    }

    /**
     * Asks the bridge to terminate. Returns false when no bridge was reachable or it did not
     * acknowledge; either way, only the port no longer answering means it stopped.
     */
    suspend fun requestShutdown(): Boolean = withContext(Dispatchers.IO) {
        openSession()?.use { session ->
            runCatching {
                session.socket.soTimeout = SHUTDOWN_REPLY_TIMEOUT_MS
                session.writer.writeLine(BridgeProtocol.CMD_SHUTDOWN)
                isShutdownAck(readReply(session.reader::readLine))
            }.getOrDefault(false)
        } ?: false
    }

    /**
     * Streams telemetry until the coroutine is cancelled or the bridge closes the connection.
     * Returns the handshake result so the caller can distinguish "never connected" from "dropped".
     */
    suspend fun stream(listener: Listener): BridgeStatus? = withContext(Dispatchers.IO) {
        val session = openSession() ?: return@withContext null
        try {
            while (currentCoroutineContext().isActive) {
                val line = session.reader.readLine() ?: break
                dispatch(line, listener)
            }
            session.status
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.d(TAG, "Bridge stream ended: ${e.message}")
            session.status
        } finally {
            session.close()
        }
    }

    private fun dispatch(line: String, listener: Listener) {
        val fields = line.split(SEP)
        when (fields.firstOrNull()) {
            BridgeProtocol.MSG_EVENT -> if (fields.size >= 6) {
                listener.onKeyEvent(
                    BridgeKeyEvent(
                        timestamp = fields[1].toLongOrNull() ?: System.currentTimeMillis(),
                        keyCode = fields[2].toIntOrNull() ?: return,
                        isDown = fields[3] == "down",
                        isMapped = fields[4] == "1",
                        source = fields[5],
                    ),
                )
            }
            BridgeProtocol.MSG_TRIGGER -> if (fields.size >= 6) {
                listener.onTrigger(
                    BridgeTrigger(
                        timestamp = fields[1].toLongOrNull() ?: System.currentTimeMillis(),
                        keyCode = fields[2].toIntOrNull() ?: return,
                        pressType = fields[3],
                        scriptId = fields[4],
                        success = fields[5] == "1",
                        actionResults = decodeActionResults(fields.getOrNull(6)),
                    ),
                )
            }
            BridgeProtocol.MSG_DEVICE -> if (fields.size >= 3) {
                listener.onDevice(fields[1], fields[2])
            }
        }
    }

    private suspend fun openSession(): Session? {
        val socket = Socket()
        return try {
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            socket.tcpNoDelay = true
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
            writer.writeLine("${BridgeProtocol.CMD_HELLO}$SEP$token")

            // A blocking read ignores coroutine timeouts, so the socket enforces it; a stream session
            // then goes back to waiting indefinitely.
            socket.soTimeout = HANDSHAKE_TIMEOUT_MS
            val reply = readReply(reader::readLine)
            val status = parseHandshake(reply)
            if (status == null) {
                Log.d(TAG, "Bridge handshake rejected: $reply")
                runCatching { socket.close() }
                return null
            }
            socket.soTimeout = 0
            Session(socket = socket, reader = reader, writer = writer, status = status)
        } catch (cancelled: CancellationException) {
            runCatching { socket.close() }
            throw cancelled
        } catch (e: Exception) {
            // A refused connection is the normal "bridge not running" answer, not an error.
            Log.d(TAG, "No bridge on $host:$port (${e.javaClass.simpleName})")
            runCatching { socket.close() }
            null
        }
    }

    private fun BufferedWriter.writeLine(line: String) {
        write(line)
        write("\n")
        flush()
    }
}
