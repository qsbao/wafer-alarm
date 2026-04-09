package com.waferalarm.catalog;

import com.waferalarm.domain.ParameterEntity;
import com.waferalarm.domain.ParameterRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CatalogService {

    private final ParameterRepository repo;

    public CatalogService(ParameterRepository repo) {
        this.repo = repo;
    }

    public List<ParameterEntity> listAll() {
        return repo.findAll();
    }

    public ParameterEntity create(ParameterRequest req) {
        validateLimits(req.defaultLowerLimit(), req.defaultUpperLimit());
        var entity = new ParameterEntity(req.name(), req.unit(), req.defaultUpperLimit(), req.defaultLowerLimit());
        entity.setDescription(req.description());
        entity.setArea(req.area());
        return repo.save(entity);
    }

    public ParameterEntity update(Long id, ParameterRequest req) {
        validateLimits(req.defaultLowerLimit(), req.defaultUpperLimit());
        var entity = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Parameter not found: " + id));
        entity.setName(req.name());
        entity.setUnit(req.unit());
        entity.setDescription(req.description());
        entity.setArea(req.area());
        entity.setDefaultLowerLimit(req.defaultLowerLimit());
        entity.setDefaultUpperLimit(req.defaultUpperLimit());
        return repo.save(entity);
    }

    public ParameterEntity disable(Long id) {
        var entity = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Parameter not found: " + id));
        entity.setEnabled(false);
        return repo.save(entity);
    }

    private void validateLimits(Double lower, Double upper) {
        if (lower != null && upper != null && lower >= upper) {
            throw new IllegalArgumentException("lower limit must be less than upper limit");
        }
    }
}
