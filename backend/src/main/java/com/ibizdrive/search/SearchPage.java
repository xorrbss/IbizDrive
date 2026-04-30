package com.ibizdrive.search;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * {@code GET /api/search} 응답 envelope — A9.1 (docs/02 §7.8).
 *
 * <p>{@code nextCursor}는 다음 페이지가 있을 때만 노출 ({@code @JsonInclude(NON_NULL)}). 클라이언트는
 * 이 값을 그대로 다음 요청의 {@code cursor=}로 echo back.
 *
 * <p>{@code totalEstimate}는 첫 페이지(cursor 없음)에서만 정확한 매칭 수 — cursor 페이지에서는
 * {@code -1} (재집계 비용 회피, ADR #33).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchPage(
    List<SearchResultDto> items,
    String nextCursor,
    long totalEstimate
) {
}
