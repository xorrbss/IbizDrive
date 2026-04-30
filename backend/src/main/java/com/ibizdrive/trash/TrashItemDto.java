package com.ibizdrive.trash;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code GET /api/trash} 응답 항목 — A8.1 (docs/02 §7.11, ADR #32).
 *
 * <p>{@code originalParentId}는 file의 {@code original_folder_id} 또는 folder의 {@code original_parent_id}.
 * NULL은 root 위치(folder만 가능). frontend는 본 id를 {@code folderTree()} 캐시에 lookup하여
 * "원래 위치" 표시 — backend는 path 문자열을 구성하지 않는다 (KISS, N+1 회피).
 *
 * <p>tombstone 계열({@code deletedAt}/{@code purgeAfter})만 노출 — 활성 entity의 다른 메타
 * (size, mimeType 등)는 휴지통 list 화면에서 불필요.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TrashItemDto(
    UUID id,
    String name,
    TrashItemType type,
    Instant deletedAt,
    Instant purgeAfter,
    UUID originalParentId
) {
}
