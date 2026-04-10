package com.waferalarm.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AlarmRepository extends JpaRepository<AlarmEntity, Long> {
    Optional<AlarmEntity> findByRuleIdAndContextKeyAndStateIn(Long ruleId, String contextKey, List<AlarmState> states);
    List<AlarmEntity> findByStateInOrderBySeverityAscLastViolationAtDesc(List<AlarmState> states);
    List<AlarmEntity> findByStateIn(List<AlarmState> states);

    @Query("SELECT a FROM AlarmEntity a WHERE a.parameterId = :parameterId" +
            " AND a.firstViolationAt < :to AND a.lastViolationAt >= :from" +
            " ORDER BY a.firstViolationAt ASC")
    List<AlarmEntity> findOverlapping(Long parameterId, Instant from, Instant to);
}
