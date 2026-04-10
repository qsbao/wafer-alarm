package com.waferalarm.evaluator;

import com.waferalarm.domain.*;

import org.springframework.stereotype.Component;

import java.util.*;

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

        return evaluateWithState(rules, measurements, limits, Map.of()).events();
    }

    public EvaluationResult evaluateWithState(
            List<RuleData> rules,
            List<MeasurementData> measurements,
            List<ParameterLimitData> limits,
            Map<String, RuleStateData> currentState) {

        List<AlarmEvent> events = new ArrayList<>();
        Map<String, RuleStateData> updatedState = new HashMap<>(currentState);

        // Sort measurements by timestamp for correct ROC ordering
        List<MeasurementData> sorted = measurements.stream()
                .sorted(Comparator.comparing(MeasurementData::ts))
                .toList();

        for (RuleData rule : rules) {
            if (!rule.enabled()) continue;

            for (MeasurementData m : sorted) {
                if (m.parameterId() != rule.parameterId()) continue;

                if (rule.ruleType() == RuleType.RATE_OF_CHANGE) {
                    evaluateRoc(rule, m, updatedState, events);
                } else {
                    evaluateThreshold(rule, m, limits, events);
                }
            }
        }

        return new EvaluationResult(events, updatedState);
    }

    private void evaluateThreshold(RuleData rule, MeasurementData m,
                                   List<ParameterLimitData> limits, List<AlarmEvent> events) {
        Optional<LimitData> resolved = limitResolver.resolve(
                rule.parameterId(), m.context(), limits);
        if (resolved.isEmpty()) return;

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

    private void evaluateRoc(RuleData rule, MeasurementData m,
                             Map<String, RuleStateData> state, List<AlarmEvent> events) {
        String stateKey = rule.ruleId() + "|" + m.contextKey();
        RuleStateData prev = state.get(stateKey);

        if (prev != null) {
            double delta = Math.abs(m.value() - prev.lastValue());

            boolean fired = false;

            // Absolute delta check
            if (rule.absoluteDelta() != null && delta > rule.absoluteDelta()) {
                fired = true;
            }

            // Percentage delta check with minimum-baseline guard
            if (!fired && rule.percentageDelta() != null) {
                double baseline = Math.abs(prev.lastValue());
                if (rule.minimumBaseline() == null || baseline >= rule.minimumBaseline()) {
                    if (baseline > 0) {
                        double pctDelta = (delta / baseline) * 100.0;
                        if (pctDelta > rule.percentageDelta()) {
                            fired = true;
                        }
                    }
                }
            }

            if (fired) {
                events.add(new AlarmEvent(
                        rule.ruleId(), rule.parameterId(), m.contextKey(),
                        rule.severity(), m.value(), prev.lastValue(),
                        m.ts(), m.waferId(), rule.currentVersionId()));
            }
        }

        // Always update state
        state.put(stateKey, new RuleStateData(
                rule.ruleId(), m.contextKey(), m.value(), m.ts(), m.waferId()));
    }
}
