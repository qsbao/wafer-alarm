package com.waferalarm.evaluator;

import com.waferalarm.alarm.AlarmLifecycle;
import com.waferalarm.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

@Component
public class EvaluatorRunner {

    private static final Logger log = LoggerFactory.getLogger(EvaluatorRunner.class);
    private static final String WATERMARK_KEY = "evaluator-main";

    private final MeasurementRepository measurementRepo;
    private final RuleRepository ruleRepo;
    private final ParameterRepository parameterRepo;
    private final AlarmRepository alarmRepo;
    private final EvalWatermarkRepository watermarkRepo;
    private final RuleEvaluator ruleEvaluator;
    private final AlarmLifecycle alarmLifecycle;
    private final ExecutorService evaluatorExecutor;
    private final int autoCloseThreshold;

    public EvaluatorRunner(
            MeasurementRepository measurementRepo,
            RuleRepository ruleRepo,
            ParameterRepository parameterRepo,
            AlarmRepository alarmRepo,
            EvalWatermarkRepository watermarkRepo,
            RuleEvaluator ruleEvaluator,
            AlarmLifecycle alarmLifecycle,
            @Qualifier("evaluatorExecutor") ExecutorService evaluatorExecutor,
            @Value("${app.alarm.auto-close-threshold:3}") int autoCloseThreshold) {
        this.measurementRepo = measurementRepo;
        this.ruleRepo = ruleRepo;
        this.parameterRepo = parameterRepo;
        this.alarmRepo = alarmRepo;
        this.watermarkRepo = watermarkRepo;
        this.ruleEvaluator = ruleEvaluator;
        this.alarmLifecycle = alarmLifecycle;
        this.evaluatorExecutor = evaluatorExecutor;
        this.autoCloseThreshold = autoCloseThreshold;
    }

    public void tick() {
        EvalWatermark watermark = watermarkRepo.findByWatermarkKey(WATERMARK_KEY)
                .orElse(new EvalWatermark(WATERMARK_KEY, Instant.EPOCH));

        Instant strictWatermark = watermark.getLastIngestedAt();
        Instant queryFrom = strictWatermark.minus(10, ChronoUnit.MINUTES);
        List<MeasurementEntity> allFetched = measurementRepo.findIngestedAfter(queryFrom);

        List<MeasurementEntity> measurements = allFetched.stream()
                .filter(m -> m.getIngestedAt().isAfter(strictWatermark))
                .toList();

        if (measurements.isEmpty()) {
            log.debug("No new measurements to evaluate");
            return;
        }

        List<RuleEntity> enabledRules = ruleRepo.findByEnabledTrue();
        if (enabledRules.isEmpty()) return;

        List<RuleData> rules = enabledRules.stream().map(RuleEntity::toRuleData).toList();

        Map<Long, LimitData> limits = parameterRepo.findAll().stream()
                .collect(Collectors.toMap(
                        ParameterEntity::getId,
                        p -> new LimitData(p.getId(), p.getDefaultUpperLimit(), p.getDefaultLowerLimit())
                ));

        List<MeasurementData> measurementData = measurements.stream()
                .map(m -> new MeasurementData(
                        m.getParameterId(), m.getWaferId(), m.getValue(),
                        m.getTs(), m.deriveContextKey()))
                .toList();

        List<AlarmEvent> events = ruleEvaluator.evaluate(rules, measurementData, limits);
        Instant now = Instant.now();

        // Track which (ruleId, contextKey) pairs had violations
        Set<String> violatedKeys = events.stream()
                .map(e -> e.ruleId() + "|" + e.contextKey())
                .collect(Collectors.toSet());

        // Process violations
        for (AlarmEvent event : events) {
            List<AlarmState> openStates = List.of(
                    AlarmState.FIRING, AlarmState.ACKNOWLEDGED, AlarmState.SUPPRESSED);
            AlarmEntity existing = alarmRepo
                    .findByRuleIdAndContextKeyAndStateIn(event.ruleId(), event.contextKey(), openStates)
                    .orElse(null);

            AlarmSnapshot currentSnapshot = existing != null ? existing.toSnapshot() : null;
            AlarmSnapshot newSnapshot = alarmLifecycle.apply(event, currentSnapshot, now);

            if (existing != null && newSnapshot.alarmId() != null) {
                existing.updateFromSnapshot(newSnapshot);
                alarmRepo.save(existing);
            } else if (newSnapshot.alarmId() == null) {
                alarmRepo.save(AlarmEntity.fromSnapshot(newSnapshot));
            }
            // else: suppressed alarm returned unchanged, already saved
        }

        // Auto-close: for each open alarm, check if measurements arrived for
        // same (parameter, context-key) but did NOT violate the rule
        Set<String> measurementContextKeys = measurementData.stream()
                .map(m -> m.parameterId() + "|" + m.contextKey())
                .collect(Collectors.toSet());

        // Build parameter→rules mapping for context key lookup
        Map<Long, List<RuleData>> rulesByParameter = rules.stream()
                .collect(Collectors.groupingBy(RuleData::parameterId));

        List<AlarmState> openStates = List.of(AlarmState.FIRING, AlarmState.ACKNOWLEDGED);
        List<AlarmEntity> openAlarms = alarmRepo.findByStateIn(openStates);

        for (AlarmEntity alarmEntity : openAlarms) {
            String paramContextKey = alarmEntity.getParameterId() + "|" + alarmEntity.getContextKey();
            String ruleContextKey = alarmEntity.getRuleId() + "|" + alarmEntity.getContextKey();

            // Only consider if we got measurements for this parameter+context
            if (!measurementContextKeys.contains(paramContextKey)) continue;
            // Skip if this rule+context had a violation (already handled above)
            if (violatedKeys.contains(ruleContextKey)) continue;

            // Clean wafer for this alarm
            AlarmSnapshot updated = alarmLifecycle.onCleanWafer(
                    alarmEntity.toSnapshot(), autoCloseThreshold);
            alarmEntity.updateFromSnapshot(updated);
            alarmRepo.save(alarmEntity);
        }

        Instant maxIngestedAt = measurements.stream()
                .map(MeasurementEntity::getIngestedAt)
                .max(Instant::compareTo)
                .orElse(watermark.getLastIngestedAt());

        watermark.advanceTo(maxIngestedAt);
        watermarkRepo.save(watermark);

        log.info("Evaluator tick: {} measurements, {} events, watermark={}",
                measurements.size(), events.size(), maxIngestedAt);
    }
}
