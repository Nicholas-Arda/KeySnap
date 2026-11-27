package com.example.service.adb

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in phone probe: can the app's own ADB identity open a shell on adbd's TCP port? */
@RunWith(AndroidJUnit4::class)
class TcpModeProbeDeviceTest {
    @Test fun appIdentityOpensShellOnLoopbackTcpPort() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("tcpModeProbe") == "true")
        val port = args.getString("tcpPort")?.toIntOrNull() ?: 5555
        val manager = LibAdbConnectionManager.get(InstrumentationRegistry.getInstrumentation().targetContext)
        val connected = runCatching { manager.connect("127.0.0.1", port) }
            .onFailure { Log.e(TAG, "connect failed: ${adbFailureSummary(it)}") }
            .getOrDefault(false)
        Log.i(TAG, "connected=$connected")
        assertTrue("connect to 127.0.0.1:$port", connected)
        val out = manager.openStream("shell:id -u; echo tcp-probe-ok").openInputStream().bufferedReader().readText()
        Log.i(TAG, "shell output: ${out.trim().replace('\n', ' ')}")
        manager.disconnect()
        assertTrue(out.contains("tcp-probe-ok"))
    }

    @Test fun appCanReadAdbdTcpPort() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tcpModeProbe") == "true")
        val value = ProcessBuilder("getprop", "service.adb.tcp.port").redirectErrorStream(true).start()
            .inputStream.bufferedReader().readText().trim()
        Log.i(TAG, "service.adb.tcp.port as app='$value'")
    }

    private companion object { const val TAG = "TcpModeProbe" }
}
