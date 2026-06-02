package com.ssf.autodesign.service;

import com.ssf.autodesign.domain.SpiInstanceEntity;
import com.ssf.autodesign.dto.InstanceFileDtos.FileContentDto;
import com.ssf.autodesign.dto.InstanceFileDtos.FileTreeNodeDto;
import com.ssf.autodesign.repository.SpiInstanceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class InstanceFileService {
    private static final long MAX_TEXT_PREVIEW_BYTES = 512 * 1024;
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "txt", "md", "markdown", "html", "htm", "css", "js", "ts", "json", "xml", "yaml", "yml", "csv", "log"
    );

    private final SpiInstanceRepository spiInstanceRepository;

    public InstanceFileService(SpiInstanceRepository spiInstanceRepository) {
        this.spiInstanceRepository = spiInstanceRepository;
    }

    public FileTreeNodeDto loadTree(Long instanceId) {
        Path root = instanceRoot(instanceId);
        return toNode(root, root);
    }

    public FileContentDto readFile(Long instanceId, String relativePath) {
        Path root = instanceRoot(instanceId);
        Path file = resolveInsideRoot(root, relativePath);
        if (!Files.isRegularFile(file)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请选择一个文件进行预览");
        }
        String name = file.getFileName().toString();
        String contentType = contentType(name);
        boolean previewable = isTextPreviewable(name);
        try {
            long size = Files.size(file);
            if (!previewable) {
                return new FileContentDto(name, normalizeRelative(root, file), contentType, false, false, "");
            }
            if (size > MAX_TEXT_PREVIEW_BYTES) {
                return new FileContentDto(name, normalizeRelative(root, file), contentType, true, true, "");
            }
            return new FileContentDto(
                    name,
                    normalizeRelative(root, file),
                    contentType,
                    true,
                    false,
                    Files.readString(file, StandardCharsets.UTF_8)
            );
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "读取文件失败：" + ex.getMessage());
        }
    }

    private FileTreeNodeDto toNode(Path root, Path path) {
        boolean directory = Files.isDirectory(path);
        List<FileTreeNodeDto> children = List.of();
        if (directory) {
            try (var stream = Files.list(path)) {
                children = stream
                        .filter(child -> !child.getFileName().toString().equalsIgnoreCase(".DS_Store"))
                        .sorted(Comparator
                                .comparing((Path child) -> !Files.isDirectory(child))
                                .thenComparing(child -> child.getFileName().toString().toLowerCase(Locale.ROOT)))
                        .map(child -> toNode(root, child))
                        .toList();
            } catch (IOException ex) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "读取目录失败：" + ex.getMessage());
            }
        }
        return new FileTreeNodeDto(
                path.getFileName() == null ? root.toString() : path.getFileName().toString(),
                normalizeRelative(root, path),
                directory,
                children
        );
    }

    private Path instanceRoot(Long instanceId) {
        SpiInstanceEntity instance = spiInstanceRepository.findById(instanceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SPI 实例不存在：" + instanceId));
        return Path.of(instance.getRootPath()).toAbsolutePath().normalize();
    }

    private Path resolveInsideRoot(Path root, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return root;
        }
        Path resolved = root.resolve(relativePath).toAbsolutePath().normalize();
        if (!resolved.startsWith(root)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "文件路径不合法：" + relativePath);
        }
        return resolved;
    }

    private String normalizeRelative(Path root, Path path) {
        if (root.equals(path)) {
            return "";
        }
        return root.relativize(path).toString().replace("\\", "/");
    }

    private boolean isTextPreviewable(String name) {
        String extension = extension(name);
        return TEXT_EXTENSIONS.contains(extension);
    }

    private String contentType(String name) {
        String extension = extension(name);
        return switch (extension) {
            case "md", "markdown" -> "markdown";
            case "html", "htm" -> "html";
            case "json" -> "json";
            case "css" -> "css";
            case "js", "ts" -> "code";
            default -> isTextPreviewable(name) ? "text" : "binary";
        };
    }

    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 && dot < name.length() - 1 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }
}
