package com.ssf.autodesign.dto;

import java.time.Instant;

public record ChangeRequestDto(
        Long id,
        Long spiInstanceId,
        String changeCode,
        String title,
        String status,
        String screenId,
        String componentId,
        String userIntent,
        String extraNotes,
        String filePath,
        Instant createdAt,
        Instant updatedAt
) {
}