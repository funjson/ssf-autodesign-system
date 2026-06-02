package com.ssf.autodesign.service;

import com.ssf.autodesign.domain.SpiInstanceEntity;
import com.ssf.autodesign.dto.SpecGraphDtos.PrototypeFrameDto;
import com.ssf.autodesign.dto.SpecGraphDtos.PrototypeSourceDto;
import com.ssf.autodesign.dto.SpecGraphDtos.ScreenNodeDto;
import com.ssf.autodesign.repository.SpiInstanceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PrototypeAssetService {
    private static final List<String> IMAGE_EXTENSIONS = List.of("png", "jpg", "jpeg", "webp", "svg");
    private static final Pattern SCREEN_ID_IN_FILENAME = Pattern.compile("(SCR-\\d{3})", Pattern.CASE_INSENSITIVE);

    private final SpiInstanceRepository spiInstanceRepository;

    public PrototypeAssetService(SpiInstanceRepository spiInstanceRepository) {
        this.spiInstanceRepository = spiInstanceRepository;
    }

    public PrototypeSourceDto buildSource(Long instanceId, Path instanceRoot, List<ScreenNodeDto> screens) {
        List<PrototypeFrameDto> frames = screens.stream()
                .map(screen -> buildFrame(instanceId, instanceRoot, screen.screenId()))
                .toList();
        boolean hasUserFrames = frames.stream().anyMatch(PrototypeFrameDto::exists);
        String prototypeRoot = instanceRoot.resolve("requirement-prototype").toString();
        if (hasUserFrames) {
            return new PrototypeSourceDto(
                    "user-prototype",
                    "用户原型",
                    "检测到 requirement-prototype 中的真实原型帧，当前优先展示用户原型图片。",
                    prototypeRoot,
                    true,
                    frames
            );
        }
        return new PrototypeSourceDto(
                "generated-contract-preview",
                "系统合成预览",
                "未检测到真实原型帧，当前由 prototype-input 的 SCR/CMP 契约自动合成需求块预览。",
                prototypeRoot,
                false,
                frames
        );
    }

    public Path requireFrame(Long instanceId, String screenId) {
        SpiInstanceEntity instance = spiInstanceRepository.findById(instanceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SPI 实例不存在：" + instanceId));
        return findFrame(Path.of(instance.getRootPath()), screenId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "未找到原型帧：" + screenId));
    }

    public MediaType mediaType(Path file) {
        String filename = file.getFileName().toString().toLowerCase();
        if (filename.endsWith(".svg")) {
            return MediaType.valueOf("image/svg+xml");
        }
        if (filename.endsWith(".webp")) {
            return MediaType.valueOf("image/webp");
        }
        if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        return MediaType.IMAGE_PNG;
    }

    public int uploadFrames(Long instanceId, List<MultipartFile> files, boolean overwrite) {
        SpiInstanceEntity instance = spiInstanceRepository.findById(instanceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SPI 实例不存在：" + instanceId));
        if (files == null || files.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请至少选择一张原型图");
        }

        Path framesDir = Path.of(instance.getRootPath()).resolve("requirement-prototype/frames");
        try {
            if (Files.exists(framesDir) && containsPrototypeImages(framesDir) && !overwrite) {
                throw new ApiException(HttpStatus.CONFLICT, "当前 SPI 已存在原型图，继续上传会覆盖现有文件");
            }
            Files.createDirectories(framesDir);
            if (overwrite) {
                deletePrototypeImages(framesDir);
            }

            int saved = 0;
            List<String> rejected = new ArrayList<>();
            for (MultipartFile file : files) {
                if (file.isEmpty()) {
                    continue;
                }
                String originalName = Optional.ofNullable(file.getOriginalFilename()).orElse("");
                String extension = extension(originalName);
                String screenId = extractScreenId(originalName);
                if (screenId.isBlank() || !IMAGE_EXTENSIONS.contains(extension)) {
                    rejected.add(originalName);
                    continue;
                }
                Path target = framesDir.resolve(screenId + "." + extension).normalize();
                if (!target.startsWith(framesDir)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "原型图文件名不合法：" + originalName);
                }
                Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
                saved++;
            }
            if (saved == 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "未识别到有效原型图。文件名需要包含 SCR-001 这样的页面 ID");
            }
            if (!rejected.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "以下文件未上传，文件名需包含 SCR-xxx 且为图片：" + String.join(", ", rejected));
            }
            return saved;
        } catch (ApiException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "原型图上传失败：" + ex.getMessage());
        }
    }

    private PrototypeFrameDto buildFrame(Long instanceId, Path instanceRoot, String screenId) {
        Optional<Path> frame = findFrame(instanceRoot, screenId);
        return frame.map(path -> new PrototypeFrameDto(
                        screenId,
                        path.toString(),
                        "/api/instances/" + instanceId + "/prototype-frames/" + screenId,
                        true
                ))
                .orElseGet(() -> new PrototypeFrameDto(screenId, "", "", false));
    }

    private Optional<Path> findFrame(Path instanceRoot, String screenId) {
        List<Path> candidateDirs = List.of(
                instanceRoot.resolve("requirement-prototype/frames"),
                instanceRoot.resolve("requirement-prototype"),
                instanceRoot.resolve("prototype/frames"),
                instanceRoot.resolve("prototype")
        );
        for (Path dir : candidateDirs) {
            for (String extension : IMAGE_EXTENSIONS) {
                Path exact = dir.resolve(screenId + "." + extension);
                if (Files.isRegularFile(exact)) {
                    return Optional.of(exact);
                }
                Path lower = dir.resolve(screenId.toLowerCase() + "." + extension);
                if (Files.isRegularFile(lower)) {
                    return Optional.of(lower);
                }
            }
        }
        return Optional.empty();
    }

    private boolean containsPrototypeImages(Path framesDir) throws IOException {
        try (var stream = Files.list(framesDir)) {
            return stream.anyMatch(path -> Files.isRegularFile(path) && IMAGE_EXTENSIONS.contains(extension(path.getFileName().toString())));
        }
    }

    private void deletePrototypeImages(Path framesDir) throws IOException {
        try (var stream = Files.list(framesDir)) {
            for (Path path : stream.filter(path -> Files.isRegularFile(path) && IMAGE_EXTENSIONS.contains(extension(path.getFileName().toString()))).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static String extractScreenId(String filename) {
        Matcher matcher = SCREEN_ID_IN_FILENAME.matcher(filename);
        return matcher.find() ? matcher.group(1).toUpperCase() : "";
    }

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 && dot < filename.length() - 1 ? filename.substring(dot + 1).toLowerCase() : "";
    }
}
