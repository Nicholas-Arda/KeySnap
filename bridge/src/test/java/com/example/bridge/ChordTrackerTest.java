package com.example.bridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class ChordTrackerTest {
    private static final int UP = 24, DOWN = 25, POWER = 26;
    private static final List<List<Integer>> VOL = Collections.singletonList(Arrays.asList(UP, DOWN));
    private static final int VOL_ID = ChordTracker.chordId(Arrays.asList(UP, DOWN));

    private static ChordTracker.Command pass(int k, boolean d) { return new ChordTracker.Command(ChordTracker.PASS, k, d); }
    private static ChordTracker.Command cancel(int k) { return new ChordTracker.Command(ChordTracker.CANCEL, k, false); }
    private static ChordTracker.Command chord(int id, boolean d) { return new ChordTracker.Command(ChordTracker.CHORD, id, d); }

    @Test
    public void chordIdIsNegativeStableAndOrderIndependent() {
        assertTrue(VOL_ID < 0);
        assertEquals(VOL_ID, ChordTracker.chordId(Arrays.asList(DOWN, UP, DOWN)));
        assertNotEquals(VOL_ID, ChordTracker.chordId(Arrays.asList(POWER, DOWN)));
    }

    @Test
    public void chordIdsOfLookalikeListsDiffer() {
        // List.hashCode() collides on these: 31 * (31 + 24) + 25 == 31 * (31 + 23) + 56.
        assertNotEquals(ChordTracker.chordId(Arrays.asList(24, 25)), ChordTracker.chordId(Arrays.asList(23, 56)));
    }

    @Test
    public void triggerKeyIdIsTheKeyForOneKeyAndTheChordIdForMore() {
        assertEquals(UP, ChordTracker.triggerKeyId(Collections.singletonList(UP)));
        assertEquals(UP, ChordTracker.triggerKeyId(Arrays.asList(UP, UP)));
        assertEquals(VOL_ID, ChordTracker.triggerKeyId(Arrays.asList(DOWN, UP)));
    }

    @Test
    public void chordsOfKeepsDistinctMultiKeySetsOnly() {
        List<List<Integer>> chords = ChordTracker.chordsOf(Arrays.asList(
                Collections.singletonList(UP), Arrays.asList(DOWN, UP), Arrays.asList(UP, DOWN)));
        assertEquals(Collections.singletonList(Arrays.asList(UP, DOWN)), chords);
    }

    @Test
    public void formsWithinTheWindowInEitherOrderAndCancelsThePassedMember() {
        ChordTracker t = new ChordTracker();
        assertEquals(Collections.singletonList(pass(DOWN, true)), t.onEvent(DOWN, true, 1_000, VOL));
        assertEquals(Arrays.asList(cancel(DOWN), chord(VOL_ID, true)), t.onEvent(UP, true, 1_250, VOL));
    }

    @Test
    public void formsInTheOtherOrderToo() {
        ChordTracker t = new ChordTracker();
        assertEquals(Collections.singletonList(pass(UP, true)), t.onEvent(UP, true, 1_000, VOL));
        assertEquals(Arrays.asList(cancel(UP), chord(VOL_ID, true)), t.onEvent(DOWN, true, 1_100, VOL));
    }

    @Test
    public void biggerChordTakesOverWhenItsLastKeyComesAfterTheSmallerChord() {
        List<List<Integer>> chords = Arrays.asList(Arrays.asList(UP, DOWN), Arrays.asList(UP, DOWN, POWER));
        int triple = ChordTracker.chordId(Arrays.asList(UP, DOWN, POWER));
        ChordTracker t = new ChordTracker();
        t.onEvent(UP, true, 1_000, chords);
        assertEquals(Arrays.asList(cancel(UP), chord(VOL_ID, true)), t.onEvent(DOWN, true, 1_010, chords));
        assertEquals(Arrays.asList(cancel(VOL_ID), chord(triple, true)), t.onEvent(POWER, true, 1_020, chords));
        assertEquals(Collections.singletonList(chord(triple, false)), t.onEvent(UP, false, 1_200, chords));
        assertEquals(Collections.emptyList(), t.onEvent(DOWN, false, 1_210, chords));
        assertEquals(Collections.emptyList(), t.onEvent(POWER, false, 1_220, chords));
    }

    @Test
    public void resetForgetsHeldKeys() {
        ChordTracker t = new ChordTracker();
        t.onEvent(UP, true, 1_000, VOL);
        t.onEvent(DOWN, true, 1_000, VOL);
        t.reset();
        assertEquals(Collections.emptyList(), t.onEvent(DOWN, false, 1_100, VOL));
        assertEquals(Collections.singletonList(pass(UP, true)), t.onEvent(UP, true, 2_000, VOL));
    }

    @Test
    public void doesNotFormPastTheWindow() {
        ChordTracker t = new ChordTracker();
        t.onEvent(UP, true, 1_000, VOL);
        assertEquals(Collections.singletonList(pass(DOWN, true)), t.onEvent(DOWN, true, 1_251, VOL));
    }

    @Test
    public void laterMemberUpsAreSwallowed() {
        ChordTracker t = new ChordTracker();
        t.onEvent(UP, true, 1_000, VOL);
        t.onEvent(DOWN, true, 1_050, VOL);
        assertEquals(Collections.singletonList(chord(VOL_ID, false)), t.onEvent(UP, false, 1_200, VOL));
        assertEquals(Collections.emptyList(), t.onEvent(DOWN, false, 1_900, VOL));
    }

    @Test
    public void duplicateDownAndUpAreIgnored() {
        ChordTracker t = new ChordTracker();
        t.onEvent(UP, true, 1_000, VOL);
        assertEquals(Collections.emptyList(), t.onEvent(UP, true, 1_010, VOL));
        t.onEvent(DOWN, true, 1_020, VOL);
        assertEquals(Collections.emptyList(), t.onEvent(DOWN, true, 1_030, VOL));
        t.onEvent(UP, false, 1_100, VOL);
        t.onEvent(DOWN, false, 1_110, VOL);
        assertEquals(Collections.emptyList(), t.onEvent(DOWN, false, 1_120, VOL));
    }

    @Test
    public void unrelatedKeysPassThroughWhileAChordIsHeld() {
        ChordTracker t = new ChordTracker();
        t.onEvent(UP, true, 1_000, VOL);
        t.onEvent(DOWN, true, 1_000, VOL);
        assertEquals(Collections.singletonList(pass(POWER, true)), t.onEvent(POWER, true, 1_100, VOL));
        assertEquals(Collections.singletonList(pass(POWER, false)), t.onEvent(POWER, false, 1_150, VOL));
    }

    @Test
    public void largestChordWins() {
        List<List<Integer>> chords = Arrays.asList(Arrays.asList(UP, DOWN), Arrays.asList(UP, DOWN, POWER));
        int triple = ChordTracker.chordId(Arrays.asList(UP, DOWN, POWER));
        ChordTracker t = new ChordTracker();
        t.onEvent(POWER, true, 1_000, chords);
        t.onEvent(UP, true, 1_010, chords);
        // Cancels come in the chord's ascending key order: UP (24) before POWER (26).
        assertEquals(Arrays.asList(cancel(UP), cancel(POWER), chord(triple, true)), t.onEvent(DOWN, true, 1_020, chords));
    }

    @Test
    public void chordCanBePressedAgainAfterFullRelease() {
        ChordTracker t = new ChordTracker();
        t.onEvent(UP, true, 1_000, VOL);
        t.onEvent(DOWN, true, 1_000, VOL);
        t.onEvent(UP, false, 1_100, VOL);
        t.onEvent(DOWN, false, 1_100, VOL);
        t.onEvent(UP, true, 1_200, VOL);
        assertEquals(Arrays.asList(cancel(UP), chord(VOL_ID, true)), t.onEvent(DOWN, true, 1_210, VOL));
    }
}
