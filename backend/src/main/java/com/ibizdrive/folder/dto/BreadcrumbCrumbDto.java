package com.ibizdrive.folder.dto;

import java.util.UUID;

/**
 * Breadcrumb 한 단계 — root → ... → self 체인의 한 노드.
 *
 * <p>frontend {@code BreadcrumbItem.slugPath}는 누적 slug 배열이지만 본 wire DTO는 단일 segment의
 * {@code slug}만 반환. 누적은 frontend가 list 순회 시 prefix scan으로 조립한다 — backend가
 * 누적값을 만들면 슬러그 인코딩 정책이 두 곳에 분리되므로 conversion 로직 단일화 (KISS).
 */
public record BreadcrumbCrumbDto(UUID id, String name, String slug) {
}
