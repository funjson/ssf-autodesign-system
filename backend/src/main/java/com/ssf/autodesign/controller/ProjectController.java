package com.ssf.autodesign.controller;

import com.ssf.autodesign.dto.ProjectDtos.ProjectDto;
import com.ssf.autodesign.dto.ProjectDtos.ProjectTreeDto;
import com.ssf.autodesign.dto.ProjectDtos.WorkspaceDto;
import com.ssf.autodesign.dto.Requests.AddWorkspaceRequest;
import com.ssf.autodesign.dto.Requests.CreateProjectRequest;
import com.ssf.autodesign.dto.Requests.RenameWorkspaceRequest;
import com.ssf.autodesign.service.ProjectService;
import com.ssf.autodesign.service.WorkspaceImportService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectService projectService;
    private final WorkspaceImportService workspaceImportService;

    public ProjectController(ProjectService projectService, WorkspaceImportService workspaceImportService) {
        this.projectService = projectService;
        this.workspaceImportService = workspaceImportService;
    }

    @GetMapping
    public List<ProjectDto> listProjects() {
        return projectService.listProjects();
    }

    @PostMapping
    public ProjectDto createProject(@Valid @RequestBody CreateProjectRequest request) {
        return projectService.createProject(request.name());
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> deleteProject(@PathVariable Long projectId) {
        projectService.deleteProject(projectId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{projectId}/workspaces")
    public WorkspaceDto addWorkspace(@PathVariable Long projectId, @Valid @RequestBody AddWorkspaceRequest request) {
        return projectService.addWorkspace(projectId, request.path());
    }

    @DeleteMapping("/{projectId}/workspaces/{workspaceId}")
    public ResponseEntity<Void> removeWorkspace(@PathVariable Long projectId, @PathVariable Long workspaceId) {
        projectService.removeWorkspace(projectId, workspaceId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{projectId}/workspaces/{workspaceId}")
    public WorkspaceDto renameWorkspace(@PathVariable Long projectId,
                                        @PathVariable Long workspaceId,
                                        @Valid @RequestBody RenameWorkspaceRequest request) {
        return projectService.renameWorkspace(projectId, workspaceId, request.displayName());
    }

    @PostMapping(value = "/{projectId}/workspace-imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public WorkspaceDto importWorkspace(@PathVariable Long projectId,
                                        @RequestParam("files") List<MultipartFile> files,
                                        @RequestParam(value = "relativePaths", required = false) List<String> relativePaths,
                                        @RequestParam(value = "displayName", required = false) String displayName) {
        return workspaceImportService.importWorkspace(projectId, files, relativePaths, displayName);
    }

    @GetMapping("/{projectId}/tree")
    public ProjectTreeDto getTree(@PathVariable Long projectId,
                                  @RequestParam(name = "scan", defaultValue = "false") boolean scan) {
        return scan ? projectService.scanAndGetTree(projectId) : projectService.getTree(projectId);
    }
}
