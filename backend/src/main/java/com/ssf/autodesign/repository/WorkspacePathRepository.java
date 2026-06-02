package com.ssf.autodesign.repository;

import com.ssf.autodesign.domain.WorkspacePathEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkspacePathRepository extends JpaRepository<WorkspacePathEntity, Long> {
    List<WorkspacePathEntity> findByProjectIdOrderByCreatedAtAsc(Long projectId);

    Optional<WorkspacePathEntity> findByProjectIdAndPath(Long projectId, String path);

    Optional<WorkspacePathEntity> findFirstByPath(String path);

    Optional<WorkspacePathEntity> findFirstBySourceFingerprint(String sourceFingerprint);
}
