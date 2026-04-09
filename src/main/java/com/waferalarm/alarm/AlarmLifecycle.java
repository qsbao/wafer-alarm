package com.waferalarm.alarm;

import com.waferalarm.domain.*;
import org.springframework.stereotype.Component;

@Component
public class AlarmLifecycle {

    public AlarmSnapshot apply(AlarmEvent event, AlarmSnapshot current) {
        if (current == null || current.state() == AlarmState.RESOLVED || current.state() == AlarmState.SUPPRESSED) {
            return newFiringAlarm(event);
        }

        // FIRING or ACKNOWLEDGED: accumulate occurrence
        return current.withOccurrence(event.violatingValue(), event.violationTime());
    }

    private AlarmSnapshot newFiringAlarm(AlarmEvent event) {
        return new AlarmSnapshot(
                null,
                event.ruleId(),
                event.parameterId(),
                event.contextKey(),
                AlarmState.FIRING,
                event.severity(),
                1,
                event.violationTime(),
                event.violationTime(),
                event.violatingValue(),
                event.thresholdValue()
        );
    }
}
