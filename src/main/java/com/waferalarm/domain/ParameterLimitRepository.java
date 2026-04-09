package com.waferalarm.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ParameterLimitRepository extends JpaRepository<ParameterLimitEntity, Long> {
    List<ParameterLimitEntity> findByParameterId(Long parameterId);
}
