package com.ssf.autodesign.service;

import com.ssf.autodesign.dto.ProjectDtos.WorkspaceDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class WorkspaceImportService {
    private static final DateTimeFormatter IMPORT_NAME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm '导入'")
            .withZone(ZoneId.systemDefault());

    private final ProjectService projectService;
    private final Path dataRoot;

    public WorkspaceImportService(ProjectService projectService,
                                  @Value("${ssf.data-root:../data}") String dataRoot) {
        this.projectService = projectService;
        this.dataRoot = resolveDataRoot(dataRoot);
    }

    public WorkspaceDto importWorkspace(Long projectId,
                                        List<MultipartFile> files,
                                        List<String> relativePaths,
                                        String displayName) {
        if (files == null || files.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请选择 ssf-workspace 文件夹");
        }
        try {
            String folderName = System.currentTimeMillis() + "-" + sanitize(defaultDisplayName(files, relativePaths, displayName));
            Path importRoot = dataRoot.resolve("imported-workspaces").resolve("project-" + projectId).resolve(folderName).normalize();
            Files.createDirectories(importRoot);

            for (int index = 0; index < files.size(); index++) {
                MultipartFile file = files.get(index);
                if (file.isEmpty()) {
                    continue;
                }
                String relativePath = relativePathAt(file, relativePaths, index);
                Path target = importRoot.resolve(relativePath).normalize();
                if (!target.startsWith(importRoot)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "选择的文件夹中包含非法路径：" + relativePath);
                }
                Files.createDirectories(target.getParent());
                Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            }

            Path workspaceRoot = findWorkspaceRoot(importRoot);
            return projectService.addImportedWorkspace(
                    projectId,
                    workspaceRoot,
                    IMPORT_NAME_FORMATTER.format(Instant.now()),
                    null
            );
        } catch (ApiException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "导入 workspace 失败：" + ex.getMessage());
        }
    }

    private Path findWorkspaceRoot(Path importRoot) throws IOException {
        if (Files.isDirectory(importRoot.resolve("instances"))) {
            return importRoot;
        }
        try (var stream = Files.walk(importRoot, 6)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(path -> Files.isDirectory(path.resolve("instances")))
                    .min(Comparator.comparingInt(path -> path.getNameCount()))
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "未找到 instances 目录，请选择 Skill 生成的 ssf-workspace 文件夹"));
        }
    }

    private String relativePathAt(MultipartFile file, List<String> relativePaths, int index) {
        if (relativePaths != null && index < relativePaths.size() && relativePaths.get(index) != null && !relativePaths.get(index).isBlank()) {
            return relativePaths.get(index).replace("\\", "/");
        }
        return Path.of(file.getOriginalFilename() == null ? "file-" + index : file.getOriginalFilename()).getFileName().toString();
    }

    private String defaultDisplayName(List<MultipartFile> files, List<String> relativePaths, String requestedName) {
        if (requestedName != null && !requestedName.isBlank()) {
            return requestedName.trim();
        }
        if (relativePaths != null && !relativePaths.isEmpty() && relativePaths.get(0) != null) {
            String normalized = relativePaths.get(0).replace("\\", "/");
            int slash = normalized.indexOf('/');
            if (slash > 0) {
                return normalized.substring(0, slash);
            }
        }
        String firstName = files.get(0).getOriginalFilename();
        return firstName == null || firstName.isBlank() ? "ssf-workspace" : Path.of(firstName).getFileName().toString();
    }

    private String sanitize(String value) {
        String cleaned = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-");
        return cleaned.isBlank() ? "ssf-workspace" : cleaned;
    }

    private Path resolveDataRoot(String configured) {
        Path path = Path.of(configured);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return Path.of(System.getProperty("user.dir")).resolve(path).normalize();
    }
}
