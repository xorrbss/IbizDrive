package com.ibizdrive.user;

import java.util.List;

/**
 * A14 — {@code GET /api/users/search} 응답 envelope.
 *
 * <p>{@code items}만 포함 — cursor 미지원 (ADR #35). 추후 페이지네이션 도입 시 nextCursor 필드 추가.
 */
public record UserSearchResponse(List<UserSummaryDto> items) {
}
