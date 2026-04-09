package com.waferalarm.alarm;

import com.waferalarm.domain.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AlarmLifecycleTest {

    private final AlarmLifecycle lifecycle = new AlarmLifecycle();

    @Test
    void new_violation_with_no_existing_alarm_opens_firing_alarm() {
        var event = new AlarmEvent(1L, 10L, "tool=A", Severity.WARNING,
                105.0, 100.0, Instant.parse("2026-01-01T00:00:00Z"), "W001");

        AlarmSnapshot result = lifecycle.apply(event, null);

        assertThat(result.alarmId()).isNull();
        assertThat(result.state()).isEqualTo(AlarmState.FIRING);
        assertThat(result.ruleId()).isEqualTo(1L);
        assertThat(result.parameterId()).isEqualTo(10L);
        assertThat(result.contextKey()).isEqualTo("tool=A");
        assertThat(result.severity()).isEqualTo(Severity.WARNING);
        assertThat(result.occurrenceCount()).isEqualTo(1);
        assertThat(result.lastValue()).isEqualTo(105.0);
        assertThat(result.thresholdValue()).isEqualTo(100.0);
        assertThat(result.firstViolationAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(result.lastViolationAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void subsequent_violation_on_existing_firing_alarm_increments_occurrence() {
        var existing = new AlarmSnapshot(42L, 1L, 10L, "tool=A",
                AlarmState.FIRING, Severity.WARNING, 1,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"),
                105.0, 100.0);

        var event = new AlarmEvent(1L, 10L, "tool=A", Severity.WARNING,
                110.0, 100.0, Instant.parse("2026-01-01T01:00:00Z"), "W002");

        AlarmSnapshot result = lifecycle.apply(event, existing);

        assertThat(result.alarmId()).isEqualTo(42L);
        assertThat(result.state()).isEqualTo(AlarmState.FIRING);
        assertThat(result.occurrenceCount()).isEqualTo(2);
        assertThat(result.lastValue()).isEqualTo(110.0);
        assertThat(result.lastViolationAt()).isEqualTo(Instant.parse("2026-01-01T01:00:00Z"));
        assertThat(result.firstViolationAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void subsequent_violation_on_acknowledged_alarm_increments_occurrence_keeps_state() {
        var existing = new AlarmSnapshot(42L, 1L, 10L, "tool=A",
                AlarmState.ACKNOWLEDGED, Severity.WARNING, 3,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T02:00:00Z"),
                108.0, 100.0);

        var event = new AlarmEvent(1L, 10L, "tool=A", Severity.WARNING,
                112.0, 100.0, Instant.parse("2026-01-01T03:00:00Z"), "W005");

        AlarmSnapshot result = lifecycle.apply(event, existing);

        assertThat(result.state()).isEqualTo(AlarmState.ACKNOWLEDGED);
        assertThat(result.occurrenceCount()).isEqualTo(4);
    }

    @Test
    void resolved_alarm_gets_reopened_as_firing_on_new_violation() {
        var existing = new AlarmSnapshot(42L, 1L, 10L, "tool=A",
                AlarmState.RESOLVED, Severity.WARNING, 5,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T04:00:00Z"),
                106.0, 100.0);

        var event = new AlarmEvent(1L, 10L, "tool=A", Severity.WARNING,
                115.0, 100.0, Instant.parse("2026-01-02T00:00:00Z"), "W010");

        AlarmSnapshot result = lifecycle.apply(event, existing);

        assertThat(result.alarmId()).isNull(); // new alarm row
        assertThat(result.state()).isEqualTo(AlarmState.FIRING);
        assertThat(result.occurrenceCount()).isEqualTo(1);
    }
}
