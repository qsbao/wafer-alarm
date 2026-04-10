package com.waferalarm.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EvalRunRepository extends JpaRepository<EvalRunEntity, Long> {
    @Query("SELECT e FROM EvalRunEntity e ORDER BY e.startedAt DESC")
    List<EvalRunEntity> findRecentRuns();

    @Query("SELECT e FROM EvalRunEntity e WHERE e.error IS NULL ORDER BY e.startedAt DESC LIMIT 1")
    Optional<EvalRunEntity> findLastSuccessful();

    @Query("SELECT e FROM EvalRunEntity e WHERE e.startedAt > :since ORDER BY e.startedAt DESC")
    List<EvalRunEntity> findSince(Instant since);
}
