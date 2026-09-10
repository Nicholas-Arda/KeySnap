package com.example.service.adb

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * This project keeps device IP addresses out of logs in every build type, debug included, because
 * `bridge.log` and logcat both outlive the app. The rule is easy to break by accident: the
 * address is right there in scope at every one of these call sites, and a log line that prints
 * it looks like ordinary diagnostics. It had in fact been broken in three places at once.
 *
 * This is a source scan rather than a runtime assertion because the leak is in the format string
 * itself — there is nothing to observe at runtime unless the pairing flow actually runs against a
 * real device, which the JVM suite cannot do.
 */
class LogsOmitDeviceAddressTest {

    /** Interpolations that carry a network address rather than a port or a status. */
    private val addressInterpolation =
        Regex("""Log\.[dviwe]\([^)]*\$\{?(host|ip|Ip|IP|address|Address|inet|Inet)""")

    /**
     * `BridgeClient` connects to `BridgeProtocol.HOST`, a loopback constant that is the same on
     * every device and reveals nothing about the user's network. Everything else that has a host
     * in scope got it from mDNS, which means it is the real address.
     */
    private val loopbackOnly = setOf("BridgeClient.kt")

    @Test fun noLogLineInterpolatesADeviceAddress() {
        val offenders = sourceFiles()
            .filter { it.name !in loopbackOnly }
            .flatMap { file ->
                file.readLines().withIndex()
                    .filter { (_, line) -> addressInterpolation.containsMatchIn(line) }
                    .map { (index, line) -> "${file.name}:${index + 1}  ${line.trim()}" }
            }
        assertTrue(
            "these log lines interpolate a device address, which must not reach logcat in any " +
                "build type — log the port and the outcome instead:\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    private fun sourceFiles(): List<File> =
        listOf(File("src/main/java"), File("../bridge/src/main/java"))
            .filter { it.isDirectory }
            .flatMap { it.walkTopDown().filter { f -> f.extension == "kt" || f.extension == "java" } }
}
