package com.waferalarm.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ParameterRepository extends JpaRepository<ParameterEntity, Long> {
    Optional<ParameterEntity> findByName(String name);
}
