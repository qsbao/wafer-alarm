package com.waferalarm.web;

import com.waferalarm.alarm.AlarmLifecycle;
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

    public AlarmController(AlarmRepository alarmRepo, AlarmLifecycle alarmLifecycle) {
        this.alarmRepo = alarmRepo;
        this.alarmLifecycle = alarmLifecycle;
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
        AlarmSnapshot updated = alarmLifecycle.acknowledge(entity.toSnapshot());
        entity.updateFromSnapshot(updated);
        alarmRepo.save(entity);
        return AlarmDto.from(entity);
    }

    @PostMapping("/{id}/resolve")
    public AlarmDto resolve(@PathVariable Long id) {
        AlarmEntity entity = findAlarmOrThrow(id);
        AlarmSnapshot updated = alarmLifecycle.resolve(entity.toSnapshot());
        entity.updateFromSnapshot(updated);
        alarmRepo.save(entity);
        return AlarmDto.from(entity);
    }

    @PostMapping("/{id}/suppress")
    public AlarmDto suppress(@PathVariable Long id, @RequestBody SuppressRequest request) {
        AlarmEntity entity = findAlarmOrThrow(id);
        AlarmSnapshot updated = alarmLifecycle.suppress(entity.toSnapshot(), request.until());
        entity.updateFromSnapshot(updated);
        alarmRepo.save(entity);
        return AlarmDto.from(entity);
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
