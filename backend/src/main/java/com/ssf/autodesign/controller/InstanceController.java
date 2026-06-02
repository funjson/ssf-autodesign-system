package com.ssf.autodesign.controller;

import com.ssf.autodesign.dto.ChangeRequestDto;
import com.ssf.autodesign.dto.HtmlPrototypeDtos.HtmlPrototypeBindingDto;
import com.ssf.autodesign.dto.HtmlPrototypeDtos.HtmlPrototypePackageDto;
import com.ssf.autodesign.dto.HtmlPrototypeDtos.HtmlPrototypeUploadResult;
import com.ssf.autodesign.dto.HtmlPrototypeDtos.SaveHtmlPrototypeBindingRequest;
import com.ssf.autodesign.dto.InstanceFileDtos.FileContentDto;
import com.ssf.autodesign.dto.InstanceFileDtos.FileTreeNodeDto;
import com.ssf.autodesign.dto.Requests.CreateChangeRequestRequest;
import com.ssf.autodesign.dto.Requests.PrototypeUploadResult;
import com.ssf.autodesign.dto.SpecGraphDtos.SpecGraphDto;
import com.ssf.autodesign.service.ChangeRequestService;
import com.ssf.autodesign.service.HtmlPrototypeService;
import com.ssf.autodesign.service.InstanceFileService;
import com.ssf.autodesign.service.PrototypeAssetService;
import com.ssf.autodesign.service.SpecGraphService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/instances")
public class InstanceController {
    private final SpecGraphService specGraphService;
    private final ChangeRequestService changeRequestService;
    private final PrototypeAssetService prototypeAssetService;
    private final InstanceFileService instanceFileService;
    private final HtmlPrototypeService htmlPrototypeService;

    public InstanceController(SpecGraphService specGraphService,
                              ChangeRequestService changeRequestService,
                              PrototypeAssetService prototypeAssetService,
                              InstanceFileService instanceFileService,
                              HtmlPrototypeService htmlPrototypeService) {
        this.specGraphService = specGraphService;
        this.changeRequestService = changeRequestService;
        this.prototypeAssetService = prototypeAssetService;
        this.instanceFileService = instanceFileService;
        this.htmlPrototypeService = htmlPrototypeService;
    }

    @GetMapping("/{instanceId}/spec-graph")
    public SpecGraphDto getSpecGraph(@PathVariable Long instanceId) {
        return specGraphService.loadGraph(instanceId);
    }

    @GetMapping("/{instanceId}/change-requests")
    public List<ChangeRequestDto> listChangeRequests(@PathVariable Long instanceId) {
        return changeRequestService.list(instanceId);
    }

    @GetMapping("/{instanceId}/files")
    public FileTreeNodeDto getFileTree(@PathVariable Long instanceId) {
        return instanceFileService.loadTree(instanceId);
    }

    @GetMapping("/{instanceId}/files/content")
    public FileContentDto getFileContent(@PathVariable Long instanceId,
                                         @RequestParam("path") String relativePath) {
        return instanceFileService.readFile(instanceId, relativePath);
    }

    @GetMapping("/{instanceId}/prototype-frames/{screenId}")
    public ResponseEntity<Resource> getPrototypeFrame(@PathVariable Long instanceId, @PathVariable String screenId) {
        var frame = prototypeAssetService.requireFrame(instanceId, screenId);
        return ResponseEntity.ok()
                .contentType(prototypeAssetService.mediaType(frame))
                .body(new FileSystemResource(frame));
    }

    @PostMapping(value = "/{instanceId}/prototype-frames", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PrototypeUploadResult uploadPrototypeFrames(@PathVariable Long instanceId,
                                                       @RequestParam("files") List<MultipartFile> files,
                                                       @RequestParam(name = "overwrite", defaultValue = "false") boolean overwrite) {
        int savedCount = prototypeAssetService.uploadFrames(instanceId, files, overwrite);
        return new PrototypeUploadResult(savedCount, "requirement-prototype/frames", overwrite, "原型图已上传");
    }

    @GetMapping("/{instanceId}/prototype-html")
    public HtmlPrototypePackageDto getHtmlPrototype(@PathVariable Long instanceId) {
        return htmlPrototypeService.packageInfo(instanceId);
    }

    @PostMapping(value = "/{instanceId}/prototype-html", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public HtmlPrototypeUploadResult uploadHtmlPrototype(@PathVariable Long instanceId,
                                                         @RequestParam("files") List<MultipartFile> files,
                                                         @RequestParam(name = "relativePaths", required = false) List<String> relativePaths,
                                                         @RequestParam(name = "overwrite", defaultValue = "false") boolean overwrite,
                                                         @RequestParam(name = "entryPath", required = false) String entryPath) {
        return htmlPrototypeService.upload(instanceId, files, relativePaths, overwrite, entryPath);
    }

    @GetMapping("/{instanceId}/prototype-html/files/**")
    public ResponseEntity<?> getHtmlPrototypeFile(@PathVariable Long instanceId, HttpServletRequest request) {
        String marker = "/api/instances/" + instanceId + "/prototype-html/files/";
        String uri = request.getRequestURI();
        int start = uri.indexOf(marker);
        String relativePath = start >= 0 ? URLDecoder.decode(uri.substring(start + marker.length()), StandardCharsets.UTF_8) : "";
        return htmlPrototypeService.serveFile(instanceId, relativePath);
    }

    @GetMapping("/{instanceId}/prototype-html/bindings")
    public List<HtmlPrototypeBindingDto> getHtmlPrototypeBindings(@PathVariable Long instanceId) {
        return htmlPrototypeService.bindings(instanceId);
    }

    @PostMapping("/{instanceId}/prototype-html/bindings")
    public HtmlPrototypeBindingDto saveHtmlPrototypeBinding(@PathVariable Long instanceId,
                                                            @RequestBody SaveHtmlPrototypeBindingRequest request) {
        return htmlPrototypeService.saveBinding(instanceId, request);
    }

    @DeleteMapping("/{instanceId}/prototype-html/bindings/{bindingId}")
    public ResponseEntity<Void> deleteHtmlPrototypeBinding(@PathVariable Long instanceId,
                                                           @PathVariable String bindingId) {
        htmlPrototypeService.deleteBinding(instanceId, bindingId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{instanceId}/change-requests")
    public ChangeRequestDto createChangeRequest(@PathVariable Long instanceId,
                                                @Valid @RequestBody CreateChangeRequestRequest request) {
        return changeRequestService.create(
                instanceId,
                request.title(),
                request.screenId(),
                request.componentId(),
                request.userIntent(),
                request.extraNotes()
        );
    }
}
