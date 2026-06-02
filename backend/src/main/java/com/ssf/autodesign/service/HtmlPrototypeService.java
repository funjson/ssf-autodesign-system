package com.ssf.autodesign.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssf.autodesign.domain.SpiInstanceEntity;
import com.ssf.autodesign.dto.HtmlPrototypeDtos.HtmlFileDto;
import com.ssf.autodesign.dto.HtmlPrototypeDtos.HtmlPrototypeBindingDto;
import com.ssf.autodesign.dto.HtmlPrototypeDtos.HtmlPrototypePackageDto;
import com.ssf.autodesign.dto.HtmlPrototypeDtos.HtmlPrototypeTargetDto;
import com.ssf.autodesign.dto.HtmlPrototypeDtos.HtmlPrototypeUploadResult;
import com.ssf.autodesign.dto.HtmlPrototypeDtos.SaveHtmlPrototypeBindingRequest;
import com.ssf.autodesign.repository.SpiInstanceRepository;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class HtmlPrototypeService {
    private static final List<String> ENTRY_PRIORITIES = List.of(
            "index.html", "start.html", "home.html", "main.html", "default.html"
    );

    private final SpiInstanceRepository spiInstanceRepository;
    private final ObjectMapper objectMapper;

    public HtmlPrototypeService(SpiInstanceRepository spiInstanceRepository, ObjectMapper objectMapper) {
        this.spiInstanceRepository = spiInstanceRepository;
        this.objectMapper = objectMapper;
    }

    public HtmlPrototypePackageDto packageInfo(Long instanceId) {
        Path instanceRoot = instanceRoot(instanceId);
        Path packageRoot = readablePackageRoot(instanceRoot);
        Path sourceRoot = sourceRoot(instanceRoot);
        if (!Files.isDirectory(sourceRoot)) {
            return emptyPackage(packageRoot, sourceRoot);
        }
        List<HtmlFileDto> htmlFiles = htmlFiles(sourceRoot, entryPath(sourceRoot, ""));
        String entryPath = htmlFiles.stream().filter(HtmlFileDto::entry).map(HtmlFileDto::path).findFirst().orElse("");
        return new HtmlPrototypePackageDto(
                !htmlFiles.isEmpty(),
                packageRoot.toString(),
                sourceRoot.toString(),
                entryPath,
                entryPath.isBlank() ? "" : fileUrl(instanceId, entryPath),
                htmlFiles,
                bindings(instanceRoot),
                warnings(sourceRoot)
        );
    }

    public HtmlPrototypeUploadResult upload(Long instanceId,
                                            List<MultipartFile> files,
                                            List<String> relativePaths,
                                            boolean overwrite,
                                            String requestedEntryPath) {
        if (files == null || files.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请上传 HTML 文件、ZIP 包，或选择一个 HTML 原型文件夹");
        }
        Path instanceRoot = instanceRoot(instanceId);
        migrateLegacyPackage(instanceRoot);
        Path sourceRoot = canonicalSourceRoot(instanceRoot);
        try {
            if (Files.exists(sourceRoot) && hasAnyFile(sourceRoot) && !overwrite) {
                throw new ApiException(HttpStatus.CONFLICT, "当前 SPI 已存在 HTML 原型包，继续上传会覆盖现有 HTML 原型");
            }
            if (overwrite && Files.exists(sourceRoot)) {
                deleteDirectory(sourceRoot);
                Files.deleteIfExists(packageRoot(instanceRoot).resolve("layer-map.json"));
            }
            Files.createDirectories(sourceRoot);

            int saved = 0;
            if (isSingleZip(files)) {
                saved = extractZip(files.get(0), sourceRoot);
            } else {
                saved = saveMultipartFiles(files, relativePaths, sourceRoot);
            }
            if (saved == 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "未识别到可保存的 HTML 原型文件");
            }

            String entryPath = entryPath(sourceRoot, requestedEntryPath);
            if (entryPath.isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "未找到 HTML 入口页，请确认包内包含 index.html、start.html 或至少一个 .html 文件");
            }
            writeManifest(instanceRoot, entryPath);
            return new HtmlPrototypeUploadResult(saved, overwrite, packageInfo(instanceId), "HTML 原型包已上传");
        } catch (ApiException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "HTML 原型上传失败：" + ex.getMessage());
        }
    }

    public ResponseEntity<?> serveFile(Long instanceId, String relativePath) {
        Path instanceRoot = instanceRoot(instanceId);
        Path sourceRoot = sourceRoot(instanceRoot);
        String normalizedPath = relativePath == null || relativePath.isBlank()
                ? entryPath(sourceRoot, "")
                : safeRelativePath(relativePath);
        Path file = resolveInside(sourceRoot, normalizedPath);
        if (!Files.isRegularFile(file)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "HTML 原型文件不存在：" + normalizedPath);
        }
        if (isHtml(file)) {
            try {
                String html = Files.readString(file, StandardCharsets.UTF_8);
                return ResponseEntity.ok()
                        .contentType(MediaType.valueOf("text/html;charset=UTF-8"))
                        .body(injectBridge(html, instanceId, normalizeRelative(sourceRoot, file)));
            } catch (IOException ex) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "读取 HTML 文件失败：" + ex.getMessage());
            }
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(mediaType(file))
                .body((Resource) new FileSystemResource(file));
    }

    public List<HtmlPrototypeBindingDto> bindings(Long instanceId) {
        return bindings(instanceRoot(instanceId));
    }

    public HtmlPrototypeBindingDto saveBinding(Long instanceId, SaveHtmlPrototypeBindingRequest request) {
        if (request.screenId() == null || request.screenId().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请选择绑定的 screen_id");
        }
        if (request.targets() == null || request.targets().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请先在原型中选择至少一个 DOM 元素");
        }
        Path instanceRoot = instanceRoot(instanceId);
        List<HtmlPrototypeBindingDto> current = new ArrayList<>(bindings(instanceRoot));
        String bindingId = Optional.ofNullable(request.bindingId())
                .filter(value -> !value.isBlank())
                .orElse(nextAnnotationId(current));
        List<HtmlPrototypeTargetDto> targets = normalizeTargets(request.targets());
        String defaultName = request.componentId() == null || request.componentId().isBlank()
                ? request.screenId() + " 页面标注"
                : request.screenId() + "/" + request.componentId() + " 组件标注";
        HtmlPrototypeBindingDto next = new HtmlPrototypeBindingDto(
                bindingId,
                Optional.ofNullable(request.name()).filter(value -> !value.isBlank()).orElse(defaultName),
                "html",
                request.screenId(),
                nullToBlank(request.componentId()),
                targets,
                safeList(request.relatedFeatures()),
                safeList(request.relatedRules()),
                safeList(request.relatedAcceptances())
        );
        current.removeIf(binding -> binding.bindingId().equals(bindingId));
        current.add(next);
        writeLayerMap(instanceRoot, current);
        return next;
    }

    public void deleteBinding(Long instanceId, String bindingId) {
        if (bindingId == null || bindingId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "binding_id is required");
        }
        Path instanceRoot = instanceRoot(instanceId);
        List<HtmlPrototypeBindingDto> current = new ArrayList<>(bindings(instanceRoot));
        boolean removed = current.removeIf(binding -> binding.bindingId().equals(bindingId));
        if (!removed) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ANN binding not found: " + bindingId);
        }
        writeLayerMap(instanceRoot, current);
    }

    private HtmlPrototypePackageDto emptyPackage(Path packageRoot, Path sourceRoot) {
        return new HtmlPrototypePackageDto(
                false,
                packageRoot.toString(),
                sourceRoot.toString(),
                "",
                "",
                List.of(),
                bindingsFromLayerMap(packageRoot.resolve("layer-map.json")),
                List.of("尚未上传 HTML 原型包")
        );
    }

    private int saveMultipartFiles(List<MultipartFile> files, List<String> relativePaths, Path sourceRoot) throws IOException {
        int saved = 0;
        for (int index = 0; index < files.size(); index++) {
            MultipartFile file = files.get(index);
            if (file.isEmpty()) {
                continue;
            }
            String fallbackName = Optional.ofNullable(file.getOriginalFilename()).orElse("file-" + index);
            String rawPath = relativePaths != null && index < relativePaths.size() && relativePaths.get(index) != null && !relativePaths.get(index).isBlank()
                    ? relativePaths.get(index)
                    : fallbackName;
            Path target = resolveInside(sourceRoot, rawPath);
            Files.createDirectories(target.getParent());
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            saved++;
        }
        return saved;
    }

    private int extractZip(MultipartFile file, Path sourceRoot) throws IOException {
        int saved = 0;
        try (InputStream inputStream = file.getInputStream();
             ZipInputStream zip = new ZipInputStream(inputStream, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    Files.createDirectories(resolveInside(sourceRoot, entry.getName()));
                    continue;
                }
                Path target = resolveInside(sourceRoot, entry.getName());
                Files.createDirectories(target.getParent());
                Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
                saved++;
            }
        }
        return saved;
    }

    private boolean isSingleZip(List<MultipartFile> files) {
        if (files.size() != 1) {
            return false;
        }
        String name = Optional.ofNullable(files.get(0).getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        return name.endsWith(".zip");
    }

    private List<HtmlFileDto> htmlFiles(Path sourceRoot, String entryPath) {
        if (!Files.isDirectory(sourceRoot)) {
            return List.of();
        }
        try (var stream = Files.walk(sourceRoot)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(HtmlPrototypeService::isHtml)
                    .sorted(Comparator.comparing(path -> normalizeRelative(sourceRoot, path).toLowerCase(Locale.ROOT)))
                    .map(path -> {
                        String relative = normalizeRelative(sourceRoot, path);
                        return new HtmlFileDto(relative, path.getFileName().toString(), relative.equals(entryPath));
                    })
                    .toList();
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "读取 HTML 原型目录失败：" + ex.getMessage());
        }
    }

    private String entryPath(Path sourceRoot, String requestedEntryPath) {
        if (!Files.isDirectory(sourceRoot)) {
            return "";
        }
        if (requestedEntryPath != null && !requestedEntryPath.isBlank()) {
            Path requested = resolveInside(sourceRoot, requestedEntryPath);
            if (Files.isRegularFile(requested) && isHtml(requested)) {
                return normalizeRelative(sourceRoot, requested);
            }
        }
        Path manifest = packageRootFromSource(sourceRoot).resolve("prototype-manifest.json");
        String manifestEntry = readManifestEntry(manifest);
        if (!manifestEntry.isBlank()) {
            Path fromManifest = resolveInside(sourceRoot, manifestEntry);
            if (Files.isRegularFile(fromManifest) && isHtml(fromManifest)) {
                return normalizeRelative(sourceRoot, fromManifest);
            }
        }
        List<Path> htmlPaths;
        try (var stream = Files.walk(sourceRoot)) {
            htmlPaths = stream.filter(Files::isRegularFile).filter(HtmlPrototypeService::isHtml).toList();
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "识别 HTML 入口失败：" + ex.getMessage());
        }
        for (String priority : ENTRY_PRIORITIES) {
            Optional<Path> match = htmlPaths.stream()
                    .filter(path -> path.getFileName().toString().equalsIgnoreCase(priority))
                    .findFirst();
            if (match.isPresent()) {
                return normalizeRelative(sourceRoot, match.get());
            }
        }
        return htmlPaths.stream()
                .sorted(Comparator.comparing(path -> normalizeRelative(sourceRoot, path).length()))
                .map(path -> normalizeRelative(sourceRoot, path))
                .findFirst()
                .orElse("");
    }

    private void writeManifest(Path instanceRoot, String entryPath) throws IOException {
        Path packageRoot = packageRoot(instanceRoot);
        Files.createDirectories(packageRoot);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                packageRoot.resolve("prototype-manifest.json").toFile(),
                new PrototypeManifest("html", entryPath, Instant.now().toString())
        );
    }

    private String readManifestEntry(Path manifest) {
        if (!Files.isRegularFile(manifest)) {
            return "";
        }
        try {
            return Optional.ofNullable(objectMapper.readValue(manifest.toFile(), PrototypeManifest.class).entryPath()).orElse("");
        } catch (IOException ex) {
            return "";
        }
    }

    private String injectBridge(String html, Long instanceId, String pagePath) {
        String parentBase = parentBaseUrl(instanceId, pagePath);
        String bridge = """
                <style id="ssf-prototype-bridge-style">
                  [data-ssf-hovered="true"] { outline: 2px solid #f0a63b !important; outline-offset: 2px !important; cursor: crosshair !important; }
                  #ssf-annotation-layer { position: fixed !important; inset: 0 !important; width: 100vw !important; height: 100vh !important; pointer-events: none !important; z-index: 2147483646 !important; }
                  .ssf-annotation-box { position: fixed !important; box-sizing: border-box !important; border: 2px solid var(--ssf-ann-color, #1f6f5b) !important; border-radius: 8px !important; background: rgba(31, 111, 91, 0.06) !important; box-shadow: 0 0 0 1px rgba(255,255,255,.85), 0 8px 18px rgba(24,32,29,.12) !important; pointer-events: none !important; }
                  .ssf-annotation-box.active { border-width: 3px !important; background: rgba(240, 166, 59, 0.12) !important; box-shadow: 0 0 0 2px rgba(240,166,59,.25), 0 10px 24px rgba(24,32,29,.18) !important; }
                  .ssf-annotation-box.draft { border: 3px solid #1f6f5b !important; background: rgba(31,111,91,.13) !important; box-shadow: 0 0 0 2px rgba(31,111,91,.18), 0 10px 24px rgba(24,32,29,.16) !important; }
                  .ssf-annotation-label { position: absolute !important; top: -23px !important; right: -2px !important; max-width: 220px !important; overflow: hidden !important; text-overflow: ellipsis !important; white-space: nowrap !important; border-radius: 999px !important; padding: 3px 7px !important; background: var(--ssf-ann-color, #1f6f5b) !important; color: #fff !important; font: 700 11px/1.25 system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif !important; box-shadow: 0 6px 14px rgba(24,32,29,.18) !important; }
                </style>
                <script id="ssf-prototype-bridge">
                (() => {
                  const pagePath = "__PAGE_PATH__";
                  let hovered = null;
                  let annotationMode = false;
                  let annotations = [];
                  let draftTargets = [];
                  let activeAnnotationId = "";
                  let renderScheduled = false;
                  let nextTargetId = 1;
                  let lastParentMessageType = "";
                  let lastOutboundMessageType = "";
                  let lastDraftChangeCount = 0;
                  let lastSyncDraftCount = 0;
                  let lastToggleSelector = "";
                  const colors = ["#1f6f5b", "#a55816", "#3b65b0", "#8a4a9b", "#2f7f7a", "#b4443c"];
                  const ignoreTags = new Set(["HTML", "BODY", "SCRIPT", "STYLE", "LINK", "META"]);
                  function postToParent(message) {
                    lastOutboundMessageType = message.type || "";
                    window.parent.postMessage(message, "*");
                  }
                  function reportStatus(reason) {
                    window.parent.postMessage({
                      type: "ssf-prototype-debug-state",
                      reason,
                      pagePath,
                      annotationMode,
                      draftTargetCount: draftTargets.length,
                      annotationCount: annotations.length,
                      activeAnnotationId,
                      lastParentMessageType,
                      lastOutboundMessageType,
                      lastDraftChangeCount,
                      lastSyncDraftCount,
                      lastToggleSelector
                    }, "*");
                  }
                  function cssEscape(value) {
                    if (window.CSS && CSS.escape) return CSS.escape(value);
                    return String(value).replace(/[^a-zA-Z0-9_-]/g, "\\\\$&");
                  }
                  function ensureTargetId(el) {
                    const existing = el.getAttribute("data-ssf-target-id");
                    if (existing) return existing;
                    const id = "ssf-dom-" + nextTargetId++;
                    el.setAttribute("data-ssf-target-id", id);
                    return id;
                  }
                  function selectorFor(el) {
                    if (el.id) return "#" + cssEscape(el.id);
                    const parts = [];
                    let node = el;
                    while (node && node.nodeType === 1 && node !== document.body && parts.length < 6) {
                      let part = node.tagName.toLowerCase();
                      if (node.getAttribute("data-component-id")) {
                        part += "[data-component-id='" + node.getAttribute("data-component-id") + "']";
                        parts.unshift(part);
                        break;
                      }
                      const parent = node.parentElement;
                      if (parent) {
                        const sameTag = Array.from(parent.children).filter(child => child.tagName === node.tagName);
                        if (sameTag.length > 1) part += ":nth-of-type(" + (sameTag.indexOf(node) + 1) + ")";
                      }
                      parts.unshift(part);
                      node = parent;
                    }
                    return parts.join(" > ");
                  }
                  function closestValue(el, attr) {
                    return el.closest("[" + attr + "]")?.getAttribute(attr) || "";
                  }
                  function findTarget(target) {
                    if (!target || target.pagePath !== pagePath || !target.selector) return null;
                    if (target.targetId) {
                      const byTargetId = document.querySelector("[data-ssf-target-id='" + cssEscape(target.targetId) + "']");
                      if (byTargetId) return byTargetId;
                    }
                    try {
                      return document.querySelector(target.selector);
                    } catch (error) {
                      return null;
                    }
                  }
                  function annotationLabel(annotation) {
                    const relation = annotation.componentId ? annotation.screenId + "/" + annotation.componentId : annotation.screenId;
                    return (annotation.name || annotation.bindingId) + " (" + relation + ")";
                  }
                  function annotationIdsFor(el) {
                    const ids = [];
                    for (const annotation of annotations) {
                      for (const target of annotation.targets || []) {
                        const node = findTarget(target);
                        if (node && (node === el || node.contains(el) || el.contains(node))) {
                          ids.push(annotation.bindingId);
                          break;
                        }
                      }
                    }
                    return ids;
                  }
                  function canonicalClickTarget(el) {
                    for (const target of draftTargets) {
                      const node = findTarget(target);
                      if (node && node === el) {
                        return node;
                      }
                    }
                    return el;
                  }
                  function payload(el, eventType) {
                    const targetEl = annotationMode ? canonicalClickTarget(el) : el;
                    const text = (targetEl.innerText || targetEl.textContent || "").trim().replace(/\\s+/g, " ").slice(0, 160);
                    const rect = targetEl.getBoundingClientRect();
                    return {
                      type: eventType,
                      pagePath,
                      targetId: ensureTargetId(targetEl),
                      selector: selectorFor(targetEl),
                      tagName: targetEl.tagName.toLowerCase(),
                      elementId: targetEl.id || "",
                      className: typeof targetEl.className === "string" ? targetEl.className : "",
                      text,
                      rectX: rect.left,
                      rectY: rect.top,
                      rectWidth: rect.width,
                      rectHeight: rect.height,
                      screenId: targetEl.getAttribute("data-screen-id") || closestValue(targetEl, "data-screen-id") || closestValue(targetEl, "id").toUpperCase().match(/SCR-\\d{3}/)?.[0] || "",
                      componentId: targetEl.getAttribute("data-component-id") || closestValue(targetEl, "data-component-id") || "",
                      featureIds: (targetEl.getAttribute("data-feature-ids") || "").split(",").map(v => v.trim()).filter(Boolean),
                      ruleIds: (targetEl.getAttribute("data-rule-ids") || "").split(",").map(v => v.trim()).filter(Boolean),
                      acceptanceIds: (targetEl.getAttribute("data-ac-ids") || "").split(",").map(v => v.trim()).filter(Boolean),
                      annotationIds: annotationIdsFor(targetEl)
                    };
                  }
                  function targetFromPayload(data) {
                    return {
                      targetId: data.targetId || "",
                      kind: "dom",
                      pagePath: data.pagePath || pagePath,
                      screenSelector: data.screenId || "",
                      selector: data.selector || "",
                      textFingerprint: data.text || "",
                      tagName: data.tagName || "",
                      elementId: data.elementId || "",
                      className: data.className || "",
                      rectX: Number.isFinite(data.rectX) ? data.rectX : 0,
                      rectY: Number.isFinite(data.rectY) ? data.rectY : 0,
                      rectWidth: Number.isFinite(data.rectWidth) ? data.rectWidth : 0,
                      rectHeight: Number.isFinite(data.rectHeight) ? data.rectHeight : 0
                    };
                  }
                  function sameTarget(left, right) {
                    if (!left || !right || left.pagePath !== right.pagePath) return false;
                    if (left.targetId && right.targetId && left.targetId === right.targetId) return true;
                    return left.selector === right.selector;
                  }
                  function publishDraftTargets(target, source, payloadData) {
                    lastDraftChangeCount = draftTargets.length;
                    lastToggleSelector = target?.selector || "";
                    postToParent({
                      ...(payloadData || {}),
                      type: "ssf-prototype-draft-targets-change",
                      pagePath,
                      target,
                      draftTargets
                    });
                    reportStatus(source);
                  }
                  function toggleDraftTarget(target, payloadData) {
                    const existingIndex = draftTargets.findIndex(item => sameTarget(item, target));
                    if (existingIndex >= 0) {
                      draftTargets.splice(existingIndex, 1);
                    } else {
                      draftTargets.push(target);
                    }
                    renderAnnotationOverlays();
                    publishDraftTargets(target, "draft-targets-toggled", payloadData);
                  }
                  function normalizeDraftTargets(value) {
                    if (!Array.isArray(value)) return [];
                    const result = [];
                    value.forEach(raw => {
                      const target = {
                        targetId: typeof raw?.targetId === "string" ? raw.targetId : "",
                        kind: typeof raw?.kind === "string" && raw.kind ? raw.kind : "dom",
                        pagePath: typeof raw?.pagePath === "string" && raw.pagePath ? raw.pagePath : pagePath,
                        screenSelector: typeof raw?.screenSelector === "string" ? raw.screenSelector : "",
                        selector: typeof raw?.selector === "string" ? raw.selector : "",
                        textFingerprint: typeof raw?.textFingerprint === "string" ? raw.textFingerprint : "",
                        tagName: typeof raw?.tagName === "string" ? raw.tagName : "",
                        elementId: typeof raw?.elementId === "string" ? raw.elementId : "",
                        className: typeof raw?.className === "string" ? raw.className : "",
                        rectX: Number.isFinite(raw?.rectX) ? raw.rectX : 0,
                        rectY: Number.isFinite(raw?.rectY) ? raw.rectY : 0,
                        rectWidth: Number.isFinite(raw?.rectWidth) ? raw.rectWidth : 0,
                        rectHeight: Number.isFinite(raw?.rectHeight) ? raw.rectHeight : 0
                      };
                      if (!target.selector || result.some(item => sameTarget(item, target))) return;
                      result.push(target);
                    });
                    return result;
                  }
                  function markHover(el) {
                    if (hovered) hovered.removeAttribute("data-ssf-hovered");
                    hovered = el;
                    if (hovered) hovered.setAttribute("data-ssf-hovered", "true");
                  }
                  function clearMarks() {
                    if (hovered) hovered.removeAttribute("data-ssf-hovered");
                    hovered = null;
                  }
                  function annotationLayer() {
                    let layer = document.getElementById("ssf-annotation-layer");
                    if (!layer) {
                      layer = document.createElement("div");
                      layer.id = "ssf-annotation-layer";
                      document.body.appendChild(layer);
                    }
                    return layer;
                  }
                  function renderAnnotationOverlays() {
                    renderScheduled = false;
                    const layer = annotationLayer();
                    layer.innerHTML = "";
                    document.querySelectorAll("[data-ssf-annotation-ids]").forEach(node => node.removeAttribute("data-ssf-annotation-ids"));
                    document.querySelectorAll("[data-ssf-draft-target]").forEach(node => node.removeAttribute("data-ssf-draft-target"));
                    annotations.forEach((annotation, annotationIndex) => {
                      const color = colors[annotationIndex % colors.length];
                      (annotation.targets || []).forEach((target, targetIndex) => {
                        const node = findTarget(target);
                        if (!node) return;
                        if (draftTargets.some(draft => sameTarget(draft, target))) return;
                        const rect = node.getBoundingClientRect();
                        if (rect.width < 2 || rect.height < 2) return;
                        node.setAttribute("data-ssf-annotation-ids", annotation.bindingId);
                        const box = document.createElement("div");
                        box.className = "ssf-annotation-box" + (annotation.bindingId === activeAnnotationId ? " active" : "");
                        box.style.setProperty("--ssf-ann-color", color);
                        box.style.left = (rect.left - 2 + targetIndex * 3) + "px";
                        box.style.top = (rect.top - 2 + targetIndex * 3) + "px";
                        box.style.width = Math.max(8, rect.width + 4) + "px";
                        box.style.height = Math.max(8, rect.height + 4) + "px";
                        const label = document.createElement("div");
                        label.className = "ssf-annotation-label";
                        label.textContent = annotationLabel(annotation);
                        box.appendChild(label);
                        layer.appendChild(box);
                      });
                    });
                    draftTargets.forEach((target, targetIndex) => {
                      const node = findTarget(target);
                      if (!node) return;
                      const rect = node.getBoundingClientRect();
                      if (rect.width < 2 || rect.height < 2) return;
                      node.setAttribute("data-ssf-draft-target", "true");
                      const box = document.createElement("div");
                      box.className = "ssf-annotation-box draft";
                      box.style.setProperty("--ssf-ann-color", "#1f6f5b");
                      box.style.left = (rect.left - 2 + targetIndex * 2) + "px";
                      box.style.top = (rect.top - 2 + targetIndex * 2) + "px";
                      box.style.width = Math.max(8, rect.width + 4) + "px";
                      box.style.height = Math.max(8, rect.height + 4) + "px";
                      const label = document.createElement("div");
                      label.className = "ssf-annotation-label";
                      label.textContent = "Draft DOM-" + (targetIndex + 1);
                      box.appendChild(label);
                      layer.appendChild(box);
                    });
                  }
                  function scheduleRenderAnnotationOverlays() {
                    if (renderScheduled) return;
                    renderScheduled = true;
                    requestAnimationFrame(renderAnnotationOverlays);
                  }
                  window.addEventListener("message", (event) => {
                    const data = event.data || {};
                    lastParentMessageType = data.type || "";
                    if (data.type === "ssf-prototype-set-mode") {
                      annotationMode = data.mode === "annotate";
                      document.documentElement.setAttribute("data-ssf-annotation-mode", annotationMode ? "true" : "false");
                      if (!annotationMode) clearMarks();
                      reportStatus("mode-updated");
                      return;
                    }
                    if (data.type === "ssf-prototype-sync-annotations") {
                      annotations = Array.isArray(data.annotations) ? data.annotations : [];
                      draftTargets = normalizeDraftTargets(data.draftTargets);
                      lastSyncDraftCount = draftTargets.length;
                      activeAnnotationId = data.activeAnnotationId || "";
                      renderAnnotationOverlays();
                      reportStatus("annotations-synced");
                    }
                  });
                  window.addEventListener("scroll", scheduleRenderAnnotationOverlays, true);
                  window.addEventListener("resize", scheduleRenderAnnotationOverlays);
                  document.addEventListener("mouseover", (event) => {
                    if (!annotationMode) return;
                    const target = event.target instanceof Element ? event.target : null;
                    if (!target || ignoreTags.has(target.tagName)) return;
                    markHover(target);
                    postToParent(payload(target, "ssf-prototype-element-hover"));
                  }, true);
                  document.addEventListener("click", (event) => {
                    const target = event.target instanceof Element ? event.target : null;
                    if (!target || ignoreTags.has(target.tagName)) return;
                    const shouldAnnotate = annotationMode || event.altKey || event.ctrlKey || event.metaKey;
                    if (!shouldAnnotate) return;
                    event.preventDefault();
                    event.stopPropagation();
                    if (annotationMode) {
                      const data = payload(target, "ssf-prototype-draft-target-click");
                      toggleDraftTarget(targetFromPayload(data), data);
                    } else {
                      const data = payload(target, "ssf-prototype-element-select");
                      postToParent(data);
                      reportStatus("element-selected");
                    }
                  }, true);
                  postToParent({ type: "ssf-prototype-ready", pagePath });
                  reportStatus("ready");
                })();
                </script>
                """.replace("__PAGE_PATH__", escapeScript(pagePath));
        String base = "<base href=\"" + parentBase + "\">";
        String withBase = html.replaceFirst("(?i)<head([^>]*)>", "<head$1>" + base);
        if (withBase.equals(html)) {
            withBase = base + html;
        }
        if (withBase.toLowerCase(Locale.ROOT).contains("</body>")) {
            return withBase.replaceFirst("(?i)</body>", Matcher.quoteReplacement(bridge + "</body>"));
        }
        return withBase + bridge;
    }

    private String parentBaseUrl(Long instanceId, String pagePath) {
        String parent = "";
        int slash = pagePath.lastIndexOf('/');
        if (slash >= 0) {
            parent = pagePath.substring(0, slash + 1);
        }
        return "/api/instances/" + instanceId + "/prototype-html/files/" + parent;
    }

    private String fileUrl(Long instanceId, String path) {
        return "/api/instances/" + instanceId + "/prototype-html/files/" + path;
    }

    private List<String> warnings(Path sourceRoot) {
        List<String> warnings = new ArrayList<>();
        try (var stream = Files.walk(sourceRoot)) {
            boolean hasExternalScripts = stream
                    .filter(Files::isRegularFile)
                    .filter(HtmlPrototypeService::isHtml)
                    .anyMatch(this::hasExternalScript);
            if (hasExternalScripts) {
                warnings.add("检测到外链脚本或 CDN 资源，离线或沙箱环境下可能影响渲染");
            }
        } catch (IOException ignored) {
            warnings.add("未能完整扫描 HTML 外部依赖");
        }
        return warnings;
    }

    private boolean hasExternalScript(Path path) {
        try {
            String html = Files.readString(path, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            return html.contains("src=\"http://") || html.contains("src=\"https://")
                    || html.contains("href=\"http://") || html.contains("href=\"https://");
        } catch (IOException ex) {
            return false;
        }
    }

    private List<HtmlPrototypeBindingDto> bindings(Path instanceRoot) {
        return bindingsFromLayerMap(readablePackageRoot(instanceRoot).resolve("layer-map.json"));
    }

    private List<HtmlPrototypeBindingDto> bindingsFromLayerMap(Path layerMap) {
        if (!Files.isRegularFile(layerMap)) {
            return List.of();
        }
        try {
            LayerMap map = objectMapper.readValue(layerMap.toFile(), LayerMap.class);
            if (map.version() != 3 || map.bindings() == null) {
                return List.of();
            }
            return map.bindings();
        } catch (IOException ex) {
            return List.of();
        }
    }

    private void writeLayerMap(Path instanceRoot, List<HtmlPrototypeBindingDto> bindings) {
        Path packageRoot = readablePackageRoot(instanceRoot);
        try {
            Files.createDirectories(packageRoot);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                    packageRoot.resolve("layer-map.json").toFile(),
                    new LayerMap(3, bindings)
            );
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "保存 layer-map 失败：" + ex.getMessage());
        }
    }

    private String nextAnnotationId(List<HtmlPrototypeBindingDto> bindings) {
        int next = bindings.stream()
                .map(HtmlPrototypeBindingDto::bindingId)
                .filter(id -> id != null && id.startsWith("ANN-"))
                .map(id -> id.substring("ANN-".length()))
                .mapToInt(value -> {
                    try {
                        return Integer.parseInt(value);
                    } catch (NumberFormatException ex) {
                        return 0;
                    }
                })
                .max()
                .orElse(0) + 1;
        return "ANN-%03d".formatted(next);
    }

    private List<HtmlPrototypeTargetDto> normalizeTargets(List<HtmlPrototypeTargetDto> targets) {
        List<HtmlPrototypeTargetDto> normalized = new ArrayList<>();
        for (HtmlPrototypeTargetDto target : targets) {
            if (target == null || target.selector() == null || target.selector().isBlank()) {
                continue;
            }
            HtmlPrototypeTargetDto next = new HtmlPrototypeTargetDto(
                    "TGT-%03d".formatted(normalized.size() + 1),
                    Optional.ofNullable(target.kind()).filter(value -> !value.isBlank()).orElse("dom"),
                    nullToBlank(target.pagePath()),
                    nullToBlank(target.screenSelector()),
                    nullToBlank(target.selector()),
                    nullToBlank(target.textFingerprint()),
                    nullToBlank(target.tagName()),
                    nullToBlank(target.elementId()),
                    nullToBlank(target.className()),
                    target.rectX(),
                    target.rectY(),
                    target.rectWidth(),
                    target.rectHeight()
            );
            normalized.removeIf(item -> sameDomTarget(item, next));
            normalized.add(next);
        }
        if (normalized.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请先在原型中选择至少一个有效 DOM 元素");
        }
        return normalized;
    }

    private boolean sameDomTarget(HtmlPrototypeTargetDto left, HtmlPrototypeTargetDto right) {
        if (left == null || right == null) {
            return false;
        }
        return left.pagePath().equals(right.pagePath())
                && left.selector().equals(right.selector());
    }

    private Path instanceRoot(Long instanceId) {
        SpiInstanceEntity instance = spiInstanceRepository.findById(instanceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SPI 实例不存在：" + instanceId));
        return Path.of(instance.getRootPath()).toAbsolutePath().normalize();
    }

    private Path packageRoot(Path instanceRoot) {
        return instanceRoot.resolve("requirement-prototype/packages/current").toAbsolutePath().normalize();
    }

    private Path legacyPackageRoot(Path instanceRoot) {
        return instanceRoot.resolve("requirement-prototype/html-packages/current").toAbsolutePath().normalize();
    }

    private Path readablePackageRoot(Path instanceRoot) {
        migrateLegacyPackage(instanceRoot);
        Path current = packageRoot(instanceRoot);
        if (hasPackageContent(current)) {
            return current;
        }
        Path legacy = legacyPackageRoot(instanceRoot);
        return hasPackageContent(legacy) ? legacy : current;
    }

    private Path packageRootFromSource(Path sourceRoot) {
        return sourceRoot.getParent();
    }

    private Path sourceRoot(Path instanceRoot) {
        return readablePackageRoot(instanceRoot).resolve("source").toAbsolutePath().normalize();
    }

    private Path canonicalSourceRoot(Path instanceRoot) {
        return packageRoot(instanceRoot).resolve("source").toAbsolutePath().normalize();
    }

    private void migrateLegacyPackage(Path instanceRoot) {
        Path current = packageRoot(instanceRoot);
        Path legacy = legacyPackageRoot(instanceRoot);
        if (hasPackageContent(current) || !hasPackageContent(legacy)) {
            return;
        }
        try {
            copyDirectory(legacy, current);
        } catch (IOException ignored) {
            // Legacy data remains readable through the fallback path if migration cannot be copied.
        }
    }

    private boolean hasPackageContent(Path packageRoot) {
        return Files.isDirectory(packageRoot.resolve("source"))
                || Files.isRegularFile(packageRoot.resolve("prototype-manifest.json"))
                || Files.isRegularFile(packageRoot.resolve("layer-map.json"));
    }

    private Path resolveInside(Path root, String relativePath) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        String safePath = safeRelativePath(relativePath);
        Path resolved = normalizedRoot.resolve(safePath).normalize();
        if (!resolved.startsWith(normalizedRoot)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "文件路径不合法：" + relativePath);
        }
        return resolved;
    }

    private String safeRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return "";
        }
        String path = relativePath.replace("\\", "/");
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.contains("\0") || path.contains("../") || path.equals("..") || path.startsWith("..")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "文件路径不合法：" + relativePath);
        }
        return path;
    }

    private static String normalizeRelative(Path root, Path path) {
        return root.toAbsolutePath().normalize()
                .relativize(path.toAbsolutePath().normalize())
                .toString()
                .replace("\\", "/");
    }

    private static boolean isHtml(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".html") || name.endsWith(".htm");
    }

    private boolean hasAnyFile(Path root) throws IOException {
        try (var stream = Files.walk(root)) {
            return stream.anyMatch(Files::isRegularFile);
        }
    }

    private void deleteDirectory(Path directory) throws IOException {
        try (var stream = Files.walk(directory)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        try (var stream = Files.walk(source)) {
            for (Path path : stream.sorted(Comparator.naturalOrder()).toList()) {
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative).normalize();
                if (!destination.startsWith(target)) {
                    throw new IOException("Legacy prototype package path is invalid: " + relative);
                }
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private MediaType mediaType(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".css")) return MediaType.valueOf("text/css");
        if (name.endsWith(".js")) return MediaType.valueOf("application/javascript");
        if (name.endsWith(".svg")) return MediaType.valueOf("image/svg+xml");
        if (name.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (name.endsWith(".webp")) return MediaType.valueOf("image/webp");
        String detected = URLConnection.guessContentTypeFromName(file.getFileName().toString());
        return detected == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(detected);
    }

    private String escapeScript(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values.stream().filter(value -> value != null && !value.isBlank()).toList();
    }

    private record PrototypeManifest(String prototypeType, String entryPath, String updatedAt) {
    }

    private record LayerMap(int version, List<HtmlPrototypeBindingDto> bindings) {
    }
}
