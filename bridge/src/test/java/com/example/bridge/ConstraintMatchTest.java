package com.example.bridge;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

public class ConstraintMatchTest {
    private static boolean match(boolean any, String... typesThatHold) {
        Set<String> holding = new HashSet<>(Arrays.asList(typesThatHold));
        List<String> constraints = Arrays.asList("wifi_on", "charging", BridgeConfig.CONSTRAINT_COOLDOWN);
        // The cooldown always reads as satisfied, like the real evaluators.
        return ConstraintMatch.matches(constraints, any, t -> t,
                t -> t.equals(BridgeConfig.CONSTRAINT_COOLDOWN) || holding.contains(t));
    }

    @Test public void allNeedsEveryCondition() {
        assertTrue(match(false, "wifi_on", "charging"));
        assertFalse(match(false, "wifi_on"));
    }

    @Test public void anyNeedsOneConditionAndTheCooldownIsNotOne() {
        assertTrue(match(true, "charging"));
        assertFalse("the always-true cooldown must not satisfy Any on its own", match(true));
    }

    @Test public void anyWithNoRealConditionBehavesLikeAll() {
        List<String> onlyCooldown = Collections.singletonList(BridgeConfig.CONSTRAINT_COOLDOWN);
        assertTrue(ConstraintMatch.matches(onlyCooldown, true, t -> t, t -> true));
        assertTrue(ConstraintMatch.matches(Collections.<String>emptyList(), true, t -> t, t -> false));
    }
}
