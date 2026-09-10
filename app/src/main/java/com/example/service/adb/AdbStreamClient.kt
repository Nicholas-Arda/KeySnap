package com.example.service.adb

import android.content.Context
import android.util.Log
import io.github.muntashirakon.adb.AdbStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.InputStream

class AdbStreamClient(private val context: Context) {

    companion object {
        private const val TAG = "AdbStreamClient"
    }

    private val sessionLock = Any()
    private var activeStream: AdbStream? = null
    private var sessionGeneration = 0L

    suspend fun startShellStream(
        host: String = "127.0.0.1",
        connectPort: Int,
        command: String = "getevent -l",
        onLineReceived: (String) -> Unit,
        onConnected: () -> Unit = {},
        onError: (String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val session = beginSession()
        var stream: AdbStream? = null
        try {
            // [host] is left out of every log line in this class: WirelessAdbManager passes the
            // device's real address on the user's network, which this project keeps out of logs in
            // every build type. Port, command and failure detail stay, so the diagnostics this
            // class exists for are unaffected.
            Log.d(TAG, "Connecting with libadb on port $connectPort for '$command'")
            val manager = LibAdbConnectionManager.get(context)
            if (!manager.connect(host, connectPort)) {
                error("ADB connection was rejected by the daemon")
            }
            if (!isCurrentSession(session)) return@withContext

            stream = manager.openStream("shell:$command")
            if (!installStream(session, stream)) return@withContext
            onConnected()

            readLines(stream.openInputStream(), session, onLineReceived)
        } catch (cancelled: CancellationException) {
            Log.d(TAG, "ADB stream session $session cancelled")
            throw cancelled
        } catch (error: Exception) {
            if (isCurrentSession(session) && currentCoroutineContext().isActive) {
                var cause: Throwable? = error
                while (cause != null && cause !is java.net.ConnectException) {
                    cause = cause.cause
                }
                val detail = cause?.message ?: error.localizedMessage ?: error.message ?: error.javaClass.simpleName
                Log.e(TAG, "ADB shell stream failed: $detail", error)
                onError("ADB Stream Error: $detail")
            }
        } finally {
            runCatching { stream?.close() }
            clearSession(session)
        }
    }

    /**
     * Runs one short-lived shell command and returns once [completeWhen] matches a line or
     * [timeoutMs] elapses, closing the stream either way.
     *
     * A detached spawn deliberately leaves `adb` with nothing more to send: the child holds no
     * standard stream, so the shell never reports EOF and [startShellStream] would block forever.
     * Completing on a marker line and then closing is what lets the app drop the ADB connection
     * while the spawned bridge keeps running.
     */
    suspend fun runCommand(
        host: String,
        connectPort: Int,
        command: String,
        timeoutMs: Long = 20_000,
        onLine: (String) -> Unit = {},
        completeWhen: (String) -> Boolean,
    ): Boolean = withContext(Dispatchers.IO) {
        val session = beginSession()
        var stream: AdbStream? = null
        try {
            Log.d(TAG, "Running one-shot command on port $connectPort")
            val manager = LibAdbConnectionManager.get(context)
            if (!manager.connect(host, connectPort)) {
                error("ADB connection was rejected by the daemon")
            }
            if (!isCurrentSession(session)) return@withContext false

            stream = manager.openStream("shell:$command")
            if (!installStream(session, stream)) return@withContext false

            val completed = withTimeoutOrNull(timeoutMs) {
                readUntil(stream.openInputStream(), session, onLine, completeWhen)
            }
            // A timeout is not a failure here: the marker may be lost when the shell's stdout is
            // taken over by the detached child. The caller confirms success by probing the bridge.
            completed ?: true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val detail = error.localizedMessage ?: error.message ?: error.javaClass.simpleName
            Log.e(TAG, "ADB one-shot command failed: $detail", error)
            false
        } finally {
            runCatching { stream?.close() }
            clearSession(session)
        }
    }

    private fun readUntil(
        input: InputStream,
        session: Long,
        onLine: (String) -> Unit,
        completeWhen: (String) -> Boolean,
    ): Boolean {
        val bytes = ByteArray(8192)
        val lineBuffer = StringBuilder()
        while (isCurrentSession(session)) {
            val count = input.read(bytes)
            if (count < 0) return true
            lineBuffer.append(String(bytes, 0, count, Charsets.UTF_8))
            var newline = lineBuffer.indexOf('\n')
            while (newline >= 0) {
                val line = lineBuffer.substring(0, newline).trim()
                lineBuffer.delete(0, newline + 1)
                if (line.isNotEmpty()) {
                    onLine(line)
                    if (completeWhen(line)) return true
                }
                newline = lineBuffer.indexOf('\n')
            }
        }
        return false
    }

    fun stop() {
        val streamToClose = synchronized(sessionLock) {
            sessionGeneration++
            activeStream.also { activeStream = null }
        }
        runCatching { streamToClose?.close() }
        runCatching { LibAdbConnectionManager.get(context).disconnect() }
    }

    private fun readLines(
        input: InputStream,
        session: Long,
        onLineReceived: (String) -> Unit,
    ) {
        val bytes = ByteArray(8192)
        val lineBuffer = StringBuilder()
        while (isCurrentSession(session)) {
            val count = input.read(bytes)
            if (count < 0) break
            lineBuffer.append(String(bytes, 0, count, Charsets.UTF_8))
            var newline = lineBuffer.indexOf('\n')
            while (newline >= 0) {
                val line = lineBuffer.substring(0, newline).trim()
                lineBuffer.delete(0, newline + 1)
                if (line.isNotEmpty()) onLineReceived(line)
                newline = lineBuffer.indexOf('\n')
            }
        }
    }

    private fun beginSession(): Long {
        val oldStream: AdbStream?
        val session: Long
        synchronized(sessionLock) {
            oldStream = activeStream
            activeStream = null
            session = ++sessionGeneration
        }
        runCatching { oldStream?.close() }
        return session
    }

    private fun installStream(session: Long, stream: AdbStream): Boolean = synchronized(sessionLock) {
        if (sessionGeneration != session) {
            runCatching { stream.close() }
            false
        } else {
            activeStream = stream
            true
        }
    }

    private fun isCurrentSession(session: Long): Boolean = synchronized(sessionLock) {
        sessionGeneration == session
    }

    private fun clearSession(session: Long) {
        synchronized(sessionLock) {
            if (sessionGeneration == session) activeStream = null
        }
    }
}
