package com.waferalarm.catalog;

import com.waferalarm.domain.ParameterEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/parameters")
public class CatalogController {

    private final CatalogService service;

    public CatalogController(CatalogService service) {
        this.service = service;
    }

    @GetMapping
    public List<ParameterEntity> list() {
        return service.listAll();
    }

    @PostMapping
    public ResponseEntity<ParameterEntity> create(@RequestBody ParameterRequest req) {
        try {
            var entity = service.create(req);
            return ResponseEntity.status(HttpStatus.CREATED).body(entity);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ParameterEntity> update(@PathVariable Long id, @RequestBody ParameterRequest req) {
        try {
            return ResponseEntity.ok(service.update(id, req));
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("not found")) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{id}/disable")
    public ResponseEntity<ParameterEntity> disable(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(service.disable(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
