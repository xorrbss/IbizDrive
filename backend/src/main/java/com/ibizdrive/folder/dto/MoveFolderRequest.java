package com.ibizdrive.folder.dto;

import java.util.UUID;

/**
 * {@code POST /api/folders/{id}/move} 요청 본문 (docs/02 §7.5).
 *
 * <p>shape: {@code { targetParentId?: UUID }}.
 *
 * <p>{@code targetParentId == null}이면 root로 이동 — controller SpEL이 ROLE ADMIN으로 분기 (ADR #30).
 * cycle 검사 (자기 자신/후손)는 service({@link com.ibizdrive.folder.FolderMutationService#move})가
 * {@code IllegalArgumentException}으로 차단 → 400 매핑.
 */
public record MoveFolderRequest(
    UUID targetParentId
) {}
