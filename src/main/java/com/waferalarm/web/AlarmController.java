package com.waferalarm.web;

import com.waferalarm.domain.AlarmRepository;
import com.waferalarm.domain.AlarmState;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/alarms")
public class AlarmController {

    private final AlarmRepository alarmRepo;

    public AlarmController(AlarmRepository alarmRepo) {
        this.alarmRepo = alarmRepo;
    }

    @GetMapping
    public List<AlarmDto> listActiveAlarms() {
        List<AlarmState> activeStates = List.of(AlarmState.FIRING, AlarmState.ACKNOWLEDGED);
        return alarmRepo.findByStateInOrderBySeverityAscLastViolationAtDesc(activeStates)
                .stream()
                .map(AlarmDto::from)
                .toList();
    }
}
