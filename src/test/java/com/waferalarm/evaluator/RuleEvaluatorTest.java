package com.waferalarm.evaluator;

import com.waferalarm.domain.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuleEvaluatorTest {

    private final RuleEvaluator evaluator = new RuleEvaluator();

    // --- Upper threshold ---

    @Test
    void upperThreshold_fires_when_value_exceeds_limit() {
        var rule = new RuleData(1L, 10L, RuleType.UPPER_THRESHOLD, Severity.WARNING, true);
        var limit = new LimitData(10L, 100.0, null);
        var measurement = new MeasurementData(10L, "W001", 101.0, Instant.now(), "tool=A");

        List<AlarmEvent> events = evaluator.evaluate(
                List.of(rule), List.of(measurement), Map.of(10L, limit));

        assertThat(events).hasSize(1);
        AlarmEvent e = events.getFirst();
        assertThat(e.ruleId()).isEqualTo(1L);
        assertThat(e.violatingValue()).isEqualTo(101.0);
        assertThat(e.thresholdValue()).isEqualTo(100.0);
        assertThat(e.severity()).isEqualTo(Severity.WARNING);
        assertThat(e.contextKey()).isEqualTo("tool=A");
        assertThat(e.waferId()).isEqualTo("W001");
    }

    @Test
    void upperThreshold_does_not_fire_when_value_at_limit() {
        var rule = new RuleData(1L, 10L, RuleType.UPPER_THRESHOLD, Severity.WARNING, true);
        var limit = new LimitData(10L, 100.0, null);
        var measurement = new MeasurementData(10L, "W002", 100.0, Instant.now(), "tool=A");

        List<AlarmEvent> events = evaluator.evaluate(
                List.of(rule), List.of(measurement), Map.of(10L, limit));

        assertThat(events).isEmpty();
    }

    @Test
    void upperThreshold_does_not_fire_when_value_below_limit() {
        var rule = new RuleData(1L, 10L, RuleType.UPPER_THRESHOLD, Severity.WARNING, true);
        var limit = new LimitData(10L, 100.0, null);
        var measurement = new MeasurementData(10L, "W003", 99.0, Instant.now(), "tool=A");

        List<AlarmEvent> events = evaluator.evaluate(
                List.of(rule), List.of(measurement), Map.of(10L, limit));

        assertThat(events).isEmpty();
    }

    @Test
    void disabled_rule_does_not_fire() {
        var rule = new RuleData(1L, 10L, RuleType.UPPER_THRESHOLD, Severity.WARNING, false);
        var limit = new LimitData(10L, 100.0, null);
        var measurement = new MeasurementData(10L, "W004", 200.0, Instant.now(), "tool=A");

        List<AlarmEvent> events = evaluator.evaluate(
                List.of(rule), List.of(measurement), Map.of(10L, limit));

        assertThat(events).isEmpty();
    }

    @Test
    void no_limit_defined_does_not_fire() {
        var rule = new RuleData(1L, 10L, RuleType.UPPER_THRESHOLD, Severity.WARNING, true);
        var measurement = new MeasurementData(10L, "W005", 200.0, Instant.now(), "tool=A");

        List<AlarmEvent> events = evaluator.evaluate(
                List.of(rule), List.of(measurement), Map.of());

        assertThat(events).isEmpty();
    }

    @Test
    void wrong_parameter_does_not_fire() {
        var rule = new RuleData(1L, 10L, RuleType.UPPER_THRESHOLD, Severity.WARNING, true);
        var limit = new LimitData(10L, 100.0, null);
        var measurement = new MeasurementData(99L, "W006", 200.0, Instant.now(), "tool=A");

        List<AlarmEvent> events = evaluator.evaluate(
                List.of(rule), List.of(measurement), Map.of(10L, limit));

        assertThat(events).isEmpty();
    }

    @Test
    void multiple_measurements_multiple_violations() {
        var rule = new RuleData(1L, 10L, RuleType.UPPER_THRESHOLD, Severity.CRITICAL, true);
        var limit = new LimitData(10L, 50.0, null);
        var m1 = new MeasurementData(10L, "W010", 51.0, Instant.now(), "tool=A");
        var m2 = new MeasurementData(10L, "W011", 49.0, Instant.now(), "tool=A");
        var m3 = new MeasurementData(10L, "W012", 60.0, Instant.now(), "tool=B");

        List<AlarmEvent> events = evaluator.evaluate(
                List.of(rule), List.of(m1, m2, m3), Map.of(10L, limit));

        assertThat(events).hasSize(2);
        assertThat(events).extracting(AlarmEvent::waferId).containsExactlyInAnyOrder("W010", "W012");
    }
}
