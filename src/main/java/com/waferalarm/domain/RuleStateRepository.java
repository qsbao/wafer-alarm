package com.waferalarm.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RuleStateRepository extends JpaRepository<RuleStateEntity, Long> {
    Optional<RuleStateEntity> findByRuleIdAndContextKeyHash(Long ruleId, String contextKeyHash);
}
