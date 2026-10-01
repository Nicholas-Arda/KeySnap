package com.example.bridge;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The safety lock in front of every tap: SendButton.findTap reads one uiautomator dump. */
public class SendButtonTest {

    private static final String PKG = "com.example.chat";
    private static final String MESSAGE = "On my way";

    private static final String NAME = "Alex Example";
    private static final String DIGITS = "15550123456";

    /** The recipient this action was set up for: the name picked from contacts and the number's digits. */
    private static int[] findTap(String xml, String pkg, String message) {
        return SendButton.findTap(xml, pkg, message, NAME, DIGITS);
    }

    private static String screen(String pkg, String fieldText, String extraNodes) {
        return screen(pkg, NAME, fieldText, extraNodes);
    }

    /** A chat screen whose header (a toolbar title) shows [header]. */
    private static String screen(String pkg, String header, String fieldText, String extraNodes) {
        return "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?><hierarchy rotation=\"0\">"
                + "<node index=\"0\" text=\"\" resource-id=\"\" class=\"android.widget.FrameLayout\" package=\"" + pkg
                + "\" content-desc=\"\" clickable=\"false\" bounds=\"[0,0][1080,2400]\">"
                + "<node index=\"0\" text=\"" + header + "\" resource-id=\"" + pkg + ":id/toolbar_title\""
                + " class=\"android.widget.TextView\" package=\"" + pkg + "\" content-desc=\"\" clickable=\"true\""
                + " bounds=\"[160,80][800,180]\" />"
                + "<node index=\"0\" text=\"" + fieldText + "\" resource-id=\"" + pkg + ":id/entry\""
                + " class=\"android.widget.EditText\" package=\"" + pkg + "\" content-desc=\"\" clickable=\"true\""
                + " bounds=\"[40,2200][900,2300]\" />"
                + extraNodes
                + "</node></hierarchy>";
    }

    private static String node(String pkg, String id, String desc, boolean clickable, String bounds) {
        return "<node index=\"1\" text=\"\" resource-id=\"" + id + "\" class=\"android.widget.ImageButton\" package=\""
                + pkg + "\" content-desc=\"" + desc + "\" clickable=\"" + clickable + "\" bounds=\"" + bounds + "\" />";
    }

    private static final String SEND_BY_ID = node(PKG, PKG + ":id/send", "", true, "[920,2200][1040,2320]");

    @Test public void picksTheSendNodeByResourceId() {
        assertArrayEquals(new int[] {980, 2260}, findTap(screen(PKG, MESSAGE, SEND_BY_ID), PKG, MESSAGE));
    }

    @Test public void aResourceIdBeatsAContentDescription() {
        String nodes = node(PKG, "", "Send", true, "[0,0][100,100]") + SEND_BY_ID;
        assertArrayEquals(new int[] {980, 2260}, findTap(screen(PKG, MESSAGE, nodes), PKG, MESSAGE));
    }

    @Test public void fallsBackToTheContentDescriptionInTheAppsLanguages() {
        for (String word : new String[] {"send", "Gönder", "Отправить", "إرسال", "भेजें"}) {
            String nodes = node(PKG, PKG + ":id/icon", word, true, "[920,2200][1040,2320]");
            assertArrayEquals(word, new int[] {980, 2260}, findTap(screen(PKG, MESSAGE, nodes), PKG, MESSAGE));
        }
    }

    @Test public void anUnclickableIconCountsWhenItsParentIsClickable() {
        String nodes = "<node index=\"1\" text=\"\" resource-id=\"\" class=\"android.widget.FrameLayout\" package=\"" + PKG
                + "\" content-desc=\"\" clickable=\"true\" bounds=\"[900,2180][1060,2340]\">"
                + node(PKG, PKG + ":id/send_button", "", false, "[920,2200][1040,2320]") + "</node>";
        assertArrayEquals(new int[] {980, 2260}, findTap(screen(PKG, MESSAGE, nodes), PKG, MESSAGE));
    }

    @Test public void refusesWhenAnotherAppIsInFront() {
        assertNull(findTap(screen("com.example.other", MESSAGE, node("com.example.other",
                "com.example.other:id/send", "", true, "[920,2200][1040,2320]")), PKG, MESSAGE));
    }

    @Test public void refusesWhenTheFieldHoldsSomethingElse() {
        assertNull(findTap(screen(PKG, "On my way home", SEND_BY_ID), PKG, MESSAGE));
        assertNull(findTap(screen(PKG, "", SEND_BY_ID), PKG, MESSAGE));
    }

    @Test public void surroundingWhitespaceDoesNotMatter() {
        assertArrayEquals(new int[] {980, 2260}, findTap(screen(PKG, "  On my way ", SEND_BY_ID), PKG, " On my way"));
    }

    @Test public void refusesWithoutASendControl() {
        String other = node(PKG, PKG + ":id/attach", "Attach", true, "[920,2200][1040,2320]");
        assertNull(findTap(screen(PKG, MESSAGE, other), PKG, MESSAGE));
        String notClickable = node(PKG, PKG + ":id/send", "", false, "[920,2200][1040,2320]");
        assertNull(findTap(screen(PKG, MESSAGE, notClickable), PKG, MESSAGE));
    }

