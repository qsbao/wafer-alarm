package com.waferalarm.trend;

import com.waferalarm.domain.AlarmEntity;
import com.waferalarm.domain.AlarmRepository;
import com.waferalarm.domain.LimitData;
import com.waferalarm.domain.MeasurementEntity;
import com.waferalarm.domain.MeasurementRepository;
import com.waferalarm.domain.ParameterLimitRepository;
import com.waferalarm.evaluator.LimitResolver;
import com.waferalarm.evaluator.ParameterLimitData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TrendChartService {

    private final MeasurementRepository measurementRepo;
    private final ParameterLimitRepository limitRepo;
    private final AlarmRepository alarmRepo;
    private final LimitResolver limitResolver;
    private final TrendChartDownsampler downsampler;
    private final int downsampleThreshold;

    public TrendChartService(MeasurementRepository measurementRepo,
                             ParameterLimitRepository limitRepo,
                             AlarmRepository alarmRepo,
                             LimitResolver limitResolver,
                             TrendChartDownsampler downsampler,
                             @Value("${app.trend.downsample-threshold:500}") int downsampleThreshold) {
        this.measurementRepo = measurementRepo;
        this.limitRepo = limitRepo;
        this.alarmRepo = alarmRepo;
        this.limitResolver = limitResolver;
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
            points = downsampler.downsample(points, bucketCount);
            return buildResponse(points, true, parameterId, from, to, tool, recipe, product, lot);
        }

        return buildResponse(points, false, parameterId, from, to, tool, recipe, product, lot);
    }

    private TrendChartResponse buildResponse(List<TrendPoint> points, boolean downsampled,
                                              Long parameterId, Instant from, Instant to,
                                              String tool, String recipe,
                                              String product, String lot) {
        // Resolve limits for the active filter context
        Map<String, String> filterContext = buildFilterContext(tool, recipe, product, lot);
        List<ParameterLimitData> allLimits = limitRepo.findByParameterId(parameterId).stream()
                .map(e -> e.toData())
                .toList();

        var resolved = limitResolver.resolve(parameterId, filterContext, allLimits);
        Double upper = resolved.map(LimitData::upperLimit).orElse(null);
        Double lower = resolved.map(LimitData::lowerLimit).orElse(null);

        // Alarm bands intersecting the view
        List<AlarmBand> bands = alarmRepo.findOverlapping(parameterId, from, to).stream()
                .map(a -> new AlarmBand(a.getFirstViolationAt(), a.getLastViolationAt(),
                        a.getSeverity().name()))
                .toList();

        return new TrendChartResponse(points, downsampled, upper, lower, bands.isEmpty() ? null : bands);
    }

    private Map<String, String> buildFilterContext(String tool, String recipe, String product, String lot) {
        Map<String, String> ctx = new LinkedHashMap<>();
        if (tool != null) ctx.put("tool", tool);
        if (recipe != null) ctx.put("recipe", recipe);
        if (product != null) ctx.put("product", product);
        if (lot != null) ctx.put("lot_id", lot);
        return ctx;
    }

    private TrendPoint toTrendPoint(MeasurementEntity m) {
        return new TrendPoint(m.getTs(), m.getValue(), m.getTool(), m.getRecipe(),
                m.getProduct(), m.getLotId(), m.getWaferId());
    }
}
