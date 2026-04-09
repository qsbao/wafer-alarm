package com.waferalarm.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SourceMappingRepository extends JpaRepository<SourceMappingEntity, Long> {
    List<SourceMappingEntity> findByEnabledTrue();
    List<SourceMappingEntity> findBySourceSystemId(Long sourceSystemId);
}