    @Test public void refusesASendControlOfAnotherPackage() {
        String foreign = node("com.example.other", "com.example.other:id/send", "", true, "[920,2200][1040,2320]");
        assertNull(findTap(screen(PKG, MESSAGE, foreign), PKG, MESSAGE));
    }

    @Test public void choosesTheCandidateNearestTheFieldsBottomRight() {
        String top = node(PKG, PKG + ":id/send", "", true, "[920,100][1040,220]");
        String bottom = node(PKG, PKG + ":id/send", "", true, "[920,2200][1040,2320]");
        assertArrayEquals(new int[] {980, 2260}, findTap(screen(PKG, MESSAGE, top + bottom), PKG, MESSAGE));
    }

    @Test public void unreadableXmlIsARefusal() {
        assertNull(findTap("<hierarchy><node", PKG, MESSAGE));
        assertNull(findTap("", PKG, MESSAGE));
    }

    @Test public void theRightChatsHeaderPasses() {
        assertArrayEquals(new int[] {980, 2260}, findTap(screen(PKG, "alex example", MESSAGE, SEND_BY_ID), PKG, MESSAGE));
    }

    @Test public void anotherChatHoldingTheSameDraftIsRefused() {
        assertNull(findTap(screen(PKG, "Sam Sample", MESSAGE, SEND_BY_ID), PKG, MESSAGE));
        // Nothing to match against: no name and too few digits.
        assertNull(SendButton.findTap(screen(PKG, MESSAGE, SEND_BY_ID), PKG, MESSAGE, "", "12345"));
    }

    @Test public void aHeaderSuffixSuchAsYouStillMatches() {
        assertArrayEquals(new int[] {980, 2260}, findTap(screen(PKG, "Alex Example (You)", MESSAGE, SEND_BY_ID), PKG, MESSAGE));
    }

    @Test public void aTypedNumberMatchesAFormattedHeader() {
        String xml = screen(PKG, "+1 (555) 012-3456", MESSAGE, SEND_BY_ID);
        assertArrayEquals(new int[] {980, 2260}, SendButton.findTap(xml, PKG, MESSAGE, "", "15550123456"));
        assertArrayEquals(new int[] {980, 2260}, SendButton.findTap(xml, PKG, MESSAGE, "", "5550123456"));
        assertNull(SendButton.findTap(xml, PKG, MESSAGE, "", "15550123999"));
        // With a name picked, the number alone still identifies the chat.
        assertArrayEquals(new int[] {980, 2260}, SendButton.findTap(xml, PKG, MESSAGE, NAME, "15550123456"));
    }

    @Test public void theDraftItselfDoesNotCountAsTheRecipient() {
        // A message naming the person is in the editable field, not in a header.
        String draft = "Alex Example is here";
        assertNull(findTap(screen(PKG, "Sam Sample", draft, SEND_BY_ID), PKG, draft));
    }

    /** The screen is dumped only while the chat app is resumed; an unknown format counts as not in front. */
    @Test public void theResumedPackageIsReadStrictly() {
        assertEquals(PKG, ConstraintEvaluator.parseResumedPackage(
                "  topResumedActivity=ActivityRecord{a1b2c3 u0 " + PKG + "/.Conversation t42}\n"));
        assertNull(ConstraintEvaluator.parseResumedPackage("no activity lines here"));
    }

    /** A virtual clock: the chat comes to the front at [frontAtMs]; the field empties once Send is tapped. */
    private static final class FakeScreen implements SendButton.Screen {
        final long frontAtMs;
        long now;
        int dumps;
        int taps;
        /** The screen locks once the clock reaches this. */
        long locksAtMs = Long.MAX_VALUE;

        FakeScreen(long frontAtMs) {
            this.frontAtMs = frontAtMs;
        }

        @Override public boolean unlocked() {
            return now < locksAtMs;
        }

        @Override public boolean inFront(String packageName) {
            return now >= frontAtMs;
        }

        @Override public String dump() {
            dumps++;
            return screen(PKG, taps > 0 ? "" : MESSAGE, SEND_BY_ID);
        }

        @Override public boolean tap(int x, int y) {
            taps++;
            return true;
        }

        @Override public long nowMs() {
            return now;
        }

        @Override public boolean sleep(long ms) {
            now += ms;
            return true;
        }
    }

    /** An OEM "allow opening" dialog held the chat back for 12 s; the press window starts only then. */
    @Test public void aChatThatOpensLateIsStillPressed() {
        FakeScreen screen = new FakeScreen(12_000L);
        assertEquals(BridgeProtocol.PRESS_SEND_OK, SendButton.press(screen, 0L, PKG, MESSAGE, NAME, DIGITS));
        assertEquals(1, screen.taps);
    }

    @Test public void aChatThatNeverOpensTimesOutWithoutReadingTheScreen() {
        FakeScreen screen = new FakeScreen(Long.MAX_VALUE);
        assertEquals(BridgeProtocol.PRESS_SEND_TIMEOUT, SendButton.press(screen, 0L, PKG, MESSAGE, NAME, DIGITS));
        assertEquals(0, screen.dumps);
        assertEquals(0, screen.taps);
        assertTrue(screen.now <= SendButton.OPEN_DEADLINE_MS);
    }

