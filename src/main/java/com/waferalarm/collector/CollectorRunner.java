package com.waferalarm.collector;

import com.waferalarm.domain.MeasurementEntity;
import com.waferalarm.domain.MeasurementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;

@Component
public class CollectorRunner {

    private static final Logger log = LoggerFactory.getLogger(CollectorRunner.class);

    private final HardcodedSourceConnector connector;
    private final MeasurementRepository measurementRepo;
    private final ExecutorService collectorExecutor;

    public CollectorRunner(
            HardcodedSourceConnector connector,
            MeasurementRepository measurementRepo,
            @Qualifier("collectorExecutor") ExecutorService collectorExecutor) {
        this.connector = connector;
        this.measurementRepo = measurementRepo;
        this.collectorExecutor = collectorExecutor;
    }

    @Scheduled(fixedDelayString = "${app.collector.poll-interval-seconds:60}000")
    public void collect() {
        collectorExecutor.submit(() -> {
            try {
                List<MeasurementEntity> measurements = connector.pull();
                if (!measurements.isEmpty()) {
                    measurementRepo.saveAll(measurements);
                    log.info("Collector saved {} measurements", measurements.size());
                }
            } catch (Exception e) {
                log.error("Collector tick failed", e);
            }
        });
    }
}
