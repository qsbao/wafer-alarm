package com.waferalarm.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConnectorRunRepository extends JpaRepository<ConnectorRunEntity, Long> {
    List<ConnectorRunEntity> findBySourceMappingIdOrderByStartedAtDesc(Long sourceMappingId);
}
