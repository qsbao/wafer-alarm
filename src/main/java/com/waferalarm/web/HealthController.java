package com.waferalarm.web;

import com.waferalarm.collector.CollectorRegistrationService;
import com.waferalarm.domain.CollectorRegistrationEntity;
import com.waferalarm.domain.CollectorRegistrationRepository;
import com.waferalarm.health.HealthReport;
import com.waferalarm.health.HealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final CollectorRegistrationRepository registrationRepo;
    private final CollectorRegistrationService registrationService;
    private final HealthService healthService;

    public HealthController(CollectorRegistrationRepository registrationRepo,
                           CollectorRegistrationService registrationService,
                           HealthService healthService) {
        this.registrationRepo = registrationRepo;
        this.registrationService = registrationService;
        this.healthService = healthService;
    }

    @GetMapping("/collectors")
    public CollectorHealthResponse getCollectorHealth() {
        List<CollectorRegistrationEntity> all = registrationRepo.findAll();
        List<RegistrationDto> registrations = all.stream()
                .map(r -> new RegistrationDto(
                        r.getCollectorId(),
                        r.getOwnedSourceSystemIds(),
                        r.getRegisteredAt(),
                        r.getLastHeartbeatAt()))
                .toList();

        List<CollectorRegistrationService.OverlapInfo> overlaps = registrationService.detectOverlaps();

        return new CollectorHealthResponse(registrations, overlaps);
    }

    @GetMapping("/report")
    public HealthReport getHealthReport() {
        return healthService.buildReport();
    }

    public record CollectorHealthResponse(
            List<RegistrationDto> registrations,
            List<CollectorRegistrationService.OverlapInfo> overlaps) {}

    public record RegistrationDto(
            String collectorId,
            String ownedSourceSystemIds,
            Instant registeredAt,
            Instant lastHeartbeatAt) {}
}
