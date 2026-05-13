package com.ibizdrive.favorite.dto;

import java.util.List;

/**
 * v1.x {@code GET /api/me/favorites} 응답 envelope.
 *
 * <p>v1 단순: 페이지네이션 없음, 한 user의 활성 즐겨찾기 전체 반환 (최신순).
 * soft-deleted resource는 자연 제외 (favorites 행은 DB에 남지만 응답에서 hidden).
 *
 * <p>UI는 sidebar count badge = items.length 사용. 100+ 발생 시 페이지네이션 도입 검토.
 */
public record FavoriteListResponse(
    List<FavoriteItemDto> items
) {
}
