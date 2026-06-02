package com.ssf.autodesign.repository;

import com.ssf.autodesign.domain.ProjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<ProjectEntity, Long> {
    List<ProjectEntity> findAllByOrderByUpdatedAtDesc();

    Optional<ProjectEntity> findFirstByDemoProjectTrue();
}
