package com.example.bridge;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class AppLauncherTest {
    private static class Fake implements AppLauncher.Commands {
        String resolved;
        String[] query;
        String[] started;
        boolean result = true;
        Fake(String resolved) { this.resolved = resolved; }
        public String output(String... command) { query = command; return resolved; }
        public boolean start(String... command) { started = command; return result; }
    }

    @Test public void launchesResolvedNonDefaultActivityExplicitly() {
        Fake fake = new Fake("priority=0 isDefault=false\ncom.coloros.calculator/com.android.calculator2.Calculator\n");
        assertTrue(AppLauncher.launch("com.coloros.calculator", fake));
        assertTrue(Arrays.asList(fake.query).contains("resolve-activity"));
        assertArrayEquals(new String[] {"am", "start", "--user", "current", "-a", "android.intent.action.MAIN",
                "-c", "android.intent.category.LAUNCHER", "-n", "com.coloros.calculator/com.android.calculator2.Calculator",
                "-f", "0x10200000"}, fake.started);
    }

    @Test public void acceptsRelativeActivityAndReturnsStartFailure() {
        Fake fake = new Fake("com.example.app/.MainActivity\n");
        fake.result = false;
        assertFalse(AppLauncher.launch("com.example.app", fake));
        assertNotNull(fake.started);
    }

    @Test public void missingOrForeignComponentsNeverLaunch() {
        for (String response : new String[] {"", "No activity found", "com.other.app/.Main", "com.example.app/.Main;reboot"}) {
            Fake fake = new Fake(response);
            assertFalse(AppLauncher.launch("com.example.app", fake));
            assertNull(fake.started);
        }
    }

    @Test public void invalidPackagesNeverRunCommands() {
        for (String name : new String[] {null, "", "--user", "com.app;reboot", "com.app\nother"}) {
            Fake fake = new Fake("");
            assertFalse(AppLauncher.launch(name, fake));
            assertNull(fake.query);
        }
    }
}
