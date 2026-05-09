package com.ibizdrive.folder.dto;

import java.util.UUID;

/**
 * {@code POST /api/folders/{id}/move} 요청 본문 (docs/02 §7.5 + §5.6).
 *
 * <p>shape: {@code { targetParentId?: UUID, allowCrossScope?: Boolean }}.
 *
 * <p>{@code targetParentId == null}이면 root로 이동 — controller SpEL이 ROLE ADMIN으로 분기 (ADR #30).
 * cycle 검사 (자기 자신/후손)는 service({@link com.ibizdrive.folder.FolderMutationService#move})가
 * {@code IllegalArgumentException}으로 차단 → 400 매핑.
 *
 * <p>{@code allowCrossScope: true}이면 cross-workspace move 트랜잭션 분기로 위임 (spec §5.6).
 * null/false/absent는 기존 same-scope 가드 그대로.
 */
public record MoveFolderRequest(
    UUID targetParentId,
    Boolean allowCrossScope
) {
    public boolean allowCrossScopeOrFalse() {
        return Boolean.TRUE.equals(allowCrossScope);
    }
}
