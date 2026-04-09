package com.waferalarm.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SourceSystemRepository extends JpaRepository<SourceSystemEntity, Long> {
    List<SourceSystemEntity> findByEnabledTrue();
}
