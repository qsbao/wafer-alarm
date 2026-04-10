package com.waferalarm.evaluator;

import com.waferalarm.domain.*;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Pure function: evaluates rules against measurements using LimitResolver.
 * No DB dependency — the Spring @Component is for wiring convenience only.
 */
@Component
public class RuleEvaluator {

    private final LimitResolver limitResolver;

    public RuleEvaluator() {
        this.limitResolver = new LimitResolver();
    }

    public RuleEvaluator(LimitResolver limitResolver) {
        this.limitResolver = limitResolver;
    }

    public List<AlarmEvent> evaluate(
            List<RuleData> rules,
            List<MeasurementData> measurements,
            List<ParameterLimitData> limits) {

        List<AlarmEvent> events = new ArrayList<>();

        for (RuleData rule : rules) {
            if (!rule.enabled()) continue;

            for (MeasurementData m : measurements) {
                if (m.parameterId() != rule.parameterId()) continue;

                Optional<LimitData> resolved = limitResolver.resolve(
                        rule.parameterId(), m.context(), limits);
                if (resolved.isEmpty()) continue;

                LimitData limit = resolved.get();

                if (rule.ruleType() == RuleType.UPPER_THRESHOLD
                        && limit.upperLimit() != null
                        && m.value() > limit.upperLimit()) {
                    events.add(new AlarmEvent(
                            rule.ruleId(), rule.parameterId(), m.contextKey(),
                            rule.severity(), m.value(), limit.upperLimit(),
                            m.ts(), m.waferId(), rule.currentVersionId()));
                }

                if (rule.ruleType() == RuleType.LOWER_THRESHOLD
                        && limit.lowerLimit() != null
                        && m.value() < limit.lowerLimit()) {
                    events.add(new AlarmEvent(
                            rule.ruleId(), rule.parameterId(), m.contextKey(),
                            rule.severity(), m.value(), limit.lowerLimit(),
                            m.ts(), m.waferId(), rule.currentVersionId()));
                }
            }
        }

        return events;
    }
}
