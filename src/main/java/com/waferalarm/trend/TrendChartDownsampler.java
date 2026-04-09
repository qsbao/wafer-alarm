package com.waferalarm.trend;

import java.util.ArrayList;
import java.util.List;

public class TrendChartDownsampler {

    public List<TrendPoint> downsample(List<TrendPoint> points, int bucketCount) {
        if (points.isEmpty() || points.size() <= bucketCount * 2) {
            return points;
        }

        int size = points.size();
        int pointsPerBucket = size / bucketCount;
        List<TrendPoint> result = new ArrayList<>();

        for (int b = 0; b < bucketCount; b++) {
            int start = b * pointsPerBucket;
            int end = (b == bucketCount - 1) ? size : (b + 1) * pointsPerBucket;

            TrendPoint min = points.get(start);
            TrendPoint max = points.get(start);
            for (int i = start; i < end; i++) {
                TrendPoint p = points.get(i);
                if (p.value() < min.value()) min = p;
                if (p.value() > max.value()) max = p;
            }

            // Emit in time order: whichever came first in the original data
            if (min.ts().isBefore(max.ts()) || min.ts().equals(max.ts())) {
                result.add(min);
                if (!min.equals(max)) result.add(max);
            } else {
                result.add(max);
                result.add(min);
            }
        }

        return result;
    }
}
