package com.waferalarm.audit;

import com.waferalarm.domain.AuditLogEntity;
import com.waferalarm.domain.AuditLogRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/audit-log")
public class AuditLogController {

    private final AuditLogRepository repo;

    public AuditLogController(AuditLogRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<AuditLogDto> list(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {

        return repo.findFiltered(entityType, entityId, action, from, to)
                .stream()
                .map(AuditLogDto::from)
                .toList();
    }

    public record AuditLogDto(
            Long id, String entityType, Long entityId, String action,
            String actor, String sourceIp,
            String beforeJson, String afterJson,
            String createdAt) {
        static AuditLogDto from(AuditLogEntity e) {
            return new AuditLogDto(
                    e.getId(), e.getEntityType(), e.getEntityId(), e.getAction(),
                    e.getActor(), e.getSourceIp(),
                    e.getBeforeJson(), e.getAfterJson(),
                    e.getCreatedAt().toString());
        }
    }
}
