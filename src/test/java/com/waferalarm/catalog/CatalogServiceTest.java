package com.waferalarm.catalog;

import com.waferalarm.domain.ParameterEntity;
import com.waferalarm.domain.ParameterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogServiceTest {

    private ParameterRepository repo;
    private CatalogService service;

    @BeforeEach
    void setUp() {
        repo = mock(ParameterRepository.class);
        service = new CatalogService(repo);
    }

    // --- Validation: lower < upper ---

    @Test
    void create_rejectsWhenLowerGreaterThanUpper() {
        var req = new ParameterRequest("CD", "nm", "description", "Litho", 50.0, 100.0);
        // lower=100, upper=50 → invalid
        var bad = new ParameterRequest("CD", "nm", "description", "Litho", 100.0, 50.0);

        assertThatThrownBy(() -> service.create(bad))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lower limit must be less than upper limit");
    }

    @Test
    void create_rejectsWhenLowerEqualsUpper() {
        var req = new ParameterRequest("CD", "nm", "description", "Litho", 50.0, 50.0);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lower limit must be less than upper limit");
    }

    @Test
    void create_allowsWhenOnlyUpperPresent() {
        var req = new ParameterRequest("CD", "nm", "description", "Litho", null, 100.0);
        var saved = new ParameterEntity("CD", "nm", null, 100.0);
        when(repo.save(any())).thenReturn(saved);

        var result = service.create(req);
        assertThat(result.getName()).isEqualTo("CD");
    }

    @Test
    void create_allowsWhenOnlyLowerPresent() {
        var req = new ParameterRequest("CD", "nm", "description", "Litho", 10.0, null);
        var saved = new ParameterEntity("CD", "nm", null, null);
        when(repo.save(any())).thenReturn(saved);

        var result = service.create(req);
        verify(repo).save(any());
    }

    @Test
    void create_allowsWhenBothNull() {
        var req = new ParameterRequest("CD", "nm", "description", "Litho", null, null);
        var saved = new ParameterEntity("CD", "nm", null, null);
        when(repo.save(any())).thenReturn(saved);

        service.create(req);
        verify(repo).save(any());
    }

    @Test
    void create_allowsValidLimits() {
        var req = new ParameterRequest("CD", "nm", "description", "Litho", 10.0, 100.0);
        var saved = new ParameterEntity("CD", "nm", 100.0, 10.0);
        when(repo.save(any())).thenReturn(saved);

        service.create(req);
        verify(repo).save(any());
    }

    // --- Update validation ---

    @Test
    void update_rejectsInvalidLimits() {
        var existing = new ParameterEntity("CD", "nm", 100.0, null);
        when(repo.findById(1L)).thenReturn(Optional.of(existing));

        var req = new ParameterRequest("CD", "nm", "desc", "Litho", 200.0, 100.0);

        assertThatThrownBy(() -> service.update(1L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lower limit must be less than upper limit");
    }

    @Test
    void update_appliesAllFields() {
        var existing = new ParameterEntity("CD", "nm", 100.0, null);
        when(repo.findById(1L)).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var req = new ParameterRequest("CD-updated", "um", "new desc", "Etch", 10.0, 200.0);
        var result = service.update(1L, req);

        assertThat(result.getName()).isEqualTo("CD-updated");
        assertThat(result.getUnit()).isEqualTo("um");
        assertThat(result.getDescription()).isEqualTo("new desc");
        assertThat(result.getArea()).isEqualTo("Etch");
        assertThat(result.getDefaultLowerLimit()).isEqualTo(10.0);
        assertThat(result.getDefaultUpperLimit()).isEqualTo(200.0);
    }

    @Test
    void update_throwsWhenNotFound() {
        when(repo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, new ParameterRequest("x", "u", null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    // --- Disable ---

    @Test
    void disable_setsEnabledFalse() {
        var existing = new ParameterEntity("CD", "nm", 100.0, null);
        when(repo.findById(1L)).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.disable(1L);
        assertThat(result.isEnabled()).isFalse();
    }

    // --- List ---

    @Test
    void listAll_returnsAllParameters() {
        when(repo.findAll()).thenReturn(List.of(new ParameterEntity("CD", "nm", null, null)));

        var result = service.listAll();
        assertThat(result).hasSize(1);
    }
}
