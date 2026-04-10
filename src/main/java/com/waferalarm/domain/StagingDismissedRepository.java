package com.waferalarm.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StagingDismissedRepository extends JpaRepository<StagingDismissedEntity, Long> {
    boolean existsBySourceSystemIdAndColumnKey(Long sourceSystemId, String columnKey);
}
