package com.ibizdrive.folder.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * {@code GET /api/folders/{id}} 응답 본문 — folder 자체 + root→self 체인.
 *
 * <p>{@code breadcrumb}은 항상 non-empty — 첫 element는 root 폴더, 마지막 element는 요청한 folder.
 * 단일 root 폴더 조회 시 size=1.
 *
 * <p>{@code starred}는 현재 인증 사용자 기준 즐겨찾기 여부 (P2a, FolderItemDto.starred 동형 정책).
 * 미인증 또는 미 starred 시 null — {@code @JsonInclude(NON_NULL)}로 키 자체 omit, FE는 falsy 처리.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FolderDetailResponse(
    FolderDto folder,
    List<BreadcrumbCrumbDto> breadcrumb,
    Boolean starred
) {
}
