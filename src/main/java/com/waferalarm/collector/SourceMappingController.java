package com.waferalarm.collector;

import com.waferalarm.domain.SourceMappingEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/source-mappings")
public class SourceMappingController {

    private final SourceMappingService service;

    public SourceMappingController(SourceMappingService service) {
        this.service = service;
    }

    @GetMapping
    public List<SourceMappingEntity> list() {
        return service.listAll();
    }

    @PostMapping
    public ResponseEntity<SourceMappingEntity> create(@RequestBody SourceMappingRequest req) {
        try {
            var entity = service.create(req);
            return ResponseEntity.status(HttpStatus.CREATED).body(entity);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<SourceMappingEntity> update(@PathVariable Long id, @RequestBody SourceMappingRequest req) {
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
    public ResponseEntity<SourceMappingEntity> disable(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(service.disable(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/enable")
    public ResponseEntity<SourceMappingEntity> enable(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(service.enable(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
