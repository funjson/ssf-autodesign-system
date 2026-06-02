package com.ssf.autodesign.repository;

import com.ssf.autodesign.domain.SpiInstanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SpiInstanceRepository extends JpaRepository<SpiInstanceEntity, Long> {
    List<SpiInstanceEntity> findByWorkspaceIdOrderByInstanceCodeAsc(Long workspaceId);

    Optional<SpiInstanceEntity> findByWorkspaceIdAndInstanceCode(Long workspaceId, String instanceCode);

    void deleteByWorkspaceId(Long workspaceId);
}
