package com.example.bridge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Turns several keys held together into one virtual key, so each side's unchanged press-timing
 * machine resolves single/double/long on it. Used directly by both the app
 * (HardwareKeyTriggerCoordinator via KeyGestureFeed) and the detached bridge (BridgeMain), so the
 * two can never form different chords or derive different ids.
 *
 * <p>A chord forms when every member is held and the earliest and latest member down-times are at
 * most {@link #CHORD_WINDOW_MS} apart. Members whose down was already passed to the timing machine
 * are cancelled there; the window is below the 300 ms minimum long-press threshold, so their own
 * long press cannot have fired yet. A down that completes a bigger chord containing the active one
 * cancels the active chord and takes over, which is safe for the same reason. The most keys wins,
 * then the lowest id. The first member released ends the chord; the remaining member
 * ups are swallowed. Duplicate downs/ups (two input sources reporting one press) are ignored.
 */
public final class ChordTracker {
    public static final long CHORD_WINDOW_MS = 250L;

    public static final int PASS = 0;
    public static final int CANCEL = 1;
    public static final int CHORD = 2;

    public static final class Command {
        public final int kind;
        public final int keyCode;
        public final boolean down;

        public Command(int kind, int keyCode, boolean down) {
            this.kind = kind;
            this.keyCode = keyCode;
            this.down = down;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Command)) {
                return false;
            }
            Command c = (Command) o;
            return kind == c.kind && keyCode == c.keyCode && down == c.down;
        }

        @Override
        public int hashCode() {
            return Objects.hash(kind, keyCode, down);
        }

        @Override
        public String toString() {
            return (kind == PASS ? "Pass" : kind == CANCEL ? "Cancel" : "Chord") + "(" + keyCode + "," + down + ")";
        }
    }

    public static List<Integer> canonical(List<Integer> keyCodes) {
        return new ArrayList<>(new TreeSet<>(keyCodes));
    }

    /**
     * A fixed FNV-1a-style hash of the canonical keys, shared by the app and the bridge and stored
     * as a script's trigger key, so it must never change once shipped. Always negative, unlike real
     * (≥ 0) and synthetic OEM (positive) key codes.
     */
    public static int chordId(List<Integer> keyCodes) {
        long h = 0xcbf29ce484222325L;
        for (int k : canonical(keyCodes)) {
            h = (h ^ k) * 0x100000001b3L;
        }
        return Integer.MIN_VALUE | ((int) (h ^ (h >>> 32)) & 0x3fffffff);
    }

    public static int triggerKeyId(List<Integer> keyCodes) {
        List<Integer> keys = canonical(keyCodes);
        return keys.size() == 1 ? keys.get(0) : chordId(keys);
    }

    public static List<List<Integer>> chordsOf(List<List<Integer>> triggerKeyLists) {
        Set<List<Integer>> chords = new LinkedHashSet<>();
        for (List<Integer> keys : triggerKeyLists) {
            List<Integer> c = canonical(keys);
            if (c.size() >= 2) {
                chords.add(c);
            }
        }
        return new ArrayList<>(chords);
    }

    private final Map<Integer, Long> held = new HashMap<>();
    private final Set<Integer> passed = new HashSet<>();
    private final Set<Integer> swallowUps = new HashSet<>();
    private List<Integer> activeMembers;
    private int activeId;

    public synchronized List<Command> onEvent(int keyCode, boolean down, long eventTimeMs, List<List<Integer>> chords) {
        return down ? onDown(keyCode, eventTimeMs, chords) : onUp(keyCode);
    }

    public synchronized void reset() {
        held.clear();
        passed.clear();
        swallowUps.clear();
        activeMembers = null;
    }

    private List<Command> onDown(int keyCode, long eventTimeMs, List<List<Integer>> chords) {
        if (held.containsKey(keyCode)) {
            return Collections.emptyList();
        }
        held.put(keyCode, eventTimeMs);
        List<Integer> best = null;
        int bestId = 0;
        for (List<Integer> chord : chords) {
            if (!chord.contains(keyCode) || !held.keySet().containsAll(chord) || !withinWindow(chord)) {
                continue;
            }
            if (activeMembers != null && (chord.size() <= activeMembers.size() || !chord.containsAll(activeMembers))) {
                continue;
            }
            int id = chordId(chord);
            if (best == null || chord.size() > best.size() || (chord.size() == best.size() && id < bestId)) {
                best = chord;
                bestId = id;
            }
        }
        if (best != null) {
            List<Command> out = new ArrayList<>();
            if (activeMembers != null) {
                out.add(new Command(CANCEL, activeId, false));
            }
            for (int member : best) {
                if (passed.remove(member)) {
                    out.add(new Command(CANCEL, member, false));
                }
            }
            out.add(new Command(CHORD, bestId, true));
            activeMembers = new ArrayList<>(best);
            activeId = bestId;
            swallowUps.addAll(best);
            return out;
        }
        passed.add(keyCode);
        return Collections.singletonList(new Command(PASS, keyCode, true));
    }

    private List<Command> onUp(int keyCode) {
        if (held.remove(keyCode) == null) {
            return Collections.emptyList();
        }
        if (swallowUps.remove(keyCode)) {
            if (activeMembers != null && activeMembers.contains(keyCode)) {
                activeMembers = null;
                return Collections.singletonList(new Command(CHORD, activeId, false));
            }
            return Collections.emptyList();
        }
        passed.remove(keyCode);
        return Collections.singletonList(new Command(PASS, keyCode, false));
    }

    private boolean withinWindow(List<Integer> chord) {
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (int member : chord) {
            long t = held.get(member);
            min = Math.min(min, t);
            max = Math.max(max, t);
        }
        return max - min <= CHORD_WINDOW_MS;
    }
}
