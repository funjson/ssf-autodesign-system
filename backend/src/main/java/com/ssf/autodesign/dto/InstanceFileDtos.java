package com.ssf.autodesign.dto;

import java.util.List;

public final class InstanceFileDtos {
    private InstanceFileDtos() {
    }

    public record FileTreeNodeDto(
            String name,
            String relativePath,
            boolean directory,
            List<FileTreeNodeDto> children
    ) {
    }

    public record FileContentDto(
            String name,
            String relativePath,
            String contentType,
            boolean previewable,
            boolean tooLarge,
            String content
    ) {
    }
}
