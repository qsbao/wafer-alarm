package com.waferalarm.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CollectorWatermarkRepository extends JpaRepository<CollectorWatermarkEntity, Long> {
    Optional<CollectorWatermarkEntity> findBySourceKey(String sourceKey);
}
