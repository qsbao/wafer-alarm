package com.waferalarm.config;

import com.waferalarm.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final ParameterRepository parameterRepo;
    private final RuleRepository ruleRepo;

    public DataInitializer(ParameterRepository parameterRepo, RuleRepository ruleRepo) {
        this.parameterRepo = parameterRepo;
        this.ruleRepo = ruleRepo;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (parameterRepo.count() > 0) {
            log.info("Data already initialized, skipping");
            return;
        }

        var param = parameterRepo.save(
                new ParameterEntity("CD", "nm", 100.0, null));
        log.info("Created test parameter: id={}, name=CD, upperLimit=100.0", param.getId());

        var rule = ruleRepo.save(
                new RuleEntity(param.getId(), RuleType.UPPER_THRESHOLD, Severity.WARNING));
        log.info("Created test rule: id={}, type=UPPER_THRESHOLD, severity=WARNING", rule.getId());
    }
}
