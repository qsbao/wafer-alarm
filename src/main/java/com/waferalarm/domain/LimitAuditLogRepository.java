package com.waferalarm.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LimitAuditLogRepository extends JpaRepository<LimitAuditLogEntity, Long> {
    List<LimitAuditLogEntity> findByLimitId(Long limitId);
}
