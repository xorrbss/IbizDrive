package com.ibizdrive.file.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * spec docs/02 §7.5 + §5.6 — {@code POST /api/files/{id}/move} 요청 본문.
 *
 * <p>{@link #targetFolderId}는 {@code @NotNull} — 파일은 항상 폴더에 속하며 root로의 이동 개념이 없다
 * (V5 {@code files.folder_id NOT NULL}). 이 점이 {@code MoveFolderRequest}와의 핵심 차이.
 *
 * <p>{@code allowCrossScope: true}이면 cross-workspace move 트랜잭션 분기로 위임 (spec §5.6).
 * null/false/absent는 기존 same-scope 가드 그대로.
 */
public record MoveFileRequest(
    @NotNull UUID targetFolderId,
    Boolean allowCrossScope
) {
    public boolean allowCrossScopeOrFalse() {
        return Boolean.TRUE.equals(allowCrossScope);
    }
}
