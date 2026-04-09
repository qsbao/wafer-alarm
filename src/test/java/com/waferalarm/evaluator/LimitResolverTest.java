package com.waferalarm.evaluator;

import com.waferalarm.domain.LimitData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class LimitResolverTest {

    private final LimitResolver resolver = new LimitResolver();

    // --- Exact match ---

    @Test
    void exact_match_returns_limit() {
        var limit = new ParameterLimitData(1L, 10L, Map.of("tool", "A"), 100.0, 0.0);

        Optional<LimitData> result = resolver.resolve(10L, Map.of("tool", "A"), List.of(limit));

        assertThat(result).isPresent();
        assertThat(result.get().upperLimit()).isEqualTo(100.0);
        assertThat(result.get().lowerLimit()).isEqualTo(0.0);
    }

    // --- Global fallback ---

    @Test
    void global_fallback_when_no_specific_match() {
        var global = new ParameterLimitData(1L, 10L, Map.of(), 50.0, 5.0);

        Optional<LimitData> result = resolver.resolve(10L, Map.of("tool", "A"), List.of(global));

        assertThat(result).isPresent();
        assertThat(result.get().upperLimit()).isEqualTo(50.0);
    }

    // --- More-specific override ---

    @Test
    void more_specific_limit_overrides_global() {
        var global = new ParameterLimitData(1L, 10L, Map.of(), 50.0, 5.0);
        var specific = new ParameterLimitData(2L, 10L, Map.of("tool", "A"), 80.0, 10.0);

        Optional<LimitData> result = resolver.resolve(10L, Map.of("tool", "A"), List.of(global, specific));

        assertThat(result).isPresent();
        assertThat(result.get().upperLimit()).isEqualTo(80.0);
    }

    @Test
    void two_key_match_overrides_one_key_match() {
        var oneKey = new ParameterLimitData(1L, 10L, Map.of("tool", "A"), 80.0, 10.0);
        var twoKey = new ParameterLimitData(2L, 10L, Map.of("tool", "A", "recipe", "R1"), 90.0, 15.0);

        Optional<LimitData> result = resolver.resolve(
                10L, Map.of("tool", "A", "recipe", "R1"), List.of(oneKey, twoKey));

        assertThat(result).isPresent();
        assertThat(result.get().upperLimit()).isEqualTo(90.0);
    }

    // --- No match ---

    @Test
    void no_match_returns_empty() {
        var limit = new ParameterLimitData(1L, 10L, Map.of("tool", "B"), 100.0, 0.0);

        Optional<LimitData> result = resolver.resolve(10L, Map.of("tool", "A"), List.of(limit));

        assertThat(result).isEmpty();
    }

    @Test
    void wrong_parameter_returns_empty() {
        var limit = new ParameterLimitData(1L, 99L, Map.of(), 100.0, 0.0);

        Optional<LimitData> result = resolver.resolve(10L, Map.of("tool", "A"), List.of(limit));

        assertThat(result).isEmpty();
    }

    @Test
    void empty_limits_returns_empty() {
        Optional<LimitData> result = resolver.resolve(10L, Map.of("tool", "A"), List.of());

        assertThat(result).isEmpty();
    }

    // --- Tie-breaker: highest id wins with warning ---

    @Test
    void tie_breaker_picks_highest_id_and_logs_warning() {
        var limit1 = new ParameterLimitData(1L, 10L, Map.of("tool", "A"), 100.0, 0.0);
        var limit2 = new ParameterLimitData(5L, 10L, Map.of("tool", "A"), 200.0, 10.0);

        // Capture log output
        List<String> warnings = new ArrayList<>();
        Logger logger = Logger.getLogger(LimitResolver.class.getName());
        Handler handler = new Handler() {
            @Override public void publish(LogRecord r) {
                if (r.getLevel() == Level.WARNING) warnings.add(r.getMessage());
            }
            @Override public void flush() {}
            @Override public void close() {}
        };
        logger.addHandler(handler);
        logger.setUseParentHandlers(false);

        try {
            Optional<LimitData> result = resolver.resolve(10L, Map.of("tool", "A"), List.of(limit1, limit2));

            assertThat(result).isPresent();
            assertThat(result.get().upperLimit()).isEqualTo(200.0);
            assertThat(warnings).isNotEmpty();
            assertThat(warnings.get(0)).contains("equally-specific");
        } finally {
            logger.removeHandler(handler);
            logger.setUseParentHandlers(true);
        }
    }

    // --- Partial match does not count ---

    @Test
    void partial_context_match_does_not_match() {
        // Limit requires tool=A AND recipe=R1, but measurement only has tool=A
        var limit = new ParameterLimitData(1L, 10L, Map.of("tool", "A", "recipe", "R1"), 100.0, 0.0);

        Optional<LimitData> result = resolver.resolve(10L, Map.of("tool", "A"), List.of(limit));

        assertThat(result).isEmpty();
    }

    // --- Measurement has more context than limit (still matches) ---

    @Test
    void measurement_with_extra_context_still_matches() {
        var limit = new ParameterLimitData(1L, 10L, Map.of("tool", "A"), 100.0, 0.0);

        Optional<LimitData> result = resolver.resolve(
                10L, Map.of("tool", "A", "recipe", "R1", "product", "P1"), List.of(limit));

        assertThat(result).isPresent();
        assertThat(result.get().upperLimit()).isEqualTo(100.0);
    }
}
