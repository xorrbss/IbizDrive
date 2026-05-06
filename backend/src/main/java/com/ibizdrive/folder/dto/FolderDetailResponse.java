package com.ibizdrive.folder.dto;

import java.util.List;

/**
 * {@code GET /api/folders/{id}} 응답 본문 — folder 자체 + root→self 체인.
 *
 * <p>{@code breadcrumb}은 항상 non-empty — 첫 element는 root 폴더, 마지막 element는 요청한 folder.
 * 단일 root 폴더 조회 시 size=1.
 */
public record FolderDetailResponse(
    FolderDto folder,
    List<BreadcrumbCrumbDto> breadcrumb
) {
}
