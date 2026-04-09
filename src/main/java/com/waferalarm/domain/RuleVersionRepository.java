package com.waferalarm.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RuleVersionRepository extends JpaRepository<RuleVersionEntity, Long> {
    List<RuleVersionEntity> findByRuleIdOrderByCreatedAtDesc(Long ruleId);
}
