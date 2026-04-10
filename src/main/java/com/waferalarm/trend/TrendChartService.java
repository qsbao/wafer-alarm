package com.waferalarm.trend;

import com.waferalarm.domain.MeasurementEntity;
import com.waferalarm.domain.MeasurementRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class TrendChartService {

    private final MeasurementRepository measurementRepo;
    private final TrendChartDownsampler downsampler;
    private final int downsampleThreshold;

    public TrendChartService(MeasurementRepository measurementRepo,
                             TrendChartDownsampler downsampler,
                             @Value("${app.trend.downsample-threshold:500}") int downsampleThreshold) {
        this.measurementRepo = measurementRepo;
        this.downsampler = downsampler;
        this.downsampleThreshold = downsampleThreshold;
    }

    public TrendChartResponse query(Long parameterId, Instant from, Instant to) {
        return query(parameterId, from, to, null, null, null, null);
    }

    public TrendChartResponse query(Long parameterId, Instant from, Instant to,
                                     String tool, String recipe, String product, String lot) {
        var measurements = measurementRepo.findFiltered(parameterId, from, to, tool, recipe, product, lot);

        List<TrendPoint> points = measurements.stream()
                .map(this::toTrendPoint)
                .toList();

        if (points.size() > downsampleThreshold) {
            int bucketCount = downsampleThreshold / 2;
            List<TrendPoint> downsampled = downsampler.downsample(points, bucketCount);
            return new TrendChartResponse(downsampled, true);
        }

        return new TrendChartResponse(points, false);
    }

    private TrendPoint toTrendPoint(MeasurementEntity m) {
        return new TrendPoint(m.getTs(), m.getValue(), m.getTool(), m.getRecipe(),
                m.getProduct(), m.getLotId(), m.getWaferId());
    }
}
