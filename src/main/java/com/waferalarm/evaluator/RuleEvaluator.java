package com.waferalarm.evaluator;

import com.waferalarm.domain.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pure function: (rules, measurements, limits) → AlarmEvents.
 * Walking skeleton: only UPPER_THRESHOLD implemented.
 */
public class RuleEvaluator {

    public List<AlarmEvent> evaluate(
            List<RuleData> rules,
            List<MeasurementData> measurements,
            Map<Long, LimitData> limitsByParameterId) {

        List<AlarmEvent> events = new ArrayList<>();

        for (RuleData rule : rules) {
            if (!rule.enabled()) continue;

            LimitData limit = limitsByParameterId.get(rule.parameterId());
            if (limit == null) continue;

            for (MeasurementData m : measurements) {
                if (m.parameterId() != rule.parameterId()) continue;

                if (rule.ruleType() == RuleType.UPPER_THRESHOLD
                        && limit.upperLimit() != null
                        && m.value() > limit.upperLimit()) {
                    events.add(new AlarmEvent(
                            rule.ruleId(),
                            rule.parameterId(),
                            m.contextKey(),
                            rule.severity(),
                            m.value(),
                            limit.upperLimit(),
                            m.ts(),
                            m.waferId()
                    ));
                }
            }
        }

        return events;
    }
}
