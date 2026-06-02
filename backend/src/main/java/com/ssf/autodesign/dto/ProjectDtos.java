package com.ssf.autodesign.dto;

import java.time.Instant;
import java.util.List;

public final class ProjectDtos {
    private ProjectDtos() {
    }

    public record ProjectDto(Long id, String name, boolean demoProject, Instant createdAt, Instant updatedAt) {
    }

    public record WorkspaceDto(
            Long id,
            Long projectId,
            String path,
            String displayName,
            String sourceType,
            Instant createdAt,
            Instant updatedAt,
            Instant lastScannedAt
    ) {
    }

    public record SpiInstanceDto(
            Long id,
            Long workspaceId,
            String instanceCode,
            String instanceName,
            String productName,
            String currentPhase,
            String status,
            String rootPath,
            String manifestUpdatedAt,
            Instant updatedAt
    ) {
    }

    public record WorkspaceTreeDto(WorkspaceDto workspace, List<SpiInstanceDto> instances) {
    }

    public record ProjectTreeDto(ProjectDto project, List<WorkspaceTreeDto> workspaces) {
    }
}
