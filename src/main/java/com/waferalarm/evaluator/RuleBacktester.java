package com.waferalarm.evaluator;

import com.waferalarm.domain.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RuleBacktester {

    private final RuleEvaluator ruleEvaluator;
    private final MeasurementRepository measurementRepo;
    private final ParameterLimitRepository limitRepo;

    public RuleBacktester(RuleEvaluator ruleEvaluator,
                          MeasurementRepository measurementRepo,
                          ParameterLimitRepository limitRepo) {
        this.ruleEvaluator = ruleEvaluator;
        this.measurementRepo = measurementRepo;
        this.limitRepo = limitRepo;
    }

    public BacktestResult run(BacktestRequest request) {
        // Build a synthetic rule from the request (always enabled for backtest)
        RuleData rule = new RuleData(
                0L, // synthetic rule ID
                request.parameterId(),
                request.ruleType(),
                request.severity(),
                true, // always enabled
                null, // no version ID
                request.absoluteDelta(),
                request.percentageDelta(),
                request.minimumBaseline()
        );

        // Query measurements in the time window
        List<MeasurementEntity> entities = measurementRepo.findByParameterIdAndTsBetween(
                request.parameterId(), request.from(), request.to());

        List<MeasurementData> measurements = entities.stream()
                .map(e -> new MeasurementData(
                        e.getParameterId(), e.getWaferId(), e.getValue(),
                        e.getTs(), e.deriveContextKey(), e.deriveContextMap()))
                .toList();

        // Load limits for this parameter
        List<ParameterLimitData> limits = limitRepo.findByParameterId(request.parameterId())
                .stream().map(ParameterLimitEntity::toData).toList();

        // Evaluate — use evaluateWithState for ROC support, starting with empty state
        EvaluationResult evalResult = ruleEvaluator.evaluateWithState(
                List.of(rule), measurements, limits, new java.util.HashMap<>());

        // Convert alarm events to backtest violations
        List<BacktestViolation> violations = evalResult.events().stream()
                .map(e -> new BacktestViolation(
                        e.violationTime(), e.contextKey(), e.violatingValue(),
                        e.thresholdValue(), e.severity().name(), e.waferId()))
                .toList();

        Map<String, Long> severityBreakdown = violations.stream()
                .collect(Collectors.groupingBy(BacktestViolation::severity, Collectors.counting()));

        return new BacktestResult(violations.size(), severityBreakdown, violations);
    }
}
