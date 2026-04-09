package com.waferalarm.collector;

import com.waferalarm.domain.SourceSystemEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/source-systems")
public class SourceSystemController {

    private final SourceSystemService service;

    public SourceSystemController(SourceSystemService service) {
        this.service = service;
    }

    @GetMapping
    public List<SourceSystemEntity> list() {
        return service.listAll();
    }

    @PostMapping
    public ResponseEntity<SourceSystemEntity> create(@RequestBody SourceSystemRequest req) {
        try {
            var entity = service.create(req);
            return ResponseEntity.status(HttpStatus.CREATED).body(entity);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<SourceSystemEntity> update(@PathVariable Long id, @RequestBody SourceSystemRequest req) {
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
    public ResponseEntity<SourceSystemEntity> disable(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(service.disable(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/enable")
    public ResponseEntity<SourceSystemEntity> enable(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(service.enable(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
