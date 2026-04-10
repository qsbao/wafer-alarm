package com.waferalarm.catalog;

import com.waferalarm.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class UnmappedDataServiceTest {

    @Autowired StagingUnmappedRepository unmappedRepo;
    @Autowired StagingDismissedRepository dismissedRepo;
    @Autowired SourceSystemRepository sourceSystemRepo;
    @Autowired SourceMappingRepository sourceMappingRepo;
    @Autowired ConnectorRunRepository connectorRunRepo;
    @Autowired MeasurementRepository measurementRepo;
    @Autowired CollectorWatermarkRepository watermarkRepo;
    @Autowired BackfillTaskRepository backfillTaskRepo;
    @Autowired ParameterRepository parameterRepo;
    @Autowired AlarmRepository alarmRepo;
    @Autowired RuleRepository ruleRepo;
    @Autowired RuleVersionRepository ruleVersionRepo;
    @Autowired ParameterLimitRepository parameterLimitRepo;
    @Autowired UnmappedDataService service;

    private SourceSystemEntity source;

    @BeforeEach
    void setUp() {
        unmappedRepo.deleteAll();
        dismissedRepo.deleteAll();
        connectorRunRepo.deleteAll();
        alarmRepo.deleteAll();
        measurementRepo.deleteAll();
        parameterLimitRepo.deleteAll();
        ruleVersionRepo.deleteAll();
        ruleRepo.deleteAll();
        watermarkRepo.deleteAll();
        backfillTaskRepo.deleteAll();
        sourceMappingRepo.deleteAll();
        sourceSystemRepo.deleteAll();
        parameterRepo.deleteAll();

        source = sourceSystemRepo.save(new SourceSystemEntity(
                "MES-1", "localhost", 3306, "mes", null, "zone-a", "UTC"));
    }

    @Test
    void recordUnmapped_creates_new_entry() {
        service.recordUnmapped(source.getId(), "new_column", "42.5");

        var entries = unmappedRepo.findAll();
        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().getColumnKey()).isEqualTo("new_column");
        assertThat(entries.getFirst().getSampleValue()).isEqualTo("42.5");
        assertThat(entries.getFirst().getOccurrenceCount()).isEqualTo(1);
    }

    @Test
    void recordUnmapped_increments_count_on_repeat() {
        service.recordUnmapped(source.getId(), "new_column", "42.5");
        service.recordUnmapped(source.getId(), "new_column", "99.0");

        var entries = unmappedRepo.findAll();
        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().getOccurrenceCount()).isEqualTo(2);
        assertThat(entries.getFirst().getSampleValue()).isEqualTo("99.0");
    }

    @Test
    void recordUnmapped_skips_dismissed_column() {
        service.dismiss(source.getId(), "old_column");
        service.recordUnmapped(source.getId(), "old_column", "1.0");

        assertThat(unmappedRepo.findAll()).isEmpty();
    }

    @Test
    void listGroupedBySource_returns_entries_grouped() {
        var source2 = sourceSystemRepo.save(new SourceSystemEntity(
                "METRO-1", "localhost", 3307, "metro", null, "zone-b", "UTC"));

        service.recordUnmapped(source.getId(), "col_a", "10");
        service.recordUnmapped(source.getId(), "col_b", "20");
        service.recordUnmapped(source2.getId(), "col_c", "30");

        Map<String, List<StagingUnmappedEntity>> grouped = service.listGroupedBySourceSystem();

        assertThat(grouped).hasSize(2);
        assertThat(grouped.get("MES-1")).hasSize(2);
        assertThat(grouped.get("METRO-1")).hasSize(1);
    }

    @Test
    void dismiss_removes_entry_and_prevents_future_recording() {
        service.recordUnmapped(source.getId(), "noisy_col", "abc");
        assertThat(unmappedRepo.findAll()).hasSize(1);

        service.dismiss(source.getId(), "noisy_col");

        assertThat(unmappedRepo.findAll()).isEmpty();
        assertThat(dismissedRepo.existsBySourceSystemIdAndColumnKey(source.getId(), "noisy_col")).isTrue();

        // Future recording is ignored
        service.recordUnmapped(source.getId(), "noisy_col", "xyz");
        assertThat(unmappedRepo.findAll()).isEmpty();
    }
}
