package com.ibizdrive.trash;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code GET /api/trash} 응답 항목 — A8.1 (docs/02 §7.11, ADR #32).
 *
 * <p>{@code originalParentId}는 file의 {@code original_folder_id} 또는 folder의 {@code original_parent_id}.
 * NULL은 root 위치(folder만 가능).
 *
 * <p>{@code originalParentPath}는 삭제 직전 부모 폴더의 절대 경로 (leading {@code /}, trailing
 * slash 없음, 예 {@code /회사/팀A/문서}). backend가 페이지 단위 recursive CTE로 일괄 계산
 * ({@code FolderRepository.findAncestorPaths}). {@code originalParentId == null}이거나 부모 chain
 * 종착 실패(데이터 corruption 또는 depth 100 초과) 시 NULL — frontend는 NULL일 때 "원위치 미상"
 * fallback. flat folderTree 캐시 의존을 제거하고 admin trash와 동일한 1-단계 표시를 가능하게 한다.
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
    UUID originalParentId,
    String originalParentPath
) {
}
