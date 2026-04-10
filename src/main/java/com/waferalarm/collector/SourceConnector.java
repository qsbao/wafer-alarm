package com.waferalarm.collector;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waferalarm.domain.MeasurementEntity;
import com.waferalarm.domain.SourceMappingEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Component
public class SourceConnector {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SourceConnector(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<MeasurementEntity> pull(SourceMappingEntity mapping, Long parameterId,
                                         Instant watermarkLow, Instant watermarkHigh) {
        return pullWithUnmapped(mapping, parameterId, watermarkLow, watermarkHigh).measurements();
    }

    public PullResult pullWithUnmapped(SourceMappingEntity mapping, Long parameterId,
                                        Instant watermarkLow, Instant watermarkHigh) {
        String sql = mapping.getQueryTemplate()
                .replace(":watermark_low", "'" + Timestamp.from(watermarkLow) + "'")
                .replace(":watermark_high", "'" + Timestamp.from(watermarkHigh) + "'");

        sql = sql + " LIMIT " + mapping.getRowCap();

        String valueCol = mapping.getValueColumn();
        String watermarkCol = mapping.getWatermarkColumn();
        Set<String> knownColumns = buildKnownColumns(mapping);

        Map<String, String> unmappedColumns = new LinkedHashMap<>();
        List<MeasurementEntity> measurements = new ArrayList<>();

        jdbcTemplate.query(sql, (ResultSet rs) -> {
            // On first row, detect unmapped columns
            if (rs.isFirst() && unmappedColumns.isEmpty()) {
                ResultSetMetaData meta = rs.getMetaData();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    String col = meta.getColumnLabel(i).toLowerCase();
                    if (!knownColumns.contains(col)) {
                        String sampleVal = rs.getString(i);
                        unmappedColumns.put(col, sampleVal);
                    }
                }
            }

            String waferId = getStringOrNull(rs, "wafer_id");
            double value = rs.getDouble(valueCol);
            Timestamp wmTs = rs.getTimestamp(watermarkCol);
            Instant ts = wmTs != null ? wmTs.toInstant() : Instant.now();
            String tool = getStringOrNull(rs, "tool");
            String recipe = getStringOrNull(rs, "recipe");
            String product = getStringOrNull(rs, "product");
            String lotId = getStringOrNull(rs, "lot_id");

            measurements.add(new MeasurementEntity(parameterId, waferId, value, ts, tool, recipe, product, lotId));
        });

        return new PullResult(measurements, unmappedColumns);
    }

    private Set<String> buildKnownColumns(SourceMappingEntity mapping) {
        Set<String> known = new HashSet<>();
        known.add("wafer_id");
        known.add(mapping.getValueColumn().toLowerCase());
        known.add(mapping.getWatermarkColumn().toLowerCase());
        known.add("id"); // auto-increment PK often present

        // Add context column mapping targets
        if (mapping.getContextColumnMapping() != null && !mapping.getContextColumnMapping().isBlank()) {
            try {
                Map<String, String> contextMap = objectMapper.readValue(
                        mapping.getContextColumnMapping(), new TypeReference<>() {});
                for (String sourceCol : contextMap.values()) {
                    known.add(sourceCol.toLowerCase());
                }
            } catch (Exception e) {
                // If context mapping is unparseable, just use defaults
                known.add("tool");
                known.add("recipe");
                known.add("product");
                known.add("lot_id");
            }
        } else {
            // Default context columns
            known.add("tool");
            known.add("recipe");
            known.add("product");
            known.add("lot_id");
        }
        return known;
    }

    private String getStringOrNull(ResultSet rs, String column) {
        try {
            return rs.getString(column);
        } catch (Exception e) {
            return null;
        }
    }
}
