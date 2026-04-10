package com.waferalarm.collector;

import com.waferalarm.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ExecutorService;

@Component
public class BackfillRunner {

    private static final Logger log = LoggerFactory.getLogger(BackfillRunner.class);

    private final SourceConnector connector;
    private final SourceMappingRepository mappingRepo;
    private final MeasurementRepository measurementRepo;
    private final BackfillTaskRepository taskRepo;
    private final ExecutorService backfillExecutor;

    public BackfillRunner(
            SourceConnector connector,
            SourceMappingRepository mappingRepo,
            MeasurementRepository measurementRepo,
            BackfillTaskRepository taskRepo,
            @Qualifier("backfillExecutor") ExecutorService backfillExecutor) {
        this.connector = connector;
        this.mappingRepo = mappingRepo;
        this.measurementRepo = measurementRepo;
        this.taskRepo = taskRepo;
        this.backfillExecutor = backfillExecutor;
    }

    public BackfillTaskEntity triggerBackfill(Long mappingId) {
        var mapping = mappingRepo.findById(mappingId)
                .orElseThrow(() -> new IllegalArgumentException("Mapping not found: " + mappingId));

        Instant now = Instant.now();
        Instant backfillFrom = now.minus(mapping.getBackfillWindowDays(), ChronoUnit.DAYS);

        var task = taskRepo.save(new BackfillTaskEntity(mappingId, backfillFrom, now));

        backfillExecutor.submit(() -> executeBackfill(task.getId()));

        return task;
    }

    private void executeBackfill(Long taskId) {
        var task = taskRepo.findById(taskId).orElseThrow();
        var mapping = mappingRepo.findById(task.getSourceMappingId()).orElseThrow();

        task.markRunning();
        taskRepo.save(task);

        try {
            Instant watermarkLow = task.getLastProcessedTs() != null
                    ? task.getLastProcessedTs()
                    : task.getBackfillFrom();
            Instant watermarkHigh = task.getBackfillTo();

            List<MeasurementEntity> measurements = connector.pull(
                    mapping, mapping.getParameterId(), watermarkLow, watermarkHigh);

            // Deduplicate against existing measurements
            List<MeasurementEntity> newMeasurements = measurements.stream()
                    .filter(m -> !measurementRepo.existsByWaferIdAndParameterId(
                            m.getWaferId(), m.getParameterId()))
                    .toList();

            if (!newMeasurements.isEmpty()) {
                measurementRepo.saveAll(newMeasurements);
            }

            Instant maxTs = measurements.stream()
                    .map(MeasurementEntity::getTs)
                    .max(Instant::compareTo)
                    .orElse(watermarkHigh);

            task.recordProgress(newMeasurements.size(), maxTs);
            task.markCompleted();
            taskRepo.save(task);

            log.info("Backfill for mapping {} completed: {} rows", mapping.getId(), newMeasurements.size());

        } catch (Exception e) {
            log.error("Backfill for mapping {} failed", mapping.getId(), e);
            task.markFailed(e.getMessage());
            taskRepo.save(task);
        }
    }
}
