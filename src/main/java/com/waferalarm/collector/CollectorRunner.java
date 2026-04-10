package com.waferalarm.collector;

import com.waferalarm.catalog.UnmappedDataService;
import com.waferalarm.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

@Component
public class CollectorRunner {

    private static final Logger log = LoggerFactory.getLogger(CollectorRunner.class);
    // When no watermark exists, start from epoch to capture all historical data
    private static final Instant INITIAL_WATERMARK = Instant.EPOCH;
    private static final Duration OVERLAP_WINDOW = Duration.ofMinutes(5);

    private final SourceConnector connector;
    private final SourceMappingRepository mappingRepo;
    private final MeasurementRepository measurementRepo;
    private final CollectorWatermarkRepository watermarkRepo;
    private final ConnectorRunRepository runRepo;
    private final ExecutorService collectorExecutor;
    private final CollectorConfig collectorConfig;
    private final UnmappedDataService unmappedDataService;

    public CollectorRunner(
            SourceConnector connector,
            SourceMappingRepository mappingRepo,
            MeasurementRepository measurementRepo,
            CollectorWatermarkRepository watermarkRepo,
            ConnectorRunRepository runRepo,
            @Qualifier("collectorExecutor") ExecutorService collectorExecutor,
            CollectorConfig collectorConfig,
            UnmappedDataService unmappedDataService) {
        this.connector = connector;
        this.mappingRepo = mappingRepo;
        this.measurementRepo = measurementRepo;
        this.watermarkRepo = watermarkRepo;
        this.runRepo = runRepo;
        this.collectorExecutor = collectorExecutor;
        this.collectorConfig = collectorConfig;
        this.unmappedDataService = unmappedDataService;
        if (collectorConfig.ownsAll()) {
            log.info("Collector configured to own ALL source systems");
        } else {
            log.info("Collector configured to own source systems: {}", collectorConfig.getOwnedSourceSystemIds());
        }
    }

    @Scheduled(fixedDelayString = "${app.collector.poll-interval-seconds:60}000")
    public void scheduledCollect() {
        collectorExecutor.submit(this::collectAll);
    }

    public void collectAll() {
        List<SourceMappingEntity> mappings = mappingRepo.findByEnabledTrue().stream()
                .filter(m -> collectorConfig.owns(m.getSourceSystemId()))
                .toList();
        for (SourceMappingEntity mapping : mappings) {
            collectMapping(mapping);
        }
    }

    private void collectMapping(SourceMappingEntity mapping) {
        Instant startedAt = Instant.now();
        String sourceKey = "mapping-" + mapping.getId();

        try {
            var watermarkOpt = watermarkRepo.findBySourceKey(sourceKey);
            Instant lastWatermark = watermarkOpt
                    .map(CollectorWatermarkEntity::getLastTs)
                    .orElse(INITIAL_WATERMARK);

            // Apply overlap window so late-arriving data is re-read
            Instant watermarkLow = lastWatermark.minus(OVERLAP_WINDOW);
            Instant watermarkHigh = Instant.now();

            PullResult pullResult = connector.pullWithUnmapped(
                    mapping, mapping.getParameterId(), watermarkLow, watermarkHigh);
            List<MeasurementEntity> measurements = pullResult.measurements();

            // Record any unmapped columns detected in the result set
            for (Map.Entry<String, String> entry : pullResult.unmappedColumns().entrySet()) {
                unmappedDataService.recordUnmapped(
                        mapping.getSourceSystemId(), entry.getKey(), entry.getValue());
            }

            // Filter out duplicates from overlap window re-reads
            List<MeasurementEntity> newMeasurements = measurements.stream()
                    .filter(m -> !measurementRepo.existsByWaferIdAndParameterId(
                            m.getWaferId(), m.getParameterId()))
                    .toList();

            if (!newMeasurements.isEmpty()) {
                measurementRepo.saveAll(newMeasurements);
                log.info("Mapping {} pulled {} rows ({} new)", mapping.getId(),
                        measurements.size(), newMeasurements.size());
            }

            // Advance watermark to the max ts from pulled data, or watermarkHigh if empty
            Instant newWatermark = measurements.stream()
                    .map(MeasurementEntity::getTs)
                    .max(Instant::compareTo)
                    .orElse(watermarkHigh);

            if (watermarkOpt.isPresent()) {
                watermarkOpt.get().advanceTo(newWatermark);
                watermarkRepo.save(watermarkOpt.get());
            } else {
                watermarkRepo.save(new CollectorWatermarkEntity(sourceKey, newWatermark));
            }

            Instant finishedAt = Instant.now();
            runRepo.save(new ConnectorRunEntity(
                    mapping.getId(), startedAt, finishedAt,
                    newMeasurements.size(),
                    Duration.between(startedAt, finishedAt).toMillis(),
                    null));

        } catch (Exception e) {
            log.error("Mapping {} collection failed", mapping.getId(), e);
            Instant finishedAt = Instant.now();
            runRepo.save(new ConnectorRunEntity(
                    mapping.getId(), startedAt, finishedAt,
                    0,
                    Duration.between(startedAt, finishedAt).toMillis(),
                    e.getMessage()));
        }
    }
}
