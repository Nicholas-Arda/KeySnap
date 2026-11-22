package com.example.bridge;

import static com.example.bridge.SensitiveApps.A11Y;
import static com.example.bridge.SensitiveApps.DEV;
import static com.example.bridge.SensitiveApps.WIFI;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class SensitiveAppsTest {

    private static final String BANK = "com.garanti.tami";
    private static final String GAME = "com.strict.game";
    private static final String OTHER = "com.example.notes";
    private static final String KEYSNAP = "io.github.nicholasarda.keysnap";
    private static final String OWN = KEYSNAP + "/com.example.service.KeyMapperAccessibilityService";
    private static final String TALKBACK = "com.google.android.marvin.talkback/.TalkBackService";

    private static Map<String, Integer> apps() {
        Map<String, Integer> apps = new HashMap<>();
        apps.put(BANK, DEV | A11Y);
        apps.put(GAME, DEV);
        return apps;
    }

    /** What the guard does after a successful write of every switch the step asked for. */
    private static int[] step(SensitiveApps state, int[] step) {
        for (int bit : SensitiveApps.BITS) {
            if ((step[1] & bit) != 0) state.restored(bit);
            if ((step[0] & bit) != 0) state.hidden(bit, true);
        }
        return step;
    }

    @Test
    public void listedAppStepsAsideForItsSwitches() {
        SensitiveApps state = new SensitiveApps();
        assertArrayEquals(new int[] {DEV | A11Y, 0}, step(state, state.onForeground(BANK, apps())));
        assertEquals(BANK, state.owner());
        assertFalse(state.idle());
    }

    @Test
    public void sameAppAgainDoesNothing() {
        SensitiveApps state = new SensitiveApps();
        step(state, state.onForeground(BANK, apps()));
        assertArrayEquals(new int[] {0, 0}, state.onForeground(BANK, apps()));
    }

    @Test
    public void leavingRestoresOnlyWhatWasTurnedOff() {
        SensitiveApps state = new SensitiveApps();
        int[] first = state.onForeground(BANK, apps());
        state.hidden(DEV, true);
        state.hidden(A11Y, false); // Found off already: never turned "back" on.
        assertEquals(DEV | A11Y, first[0]);
        assertArrayEquals(new int[] {0, DEV}, step(state, state.onForeground(OTHER, apps())));
        assertTrue(state.idle());
        assertNull(state.owner());
    }

    @Test
    public void unlistedAppWhileIdleNeedsNoWork() {
        SensitiveApps state = new SensitiveApps();
        assertArrayEquals(new int[] {0, 0}, state.onForeground(OTHER, apps()));
        assertTrue(state.idle());
    }

    @Test
    public void permissionPromptOverTheAppChangesNothing() {
        SensitiveApps state = new SensitiveApps();
        step(state, state.onForeground(BANK, apps()));
        for (String prompt : SensitiveApps.PASS_THROUGH) {
            assertArrayEquals(new int[] {0, 0}, state.onForeground(prompt, apps()));
        }
        assertArrayEquals(new int[] {0, 0}, state.onForeground(BANK, apps()));
        assertEquals(DEV | A11Y, state.changed());
    }

    @Test
    public void switchingBetweenListedAppsTouchesOnlyTheDifference() {
        SensitiveApps state = new SensitiveApps();
        step(state, state.onForeground(BANK, apps()));
        assertArrayEquals(new int[] {0, A11Y}, step(state, state.onForeground(GAME, apps())));
        assertEquals(GAME, state.owner());
        assertArrayEquals(new int[] {A11Y, 0}, step(state, state.onForeground(BANK, apps())));
    }

    @Test
    public void failedRestoreIsTriedAgain() {
        SensitiveApps state = new SensitiveApps();
        step(state, state.onForeground(BANK, apps()));
        assertArrayEquals(new int[] {0, DEV | A11Y}, state.onForeground(OTHER, apps())); // writes failed
        assertArrayEquals(new int[] {0, DEV | A11Y}, state.onConfig(apps()));
    }

    @Test
    public void restoreNowLeavesTheAppInFrontAloneUntilAnotherComesUp() {
        SensitiveApps state = new SensitiveApps();
        step(state, state.onForeground(BANK, apps()));
        assertArrayEquals(new int[] {0, DEV | A11Y}, step(state, state.onRestoreNow()));
        assertArrayEquals(new int[] {0, 0}, state.onForeground(BANK, apps())); // the bank's own next screen
        state.onForeground(OTHER, apps());
        assertArrayEquals(new int[] {DEV | A11Y, 0}, state.onForeground(BANK, apps()));
    }

    @Test
    public void configCanOnlyNarrow() {
        SensitiveApps state = new SensitiveApps();
        step(state, state.onForeground(BANK, apps()));
        Map<String, Integer> narrowed = apps();
        narrowed.put(BANK, DEV);
        assertArrayEquals(new int[] {0, A11Y}, step(state, state.onConfig(narrowed)));
        Map<String, Integer> widened = apps();
        assertArrayEquals(new int[] {0, 0}, state.onConfig(widened)); // only a launch hides
        assertArrayEquals(new int[] {0, DEV}, step(state, state.onConfig(Collections.emptyMap()))); // master off
        assertTrue(state.idle());
    }

    @Test
    public void leftoverFromKilledBridgeIsRestored() {
        SensitiveApps state = new SensitiveApps();
        state.hidden(DEV, true);
        state.hidden(A11Y, true);
        assertArrayEquals(new int[] {0, DEV | A11Y}, state.onConfig(Collections.emptyMap()));
    }

    // ---- two sources: launch reports and readings of the task in front ----

    private static final String LAUNCHER = "com.android.launcher";

    @Test
    public void homeAfterListedAppRestores() {
        SensitiveApps state = new SensitiveApps();
        step(state, state.onLaunch(BANK, apps(), true));
        assertArrayEquals(new int[] {0, DEV | A11Y}, step(state, state.onFront(LAUNCHER, LAUNCHER, apps())));
        assertTrue(state.idle());
    }

    @Test
    public void listedAppAgainWithoutALaunchHides() {
        SensitiveApps state = new SensitiveApps();
        step(state, state.onLaunch(BANK, apps(), true));
        step(state, state.onFront(LAUNCHER, LAUNCHER, apps()));
        assertArrayEquals(new int[] {DEV | A11Y, 0}, step(state, state.onFront(BANK, BANK, apps()))); // from recents
        assertEquals(BANK, state.owner());
    }

    @Test
    public void bothSourcesReportingTheSameChangeActOnce() {
        SensitiveApps state = new SensitiveApps();
        assertArrayEquals(new int[] {DEV | A11Y, 0}, step(state, state.onLaunch(BANK, apps(), true)));
        assertArrayEquals(new int[] {0, 0}, state.onFront(BANK, BANK, apps()));
        assertArrayEquals(new int[] {0, 0}, state.onLaunch(BANK, apps(), true)); // the bank's next screen
        assertArrayEquals(new int[] {0, 0}, state.onFront(BANK, BANK, apps()));
        assertArrayEquals(new int[] {0, DEV | A11Y}, step(state, state.onFront(OTHER, OTHER, apps())));
        assertArrayEquals(new int[] {0, 0}, state.onLaunch(OTHER, apps(), true));
        assertArrayEquals(new int[] {0, 0}, state.onFront(OTHER, OTHER, apps()));
    }

    @Test
    public void passThroughOverTheListedAppChangesNothingFromEitherSource() {
        SensitiveApps state = new SensitiveApps();
        step(state, state.onLaunch(BANK, apps(), true));
        for (String prompt : SensitiveApps.PASS_THROUGH) {
            assertArrayEquals(new int[] {0, 0}, state.onLaunch(prompt, apps(), true));
            assertArrayEquals(new int[] {0, 0}, state.onFront(prompt, BANK, apps())); // in the bank's task
            assertArrayEquals(new int[] {0, 0}, state.onFront(prompt, prompt, apps())); // in its own task
        }
        assertArrayEquals(new int[] {0, 0}, state.onFront(BANK, BANK, apps()));
        assertEquals(DEV | A11Y, state.changed());
    }

    @Test
    public void anotherAppsScreenInsideTheListedAppsTaskKeepsItHidden() {
        SensitiveApps state = new SensitiveApps();
        step(state, state.onLaunch(BANK, apps(), true));
        assertArrayEquals(new int[] {0, 0}, state.onLaunch(OTHER, apps(), true)); // a browser tab it opens
        assertArrayEquals(new int[] {0, 0}, state.onFront(OTHER, BANK, apps()));
        step(state, state.onFront(OTHER, OTHER, apps()));
        // The listed app's own screen opened inside another app's task is the listed app in front.
        assertArrayEquals(new int[] {DEV | A11Y, 0}, state.onFront(BANK, OTHER, apps()));
    }

    @Test
    public void unlistedLaunchRestoresOnlyWhenTheFrontIsNotRead() {
        SensitiveApps tracked = new SensitiveApps();
        step(tracked, tracked.onLaunch(BANK, apps(), true));
        assertArrayEquals("a launch the system blocks puts nothing back", new int[] {0, 0},
                tracked.onLaunch(OTHER, apps(), true));
        assertEquals(BANK, tracked.owner());

        SensitiveApps launchesOnly = new SensitiveApps();
        step(launchesOnly, launchesOnly.onLaunch(BANK, apps(), false));
        assertArrayEquals(new int[] {0, DEV | A11Y}, launchesOnly.onLaunch(OTHER, apps(), false));
    }

    @Test
    public void unreadableFrontChangesNothing() {
        SensitiveApps state = new SensitiveApps();
        step(state, state.onLaunch(BANK, apps(), true));
        assertArrayEquals(new int[] {0, 0}, state.onFront(null, null, apps()));
        assertArrayEquals(new int[] {0, 0}, state.onFront(null, BANK, apps()));
        assertEquals(BANK, state.owner());
    }

    @Test
    public void restoreNowHoldsUntilAnotherAppIsReadInFront() {
        SensitiveApps state = new SensitiveApps();
        step(state, state.onLaunch(BANK, apps(), true));
        step(state, state.onRestoreNow());
        assertArrayEquals(new int[] {0, 0}, state.onFront(BANK, BANK, apps()));
        assertArrayEquals(new int[] {0, 0}, state.onFront(LAUNCHER, LAUNCHER, apps()));
        assertArrayEquals(new int[] {DEV | A11Y, 0}, state.onFront(BANK, BANK, apps()));
    }

    // ---- wireless debugging: the step that ends the bridge ----

    private static final String BANKID = "com.bankid.bus";

    private static Map<String, Integer> withBankId(int bits) {
        Map<String, Integer> apps = apps();
        apps.put(BANKID, bits);
        return apps;
    }

    @Test
    public void wirelessDebuggingEndsTheBridgeAfterTheOtherSwitches() {
        SensitiveApps state = new SensitiveApps();
        assertArrayEquals("its other switches go off first, as always", new int[] {DEV | A11Y, 0},
                state.onLaunch(BANKID, withBankId(DEV | A11Y | WIFI), true));
        assertEquals(BANKID, state.leavingFor());
    }

    @Test
    public void wirelessDebuggingAloneStillPutsBackWhatAnotherAppHad() {
        SensitiveApps state = new SensitiveApps();
        step(state, state.onLaunch(BANK, withBankId(WIFI), true));
        assertNull(state.leavingFor());
        assertArrayEquals(new int[] {0, DEV | A11Y}, step(state, state.onFront(BANKID, BANKID, withBankId(WIFI))));
        assertEquals(BANKID, state.leavingFor());
        assertEquals("nothing left off for the next start to put back", 0, state.changed());
    }

    @Test
    public void wirelessDebuggingFollowsTheOpenerOfTheTaskInFront() {
        SensitiveApps state = new SensitiveApps();
        state.onFront(OTHER, BANKID, withBankId(WIFI)); // a sign-in page inside its task
        assertEquals(BANKID, state.leavingFor());
    }

    @Test
    public void nothingElseEverEndsTheBridge() {
        Map<String, Integer> apps = withBankId(DEV | WIFI);
        SensitiveApps state = new SensitiveApps();
        state.onLaunch(OTHER, apps, true);
        state.onLaunch(OTHER, apps, false);
        state.onFront(OTHER, OTHER, apps);
        state.onFront(null, BANKID, apps); // unreadable
        state.onFront(OTHER, GAME, apps); // listed opener without the bit
        assertNull(state.leavingFor());

        for (String prompt : SensitiveApps.PASS_THROUGH) {
            Map<String, Integer> listedPrompt = new HashMap<>(apps);
            listedPrompt.put(prompt, DEV | WIFI);
            SensitiveApps fresh = new SensitiveApps();
            fresh.onLaunch(prompt, listedPrompt, true);
            fresh.onFront(prompt, prompt, listedPrompt);
            assertNull(prompt, fresh.leavingFor());
        }

        SensitiveApps config = new SensitiveApps();
        step(config, config.onLaunch(BANKID, withBankId(DEV), true));
        config.onConfig(apps); // a new config only narrows: even a new WIFI bit acts on the next launch
        assertNull(config.leavingFor());
    }

    @Test
    public void restoreNowHoldsEvenAfterWirelessDebuggingIsTicked() {
        SensitiveApps state = new SensitiveApps();
        step(state, state.onLaunch(BANKID, withBankId(DEV), true));
        step(state, state.onRestoreNow());
        assertArrayEquals(new int[] {0, 0}, state.onFront(BANKID, BANKID, withBankId(DEV | WIFI)));
        assertNull(state.leavingFor());
        state.onFront(LAUNCHER, LAUNCHER, withBankId(DEV | WIFI));
        state.onFront(BANKID, BANKID, withBankId(DEV | WIFI));
        assertEquals(BANKID, state.leavingFor());
    }

    @Test
    public void wirelessDebuggingIsOnlyEverTurnedOff() {
        assertArrayEquals(new String[] {"cmd", "settings", "put", "global", "adb_wifi_enabled", "0"},
                SensitiveApps.wirelessDebuggingOff());
        for (int bit : SensitiveApps.BITS) assertTrue("not a restorable switch", (bit & WIFI) == 0);
    }

    // ---- one round of writes per step ----

    @Test
    public void bothSwitchesAreWrittenInOneRound() {
        SensitiveApps state = new SensitiveApps();
        int[] hide = state.onForeground(BANK, apps());
        String[][] writes = state.writes(hide, new String[] {"1", TALKBACK + ":" + OWN}, KEYSNAP, "");
        assertArrayEquals(SensitiveApps.hideCommand(DEV, "1", KEYSNAP), writes[0]);
        assertArrayEquals(SensitiveApps.hideCommand(A11Y, TALKBACK + ":" + OWN, KEYSNAP), writes[1]);
        assertEquals("settled only once the writes succeed", 0, state.changed());

        state.hidden(DEV, true);
        state.hidden(A11Y, true);
        int[] restore = state.onForeground(OTHER, apps());
        writes = state.writes(restore, new String[] {"0", TALKBACK}, KEYSNAP, OWN);
        assertArrayEquals(SensitiveApps.restoreCommand(DEV, "0", OWN), writes[0]);
        assertArrayEquals(SensitiveApps.restoreCommand(A11Y, TALKBACK, OWN), writes[1]);
    }

    @Test
    public void switchesAlreadyAsWantedSettleWithoutAWrite() {
        SensitiveApps state = new SensitiveApps();
        String[][] writes = state.writes(state.onForeground(BANK, apps()), new String[] {"0", TALKBACK}, KEYSNAP, "");
        assertNull(writes[0]);
        assertNull(writes[1]);
        assertArrayEquals(new int[] {0, 0}, state.onForeground(BANK, apps()));
        assertArrayEquals("found off: never turned back on", new int[] {0, 0}, state.onForeground(OTHER, apps()));

        step(state, state.onForeground(BANK, apps()));
        writes = state.writes(state.onForeground(OTHER, apps()), new String[] {"1", OWN}, KEYSNAP, OWN);
        assertNull(writes[0]);
        assertNull(writes[1]);
        assertTrue("turned back on meanwhile: nothing left to restore", state.idle());
    }

    @Test
    public void unreadableSwitchIsLeftForTheNextStep() {
        SensitiveApps state = new SensitiveApps();
        String[][] writes = state.writes(state.onForeground(BANK, apps()), new String[] {null, OWN}, KEYSNAP, "");
        assertNull(writes[0]);
        assertArrayEquals(SensitiveApps.hideCommand(A11Y, OWN, KEYSNAP), writes[1]);
        state.hidden(A11Y, true);
        assertArrayEquals(new int[] {DEV, 0}, state.onForeground(BANK, apps()));
    }

    @Test
    public void accessibilityIsNeverWrittenForABadPackageName() {
        SensitiveApps state = new SensitiveApps();
        String[][] writes = state.writes(state.onForeground(BANK, apps()), new String[] {"1", OWN}, "", "");
        assertArrayEquals(SensitiveApps.hideCommand(DEV, "1", ""), writes[0]);
        assertNull(writes[1]);
        assertFalse(SensitiveApps.isPackageName("x; rm -rf /"));
        assertTrue(SensitiveApps.isPackageName(KEYSNAP));
    }

    // ---- commands ----

    @Test
    public void developerOptionsCommands() {
        assertArrayEquals(new String[] {"cmd", "settings", "put", "global", "development_settings_enabled", "0"},
                SensitiveApps.hideCommand(DEV, "1", KEYSNAP));
        assertNull(SensitiveApps.hideCommand(DEV, "0", KEYSNAP));
        assertNull(SensitiveApps.hideCommand(DEV, "", KEYSNAP));
        assertArrayEquals(new String[] {"cmd", "settings", "put", "global", "development_settings_enabled", "1"},
                SensitiveApps.restoreCommand(DEV, "0", ""));
        assertNull(SensitiveApps.restoreCommand(DEV, "1", ""));
    }

    @Test
    public void accessibilityHideRemovesOnlyKeySnap() {
        String shortForm = KEYSNAP + "/.Other";
        String list = TALKBACK + ":" + OWN + ":" + shortForm;
        assertArrayEquals(new String[] {"cmd", "settings", "put", "secure", "enabled_accessibility_services", TALKBACK},
                SensitiveApps.hideCommand(A11Y, list, KEYSNAP));
        assertEquals(OWN + ":" + shortForm, SensitiveApps.ownEntries(list, KEYSNAP));
        assertArrayEquals(new String[] {"cmd", "settings", "delete", "secure", "enabled_accessibility_services"},
                SensitiveApps.hideCommand(A11Y, OWN, KEYSNAP));
        assertNull(SensitiveApps.hideCommand(A11Y, TALKBACK, KEYSNAP));
        assertNull(SensitiveApps.hideCommand(A11Y, "", KEYSNAP));
        // Another package that merely starts with ours is not ours.
        assertNull(SensitiveApps.hideCommand(A11Y, KEYSNAP + ".dev/x.Service", KEYSNAP));
    }

    @Test
    public void accessibilityRestoreKeepsWhatChangedMeanwhile() {
        assertArrayEquals(new String[] {"cmd", "settings", "put", "secure", "enabled_accessibility_services", TALKBACK + ":" + OWN},
                SensitiveApps.restoreCommand(A11Y, TALKBACK, OWN));
        assertArrayEquals(new String[] {"cmd", "settings", "put", "secure", "enabled_accessibility_services", OWN},
                SensitiveApps.restoreCommand(A11Y, "", OWN));
        assertNull(SensitiveApps.restoreCommand(A11Y, OWN + ":" + TALKBACK, OWN));
        assertNull(SensitiveApps.restoreCommand(A11Y, TALKBACK, ""));
    }

    @Test
    public void unreadableValuesAreNeverActedOn() {
        assertEquals("", SensitiveApps.parse(A11Y, "null\n"));
        assertEquals("1", SensitiveApps.parse(DEV, " 1\n"));
        assertEquals(OWN, SensitiveApps.parse(A11Y, OWN));
        assertNull(SensitiveApps.parse(A11Y, "Exception occurred while executing 'get':"));
        assertNull(SensitiveApps.parse(DEV, "cmd: Can't find service: settings"));
    }

    @Test
    public void onlyTheTwoSwitchesAreEverWrittenAndNeverAdb() {
        List<String[]> commands = new ArrayList<>();
        for (int bit : SensitiveApps.BITS) {
            commands.add(SensitiveApps.read(bit));
            for (String current : Arrays.asList("", "0", "1", OWN, TALKBACK + ":" + OWN, TALKBACK)) {
                commands.add(SensitiveApps.hideCommand(bit, current, KEYSNAP));
                commands.add(SensitiveApps.restoreCommand(bit, current, OWN));
            }
        }
        for (String[] command : commands) {
            if (command == null) continue;
            assertEquals("cmd", command[0]);
            assertEquals("settings", command[1]);
            assertTrue(Arrays.asList(SensitiveApps.DEV_KEY, SensitiveApps.A11Y_KEY).contains(command[4]));
            for (String part : command) assertFalse(part, part.toLowerCase().contains("adb"));
        }
    }

    @Test
    public void configCarriesTheListAndTheRestoreNonce() throws Exception {
        File file = File.createTempFile("bridge", ".json");
        try {
            Files.write(file.toPath(), ("{\"port\":1,\"token\":\"t\",\"sensitiveRestoreNonce\":7,\"sensitiveApps\":["
                    + "{\"packageName\":\"" + BANK + "\",\"hideDeveloperOptions\":true,\"hideAccessibility\":true},"
                    + "{\"packageName\":\"" + GAME + "\",\"hideDeveloperOptions\":true,\"hideAccessibility\":false},"
                    + "{\"packageName\":\"" + OTHER + "\",\"hideDeveloperOptions\":false,\"hideAccessibility\":false}]}")
                    .getBytes(StandardCharsets.UTF_8));
            BridgeConfig config = BridgeConfig.read(file);
            assertEquals(apps(), config.sensitiveApps);
            assertEquals(7L, config.sensitiveRestoreNonce);

            Files.write(file.toPath(), ("{\"port\":1,\"token\":\"t\",\"packageName\":\"" + KEYSNAP + "\",\"sensitiveApps\":["
                    + "{\"packageName\":\"" + BANKID + "\",\"hideDeveloperOptions\":true,\"hideAccessibility\":false,\"hideWirelessDebugging\":true},"
                    + "{\"packageName\":\"" + OTHER + "\",\"hideDeveloperOptions\":false,\"hideAccessibility\":false,\"hideWirelessDebugging\":true},"
                    + "{\"packageName\":\"" + KEYSNAP + "\",\"hideDeveloperOptions\":false,\"hideAccessibility\":true,\"hideWirelessDebugging\":true},"
                    + "{\"packageName\":\"" + GAME + "\",\"hideDeveloperOptions\":true,\"hideAccessibility\":false,\"hideWirelessDebugging\":false}]}")
                    .getBytes(StandardCharsets.UTF_8));
            Map<String, Integer> third = new HashMap<>();
            third.put(BANKID, DEV | WIFI);
            third.put(OTHER, WIFI); // wireless debugging alone is something to hide
            third.put(KEYSNAP, A11Y); // never ended by opening KeySnap itself
            third.put(GAME, DEV);
            assertEquals(third, BridgeConfig.read(file).sensitiveApps);

            Files.write(file.toPath(), "{\"port\":1,\"token\":\"t\"}".getBytes(StandardCharsets.UTF_8));
            BridgeConfig old = BridgeConfig.read(file);
            assertTrue(old.sensitiveApps.isEmpty());
            assertEquals(0L, old.sensitiveRestoreNonce);
        } finally {
            file.delete();
        }
    }
}
