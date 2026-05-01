package com.ibizdrive.department;

import java.util.List;

/**
 * A16 — {@code GET /api/departments/search} 응답 envelope (ADR #36).
 *
 * <p>{@code items}만 포함 — cursor 미지원. 추후 페이지네이션 도입 시 nextCursor 필드 추가.
 */
public record DepartmentSearchResponse(List<DepartmentSummaryDto> items) {
}
