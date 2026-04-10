package com.waferalarm.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BackfillTaskRepository extends JpaRepository<BackfillTaskEntity, Long> {
    Optional<BackfillTaskEntity> findBySourceMappingId(Long sourceMappingId);
    List<BackfillTaskEntity> findByStatusIn(List<BackfillTaskEntity.Status> statuses);
}
