package com.waferalarm.web;

import com.waferalarm.audit.AuditLogger;
import com.waferalarm.domain.LimitAuditLogEntity;
import com.waferalarm.domain.LimitAuditLogRepository;
import com.waferalarm.domain.ParameterLimitEntity;
import com.waferalarm.domain.ParameterLimitRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/parameter-limits")
public class ParameterLimitController {

    private final ParameterLimitRepository repo;
    private final LimitAuditLogRepository auditRepo;
    private final AuditLogger auditLogger;

    public ParameterLimitController(ParameterLimitRepository repo, LimitAuditLogRepository auditRepo, AuditLogger auditLogger) {
        this.repo = repo;
        this.auditRepo = auditRepo;
        this.auditLogger = auditLogger;
    }

    @GetMapping
    public List<LimitResponse> list() {
        return withAmbiguityFlags(repo.findAll());
    }

    @GetMapping("/by-parameter/{parameterId}")
    public List<LimitResponse> listByParameter(@PathVariable Long parameterId) {
        return withAmbiguityFlags(repo.findByParameterId(parameterId));
    }

    @PostMapping
    public ResponseEntity<LimitResponse> create(@RequestBody LimitRequest req) {
        var entity = new ParameterLimitEntity(
                req.parameterId(),
                req.contextMatchJson() != null ? req.contextMatchJson() : "{}",
                req.upperLimit(),
                req.lowerLimit());
        var saved = repo.save(entity);
        auditRepo.save(new LimitAuditLogEntity(
                saved.getId(), saved.getParameterId(), "CREATE",
                saved.getContextMatchJson(), saved.getUpperLimit(), saved.getLowerLimit()));
        auditLogger.log("PARAMETER_LIMIT", saved.getId(), "CREATE", null, limitSnapshot(saved));
        return ResponseEntity.status(HttpStatus.CREATED).body(LimitResponse.from(saved, false));
    }

    @PutMapping("/{id}")
    public ResponseEntity<LimitResponse> update(@PathVariable Long id, @RequestBody LimitRequest req) {
        return repo.findById(id).map(entity -> {
            var before = limitSnapshot(entity);
            entity.setUpperLimit(req.upperLimit());
            entity.setLowerLimit(req.lowerLimit());
            if (req.contextMatchJson() != null) {
                entity.setContextMatchJson(req.contextMatchJson());
            }
            var saved = repo.save(entity);
            auditRepo.save(new LimitAuditLogEntity(
                    saved.getId(), saved.getParameterId(), "UPDATE",
                    saved.getContextMatchJson(), saved.getUpperLimit(), saved.getLowerLimit()));
            auditLogger.log("PARAMETER_LIMIT", saved.getId(), "UPDATE", before, limitSnapshot(saved));
            return ResponseEntity.ok(LimitResponse.from(saved, false));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        var entity = repo.findById(id).orElse(null);
        if (entity == null) return ResponseEntity.notFound().build();
        auditRepo.save(new LimitAuditLogEntity(
                entity.getId(), entity.getParameterId(), "DELETE",
                entity.getContextMatchJson(), entity.getUpperLimit(), entity.getLowerLimit()));
        auditLogger.log("PARAMETER_LIMIT", entity.getId(), "DELETE", limitSnapshot(entity), null);
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> limitSnapshot(ParameterLimitEntity e) {
        Map<String, Object> map = new HashMap<>();
        map.put("parameterId", e.getParameterId());
        map.put("contextMatchJson", e.getContextMatchJson());
        map.put("upperLimit", e.getUpperLimit());
        map.put("lowerLimit", e.getLowerLimit());
        return map;
    }

    private List<LimitResponse> withAmbiguityFlags(List<ParameterLimitEntity> limits) {
        // Group by (parameterId, contextMatchJson) — two rows with same key are ambiguous
        Map<String, List<ParameterLimitEntity>> groups = limits.stream()
                .collect(Collectors.groupingBy(
                        l -> l.getParameterId() + "|" + normalizeJson(l.getContextMatchJson())));

        Set<Long> ambiguousIds = new HashSet<>();
        for (List<ParameterLimitEntity> group : groups.values()) {
            if (group.size() > 1) {
                group.forEach(l -> ambiguousIds.add(l.getId()));
            }
        }

        return limits.stream()
                .map(l -> LimitResponse.from(l, ambiguousIds.contains(l.getId())))
                .toList();
    }

    private String normalizeJson(String json) {
        if (json == null || json.isBlank()) return "{}";
        return json.trim();
    }

    public record LimitRequest(Long parameterId, String contextMatchJson, Double upperLimit, Double lowerLimit) {}

    public record LimitResponse(
            Long id, Long parameterId, String contextMatchJson,
            Double upperLimit, Double lowerLimit,
            Instant createdAt, Instant updatedAt,
            boolean ambiguous) {
        static LimitResponse from(ParameterLimitEntity e, boolean ambiguous) {
            return new LimitResponse(
                    e.getId(), e.getParameterId(), e.getContextMatchJson(),
                    e.getUpperLimit(), e.getLowerLimit(),
                    e.getCreatedAt(), e.getUpdatedAt(), ambiguous);
        }
    }
}
