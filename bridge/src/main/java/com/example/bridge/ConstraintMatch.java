package com.example.bridge;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A shortcut's "All" / "Any" over its conditions. Shared by the app and the bridge so both decide
 * the same way. The cooldown is a gate the callers check on their own (it always reads as
 * satisfied here), so it never counts as one of the "Any" options.
 */
public final class ConstraintMatch {
    private ConstraintMatch() {}

    public static <T> boolean matches(List<T> constraints, boolean any, Function<T, String> typeOf, Predicate<T> satisfied) {
        if (!any) {
            for (T constraint : constraints) {
                if (!satisfied.test(constraint)) return false;
            }
            return true;
        }
        boolean sawCondition = false;
        for (T constraint : constraints) {
            if (BridgeConfig.CONSTRAINT_COOLDOWN.equals(typeOf.apply(constraint))) continue;
            sawCondition = true;
            if (satisfied.test(constraint)) return true;
        }
        return !sawCondition;
    }
}
