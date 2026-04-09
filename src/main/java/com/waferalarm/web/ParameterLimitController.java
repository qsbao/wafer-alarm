package com.waferalarm.web;

import com.waferalarm.domain.ParameterLimitEntity;
import com.waferalarm.domain.ParameterLimitRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/parameter-limits")
public class ParameterLimitController {

    private final ParameterLimitRepository repo;

    public ParameterLimitController(ParameterLimitRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<ParameterLimitEntity> list() {
        return repo.findAll();
    }

    @GetMapping("/by-parameter/{parameterId}")
    public List<ParameterLimitEntity> listByParameter(@PathVariable Long parameterId) {
        return repo.findByParameterId(parameterId);
    }

    @PostMapping
    public ResponseEntity<ParameterLimitEntity> create(@RequestBody LimitRequest req) {
        var entity = new ParameterLimitEntity(
                req.parameterId(),
                req.contextMatchJson() != null ? req.contextMatchJson() : "{}",
                req.upperLimit(),
                req.lowerLimit());
        return ResponseEntity.status(HttpStatus.CREATED).body(repo.save(entity));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ParameterLimitEntity> update(@PathVariable Long id, @RequestBody LimitRequest req) {
        return repo.findById(id).map(entity -> {
            entity.setUpperLimit(req.upperLimit());
            entity.setLowerLimit(req.lowerLimit());
            if (req.contextMatchJson() != null) {
                entity.setContextMatchJson(req.contextMatchJson());
            }
            return ResponseEntity.ok(repo.save(entity));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repo.existsById(id)) return ResponseEntity.notFound().build();
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    public record LimitRequest(Long parameterId, String contextMatchJson, Double upperLimit, Double lowerLimit) {}
}
