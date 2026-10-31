package com.example.bridge;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

public class ConstraintNegationRulesTest {
    @Test public void timeAndDayPredicatesNegateAndMalformedFailsClosed() {
        ConstraintEvaluator e = new ConstraintEvaluator(null);
        Map<String,String> time = new HashMap<>(Map.of("start","00:00","end","00:00","negate","true"));
        assertFalse(e.isSatisfied(new BridgeConfig.Constraint("time", time)));
        time.put("start","bad");
        assertFalse(e.isSatisfied(new BridgeConfig.Constraint("time", time)));
        String today = java.time.LocalDate.now().getDayOfWeek().name().substring(0,3).toLowerCase(Locale.ROOT);
        assertFalse(e.isSatisfied(new BridgeConfig.Constraint("day_of_week", Map.of("days",today,"negate","true"))));
        assertTrue(e.isSatisfied(new BridgeConfig.Constraint("day_of_week", Map.of("days",java.time.LocalDate.now().plusDays(1).getDayOfWeek().name().substring(0,3).toLowerCase(Locale.ROOT),"negate","true"))));
        assertFalse(e.isSatisfied(new BridgeConfig.Constraint("day_of_week", Map.of("days","invalid","negate","true"))));
    }
    @Test public void modeUnknownAndAppOnlyTypesFailClosed() {
        ConstraintEvaluator e = new ConstraintEvaluator(null);
        for (String type : List.of("mode_is","notification_received","rss_new_item","calendar_event_active","at_time","interval","future_type"))
            assertFalse(type, e.isSatisfied(new BridgeConfig.Constraint(type, Map.of("negate","true"))));
    }
    @Test public void automaticNightModeUsesTheEffectiveConfiguration() {
        for (String mode : List.of("11", "21")) {
            ConstraintEvaluator e = new ConstraintEvaluator(null,null, command ->
                    "cmd".equals(command[0]) ? "Night mode: auto" : "mCurUiMode=0x" + mode);
            assertEquals("11".equals(mode),e.isSatisfied(new BridgeConfig.Constraint("dark_mode_on",Map.of("negate","true"))));
            assertEquals("21".equals(mode),e.isSatisfied(new BridgeConfig.Constraint("dark_mode_off",Map.of("negate","true"))));
        }
    }

    @Test public void cooldownIsNegatedOnlyInTheHistoryGate() {
        Map<String,String> p = Map.of("minutes","10","negate","true");
        assertTrue(new ConstraintEvaluator(null).isSatisfied(new BridgeConfig.Constraint("cooldown",p)));
        assertFalse(ConstraintNegation.cooldownAllows(null,p,1000));
        assertTrue(ConstraintNegation.cooldownAllows(0L,p,599999));
        assertFalse(ConstraintNegation.cooldownAllows(0L,p,600000));
        assertFalse(ConstraintNegation.cooldownAllows(5000L,p,4000));
        assertFalse(ConstraintNegation.cooldownAllows(0L,Map.of("minutes","bad","negate","true"),1000));
    }
    @Test public void onlyExactTrueEnablesNegationAndUnknownStaysFalse() {
        for (Map<String,String> p : List.of(Map.<String,String>of(), Map.of("negate","false"), Map.of("negate","TRUE"))) assertFalse(ConstraintNegation.requested(p));
        assertTrue(ConstraintNegation.requested(Map.of("negate","true")));
        assertFalse(ConstraintNegation.apply(null));
    }
    @Test public void foregroundPermissionStillGatesNegatedAppConditions() {
        ConstraintEvaluator e = new ConstraintEvaluator(null,null, command -> "topResumedActivity=ActivityRecord{x u0 test.app/.Main}");
        for (String type : List.of("app_in_foreground","app_not_in_foreground","no_app_open"))
            assertFalse(e.isSatisfied(new BridgeConfig.Constraint(type,Map.of("packageName","other.app","negate","true")),false));
    }
}
