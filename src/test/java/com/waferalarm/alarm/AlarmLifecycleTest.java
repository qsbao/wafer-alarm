package com.waferalarm.alarm;

import com.waferalarm.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AlarmLifecycleTest {

    private final AlarmLifecycle lifecycle = new AlarmLifecycle();

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-01-01T01:00:00Z");
    private static final Instant T2 = Instant.parse("2026-01-01T02:00:00Z");
    private static final Instant T3 = Instant.parse("2026-01-01T03:00:00Z");

    private static AlarmEvent violation(Instant time) {
        return new AlarmEvent(1L, 10L, "tool=A", Severity.WARNING,
                105.0, 100.0, time, "W001");
    }

    private static AlarmSnapshot alarm(AlarmState state, int occurrences) {
        return new AlarmSnapshot(42L, 1L, 10L, "tool=A",
                state, Severity.WARNING, occurrences, T0, T1,
                105.0, 100.0, 0, null);
    }

    private static AlarmSnapshot alarmWithCleanCount(AlarmState state, int cleanCount) {
        return new AlarmSnapshot(42L, 1L, 10L, "tool=A",
                state, Severity.WARNING, 3, T0, T1,
                105.0, 100.0, cleanCount, null);
    }

    private static AlarmSnapshot suppressedAlarm(Instant until) {
        return new AlarmSnapshot(42L, 1L, 10L, "tool=A",
                AlarmState.SUPPRESSED, Severity.WARNING, 3, T0, T1,
                105.0, 100.0, 0, until);
    }

    // ── Violation event (apply) ──────────────────────────────────────────

    @Test
    void new_violation_with_no_existing_alarm_opens_firing_alarm() {
        AlarmSnapshot result = lifecycle.apply(violation(T0), null, T0);

        assertThat(result.alarmId()).isNull();
        assertThat(result.state()).isEqualTo(AlarmState.FIRING);
        assertThat(result.occurrenceCount()).isEqualTo(1);
        assertThat(result.consecutiveCleanCount()).isEqualTo(0);
    }

    @Test
    void subsequent_violation_on_firing_alarm_increments_occurrence() {
        AlarmSnapshot result = lifecycle.apply(violation(T2), alarm(AlarmState.FIRING, 1), T2);

        assertThat(result.state()).isEqualTo(AlarmState.FIRING);
        assertThat(result.occurrenceCount()).isEqualTo(2);
        assertThat(result.consecutiveCleanCount()).isEqualTo(0);
    }

    @Test
    void subsequent_violation_on_acknowledged_alarm_keeps_state() {
        AlarmSnapshot result = lifecycle.apply(violation(T2), alarm(AlarmState.ACKNOWLEDGED, 3), T2);

        assertThat(result.state()).isEqualTo(AlarmState.ACKNOWLEDGED);
        assertThat(result.occurrenceCount()).isEqualTo(4);
    }

    @Test
    void resolved_alarm_gets_reopened_as_firing() {
        AlarmSnapshot result = lifecycle.apply(violation(T2), alarm(AlarmState.RESOLVED, 5), T2);

        assertThat(result.alarmId()).isNull();
        assertThat(result.state()).isEqualTo(AlarmState.FIRING);
        assertThat(result.occurrenceCount()).isEqualTo(1);
    }

    // ── Suppression window ───────────────────────────────────────────────

    @Test
    void suppressed_alarm_within_window_ignores_violation() {
        AlarmSnapshot suppressed = suppressedAlarm(T3); // suppressed until T3
        AlarmSnapshot result = lifecycle.apply(violation(T2), suppressed, T2); // now=T2, before T3

        assertThat(result.state()).isEqualTo(AlarmState.SUPPRESSED);
        assertThat(result.occurrenceCount()).isEqualTo(3); // unchanged
    }

    @Test
    void suppressed_alarm_past_window_reopens_on_violation() {
        AlarmSnapshot suppressed = suppressedAlarm(T1); // suppressed until T1
        AlarmSnapshot result = lifecycle.apply(violation(T2), suppressed, T2); // now=T2, past T1

        assertThat(result.alarmId()).isNull();
        assertThat(result.state()).isEqualTo(AlarmState.FIRING);
        assertThat(result.occurrenceCount()).isEqualTo(1);
    }

    // ── Auto-close (clean wafer) ─────────────────────────────────────────

    @Test
    void clean_wafer_increments_consecutive_clean_count() {
        AlarmSnapshot firing = alarmWithCleanCount(AlarmState.FIRING, 0);
        AlarmSnapshot result = lifecycle.onCleanWafer(firing, 3);

        assertThat(result.state()).isEqualTo(AlarmState.FIRING);
        assertThat(result.consecutiveCleanCount()).isEqualTo(1);
    }

    @Test
    void n_consecutive_clean_wafers_auto_closes_alarm() {
        AlarmSnapshot firing = alarmWithCleanCount(AlarmState.FIRING, 2);
        AlarmSnapshot result = lifecycle.onCleanWafer(firing, 3);

        assertThat(result.state()).isEqualTo(AlarmState.RESOLVED);
        assertThat(result.consecutiveCleanCount()).isEqualTo(3);
    }

    @Test
    void violation_resets_consecutive_clean_count() {
        AlarmSnapshot withCleans = alarmWithCleanCount(AlarmState.FIRING, 2);
        AlarmSnapshot result = lifecycle.apply(violation(T2), withCleans, T2);

        assertThat(result.consecutiveCleanCount()).isEqualTo(0);
    }

    // ── Manual actions ───────────────────────────────────────────────────

    @Test
    void acknowledge_firing_alarm() {
        AlarmSnapshot result = lifecycle.acknowledge(alarm(AlarmState.FIRING, 1));

        assertThat(result.state()).isEqualTo(AlarmState.ACKNOWLEDGED);
    }

    @Test
    void acknowledge_non_firing_throws() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> lifecycle.acknowledge(alarm(AlarmState.RESOLVED, 1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void resolve_firing_alarm() {
        AlarmSnapshot result = lifecycle.resolve(alarm(AlarmState.FIRING, 1));

        assertThat(result.state()).isEqualTo(AlarmState.RESOLVED);
    }

    @Test
    void resolve_acknowledged_alarm() {
        AlarmSnapshot result = lifecycle.resolve(alarm(AlarmState.ACKNOWLEDGED, 3));

        assertThat(result.state()).isEqualTo(AlarmState.RESOLVED);
    }

    @Test
    void resolve_already_resolved_throws() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> lifecycle.resolve(alarm(AlarmState.RESOLVED, 1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void suppress_firing_alarm() {
        AlarmSnapshot result = lifecycle.suppress(alarm(AlarmState.FIRING, 1), T3);

        assertThat(result.state()).isEqualTo(AlarmState.SUPPRESSED);
        assertThat(result.suppressedUntil()).isEqualTo(T3);
    }

    @Test
    void suppress_acknowledged_alarm() {
        AlarmSnapshot result = lifecycle.suppress(alarm(AlarmState.ACKNOWLEDGED, 3), T3);

        assertThat(result.state()).isEqualTo(AlarmState.SUPPRESSED);
        assertThat(result.suppressedUntil()).isEqualTo(T3);
    }

    @Test
    void suppress_resolved_alarm_throws() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> lifecycle.suppress(alarm(AlarmState.RESOLVED, 1), T3))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── Table-driven state transition tests ──────────────────────────────

    static Stream<Arguments> stateTransitions() {
        return Stream.of(
                // description, initial state, action, expected state
                Arguments.of("FIRING + violation → FIRING", AlarmState.FIRING, "violation", AlarmState.FIRING),
                Arguments.of("ACKNOWLEDGED + violation → ACKNOWLEDGED", AlarmState.ACKNOWLEDGED, "violation", AlarmState.ACKNOWLEDGED),
                Arguments.of("RESOLVED + violation → FIRING (new)", AlarmState.RESOLVED, "violation", AlarmState.FIRING),
                Arguments.of("FIRING + ack → ACKNOWLEDGED", AlarmState.FIRING, "ack", AlarmState.ACKNOWLEDGED),
                Arguments.of("FIRING + resolve → RESOLVED", AlarmState.FIRING, "resolve", AlarmState.RESOLVED),
                Arguments.of("ACKNOWLEDGED + resolve → RESOLVED", AlarmState.ACKNOWLEDGED, "resolve", AlarmState.RESOLVED),
                Arguments.of("FIRING + suppress → SUPPRESSED", AlarmState.FIRING, "suppress", AlarmState.SUPPRESSED),
                Arguments.of("ACKNOWLEDGED + suppress → SUPPRESSED", AlarmState.ACKNOWLEDGED, "suppress", AlarmState.SUPPRESSED)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("stateTransitions")
    void state_transitions(String desc, AlarmState initial, String action, AlarmState expected) {
        AlarmSnapshot current = alarm(initial, 1);
        AlarmSnapshot result = switch (action) {
            case "violation" -> lifecycle.apply(violation(T2), current, T2);
            case "ack" -> lifecycle.acknowledge(current);
            case "resolve" -> lifecycle.resolve(current);
            case "suppress" -> lifecycle.suppress(current, T3);
            default -> throw new IllegalArgumentException(action);
        };
        assertThat(result.state()).isEqualTo(expected);
    }
}
