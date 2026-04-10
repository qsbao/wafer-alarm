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

    @Query("SELECT a FROM AlarmEntity a WHERE a.state IN :states" +
            " AND (:parameterId IS NULL OR a.parameterId = :parameterId)" +
            " AND (:tool IS NULL OR a.contextKey LIKE CONCAT('%tool=', :tool, '%'))" +
            " AND (:severity IS NULL OR a.severity = :severity)" +
            " ORDER BY CASE a.severity WHEN 'CRITICAL' THEN 0 WHEN 'WARNING' THEN 1 WHEN 'INFO' THEN 2 ELSE 3 END ASC," +
            " a.lastViolationAt DESC")
    List<AlarmEntity> findFiltered(List<AlarmState> states, Long parameterId,
                                   String tool, Severity severity);

    @Query("SELECT a FROM AlarmEntity a WHERE a.state = 'RESOLVED'" +
            " AND a.updatedAt >= :since" +
            " ORDER BY CASE a.severity WHEN 'CRITICAL' THEN 0 WHEN 'WARNING' THEN 1 WHEN 'INFO' THEN 2 ELSE 3 END ASC," +
            " a.updatedAt DESC")
    List<AlarmEntity> findResolvedSince(Instant since);

    @Query("SELECT a FROM AlarmEntity a WHERE a.parameterId = :parameterId" +
            " AND a.firstViolationAt < :to AND a.lastViolationAt >= :from" +
            " ORDER BY a.firstViolationAt ASC")
    List<AlarmEntity> findOverlapping(Long parameterId, Instant from, Instant to);
}
