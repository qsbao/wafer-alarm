package com.waferalarm.web;

import com.waferalarm.alarm.AlarmLifecycle;
import com.waferalarm.audit.AuditLogger;
import com.waferalarm.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alarms")
public class AlarmController {

    private final AlarmRepository alarmRepo;
    private final AlarmLifecycle alarmLifecycle;
    private final AuditLogger auditLogger;
    private final ParameterRepository parameterRepo;

    public AlarmController(AlarmRepository alarmRepo, AlarmLifecycle alarmLifecycle,
                           AuditLogger auditLogger, ParameterRepository parameterRepo) {
        this.alarmRepo = alarmRepo;
        this.alarmLifecycle = alarmLifecycle;
        this.auditLogger = auditLogger;
        this.parameterRepo = parameterRepo;
    }

    @GetMapping
    public List<AlarmDto> listAlarms(
            @RequestParam(required = false) Long parameterId,
            @RequestParam(required = false) String tool,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String state) {

        List<AlarmState> states;
        if (state != null) {
            states = List.of(AlarmState.valueOf(state));
        } else {
            states = List.of(AlarmState.FIRING, AlarmState.ACKNOWLEDGED);
        }

        Severity severityEnum = severity != null ? Severity.valueOf(severity) : null;

        return toDtos(alarmRepo.findFiltered(states, parameterId, tool, severityEnum));
    }

    @GetMapping("/resolved")
    public List<AlarmDto> listResolvedAlarms() {
        Instant since = Instant.now().minus(java.time.Duration.ofHours(24));
        return toDtos(alarmRepo.findResolvedSince(since));
    }

    private List<AlarmDto> toDtos(List<AlarmEntity> entities) {
        Map<Long, String> nameCache = new java.util.HashMap<>();
        return entities.stream().map(e -> {
            String name = nameCache.computeIfAbsent(e.getParameterId(),
                    id -> parameterRepo.findById(id).map(ParameterEntity::getName).orElse(null));
            return AlarmDto.from(e, name);
        }).toList();
    }

    @PostMapping("/{id}/acknowledge")
    public AlarmDto acknowledge(@PathVariable Long id) {
        AlarmEntity entity = findAlarmOrThrow(id);
        var before = alarmSnapshot(entity);
        AlarmSnapshot updated = alarmLifecycle.acknowledge(entity.toSnapshot());
        entity.updateFromSnapshot(updated);
        alarmRepo.save(entity);
        auditLogger.log("ALARM", entity.getId(), "ACKNOWLEDGE", before, alarmSnapshot(entity));
        return AlarmDto.from(entity);
    }

    @PostMapping("/{id}/resolve")
    public AlarmDto resolve(@PathVariable Long id) {
        AlarmEntity entity = findAlarmOrThrow(id);
        var before = alarmSnapshot(entity);
        AlarmSnapshot updated = alarmLifecycle.resolve(entity.toSnapshot());
        entity.updateFromSnapshot(updated);
        alarmRepo.save(entity);
        auditLogger.log("ALARM", entity.getId(), "RESOLVE", before, alarmSnapshot(entity));
        return AlarmDto.from(entity);
    }

    @PostMapping("/{id}/suppress")
    public AlarmDto suppress(@PathVariable Long id, @RequestBody SuppressRequest request) {
        AlarmEntity entity = findAlarmOrThrow(id);
        var before = alarmSnapshot(entity);
        AlarmSnapshot updated = alarmLifecycle.suppress(entity.toSnapshot(), request.until());
        entity.updateFromSnapshot(updated);
        alarmRepo.save(entity);
        auditLogger.log("ALARM", entity.getId(), "SUPPRESS", before, alarmSnapshot(entity));
        return AlarmDto.from(entity);
    }

    private Map<String, Object> alarmSnapshot(AlarmEntity e) {
        return Map.of(
                "state", e.getState().name(),
                "severity", e.getSeverity().name(),
                "occurrenceCount", e.getOccurrenceCount(),
                "ruleId", e.getRuleId(),
                "contextKey", e.getContextKey() != null ? e.getContextKey() : "");
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleIllegalState(IllegalStateException ex) {
        return Map.of("error", ex.getMessage());
    }

    private AlarmEntity findAlarmOrThrow(Long id) {
        return alarmRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alarm not found"));
    }

    public record SuppressRequest(Instant until) {}
}
