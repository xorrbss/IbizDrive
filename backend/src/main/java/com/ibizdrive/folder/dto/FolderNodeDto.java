package com.ibizdrive.folder.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.UUID;

/**
 * Folder tree 노드 — {@code GET /api/folders/tree} 응답의 element.
 *
 * <p>{@code parentId == null} = top-level. {@code children}은 항상 non-null (빈 leaf는 빈 리스트).
 * 재귀 record로 표현 — JSON 직렬화 시 nested 트리.
 *
 * <p>frontend {@code FolderNode}와 1:1 (children optional → empty list로 정규화).
 *
 * <p>{@link #scope}는 team-centric pivot의 workspace discriminator — 사이드바 트리에서 노드를
 * 클릭했을 때 frontend가 {@code /d/:slug/...} 또는 {@code /t/:slug/...} 라우트로 라우팅할 수
 * 있도록 노출한다 (spec §5.3). null 가능 — {@link FolderDto}와 동일하게 {@code @JsonInclude(NON_NULL)}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FolderNodeDto(
    UUID id,
    UUID parentId,
    String name,
    String slug,
    ScopeRef scope,
    List<FolderNodeDto> children
) {
}
