package com.waferalarm.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface MeasurementRepository extends JpaRepository<MeasurementEntity, Long> {
    @Query("SELECT m FROM MeasurementEntity m WHERE m.ingestedAt > :since ORDER BY m.ingestedAt ASC")
    List<MeasurementEntity> findIngestedAfter(Instant since);
}
