package com.waferalarm.evaluator;

import com.waferalarm.domain.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuleEvaluatorTest {

    private final RuleEvaluator evaluator = new RuleEvaluator();

    private static MeasurementData measurement(long paramId, String waferId, double value, String tool) {
        Map<String, String> ctx = tool != null ? Map.of("tool", tool) : Map.of();
        return new MeasurementData(paramId, waferId, value, Instant.now(),
                "tool=" + (tool != null ? tool : ""), ctx);
    }

    // --- Upper threshold ---

    @Test
    void upperThreshold_fires_when_value_exceeds_limit() {
        var rule = new RuleData(1L, 10L, RuleType.UPPER_THRESHOLD, Severity.WARNING, true, null);
        var limit = new ParameterLimitData(1L, 10L, Map.of(), 100.0, null);

        List<AlarmEvent> events = evaluator.evaluate(
                List.of(rule), List.of(measurement(10L, "W001", 101.0, "A")), List.of(limit));

        assertThat(events).hasSize(1);
        AlarmEvent e = events.getFirst();
        assertThat(e.ruleId()).isEqualTo(1L);
        assertThat(e.violatingValue()).isEqualTo(101.0);
        assertThat(e.thresholdValue()).isEqualTo(100.0);
        assertThat(e.severity()).isEqualTo(Severity.WARNING);
        assertThat(e.waferId()).isEqualTo("W001");
    }

    @Test
    void upperThreshold_does_not_fire_when_value_at_limit() {
        var rule = new RuleData(1L, 10L, RuleType.UPPER_THRESHOLD, Severity.WARNING, true, null);
        var limit = new ParameterLimitData(1L, 10L, Map.of(), 100.0, null);

        List<AlarmEvent> events = evaluator.evaluate(
                List.of(rule), List.of(measurement(10L, "W002", 100.0, "A")), List.of(limit));

        assertThat(events).isEmpty();
    }

    @Test
    void upperThreshold_does_not_fire_when_value_below_limit() {
        var rule = new RuleData(1L, 10L, RuleType.UPPER_THRESHOLD, Severity.WARNING, true, null);
        var limit = new ParameterLimitData(1L, 10L, Map.of(), 100.0, null);

        List<AlarmEvent> events = evaluator.evaluate(
                List.of(rule), List.of(measurement(10L, "W003", 99.0, "A")), List.of(limit));

        assertThat(events).isEmpty();
    }

    @Test
    void disabled_rule_does_not_fire() {
        var rule = new RuleData(1L, 10L, RuleType.UPPER_THRESHOLD, Severity.WARNING, false, null);
        var limit = new ParameterLimitData(1L, 10L, Map.of(), 100.0, null);

        List<AlarmEvent> events = evaluator.evaluate(
                List.of(rule), List.of(measurement(10L, "W004", 200.0, "A")), List.of(limit));

        assertThat(events).isEmpty();
    }

    @Test
    void no_limit_defined_does_not_fire() {
        var rule = new RuleData(1L, 10L, RuleType.UPPER_THRESHOLD, Severity.WARNING, true, null);

        List<AlarmEvent> events = evaluator.evaluate(
                List.of(rule), List.of(measurement(10L, "W005", 200.0, "A")), List.of());

        assertThat(events).isEmpty();
    }

    @Test
    void wrong_parameter_does_not_fire() {
        var rule = new RuleData(1L, 10L, RuleType.UPPER_THRESHOLD, Severity.WARNING, true, null);
        var limit = new ParameterLimitData(1L, 10L, Map.of(), 100.0, null);

        List<AlarmEvent> events = evaluator.evaluate(
                List.of(rule), List.of(measurement(99L, "W006", 200.0, "A")), List.of(limit));

        assertThat(events).isEmpty();
    }

    @Test
    void multiple_measurements_multiple_violations() {
        var rule = new RuleData(1L, 10L, RuleType.UPPER_THRESHOLD, Severity.CRITICAL, true, null);
        var limit = new ParameterLimitData(1L, 10L, Map.of(), 50.0, null);

        List<AlarmEvent> events = evaluator.evaluate(
                List.of(rule),
                List.of(measurement(10L, "W010", 51.0, "A"),
                        measurement(10L, "W011", 49.0, "A"),
                        measurement(10L, "W012", 60.0, "B")),
                List.of(limit));

        assertThat(events).hasSize(2);
        assertThat(events).extracting(AlarmEvent::waferId).containsExactlyInAnyOrder("W010", "W012");
    }

    // --- Lower threshold ---

    @Test
    void lowerThreshold_fires_when_value_below_limit() {
        var rule = new RuleData(1L, 10L, RuleType.LOWER_THRESHOLD, Severity.CRITICAL, true, null);
        var limit = new ParameterLimitData(1L, 10L, Map.of(), null, 20.0);

        List<AlarmEvent> events = evaluator.evaluate(
                List.of(rule), List.of(measurement(10L, "W020", 19.0, "A")), List.of(limit));

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().violatingValue()).isEqualTo(19.0);
        assertThat(events.getFirst().thresholdValue()).isEqualTo(20.0);
        assertThat(events.getFirst().severity()).isEqualTo(Severity.CRITICAL);
    }

    @Test
    void lowerThreshold_does_not_fire_when_value_at_limit() {
        var rule = new RuleData(1L, 10L, RuleType.LOWER_THRESHOLD, Severity.WARNING, true, null);
        var limit = new ParameterLimitData(1L, 10L, Map.of(), null, 20.0);

        List<AlarmEvent> events = evaluator.evaluate(
                List.of(rule), List.of(measurement(10L, "W021", 20.0, "A")), List.of(limit));

        assertThat(events).isEmpty();
    }

    @Test
    void lowerThreshold_does_not_fire_when_value_above_limit() {
        var rule = new RuleData(1L, 10L, RuleType.LOWER_THRESHOLD, Severity.WARNING, true, null);
        var limit = new ParameterLimitData(1L, 10L, Map.of(), null, 20.0);

        List<AlarmEvent> events = evaluator.evaluate(
                List.of(rule), List.of(measurement(10L, "W022", 21.0, "A")), List.of(limit));

        assertThat(events).isEmpty();
    }

    // --- Independent upper and lower rules ---

    @Test
    void independent_upper_and_lower_rules_with_different_severity() {
        var upperRule = new RuleData(1L, 10L, RuleType.UPPER_THRESHOLD, Severity.WARNING, true, null);
        var lowerRule = new RuleData(2L, 10L, RuleType.LOWER_THRESHOLD, Severity.CRITICAL, true, null);
        var limit = new ParameterLimitData(1L, 10L, Map.of(), 100.0, 20.0);

        // Value below lower → only lower rule fires
        List<AlarmEvent> events = evaluator.evaluate(
                List.of(upperRule, lowerRule),
                List.of(measurement(10L, "W030", 15.0, "A")),
                List.of(limit));

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().ruleId()).isEqualTo(2L);
        assertThat(events.getFirst().severity()).isEqualTo(Severity.CRITICAL);
    }

    // --- Rule version tracking ---

    @Test
    void alarm_event_carries_rule_version_id() {
        var rule = new RuleData(1L, 10L, RuleType.UPPER_THRESHOLD, Severity.WARNING, true, 42L);
        var limit = new ParameterLimitData(1L, 10L, Map.of(), 100.0, null);

        List<AlarmEvent> events = evaluator.evaluate(
                List.of(rule), List.of(measurement(10L, "W040", 101.0, "A")), List.of(limit));

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().ruleVersionId()).isEqualTo(42L);
    }

    // --- Rate-of-change rules ---

    private static RuleData rocRule(long ruleId, long paramId, Double absDelta, Double pctDelta, Double minBaseline) {
        return new RuleData(ruleId, paramId, RuleType.RATE_OF_CHANGE, Severity.WARNING, true, null,
                absDelta, pctDelta, minBaseline);
    }

    @Test
    void roc_first_measurement_does_not_fire() {
        var rule = rocRule(1L, 10L, 5.0, null, null);
        var m = measurement(10L, "W001", 50.0, "A");

        EvaluationResult result = evaluator.evaluateWithState(
                List.of(rule), List.of(m), List.of(), Map.of());

        assertThat(result.events()).isEmpty();
        // But state should be recorded
        assertThat(result.updatedState()).hasSize(1);
        RuleStateData state = result.updatedState().values().iterator().next();
        assertThat(state.lastValue()).isEqualTo(50.0);
        assertThat(state.lastWaferId()).isEqualTo("W001");
    }

    // --- LimitResolver integration: specific context overrides global ---

    @Test
    void specific_context_limit_overrides_global() {
        var rule = new RuleData(1L, 10L, RuleType.UPPER_THRESHOLD, Severity.WARNING, true, null);
        var globalLimit = new ParameterLimitData(1L, 10L, Map.of(), 100.0, null);
        var toolLimit = new ParameterLimitData(2L, 10L, Map.of("tool", "A"), 200.0, null);

        // Value 150 exceeds global (100) but not tool-specific (200)
        List<AlarmEvent> events = evaluator.evaluate(
                List.of(rule), List.of(measurement(10L, "W050", 150.0, "A")),
                List.of(globalLimit, toolLimit));

        assertThat(events).isEmpty(); // tool-specific limit of 200 takes precedence
    }
}
