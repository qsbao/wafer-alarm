package com.waferalarm.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface MeasurementRepository extends JpaRepository<MeasurementEntity, Long> {
    @Query("SELECT m FROM MeasurementEntity m WHERE m.ingestedAt > :since ORDER BY m.ingestedAt ASC")
    List<MeasurementEntity> findIngestedAfter(Instant since);

    @Query("SELECT m FROM MeasurementEntity m WHERE m.parameterId = :parameterId AND m.ts >= :from AND m.ts < :to ORDER BY m.ts ASC")
    List<MeasurementEntity> findByParameterIdAndTsBetween(Long parameterId, Instant from, Instant to);

    @Query("SELECT m FROM MeasurementEntity m WHERE m.parameterId = :parameterId AND m.ts >= :from AND m.ts < :to" +
            " AND (:tool IS NULL OR m.tool = :tool)" +
            " AND (:recipe IS NULL OR m.recipe = :recipe)" +
            " AND (:product IS NULL OR m.product = :product)" +
            " AND (:lotId IS NULL OR m.lotId = :lotId)" +
            " ORDER BY m.ts ASC")
    List<MeasurementEntity> findFiltered(Long parameterId, Instant from, Instant to,
                                         String tool, String recipe, String product, String lotId);

    @Query("SELECT DISTINCT m.tool FROM MeasurementEntity m WHERE m.tool IS NOT NULL ORDER BY m.tool")
    List<String> findDistinctTools();

    @Query("SELECT DISTINCT m.recipe FROM MeasurementEntity m WHERE m.recipe IS NOT NULL ORDER BY m.recipe")
    List<String> findDistinctRecipes();

    @Query("SELECT DISTINCT m.product FROM MeasurementEntity m WHERE m.product IS NOT NULL ORDER BY m.product")
    List<String> findDistinctProducts();

    @Query("SELECT DISTINCT m.lotId FROM MeasurementEntity m WHERE m.lotId IS NOT NULL ORDER BY m.lotId")
    List<String> findDistinctLots();

    boolean existsByWaferIdAndParameterId(String waferId, Long parameterId);
}
