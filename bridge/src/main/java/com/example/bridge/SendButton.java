package com.example.bridge;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * The bridge half of send_message's "Send automatically": right after the app opened a chat with the
 * text typed in, press that app's Send button with {@code input tap} (an output action, not input
 * capture). Reached only through {@link BridgeProtocol#CMD_PRESS_SEND} on the authenticated control
 * socket, never from a script in the config, so no user-authored shortcut can ask for it.
 *
 * <p>Safety lock: the screen is read only while the target app is the resumed one, and it taps only
 * when one dump shows the target app in the active window, an editable field of that app holding
 * exactly the message, the recipient (name or number) somewhere else on that screen, and a Send
 * control of that app. Anything else, the screen off or locked, or the deadline passing, ends
 * without a tap.
 *
 * <p>Side effect: {@code uiautomator dump} connects a UiAutomation, which can suspend the device's
 * other Accessibility services, KeySnap's own included, for the 1-3 s a dump takes. In Advanced Mode
 * keys are read by this bridge from getevent, so key capture is unaffected.
 *
 * <p>Privacy: the dump holds the chat on screen. It is parsed in memory and dropped; it, the message,
 * the recipient and every node's text stay out of the log, which records only the outcome word.
 */
final class SendButton {

    /** How long the chat may take to come to the front, e.g. behind an OEM's "allow opening" dialog. */
    static final long OPEN_DEADLINE_MS = 30_000L;
    /** A press still waiting for its turn this long after it was asked for gives up untouched. */
    static final long MAX_QUEUE_WAIT_MS = 45_000L;
    /** No tap later than this after the request: the app has stopped waiting for the answer at 60 s. */
    static final long LAST_TAP_MS = 55_000L;
    static final long FRONT_POLL_MS = 500L;
    static final long POLL_MS = 400L;
    /** The dump-and-press window, counted from the moment the chat is in front. */
    static final long DEADLINE_MS = 8_000L;
    static final long VERIFY_DELAY_MS = 600L;
    /** A dump larger than this is not a chat screen worth parsing; refused instead of read. */
    static final int MAX_DUMP_BYTES = 2 * 1024 * 1024;
    /** A number matches on its last digits: at least this many, at most {@link #MAX_NUMBER_TAIL}. */
    static final int MIN_NUMBER_TAIL = 7;
    static final int MAX_NUMBER_TAIL = 10;

    /** A Send control's content-desc in the app's ten languages, compared ignoring case. */
    static final String[] SEND_WORDS = {
        "Send", "Gönder", "Senden", "Enviar", "Envoyer", "Invia", "Отправить", "إرسال", "भेजें",
    };

    /** Resource-id endings of a Send control; preferred over a content-desc match. */
    static final String[] SEND_ID_SUFFIXES = {":id/send", "send_button", "send_btn", "sendButton"};

    private static final Pattern BOUNDS = Pattern.compile("\\[(-?\\d+),(-?\\d+)\\]\\[(-?\\d+),(-?\\d+)\\]");
    private static final String END_TAG = "</hierarchy>";
    private static final Object LOCK = new Object();

    private SendButton() {
    }

    /**
     * Waits for the chat to come to the front, polls its screen until the safety lock holds, taps
     * Send, and checks the field emptied. Returns one word for the app and the log: ok, unconfirmed,
     * not-found, timeout (the chat never came to the front, or the press waited too long for its
     * turn), locked or tap-failed.
     */
    static String press(String packageName, String message, String contactName, String numberDigits) {
        long requestedAtMs = DEVICE.nowMs();
        // One UiAutomation at a time: a second concurrent dump would fail anyway.
        synchronized (LOCK) {
            return press(DEVICE, requestedAtMs, packageName, message, contactName, numberDigits);
        }
    }

    /** What {@link #press(Screen, long, String, String, String, String)} needs from the phone; faked in tests. */
    interface Screen {
        /** The screen is on and the lock screen is not showing. */
        boolean unlocked();

        boolean inFront(String packageName);

        String dump();

        boolean tap(int x, int y);

        long nowMs();

        /** False when interrupted. */
        boolean sleep(long ms);
    }

    private static final Screen DEVICE = new Screen() {
        @Override public boolean unlocked() {
            return ConstraintEvaluator.isScreenOn() && !isLocked();
        }

        @Override public boolean inFront(String packageName) {
            return packageName.equals(ConstraintEvaluator.parseResumedPackage(
                    ConstraintEvaluator.shell("dumpsys", "activity", "activities")));
        }

        @Override public String dump() {
            return SendButton.dump();
        }

        @Override public boolean tap(int x, int y) {
            return ShellKeyInjector.runShell("input", "tap", String.valueOf(x), String.valueOf(y));
        }

        @Override public long nowMs() {
            return System.nanoTime() / 1_000_000L;
        }

        @Override public boolean sleep(long ms) {
            return SendButton.sleep(ms);
        }
    };

    /**
     * Two phases, both counted from [requestedAtMs]. First, with cheap foreground checks only and no
     * dump, wait until {@link #OPEN_DEADLINE_MS} for the chat app to be the resumed one: an OEM can
     * hold the launch behind its own "allow this app to open that one" dialog until the user answers
     * it, and that dialog is never touched. Then, from the moment the chat is in front, the
     * {@link #DEADLINE_MS} dump-and-press window. Every dump still waits for the chat to be in front,
     * so another app's screen is never read.
     *
     * <p>The app stops waiting for the answer after 60 s, and nothing may tap after that: a press
     * queued behind another one past {@link #MAX_QUEUE_WAIT_MS} gives up untouched, and none taps
     * later than {@link #LAST_TAP_MS} after it was asked for.
     */
    static String press(Screen screen, long requestedAtMs, String packageName, String message, String contactName,
            String numberDigits) {
        if (screen.nowMs() - requestedAtMs > MAX_QUEUE_WAIT_MS) {
            return BridgeProtocol.PRESS_SEND_TIMEOUT;
        }
        if (!screen.unlocked()) {
            return BridgeProtocol.PRESS_SEND_LOCKED;
        }
        while (!screen.inFront(packageName)) {
            if (screen.nowMs() - requestedAtMs + FRONT_POLL_MS > OPEN_DEADLINE_MS || !screen.sleep(FRONT_POLL_MS)) {
                return BridgeProtocol.PRESS_SEND_TIMEOUT;
            }
        }
        long deadline = screen.nowMs() + DEADLINE_MS;
        int[] tap;
        while (true) {
            String xml = screen.inFront(packageName) ? screen.dump() : null;
            tap = xml == null ? null : findTap(xml, packageName, message, contactName, numberDigits);
            if (tap != null) {
                break;
            }
            if (screen.nowMs() + POLL_MS >= deadline || !screen.sleep(POLL_MS)) {
                return BridgeProtocol.PRESS_SEND_NOT_FOUND;
            }
        }
        // The dumps took seconds: the phone may have locked, or the app given up, meanwhile.
        if (!screen.unlocked()) {
            return BridgeProtocol.PRESS_SEND_LOCKED;
        }
        if (screen.nowMs() - requestedAtMs > LAST_TAP_MS) {
            return BridgeProtocol.PRESS_SEND_NOT_FOUND;
        }
        if (!screen.tap(tap[0], tap[1])) {
            return BridgeProtocol.PRESS_SEND_TAP_FAILED;
        }
        if (!screen.sleep(VERIFY_DELAY_MS)) {
            return BridgeProtocol.PRESS_SEND_UNCONFIRMED;
        }
        String after = screen.inFront(packageName) ? screen.dump() : null;
        return after != null && wasSent(after, packageName, message)
                ? BridgeProtocol.PRESS_SEND_OK : BridgeProtocol.PRESS_SEND_UNCONFIRMED;
    }

    /**
     * Only an explicit "showing=true" counts: the constraint parser reads an unknown format as locked,
     * which would switch this off on such a build. The package check in findTap still refuses the
     * lock screen itself.
     */
    private static boolean isLocked() {
        String policy = ConstraintEvaluator.shell("dumpsys", "window", "policy");
        return policy.contains("KeyguardServiceDelegate") && ConstraintEvaluator.parseLockScreenShowing(policy);
    }

    /**
     * The XML of the active window, or null. Read from uiautomator's stdout so nothing is written to
     * disk; only if this build will not write there does it fall back to an owner-only file in the
     * bridge's own directory, deleted as soon as it is read. Either way, more than
     * {@link #MAX_DUMP_BYTES} is refused.
     */
    private static String dump() {
        String out = runCapped("uiautomator", "dump", "/dev/stdout");
        if (out != null && !out.contains("<hierarchy") && !out.contains("ERROR")) {
            out = dumpToFile();
        }
        return out == null ? null : extractHierarchy(out);
    }

    private static String dumpToFile() {
        File file = new File(BridgeProtocol.BASE_DIR, "window_dump.xml");
        try {
            // Created 0600 before uiautomator opens it; writing an existing file keeps its mode.
            Files.deleteIfExists(file.toPath());
            Files.createFile(file.toPath(),
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            if (!ShellKeyInjector.runShell("uiautomator", "dump", file.getPath()) || file.length() > MAX_DUMP_BYTES) {
                return null;
            }
            try (InputStream in = new FileInputStream(file)) {
                byte[] bytes = readCapped(in);
                return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            BridgeLog.w("press_send: dump unreadable: " + e.getClass().getSimpleName());
            return null;
        } finally {
            file.delete();
        }
    }

    /** The command's combined output, or null when it fails or passes {@link #MAX_DUMP_BYTES}. */
    private static String runCapped(String... command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            byte[] bytes = readCapped(process.getInputStream());
            if (bytes == null) {
                return null;
            }
            process.waitFor();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            BridgeLog.w("press_send: dump failed: " + e.getClass().getSimpleName());
            return null;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    /** All of [in], or null as soon as it passes {@link #MAX_DUMP_BYTES}. */
    static byte[] readCapped(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            if (out.size() + read > MAX_DUMP_BYTES) {
                return null;
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    static String extractHierarchy(String out) {
        int start = out.indexOf("<?xml");
        if (start < 0) {
            start = out.indexOf("<hierarchy");
        }
        int end = out.lastIndexOf(END_TAG);
        return start < 0 || end < start ? null : out.substring(start, end + END_TAG.length());
    }

    private static boolean sleep(long ms) {
        try {
            Thread.sleep(ms);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    // ---- pure: XML -> decision ----

    static final class UiNode {
        final UiNode parent;
        final String pkg;
        final String text;
        final String resourceId;
        final String desc;
        final boolean clickable;
        final boolean editable;
        final int[] bounds;

        UiNode(UiNode parent, Element e) {
            this.parent = parent;
            pkg = e.getAttribute("package");
            text = e.getAttribute("text");
            resourceId = e.getAttribute("resource-id");
            desc = e.getAttribute("content-desc");
            clickable = "true".equals(e.getAttribute("clickable"));
            editable = "true".equals(e.getAttribute("editable")) || e.getAttribute("class").endsWith("EditText");
            bounds = parseBounds(e.getAttribute("bounds"));
        }

        boolean clickableOrInside() {
            for (UiNode n = this; n != null; n = n.parent) {
                if (n.clickable) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Centre of the Send control to tap, or null unless the safety lock holds in this dump: the active
     * window belongs to [packageName], one of its editable fields holds exactly [message], the screen
     * names the recipient (see {@link #showsRecipient}), and it has a clickable Send control (by
     * resource-id first, else by content-desc). Among several, the one nearest the field's
     * bottom-right corner wins.
     */
    static int[] findTap(String xml, String packageName, String message, String contactName, String numberDigits) {
        List<UiNode> nodes = parse(xml);
        if (nodes == null || nodes.isEmpty() || !packageName.equals(nodes.get(0).pkg)) {
            return null;
        }
        UiNode field = typedField(nodes, packageName, message);
        if (field == null || field.bounds == null || !showsRecipient(nodes, packageName, contactName, numberDigits)) {
            return null;
        }
        List<UiNode> byId = new ArrayList<>();
        List<UiNode> byDesc = new ArrayList<>();
        for (UiNode node : nodes) {
            if (!packageName.equals(node.pkg) || node == field || node.bounds == null || !node.clickableOrInside()) {
                continue;
            }
            if (hasSendId(node.resourceId)) {
                byId.add(node);
            } else if (isSendWord(node.desc)) {
                byDesc.add(node);
            }
        }
        List<UiNode> candidates = byId.isEmpty() ? byDesc : byId;
        UiNode best = null;
        long bestDistance = Long.MAX_VALUE;
        for (UiNode node : candidates) {
            long dx = centre(node)[0] - field.bounds[2];
            long dy = centre(node)[1] - field.bounds[3];
            long distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                best = node;
                bestDistance = distance;
            }
        }
        return best == null ? null : centre(best);
    }

    /** After the tap: the app is still in front and none of its fields holds the message any more. */
    static boolean wasSent(String xml, String packageName, String message) {
        List<UiNode> nodes = parse(xml);
        return nodes != null && !nodes.isEmpty() && packageName.equals(nodes.get(0).pkg)
                && typedField(nodes, packageName, message) == null;
    }

    /**
     * Whether this is the right person's chat, not another one that happens to hold the same draft: a
     * non-editable node of the app whose text or content-desc contains [contactName] (ignoring case;
     * headers add suffixes such as "(You)"), or whose digits end with the number's last digits. With
     * neither a name nor at least {@link #MIN_NUMBER_TAIL} digits, nothing matches.
     */
    static boolean showsRecipient(List<UiNode> nodes, String packageName, String contactName, String numberDigits) {
        String name = contactName == null ? "" : contactName.trim().toLowerCase(Locale.ROOT);
        String digits = numberDigits == null ? "" : digitsOf(numberDigits);
        String tail = digits.length() < MIN_NUMBER_TAIL ? ""
                : digits.substring(Math.max(0, digits.length() - MAX_NUMBER_TAIL));
        if (name.isEmpty() && tail.isEmpty()) {
            return false;
        }
        for (UiNode node : nodes) {
            if (node.editable || !packageName.equals(node.pkg)) {
                continue;
            }
            for (String shown : new String[] {node.text, node.desc}) {
                if (!name.isEmpty() && shown.toLowerCase(Locale.ROOT).contains(name)) {
                    return true;
                }
                if (!tail.isEmpty() && digitsOf(shown).endsWith(tail)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The decimal digits of [text], any script's digits read as 0-9. */
    static String digitsOf(String text) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            int digit = Character.digit(text.charAt(i), 10);
            if (digit >= 0) {
                out.append((char) ('0' + digit));
            }
        }
        return out.toString();
    }

    private static UiNode typedField(List<UiNode> nodes, String packageName, String message) {
        String wanted = normalize(message);
        if (wanted.isEmpty()) {
            return null;
        }
        for (UiNode node : nodes) {
            if (node.editable && packageName.equals(node.pkg) && wanted.equals(normalize(node.text))) {
                return node;
            }
        }
        return null;
    }

    /**
     * Trimmed, with the characters uiautomator writes as '.' (XML-invalid controls) made '.' on both
     * sides, and tab/line breaks read as spaces, since an XML attribute may come back with either.
     */
    static String normalize(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\t' || c == '\n' || c == '\r') {
                out.append(' ');
            } else if ((c >= 0x1 && c <= 0x1F) || (c >= 0x7F && c <= 0x84) || (c >= 0x86 && c <= 0x9F)
                    || (c >= 0xFDD0 && c <= 0xFDDF)) {
                out.append('.');
            } else {
                out.append(c);
            }
        }
        return out.toString().trim();
    }

    static boolean hasSendId(String resourceId) {
        for (String suffix : SEND_ID_SUFFIXES) {
            if (resourceId.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    static boolean isSendWord(String desc) {
        String trimmed = desc.trim();
        for (String word : SEND_WORDS) {
            if (word.toLowerCase(Locale.ROOT).equals(trimmed.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static int[] centre(UiNode node) {
        return new int[] {(node.bounds[0] + node.bounds[2]) / 2, (node.bounds[1] + node.bounds[3]) / 2};
    }

    private static int[] parseBounds(String value) {
        Matcher m = BOUNDS.matcher(value);
        if (!m.matches()) {
            return null;
        }
        return new int[] {
            Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
            Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)),
        };
    }

    /**
     * Every node in document order, the active window's root first; null when the XML is unreadable.
     * A dump never declares a DTD or entities, so one that does is refused before parsing: that check
     * is the guarantee, the factory hardening below is best effort since Android's parser does not
     * support every feature.
     */
    static List<UiNode> parse(String xml) {
        String lower = xml.toLowerCase(Locale.ROOT);
        if (lower.contains("<!doctype") || lower.contains("<!entity")) {
            return null;
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            trySet(() -> factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true));
            trySet(() -> factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true));
            trySet(() -> factory.setFeature("http://xml.org/sax/features/external-general-entities", false));
            trySet(() -> factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false));
            trySet(() -> factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false));
            trySet(() -> factory.setXIncludeAware(false));
            trySet(() -> factory.setExpandEntityReferences(false));
            Element root = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
                    .getDocumentElement();
            List<UiNode> out = new ArrayList<>();
            collect(root, null, out);
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private interface FactorySetting {
        void apply() throws Exception;
    }

    private static void trySet(FactorySetting setting) {
        try {
            setting.apply();
        } catch (Throwable ignored) {
            // Unsupported on this runtime's parser; the DOCTYPE check in parse() still holds.
        }
    }

    private static void collect(Element element, UiNode parent, List<UiNode> out) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element && "node".equals(((Element) child).getTagName())) {
                UiNode node = new UiNode(parent, (Element) child);
                out.add(node);
                collect((Element) child, node, out);
            }
        }
    }
}