    /** Queued behind another press past the limit: the app has nearly stopped waiting, so nothing is read or tapped. */
    @Test public void aPressThatWaitedTooLongForItsTurnGivesUpUntouched() {
        FakeScreen screen = new FakeScreen(0L);
        screen.now = SendButton.MAX_QUEUE_WAIT_MS + 1;
        assertEquals(BridgeProtocol.PRESS_SEND_TIMEOUT, SendButton.press(screen, 0L, PKG, MESSAGE, NAME, DIGITS));
        assertEquals(0, screen.dumps);
        assertEquals(0, screen.taps);
    }

    /** The waiting before its turn counts against the 30 s for the chat to open. */
    @Test public void theOpenDeadlineCountsFromTheRequest() {
        FakeScreen screen = new FakeScreen(40_000L);
        screen.now = 20_000L;
        assertEquals(BridgeProtocol.PRESS_SEND_TIMEOUT, SendButton.press(screen, 0L, PKG, MESSAGE, NAME, DIGITS));
        assertTrue(screen.now <= SendButton.OPEN_DEADLINE_MS);
        assertEquals(0, screen.taps);
    }

    /** The phone locked while the screen was being read: no tap. */
    @Test public void aScreenThatLocksBeforeTheTapIsNotTapped() {
        // Unlocked when the press starts, locked by the time the chat is in front and read.
        FakeScreen lockedMeanwhile = new FakeScreen(1_000L);
        lockedMeanwhile.locksAtMs = 500L;
        assertEquals(BridgeProtocol.PRESS_SEND_LOCKED, SendButton.press(lockedMeanwhile, 0L, PKG, MESSAGE, NAME, DIGITS));
        assertEquals(1, lockedMeanwhile.dumps);
        assertEquals(0, lockedMeanwhile.taps);
        // Locked from the start: nothing is read either.
        FakeScreen locked = new FakeScreen(0L);
        locked.locksAtMs = 0L;
        assertEquals(BridgeProtocol.PRESS_SEND_LOCKED, SendButton.press(locked, 0L, PKG, MESSAGE, NAME, DIGITS));
        assertEquals(0, locked.dumps);
    }

    /** The app dropped the trailing newline the action's text ends with; it is still the same message. */
    @Test public void aTrailingNewlineTheAppDroppedStillMatches() {
        assertArrayEquals(new int[] {980, 2260}, findTap(screen(PKG, "deneme", SEND_BY_ID), PKG, "deneme\n"));
        assertArrayEquals(new int[] {980, 2260}, findTap(screen(PKG, "deneme&#10;", SEND_BY_ID), PKG, "deneme\n"));
        assertTrue(SendButton.wasSent(screen(PKG, "", SEND_BY_ID), PKG, "deneme\n"));
    }

    @Test public void readingStopsPastTheSizeCap() throws Exception {
        byte[] small = new byte[1000];
        assertArrayEquals(small, SendButton.readCapped(new java.io.ByteArrayInputStream(small)));
        assertNull(SendButton.readCapped(new java.io.ByteArrayInputStream(new byte[SendButton.MAX_DUMP_BYTES + 1])));
    }

    @Test public void aDumpDeclaringADoctypeOrEntityIsRefused() {
        String valid = screen(PKG, MESSAGE, SEND_BY_ID);
        String body = valid.substring(valid.indexOf("<hierarchy"));
        String withDoctype = "<?xml version='1.0' ?><!DOCTYPE hierarchy [<!ENTITY x \"On my way\">]>" + body;
        assertNull(findTap(withDoctype, PKG, MESSAGE));
        assertNull(findTap("<!doctype hierarchy>" + body, PKG, MESSAGE));
        String emptied = screen(PKG, "", "");
        assertFalse(SendButton.wasSent("<!DOCTYPE hierarchy>" + emptied.substring(emptied.indexOf("<hierarchy")), PKG, MESSAGE));
    }

    @Test public void sentOnlyWhenTheFieldNoLongerHoldsTheMessage() {
        assertTrue(SendButton.wasSent(screen(PKG, "", SEND_BY_ID), PKG, MESSAGE));
        assertFalse(SendButton.wasSent(screen(PKG, MESSAGE, SEND_BY_ID), PKG, MESSAGE));
        // Another app in front: nothing to confirm.
        assertFalse(SendButton.wasSent(screen("com.example.other", "", ""), PKG, MESSAGE));
        assertFalse(SendButton.wasSent("not xml", PKG, MESSAGE));
    }

    @Test public void aLineBreakMatchesWhetherTheDumpKeptItOrNot() {
        assertArrayEquals(new int[] {980, 2260}, findTap(screen(PKG, "On my&#10;way", SEND_BY_ID), PKG, "On my\nway"));
        assertArrayEquals(new int[] {980, 2260}, findTap(screen(PKG, "On my way", SEND_BY_ID), PKG, "On my\nway"));
    }
}
