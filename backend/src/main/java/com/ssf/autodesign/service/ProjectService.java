package com.ssf.autodesign.service;

import com.ssf.autodesign.domain.ProjectEntity;
import com.ssf.autodesign.domain.WorkspacePathEntity;
import com.ssf.autodesign.dto.ProjectDtos.ProjectDto;
import com.ssf.autodesign.dto.ProjectDtos.ProjectTreeDto;
import com.ssf.autodesign.dto.ProjectDtos.SpiInstanceDto;
import com.ssf.autodesign.dto.ProjectDtos.WorkspaceDto;
import com.ssf.autodesign.dto.ProjectDtos.WorkspaceTreeDto;
import com.ssf.autodesign.repository.ProjectRepository;
import com.ssf.autodesign.repository.ChangeRequestRepository;
import com.ssf.autodesign.repository.SpiInstanceRepository;
import com.ssf.autodesign.repository.WorkspacePathRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final ChangeRequestRepository changeRequestRepository;
    private final WorkspacePathRepository workspacePathRepository;
    private final SpiInstanceRepository spiInstanceRepository;
    private final WorkspaceScannerService workspaceScannerService;
    private final DtoMapper mapper;

    public ProjectService(ProjectRepository projectRepository,
                          ChangeRequestRepository changeRequestRepository,
                          WorkspacePathRepository workspacePathRepository,
                          SpiInstanceRepository spiInstanceRepository,
                          WorkspaceScannerService workspaceScannerService,
                          DtoMapper mapper) {
        this.projectRepository = projectRepository;
        this.changeRequestRepository = changeRequestRepository;
        this.workspacePathRepository = workspacePathRepository;
        this.spiInstanceRepository = spiInstanceRepository;
        this.workspaceScannerService = workspaceScannerService;
        this.mapper = mapper;
    }

    @Transactional
    public ProjectDto createProject(String name) {
        ProjectEntity entity = new ProjectEntity();
        entity.setName(name.trim());
        return mapper.toProjectDto(projectRepository.save(entity));
    }

    @Transactional
    public ProjectDto createDemoProject(String name) {
        ProjectEntity entity = new ProjectEntity();
        entity.setName(name.trim());
        entity.setDemoProject(true);
        return mapper.toProjectDto(projectRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<ProjectDto> listProjects() {
        return projectRepository.findAllByOrderByUpdatedAtDesc().stream()
                .map(mapper::toProjectDto)
                .toList();
    }

    @Transactional
    public WorkspaceDto addWorkspace(Long projectId, String rawPath) {
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "项目不存在：" + projectId));
        // Store the resolved real path so repeated scans compare a stable workspace location.
        Path path = normalizeExistingDirectory(rawPath);
        assertPathAvailable(project.getId(), path.toString());
        return attachWorkspace(project, path, path.getFileName() == null ? path.toString() : path.getFileName().toString(), "local", null);
    }

    @Transactional
    public WorkspaceDto addManagedWorkspace(Long projectId, Path workspaceRoot, String displayName, String sourceType) {
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "项目不存在：" + projectId));
        return attachWorkspace(project, normalizeExistingDirectory(workspaceRoot.toString()), displayName, sourceType, null);
    }

    @Transactional
    public WorkspaceDto addImportedWorkspace(Long projectId, Path workspaceRoot, String displayName, String sourceFingerprint) {
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "项目不存在：" + projectId));
        assertFingerprintAvailable(projectId, sourceFingerprint);
        return attachWorkspace(project, normalizeExistingDirectory(workspaceRoot.toString()), displayName, "imported", sourceFingerprint);
    }

    @Transactional(readOnly = true)
    public void assertFingerprintAvailable(Long projectId, String sourceFingerprint) {
        if (sourceFingerprint == null || sourceFingerprint.isBlank()) {
            return;
        }
        workspacePathRepository.findFirstBySourceFingerprint(sourceFingerprint).ifPresent(existing -> {
            String location = existing.getProject().getId().equals(projectId) ? "当前项目" : "其他项目";
            throw new ApiException(HttpStatus.CONFLICT,
                    "这个 ssf-workspace 已经添加到" + location + "中，不能重复导入。请在左侧树中使用已有目录，避免后续文件修改不一致。");
        });
    }

    @Transactional
    public void deleteProject(Long projectId) {
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "项目不存在：" + projectId));
        if (project.isDemoProject()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "演示项目不能删除");
        }
        for (WorkspacePathEntity workspace : workspacePathRepository.findByProjectIdOrderByCreatedAtAsc(projectId)) {
            removeWorkspace(workspace.getId());
        }
        projectRepository.delete(project);
    }

    @Transactional
    public void removeWorkspace(Long projectId, Long workspaceId) {
        WorkspacePathEntity workspace = workspacePathRepository.findById(workspaceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "workspace 不存在：" + workspaceId));
        if (!workspace.getProject().getId().equals(projectId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "workspace 不属于当前项目");
        }
        removeWorkspace(workspace);
    }

    @Transactional
    public WorkspaceDto renameWorkspace(Long projectId, Long workspaceId, String displayName) {
        WorkspacePathEntity workspace = workspacePathRepository.findById(workspaceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "workspace 不存在：" + workspaceId));
        if (!workspace.getProject().getId().equals(projectId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "workspace 不属于当前项目");
        }
        if (workspace.getProject().isDemoProject()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "演示项目的 workspace 不能重命名");
        }
        workspace.setDisplayName(displayName.trim());
        return mapper.toWorkspaceDto(workspacePathRepository.save(workspace));
    }

    private void removeWorkspace(Long workspaceId) {
        WorkspacePathEntity workspace = workspacePathRepository.findById(workspaceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "workspace 不存在：" + workspaceId));
        removeWorkspace(workspace);
    }

    private void removeWorkspace(WorkspacePathEntity workspace) {
        if (workspace.getProject().isDemoProject()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "演示项目的 workspace 不能移除");
        }
        Long workspaceId = workspace.getId();
        List<Long> instanceIds = spiInstanceRepository.findByWorkspaceIdOrderByInstanceCodeAsc(workspaceId).stream()
                .map(instance -> instance.getId())
                .toList();
        if (!instanceIds.isEmpty()) {
            changeRequestRepository.deleteBySpiInstanceIdIn(instanceIds);
        }
        spiInstanceRepository.deleteByWorkspaceId(workspaceId);
        workspacePathRepository.delete(workspace);
    }

    @Transactional
    public ProjectTreeDto scanAndGetTree(Long projectId) {
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "项目不存在：" + projectId));
        List<WorkspaceTreeDto> workspaces = workspacePathRepository.findByProjectIdOrderByCreatedAtAsc(projectId).stream()
                .peek(workspaceScannerService::scan)
                .map(workspace -> new WorkspaceTreeDto(
                        mapper.toWorkspaceDto(workspace),
                        spiInstanceRepository.findByWorkspaceIdOrderByInstanceCodeAsc(workspace.getId()).stream()
                                .map(mapper::toSpiInstanceDto)
                                .toList()
                ))
                .toList();
        return new ProjectTreeDto(mapper.toProjectDto(project), workspaces);
    }

    @Transactional(readOnly = true)
    public ProjectTreeDto getTree(Long projectId) {
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "项目不存在：" + projectId));
        List<WorkspaceTreeDto> workspaces = workspacePathRepository.findByProjectIdOrderByCreatedAtAsc(projectId).stream()
                .map(workspace -> new WorkspaceTreeDto(
                        mapper.toWorkspaceDto(workspace),
                        spiInstanceRepository.findByWorkspaceIdOrderByInstanceCodeAsc(workspace.getId()).stream()
                                .map(mapper::toSpiInstanceDto)
                                .toList()
                ))
                .toList();
        return new ProjectTreeDto(mapper.toProjectDto(project), workspaces);
    }

    private Path normalizeExistingDirectory(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "workspace path 不能为空");
        }
        try {
            Path path = Path.of(rawPath.trim()).toAbsolutePath().normalize();
            if (!Files.isDirectory(path)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "路径不是目录：" + path);
            }
            return path.toRealPath();
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "无法读取目录：" + ex.getMessage());
        }
    }

    private WorkspaceDto attachWorkspace(ProjectEntity project, Path path, String displayName, String sourceType, String sourceFingerprint) {
        String normalized = path.toString();
        WorkspacePathEntity workspace = workspacePathRepository.findByProjectIdAndPath(project.getId(), normalized)
                .orElseGet(WorkspacePathEntity::new);
        workspace.setProject(project);
        workspace.setPath(normalized);
        workspace.setDisplayName(displayName == null || displayName.isBlank()
                ? (path.getFileName() == null ? normalized : path.getFileName().toString())
                : displayName.trim());
        workspace.setSourceType(sourceType == null || sourceType.isBlank() ? "local" : sourceType);
        workspace.setSourceFingerprint(sourceFingerprint);
        WorkspacePathEntity saved = workspacePathRepository.save(workspace);
        workspaceScannerService.scan(saved);
        return mapper.toWorkspaceDto(saved);
    }

    private void assertPathAvailable(Long projectId, String normalizedPath) {
        workspacePathRepository.findFirstByPath(normalizedPath).ifPresent(existing -> {
            String location = existing.getProject().getId().equals(projectId) ? "当前项目" : "其他项目";
            throw new ApiException(HttpStatus.CONFLICT,
                    "这个 ssf-workspace 路径已经添加到" + location + "中，不能重复添加。请直接使用已有目录，避免后续文件修改不一致。");
        });
    }
}
