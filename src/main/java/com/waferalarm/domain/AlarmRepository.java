package com.waferalarm.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlarmRepository extends JpaRepository<AlarmEntity, Long> {
    Optional<AlarmEntity> findByRuleIdAndContextKeyAndStateIn(Long ruleId, String contextKey, List<AlarmState> states);
    List<AlarmEntity> findByStateInOrderBySeverityAscLastViolationAtDesc(List<AlarmState> states);
    List<AlarmEntity> findByStateIn(List<AlarmState> states);
}
