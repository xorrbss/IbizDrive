package com.ibizdrive.file.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * {@code POST /api/files/{id}/move} 요청 본문 (docs/02 §7.5 — files mirror).
 *
 * <p>shape: {@code { targetFolderId: UUID }}. {@link MoveFileRequest#targetFolderId}는 {@code @NotNull} —
 * 파일은 항상 폴더에 속하며 root로의 이동 개념이 없다 (V5 {@code files.folder_id NOT NULL}).
 * 이 점이 {@code MoveFolderRequest}와의 핵심 차이.
 */
public record MoveFileRequest(
    @NotNull UUID targetFolderId
) {}
