package com.ibizdrive.folder.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * spec §5.6 — {@code POST /api/{folder|file}/{id}/move/preview} 요청 본문.
 *
 * <p>shape: {@code { destinationFolderId: UUID }}. {@code @NotNull} 강제 — root 직접 이동은
 * spec §1.3 위배 ({@link com.ibizdrive.folder.InvalidMoveDestinationException}).
 */
public record MovePreviewRequest(
    @NotNull UUID destinationFolderId
) {}
