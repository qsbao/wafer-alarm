package com.waferalarm.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CollectorRegistrationRepository extends JpaRepository<CollectorRegistrationEntity, Long> {
    Optional<CollectorRegistrationEntity> findByCollectorId(String collectorId);
    List<CollectorRegistrationEntity> findAll();
}
