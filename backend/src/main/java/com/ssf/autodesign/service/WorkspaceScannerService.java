package com.ssf.autodesign.service;

import com.ssf.autodesign.domain.SpiInstanceEntity;
import com.ssf.autodesign.domain.WorkspacePathEntity;
import com.ssf.autodesign.repository.SpiInstanceRepository;
import com.ssf.autodesign.repository.WorkspacePathRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class WorkspaceScannerService {
    private final SpiInstanceRepository spiInstanceRepository;
    private final WorkspacePathRepository workspacePathRepository;

    public WorkspaceScannerService(SpiInstanceRepository spiInstanceRepository,
                                   WorkspacePathRepository workspacePathRepository) {
        this.spiInstanceRepository = spiInstanceRepository;
        this.workspacePathRepository = workspacePathRepository;
    }

    @Transactional
    public List<SpiInstanceEntity> scan(WorkspacePathEntity workspace) {
        Path workspaceRoot = Path.of(workspace.getPath());
        Path instancesDir = workspaceRoot.resolve("instances");
        if (!Files.isDirectory(instancesDir)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "未找到 instances 目录：" + instancesDir);
        }

        List<SpiInstanceEntity> result = new ArrayList<>();
        try (var stream = Files.list(instancesDir)) {
            List<Path> instanceDirs = stream
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith("SPI-"))
                    .sorted()
                    .toList();
            for (Path instanceDir : instanceDirs) {
                result.add(upsertInstance(workspace, instanceDir));
            }
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "扫描 workspace 失败：" + ex.getMessage());
        }

        workspace.setLastScannedAt(Instant.now());
        workspacePathRepository.save(workspace);
        return result;
    }

    private SpiInstanceEntity upsertInstance(WorkspacePathEntity workspace, Path instanceDir) throws IOException {
        String instanceCode = instanceDir.getFileName().toString();
        // manifest.md is the authoritative identity/status file for a SPI instance.
        Map<String, String> manifest = parseManifest(instanceDir.resolve("manifest.md"));
        String manifestInstanceCode = manifest.getOrDefault("instance_id", instanceCode);
        Optional<SpiInstanceEntity> existing = spiInstanceRepository.findByWorkspaceIdAndInstanceCode(
                workspace.getId(), manifestInstanceCode
        );
        SpiInstanceEntity entity = existing.orElseGet(SpiInstanceEntity::new);
        entity.setWorkspace(workspace);
        entity.setInstanceCode(manifestInstanceCode);
        entity.setInstanceName(defaultText(manifest.get("instance_name"), manifestInstanceCode));
        entity.setProductName(manifest.getOrDefault("product_name", ""));
        entity.setCurrentPhase(manifest.getOrDefault("current_phase", ""));
        entity.setStatus(manifest.getOrDefault("status", ""));
        entity.setRootPath(instanceDir.toAbsolutePath().normalize().toString());
        entity.setManifestUpdatedAt(manifest.getOrDefault("updated_at", ""));
        return spiInstanceRepository.save(entity);
    }

    private Map<String, String> parseManifest(Path manifestPath) throws IOException {
        Map<String, String> values = new HashMap<>();
        if (!Files.exists(manifestPath)) {
            return values;
        }
        for (String line : Files.readAllLines(manifestPath, StandardCharsets.UTF_8)) {
            List<String> cells = parseTableRow(line);
            if (cells.size() >= 2 && !isSeparatorRow(cells)) {
                String key = cells.get(0).trim();
                String value = cells.get(1).trim();
                if (!key.isBlank() && !key.equals("字段")) {
                    values.putIfAbsent(key, value);
                }
            }
        }
        return values;
    }

    private static List<String> parseTableRow(String line) {
        String trimmed = line.trim();
        if (!trimmed.startsWith("|")) {
            return List.of();
        }
        String[] parts = trimmed.split("\\|", -1);
        List<String> cells = new ArrayList<>();
        for (int i = 1; i < parts.length - 1; i++) {
            cells.add(parts[i].trim());
        }
        return cells;
    }

    private static boolean isSeparatorRow(List<String> cells) {
        return !cells.isEmpty() && cells.stream().allMatch(cell -> cell.matches(":?-{3,}:?"));
    }

    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
