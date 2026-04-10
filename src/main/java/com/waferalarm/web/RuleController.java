package com.waferalarm.web;

import com.waferalarm.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rules")
public class RuleController {

    private final RuleRepository ruleRepo;
    private final RuleVersionRepository versionRepo;

    public RuleController(RuleRepository ruleRepo, RuleVersionRepository versionRepo) {
        this.ruleRepo = ruleRepo;
        this.versionRepo = versionRepo;
    }

    @GetMapping
    public List<RuleDto> list() {
        return ruleRepo.findAll().stream().map(RuleDto::from).toList();
    }

    @PostMapping
    public ResponseEntity<RuleDto> create(@RequestBody RuleRequest req) {
        var rule = new RuleEntity(req.parameterId(), req.ruleType(), req.severity());
        rule.setAbsoluteDelta(req.absoluteDelta());
        rule.setPercentageDelta(req.percentageDelta());
        rule.setMinimumBaseline(req.minimumBaseline());
        rule = ruleRepo.save(rule);

        var version = new RuleVersionEntity(
                rule.getId(), req.ruleType(), req.severity(), true,
                req.author() != null ? req.author() : "system",
                req.absoluteDelta(), req.percentageDelta(), req.minimumBaseline());
        version = versionRepo.save(version);

        rule.setCurrentVersionId(version.getId());
        rule = ruleRepo.save(rule);

        return ResponseEntity.status(HttpStatus.CREATED).body(RuleDto.from(rule));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RuleDto> update(@PathVariable Long id, @RequestBody RuleRequest req) {
        return ruleRepo.findById(id).map(rule -> {
            rule.setSeverity(req.severity());
            rule.setRuleType(req.ruleType());
            rule.setAbsoluteDelta(req.absoluteDelta());
            rule.setPercentageDelta(req.percentageDelta());
            rule.setMinimumBaseline(req.minimumBaseline());

            var version = new RuleVersionEntity(
                    rule.getId(), req.ruleType(), req.severity(), rule.isEnabled(),
                    req.author() != null ? req.author() : "system",
                    req.absoluteDelta(), req.percentageDelta(), req.minimumBaseline());
            version = versionRepo.save(version);

            rule.setCurrentVersionId(version.getId());
            rule = ruleRepo.save(rule);
            return ResponseEntity.ok(RuleDto.from(rule));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/disable")
    public ResponseEntity<RuleDto> disable(@PathVariable Long id) {
        return ruleRepo.findById(id).map(rule -> {
            rule.setEnabled(false);

            var version = new RuleVersionEntity(
                    rule.getId(), rule.getRuleType(), rule.getSeverity(), false, "system");
            version = versionRepo.save(version);
            rule.setCurrentVersionId(version.getId());

            rule = ruleRepo.save(rule);
            return ResponseEntity.ok(RuleDto.from(rule));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/enable")
    public ResponseEntity<RuleDto> enable(@PathVariable Long id) {
        return ruleRepo.findById(id).map(rule -> {
            rule.setEnabled(true);

            var version = new RuleVersionEntity(
                    rule.getId(), rule.getRuleType(), rule.getSeverity(), true, "system");
            version = versionRepo.save(version);
            rule.setCurrentVersionId(version.getId());

            rule = ruleRepo.save(rule);
            return ResponseEntity.ok(RuleDto.from(rule));
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/versions")
    public ResponseEntity<List<RuleVersionDto>> versions(@PathVariable Long id) {
        if (!ruleRepo.existsById(id)) return ResponseEntity.notFound().build();
        var versions = versionRepo.findByRuleIdOrderByCreatedAtDesc(id)
                .stream().map(RuleVersionDto::from).toList();
        return ResponseEntity.ok(versions);
    }

    public record RuleRequest(Long parameterId, RuleType ruleType, Severity severity, String author,
                              Double absoluteDelta, Double percentageDelta, Double minimumBaseline) {
        public RuleRequest(Long parameterId, RuleType ruleType, Severity severity, String author) {
            this(parameterId, ruleType, severity, author, null, null, null);
        }
    }

    public record RuleDto(Long id, Long parameterId, String ruleType, String severity,
                          boolean enabled, Long currentVersionId,
                          Double absoluteDelta, Double percentageDelta, Double minimumBaseline) {
        public static RuleDto from(RuleEntity e) {
            return new RuleDto(e.getId(), e.getParameterId(), e.getRuleType().name(),
                    e.getSeverity().name(), e.isEnabled(), e.getCurrentVersionId(),
                    e.getAbsoluteDelta(), e.getPercentageDelta(), e.getMinimumBaseline());
        }
    }

    public record RuleVersionDto(Long id, Long ruleId, String ruleType, String severity,
                                 boolean enabled, String author, String createdAt) {
        public static RuleVersionDto from(RuleVersionEntity v) {
            return new RuleVersionDto(v.getId(), v.getRuleId(), v.getRuleType().name(),
                    v.getSeverity().name(), v.isEnabled(), v.getAuthor(),
                    v.getCreatedAt().toString());
        }
    }
}
