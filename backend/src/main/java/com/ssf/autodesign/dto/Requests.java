package com.ssf.autodesign.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class Requests {
    private Requests() {
    }

    public record CreateProjectRequest(@NotBlank @Size(max = 20) String name) {
    }

    public record AddWorkspaceRequest(@NotBlank String path) {
    }

    public record RenameWorkspaceRequest(@NotBlank @Size(max = 80) String displayName) {
    }

    public record PrototypeUploadResult(
            int savedCount,
            String targetPath,
            boolean overwritten,
            String message
    ) {
    }

    public record CreateChangeRequestRequest(
            String title,
            String screenId,
            String componentId,
            @NotBlank String userIntent,
            String extraNotes
    ) {
    }
}
