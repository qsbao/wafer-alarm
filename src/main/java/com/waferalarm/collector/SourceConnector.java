package com.waferalarm.collector;

import com.waferalarm.domain.MeasurementEntity;
import com.waferalarm.domain.SourceMappingEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Component
public class SourceConnector {

    private final JdbcTemplate jdbcTemplate;

    public SourceConnector(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<MeasurementEntity> pull(SourceMappingEntity mapping, Long parameterId,
                                         Instant watermarkLow, Instant watermarkHigh) {
        String sql = mapping.getQueryTemplate()
                .replace(":watermark_low", "'" + Timestamp.from(watermarkLow) + "'")
                .replace(":watermark_high", "'" + Timestamp.from(watermarkHigh) + "'");

        // Enforce row cap via LIMIT
        sql = sql + " LIMIT " + mapping.getRowCap();

        String valueCol = mapping.getValueColumn();
        String watermarkCol = mapping.getWatermarkColumn();

        return jdbcTemplate.query(sql, (ResultSet rs, int rowNum) -> {
            String waferId = getStringOrNull(rs, "wafer_id");
            double value = rs.getDouble(valueCol);
            Timestamp wmTs = rs.getTimestamp(watermarkCol);
            Instant ts = wmTs != null ? wmTs.toInstant() : Instant.now();
            String tool = getStringOrNull(rs, "tool");
            String recipe = getStringOrNull(rs, "recipe");
            String product = getStringOrNull(rs, "product");
            String lotId = getStringOrNull(rs, "lot_id");

            return new MeasurementEntity(parameterId, waferId, value, ts, tool, recipe, product, lotId);
        });
    }

    private String getStringOrNull(ResultSet rs, String column) {
        try {
            return rs.getString(column);
        } catch (Exception e) {
            return null;
        }
    }
}
