package com.ssf.autodesign.service;

import com.ssf.autodesign.domain.ChangeRequestEntity;
import com.ssf.autodesign.domain.ProjectEntity;
import com.ssf.autodesign.domain.SpiInstanceEntity;
import com.ssf.autodesign.domain.WorkspacePathEntity;
import com.ssf.autodesign.dto.ChangeRequestDto;
import com.ssf.autodesign.dto.ProjectDtos.ProjectDto;
import com.ssf.autodesign.dto.ProjectDtos.SpiInstanceDto;
import com.ssf.autodesign.dto.ProjectDtos.WorkspaceDto;
import org.springframework.stereotype.Component;

@Component
public class DtoMapper {
    public ProjectDto toProjectDto(ProjectEntity entity) {
        return new ProjectDto(
                entity.getId(),
                entity.getName(),
                entity.isDemoProject(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public WorkspaceDto toWorkspaceDto(WorkspacePathEntity entity) {
        return new WorkspaceDto(
                entity.getId(),
                entity.getProject().getId(),
                entity.getPath(),
                entity.getDisplayName(),
                entity.getSourceType(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getLastScannedAt()
        );
    }

    public SpiInstanceDto toSpiInstanceDto(SpiInstanceEntity entity) {
        return new SpiInstanceDto(
                entity.getId(),
                entity.getWorkspace().getId(),
                entity.getInstanceCode(),
                entity.getInstanceName(),
                entity.getProductName(),
                entity.getCurrentPhase(),
                entity.getStatus(),
                entity.getRootPath(),
                entity.getManifestUpdatedAt(),
                entity.getUpdatedAt()
        );
    }

    public ChangeRequestDto toChangeRequestDto(ChangeRequestEntity entity) {
        return new ChangeRequestDto(
                entity.getId(),
                entity.getSpiInstance().getId(),
                entity.getChangeCode(),
                entity.getTitle(),
                entity.getStatus(),
                entity.getScreenId(),
                entity.getComponentId(),
                entity.getUserIntent(),
                entity.getExtraNotes(),
                entity.getFilePath(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
