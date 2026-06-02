package com.ssf.autodesign.dto;

import java.util.List;

public final class HtmlPrototypeDtos {
    private HtmlPrototypeDtos() {
    }

    public record HtmlFileDto(String path, String name, boolean entry) {
    }

    public record HtmlPrototypePackageDto(
            boolean exists,
            String packageRoot,
            String sourceRoot,
            String entryPath,
            String entryUrl,
            List<HtmlFileDto> htmlFiles,
            List<HtmlPrototypeBindingDto> bindings,
            List<String> warnings
    ) {
    }

    public record HtmlPrototypeBindingDto(
            String bindingId,
            String name,
            String prototypeType,
            String screenId,
            String componentId,
            List<HtmlPrototypeTargetDto> targets,
            List<String> relatedFeatures,
            List<String> relatedRules,
            List<String> relatedAcceptances
    ) {
    }

    public record HtmlPrototypeTargetDto(
            String targetId,
            String kind,
            String pagePath,
            String screenSelector,
            String selector,
            String textFingerprint,
            String tagName,
            String elementId,
            String className,
            Double rectX,
            Double rectY,
            Double rectWidth,
            Double rectHeight
    ) {
    }

    public record SaveHtmlPrototypeBindingRequest(
            String bindingId,
            String name,
            String screenId,
            String componentId,
            List<HtmlPrototypeTargetDto> targets,
            List<String> relatedFeatures,
            List<String> relatedRules,
            List<String> relatedAcceptances
    ) {
    }

    public record HtmlPrototypeUploadResult(
            int savedCount,
            boolean overwritten,
            HtmlPrototypePackageDto prototypePackage,
            String message
    ) {
    }
}
