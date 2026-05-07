package com.ibizdrive.folder;

/**
 * 폴더 items 응답의 정렬 기준 키. {@code GET /api/folders/{id}/items?sort=...}의 query parameter.
 *
 * <p>frontend `useFolderItems` hook이 보내는 4 가지 — name / updatedAt / size / type 중,
 * type-first 정렬은 본 enum과 직교하게 service에서 항상 강제 (폴더 그룹 → 파일 그룹).
 *
 * <p>{@code SIZE} 정렬 시 폴더 그룹은 size 컬럼이 없으므로 {@code name asc} fallback —
 * service layer 책임 (FolderQueryService.loadItems 참조).
 */
public enum SortKey {
    NAME,
    UPDATED_AT,
    SIZE
}
