package com.example.service

import android.content.ComponentName
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccessibilityReconnectTest {
    private val own = ComponentName("io.github.nicholasarda.keysnap", "com.example.service.KeyMapperAccessibilityService")
    private val ownFull = own.flattenToString()
    private val other = "com.other/com.other.Svc"

    @Test(expected = IllegalArgumentException::class)
    fun refusesShellCharactersInTheComponent() {
        accessibilityReconnectCommand(ComponentName("a.b", "a.b.C';reboot;'"), 0)
    }

    @Test fun togglesOnlyOwnEntryAndKeepsOthers() {
        val run = runScript("$other:$ownFull:com.third/.T", enabled = "1")
        assertEquals(0, run.exit)
        // The intermediate write dropped only ours; the final one put it back after the others.
        assertEquals(listOf("$other:com.third/.T", "$other:com.third/.T:$ownFull"), run.writes)
        assertEquals("1", run.enabled)
    }

    @Test fun restoresOwnEntryWhenItWasTheOnlyOne() {
        val run = runScript(ownFull, enabled = "1")
        assertEquals(0, run.exit)
        assertEquals(listOf("<deleted>", ownFull), run.writes)
    }

    @Test fun neverAddsAnEntryTheUserRemoved() {
        val run = runScript(other, enabled = "1")
        assertEquals(3, run.exit)
        assertEquals(emptyList<String>(), run.writes)
    }

    @Test fun neverWritesBackAnUnexpectedRead() {
        val run = runScript("Error: settings provider busy; $ownFull", enabled = "1")
        assertEquals(6, run.exit)
        assertEquals(emptyList<String>(), run.writes)
    }

    private class Run(val exit: Int, val writes: List<String>, val enabled: String)

    /** Runs the real command against a fake `settings` backed by files, where a POSIX sh exists. */
    private fun runScript(services: String, enabled: String): Run {
        val sh = listOf("/bin/sh", "C:/Program Files/Git/bin/sh.exe").firstOrNull { File(it).canExecute() }
        assumeTrue("no POSIX sh on this machine", sh != null)
        val dir = Files.createTempDirectory("a11y").toFile()
        File(dir, "enabled_accessibility_services").writeText(services)
        File(dir, "accessibility_enabled").writeText(enabled)
        val d = dir.absolutePath.replace('\\', '/')
        val fake = """
            sleep() { :; }
            settings() { shift 2; f="$d/${'$'}3"; case ${'$'}1 in
              get) cat "${'$'}f" 2>/dev/null || echo null;;
              put) printf %s "${'$'}4" > "${'$'}f"; [ "${'$'}3" = accessibility_enabled ] || echo "${'$'}4" >> "$d/writes";;
              delete) rm -f "${'$'}f"; echo "<deleted>" >> "$d/writes";; esac; }
        """.trimIndent()
        val script = File(dir, "run.sh").apply { writeText(fake + "\n" + accessibilityReconnectCommand(own, 0) + "\n") }
        val process = ProcessBuilder(sh, script.absolutePath).redirectErrorStream(true).start()
        process.inputStream.readBytes()
        val exit = process.waitFor()
        val writes = File(dir, "writes").takeIf { it.exists() }?.readLines().orEmpty()
        val result = Run(exit, writes, File(dir, "accessibility_enabled").readText())
        dir.deleteRecursively()
        return result
    }
}
