package com.ibizdrive.folder.dto;

import java.util.List;
import java.util.UUID;

/**
 * Folder tree 노드 — {@code GET /api/folders/tree} 응답의 element.
 *
 * <p>{@code parentId == null} = top-level. {@code children}은 항상 non-null (빈 leaf는 빈 리스트).
 * 재귀 record로 표현 — JSON 직렬화 시 nested 트리.
 *
 * <p>frontend {@code FolderNode}와 1:1 (children optional → empty list로 정규화).
 */
public record FolderNodeDto(
    UUID id,
    UUID parentId,
    String name,
    String slug,
    List<FolderNodeDto> children
) {
}
