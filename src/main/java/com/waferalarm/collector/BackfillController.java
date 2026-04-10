package com.waferalarm.collector;

import com.waferalarm.domain.BackfillTaskEntity;
import com.waferalarm.domain.BackfillTaskRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class BackfillController {

    private final BackfillRunner backfillRunner;
    private final BackfillTaskRepository taskRepo;

    public BackfillController(BackfillRunner backfillRunner, BackfillTaskRepository taskRepo) {
        this.backfillRunner = backfillRunner;
        this.taskRepo = taskRepo;
    }

    @PostMapping("/source-mappings/{id}/backfill")
    public ResponseEntity<BackfillTaskEntity> triggerBackfill(@PathVariable Long id) {
        try {
            var task = backfillRunner.triggerBackfill(id);
            return ResponseEntity.accepted().body(task);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/backfill-tasks")
    public List<BackfillTaskEntity> listAll() {
        return taskRepo.findAll();
    }

    @GetMapping("/backfill-tasks/mapping/{mappingId}")
    public ResponseEntity<BackfillTaskEntity> getByMappingId(@PathVariable Long mappingId) {
        return taskRepo.findBySourceMappingId(mappingId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
