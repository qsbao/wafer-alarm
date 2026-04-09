package com.waferalarm.collector;

import com.waferalarm.domain.MeasurementEntity;
import com.waferalarm.domain.ParameterEntity;
import com.waferalarm.domain.ParameterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Random;

@Component
public class HardcodedSourceConnector {

    private static final Logger log = LoggerFactory.getLogger(HardcodedSourceConnector.class);
    private static final String TEST_PARAM_NAME = "CD";
    private static final double NOMINAL = 95.0;
    private static final double NOISE = 10.0;

    private final ParameterRepository parameterRepo;
    private final Random random = new Random();
    private int waferCounter = 0;

    public HardcodedSourceConnector(ParameterRepository parameterRepo) {
        this.parameterRepo = parameterRepo;
    }

    public List<MeasurementEntity> pull() {
        ParameterEntity param = parameterRepo.findAll().stream()
                .filter(p -> TEST_PARAM_NAME.equals(p.getName()))
                .findFirst()
                .orElse(null);

        if (param == null) {
            log.warn("Test parameter '{}' not found, skipping pull", TEST_PARAM_NAME);
            return List.of();
        }

        waferCounter++;
        double value = NOMINAL + (random.nextGaussian() * NOISE);
        String waferId = "W-" + String.format("%06d", waferCounter);

        var measurement = new MeasurementEntity(
                param.getId(), waferId, value, Instant.now(),
                "TOOL-A", "RCP-1", "PROD-X", "LOT-" + (waferCounter / 25 + 1));

        log.info("Pulled measurement: wafer={}, value={}, param={}",
                waferId, value, TEST_PARAM_NAME);

        return List.of(measurement);
    }
}
