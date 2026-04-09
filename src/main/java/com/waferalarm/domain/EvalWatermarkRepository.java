package com.waferalarm.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EvalWatermarkRepository extends JpaRepository<EvalWatermark, Long> {
    Optional<EvalWatermark> findByWatermarkKey(String watermarkKey);
}
