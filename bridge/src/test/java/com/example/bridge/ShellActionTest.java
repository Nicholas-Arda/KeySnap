package com.example.bridge;

import org.junit.Test;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public class ShellActionTest {
    /** Portable real child process: no Android shell binary or device needed for lifecycle tests. */
    public static void main(String[] args) throws Exception {
        if (args[0].equals("wait")) {
            Thread.sleep(30_000);
        } else {
            for (int i = 0; i < 10_000; i++) System.out.println("line " + i);
            System.err.println("last stderr line");
            System.exit(7);
        }
    }

    private String[] childCommand(String mode) {
        return new String[]{new File(System.getProperty("java.home"), "bin/java").getPath(), "-cp",
                new File(java.net.URI.create(ShellActionTest.class.getProtectionDomain().getCodeSource().getLocation().toExternalForm())).getPath(),
                ShellActionTest.class.getName(), mode};
    }

    @Test public void realChildIsKilledAtDeadline() throws Exception {
        Process process = new ProcessBuilder(childCommand("wait")).redirectErrorStream(true).start();
        ActionTestResult result = ShellKeyInjector.awaitProcess(process, 1, true, System.nanoTime());
        assertTrue(result.timedOut);
        assertTrue(process.waitFor(2, TimeUnit.SECONDS));
        assertFalse(process.isAlive());
        assertTrue(result.durationMs < 4_000);
    }

    @Test public void realLargeOutputDoesNotDeadlockAndIncludesStderr() {
        ActionTestResult result = ShellKeyInjector.runShellUnlogged(10, true, childCommand("output"));
        assertEquals(Integer.valueOf(7), result.exitCode);
        assertEquals(20, result.output.size());
        assertEquals("last stderr line", result.output.get(19));
    }

    private static final class FakeProcess extends Process {
        final boolean finished;
        final int exit;
        final InputStream input;
        boolean killed;
        long timeout;
        FakeProcess(boolean finished, int exit, String output) {
            this.finished = finished;
            this.exit = exit;
            input = new ByteArrayInputStream(output.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        public InputStream getInputStream() { return input; }
        public InputStream getErrorStream() { return new ByteArrayInputStream(new byte[0]); }
        public OutputStream getOutputStream() { return new ByteArrayOutputStream(); }
        public int waitFor() { throw new AssertionError("Unbounded wait"); }
        public boolean waitFor(long timeout, TimeUnit unit) { this.timeout = unit.toSeconds(timeout); return finished; }
        public int exitValue() { if (!finished) throw new IllegalThreadStateException(); return exit; }
        public void destroy() { killed = true; }
        public Process destroyForcibly() { killed = true; return this; }
    }

    @Test public void everyTimeoutKillsTheProcessAndReturnsNoExitCode() {
        for (int timeout : new int[]{5, 10, 30, 60}) {
            FakeProcess process = new FakeProcess(false, 0, "partial\n");
            ActionTestResult result = ShellKeyInjector.awaitProcess(process, timeout, true, System.nanoTime());
            assertTrue(process.killed);
            assertEquals(timeout, process.timeout);
            assertTrue(result.timedOut);
            assertFalse(result.ok);
            assertNull(result.exitCode);
            assertTrue(result.durationMs >= 0);
        }
    }

    @Test public void captureKeepsOnlyLastTwentyLinesAndLiteralText() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 100; i++) text.append("  line ").append(i).append("  \n");
        ActionTestResult result = ShellKeyInjector.awaitProcess(new FakeProcess(true, 7, text.toString()), 10, true, System.nanoTime());
        assertFalse(result.ok);
        assertEquals(Integer.valueOf(7), result.exitCode);
        assertEquals(20, result.output.size());
        assertEquals("  line 80  ", result.output.get(0));
        assertEquals("  line 99  ", result.output.get(19));
    }

    @Test public void normalExecutionDrainsWithoutKeepingOutput() {
        ActionTestResult result = ShellKeyInjector.awaitProcess(new FakeProcess(true, 0, "secret\n"), 10, false, System.nanoTime());
        assertTrue(result.ok);
        assertTrue(result.output.isEmpty());
    }

    @Test public void aSingleHugeLineIsBoundedAndFinalUnterminatedLineIsIncluded() {
        ActionTestResult result = ShellKeyInjector.awaitProcess(new FakeProcess(true, 0, "x".repeat(100_000) + "last"), 10, true, System.nanoTime());
        assertEquals(2048, result.output.get(0).length());
        assertTrue(result.output.get(0).endsWith("last"));
    }

    @Test public void privateWireResultEscapesTabsNewlinesAndDoesNotLeakViaToString() throws Exception {
        ActionTestResult result = new ActionTestResult(true, 0, null, 123, false, List.of("secret\tvalue", "a\nb", "\"'\\"));
        assertFalse(result.toString().contains("secret"));
        assertFalse(result.toWire().contains("\n"));
        assertFalse(result.toWire().contains("\t"));
        ActionTestResult decoded = ActionTestResult.fromWire(result.toWire());
        assertEquals(result.output, decoded.output);
        assertEquals(Integer.valueOf(0), decoded.exitCode);
        assertNull(decoded.httpStatus);
        assertEquals(123, decoded.durationMs);
        assertTrue(decoded.ok);
        assertTrue(BridgeProtocol.VERSION >= 37);
    }
}
