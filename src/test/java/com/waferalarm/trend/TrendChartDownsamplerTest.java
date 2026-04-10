package com.waferalarm.trend;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrendChartDownsamplerTest {

    private final TrendChartDownsampler downsampler = new TrendChartDownsampler();

    private TrendPoint pt(Instant ts, double value) {
        return new TrendPoint(ts, value, null, null, null, null, null);
    }

    @Test
    void empty_input_returns_empty_output() {
        List<TrendPoint> result = downsampler.downsample(List.of(), 10);
        assertThat(result).isEmpty();
    }

    @Test
    void min_max_bucketing_preserves_extremes() {
        var t = Instant.parse("2024-01-01T00:00:00Z");
        // 6 points, 2 buckets → bucket 1: [1,5,2], bucket 2: [4,0,3]
        var points = List.of(
                pt(t, 1.0),
                pt(t.plusSeconds(1), 5.0),
                pt(t.plusSeconds(2), 2.0),
                pt(t.plusSeconds(3), 4.0),
                pt(t.plusSeconds(4), 0.0),
                pt(t.plusSeconds(5), 3.0));

        List<TrendPoint> result = downsampler.downsample(points, 2);

        assertThat(result).hasSize(4);
        assertThat(result).extracting(TrendPoint::value)
                .containsExactly(1.0, 5.0, 4.0, 0.0);
    }

    @Test
    void single_bucket_returns_min_and_max() {
        var t = Instant.parse("2024-01-01T00:00:00Z");
        var points = List.of(
                pt(t, 3.0),
                pt(t.plusSeconds(1), 1.0),
                pt(t.plusSeconds(2), 5.0));

        List<TrendPoint> result = downsampler.downsample(points, 1);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(TrendPoint::value).containsExactly(1.0, 5.0);
    }

    @Test
    void points_below_bucket_count_returned_as_is() {
        var t = Instant.parse("2024-01-01T00:00:00Z");
        var points = List.of(
                pt(t, 1.0),
                pt(t.plusSeconds(60), 2.0),
                pt(t.plusSeconds(120), 3.0));

        List<TrendPoint> result = downsampler.downsample(points, 10);

        assertThat(result).isEqualTo(points);
    }
}
