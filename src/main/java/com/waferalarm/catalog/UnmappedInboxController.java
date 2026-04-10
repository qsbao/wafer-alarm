package com.waferalarm.catalog;

import com.waferalarm.domain.StagingUnmappedEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/unmapped")
public class UnmappedInboxController {

    private final UnmappedDataService service;

    public UnmappedInboxController(UnmappedDataService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, List<StagingUnmappedEntity>> listGrouped() {
        return service.listGroupedBySourceSystem();
    }

    @GetMapping("/all")
    public List<StagingUnmappedEntity> listAll() {
        return service.listAll();
    }

    @PostMapping("/dismiss")
    public ResponseEntity<Void> dismiss(@RequestBody DismissRequest request) {
        service.dismiss(request.sourceSystemId(), request.columnKey());
        return ResponseEntity.ok().build();
    }

    record DismissRequest(Long sourceSystemId, String columnKey) {}
}
