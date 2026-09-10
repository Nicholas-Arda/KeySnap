package com.example.service.bridge

import android.util.Log
import com.example.bridge.BridgeProtocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
)

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
        private const val HANDSHAKE_TIMEOUT_MS = 2_000L
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
     * Runs one action chain in the bridge now. Returns how many of its actions succeeded out of
     * how many it ran, or null when no bridge answered.
     */
    suspend fun requestRun(payloadJson: String): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        openSession()?.use { session ->
            runCatching {
                session.writer.writeLine("${BridgeProtocol.CMD_RUN}$SEP$payloadJson")
                val fields = session.reader.readLine()?.split(SEP).orEmpty()
                if (fields.firstOrNull() != BridgeProtocol.MSG_OK || fields.size < 3) {
                    null
                } else {
                    (fields[1].toIntOrNull() ?: 0) to (fields[2].toIntOrNull() ?: 0)
                }
            }.getOrNull()
        }
    }

    /** Asks the bridge to terminate. Returns false when no bridge was reachable. */
    suspend fun requestShutdown(): Boolean = withContext(Dispatchers.IO) {
        openSession()?.use { session ->
            runCatching {
                session.writer.writeLine(BridgeProtocol.CMD_SHUTDOWN)
                val reply = session.reader.readLine()
                reply == null || reply.startsWith(BridgeProtocol.MSG_BYE)
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

            val reply = withTimeoutOrNull(HANDSHAKE_TIMEOUT_MS) { reader.readLine() }
            val fields = reply?.split(SEP).orEmpty()
            if (fields.firstOrNull() != BridgeProtocol.MSG_OK || fields.size < 4) {
                Log.d(TAG, "Bridge handshake rejected: $reply")
                runCatching { socket.close() }
                return null
            }
            Session(
                socket = socket,
                reader = reader,
                writer = writer,
                status = BridgeStatus(
                    protocolVersion = fields[1].toIntOrNull() ?: 0,
                    pid = fields[2].toIntOrNull() ?: 0,
                    startedAtMillis = fields[3].toLongOrNull() ?: 0L,
                    status = fields.getOrNull(4).orEmpty(),
                ),
            )
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
