package com.waferalarm.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ConnectorRunRepository extends JpaRepository<ConnectorRunEntity, Long> {
    List<ConnectorRunEntity> findBySourceMappingIdOrderByStartedAtDesc(Long sourceMappingId);

    @Query("SELECT c FROM ConnectorRunEntity c WHERE c.sourceMappingId = :mappingId AND c.error IS NULL ORDER BY c.startedAt DESC LIMIT 1")
    Optional<ConnectorRunEntity> findLastSuccessfulByMappingId(Long mappingId);

    @Query("SELECT c FROM ConnectorRunEntity c WHERE c.sourceMappingId = :mappingId AND c.startedAt > :since ORDER BY c.startedAt DESC")
    List<ConnectorRunEntity> findByMappingIdSince(Long mappingId, Instant since);
}
