package com.waferalarm.health;

import com.waferalarm.domain.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class HealthService {

    private static final String EVAL_WATERMARK_KEY = "evaluator-main";

    private final SourceMappingRepository mappingRepo;
    private final ConnectorRunRepository connectorRunRepo;
    private final EvalRunRepository evalRunRepo;
    private final EvalWatermarkRepository evalWatermarkRepo;
    private final int stalledThresholdTicks;

    public HealthService(
            SourceMappingRepository mappingRepo,
            ConnectorRunRepository connectorRunRepo,
            EvalRunRepository evalRunRepo,
            EvalWatermarkRepository evalWatermarkRepo,
            @Value("${app.health.stalled-threshold-ticks:3}") int stalledThresholdTicks) {
        this.mappingRepo = mappingRepo;
        this.connectorRunRepo = connectorRunRepo;
        this.evalRunRepo = evalRunRepo;
        this.evalWatermarkRepo = evalWatermarkRepo;
        this.stalledThresholdTicks = stalledThresholdTicks;
    }

    public HealthReport buildReport() {
        List<ConnectorHealth> connectors = buildConnectorHealth();
        EvaluatorHealth evaluator = buildEvaluatorHealth();
        return new HealthReport(connectors, evaluator);
    }

    private List<ConnectorHealth> buildConnectorHealth() {
        List<SourceMappingEntity> mappings = mappingRepo.findByEnabledTrue();
        List<ConnectorHealth> result = new ArrayList<>();

        Instant lookback = Instant.now().minus(Duration.ofHours(24));

        for (SourceMappingEntity mapping : mappings) {
            List<ConnectorRunEntity> recentRuns =
                    connectorRunRepo.findByMappingIdSince(mapping.getId(), lookback);

            Instant lastSuccess = connectorRunRepo.findLastSuccessfulByMappingId(mapping.getId())
                    .map(ConnectorRunEntity::getFinishedAt)
                    .orElse(null);

            long totalRows = recentRuns.stream()
                    .mapToLong(ConnectorRunEntity::getRowsPulled)
                    .sum();

            long errors = recentRuns.stream()
                    .filter(r -> r.getError() != null)
                    .count();

            int consecutiveZero = countConsecutiveZeroRowTicks(recentRuns);
            boolean stalled = consecutiveZero >= stalledThresholdTicks;

            result.add(new ConnectorHealth(
                    mapping.getId(), lastSuccess, totalRows, errors, stalled, consecutiveZero));
        }

        return result;
    }

    private int countConsecutiveZeroRowTicks(List<ConnectorRunEntity> runsNewestFirst) {
        int count = 0;
        for (ConnectorRunEntity run : runsNewestFirst) {
            if (run.getError() != null) continue;
            if (run.getRowsPulled() == 0) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    private EvaluatorHealth buildEvaluatorHealth() {
        Instant lookback = Instant.now().minus(Duration.ofHours(24));
        List<EvalRunEntity> recentRuns = evalRunRepo.findSince(lookback);

        Instant lastTick = evalRunRepo.findLastSuccessful()
                .map(EvalRunEntity::getFinishedAt)
                .orElse(null);

        long errors = recentRuns.stream()
                .filter(r -> r.getError() != null)
                .count();

        long lagSeconds = evalWatermarkRepo.findByWatermarkKey(EVAL_WATERMARK_KEY)
                .map(wm -> Duration.between(wm.getLastIngestedAt(), Instant.now()).getSeconds())
                .orElse(0L);

        return new EvaluatorHealth(lastTick, lagSeconds, errors);
    }
}
