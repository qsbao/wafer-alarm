package com.waferalarm.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StagingUnmappedRepository extends JpaRepository<StagingUnmappedEntity, Long> {
    Optional<StagingUnmappedEntity> findBySourceSystemIdAndColumnKey(Long sourceSystemId, String columnKey);
    List<StagingUnmappedEntity> findBySourceSystemId(Long sourceSystemId);
}
