package com.waferalarm.web;

import com.waferalarm.alarm.AlarmLifecycle;
import com.waferalarm.audit.AuditLogger;
import com.waferalarm.domain.AlarmEntity;
import com.waferalarm.domain.AlarmRepository;
import com.waferalarm.domain.AlarmSnapshot;
import com.waferalarm.domain.AlarmState;
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

    public AlarmController(AlarmRepository alarmRepo, AlarmLifecycle alarmLifecycle, AuditLogger auditLogger) {
        this.alarmRepo = alarmRepo;
        this.alarmLifecycle = alarmLifecycle;
        this.auditLogger = auditLogger;
    }

    @GetMapping
    public List<AlarmDto> listActiveAlarms() {
        List<AlarmState> activeStates = List.of(AlarmState.FIRING, AlarmState.ACKNOWLEDGED);
        return alarmRepo.findByStateInOrderBySeverityAscLastViolationAtDesc(activeStates)
                .stream()
                .map(AlarmDto::from)
                .toList();
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
