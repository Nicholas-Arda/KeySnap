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
        } else if (args[0].equals("nap")) {
            Thread.sleep(2_000);
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

    /** Sensitive apps' two switches: one launch must wait for the slower command, not the sum. */
    @Test public void severalCommandsRunSideBySide() {
        long start = System.nanoTime();
        ActionTestResult[] results = ShellKeyInjector.runAllUnlogged(10, false,
                new String[][]{childCommand("nap"), null, childCommand("nap")});
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(results[0].ok);
        assertNull(results[1]);
        assertTrue(results[2].ok);
        assertTrue("took " + elapsedMs + " ms; one after the other takes at least 4000", elapsedMs < 4_000);
    }

    @Test public void realLargeOutputDoesNotDeadlockAndIncludesStderr() {
        ActionTestResult result = ShellKeyInjector.runShellUnlogged(10, true, childCommand("output"));
        assertEquals(Integer.valueOf(7), result.exitCode);
        assertEquals(20, result.output.size());
        assertEquals("last stderr line", result.output.get(19));
    }

    @Test public void editorTestCapturesFixedCommandExitAndMessage() {
        ActionTestResult[] slot = new ActionTestResult[1];
        ShellKeyInjector.TEST_CAPTURE.set(slot);
        try {
            assertFalse(ShellKeyInjector.runShell(childCommand("output")));
        } finally {
            ShellKeyInjector.TEST_CAPTURE.remove();
        }
        assertEquals(Integer.valueOf(7), slot[0].exitCode);
        assertEquals("last stderr line", slot[0].output.get(slot[0].output.size() - 1));
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

    @Test public void shadeIsOpenOnlyWhileItHoldsFocus() {
        assertTrue(BridgeMain.shadeOpen("  mCurrentFocus=null\n  mCurrentFocus=Window{8e416b5 u0 NotificationShade}\n"));
        assertFalse(BridgeMain.shadeOpen("  mCurrentFocus=Window{36ac4c5 u0 com.android.launcher/com.android.launcher.Launcher}\n"));
        assertFalse(BridgeMain.shadeOpen(null));
    }

    @Test public void callStateTakesTheBusiestSlot() {
        assertEquals(0, BridgeMain.callState("    mCallState=0\n    mCallState=0\n"));
        assertEquals(1, BridgeMain.callState("    mCallState=0\n    mCallState=1\n"));
        assertEquals(2, BridgeMain.callState("    mCallState=2\n    mCallState=0\n"));
        assertEquals(-1, BridgeMain.callState("Phone Id=0\n"));
    }

    @Test public void recentTaskIdsMatchOnlyThatPackage() {
        String dump = "  * Recent #0: Task{b11 #4831 type=standard A=10700:io.github.nicholasarda.keysnap.dev}\n"
                + "  * Recent #1: Task{73097cd #4830 type=standard A=10301:com.coloros.calculator}\n"
                + "  * Recent #2: Task{501c655 #2 type=undefined I=com.android.launcher/.Launcher}\n"
                + "  * Recent #3: Task{9a #4700 type=standard A=10302:com.coloros.calculator.extra}\n";
        assertEquals(java.util.Collections.singletonList("4830"), BridgeMain.recentTaskIds(dump, "com.coloros.calculator"));
        assertEquals(java.util.Collections.singletonList("2"), BridgeMain.recentTaskIds(dump, "com.android.launcher"));
        assertTrue(BridgeMain.recentTaskIds(null, "com.coloros.calculator").isEmpty());
    }
}
