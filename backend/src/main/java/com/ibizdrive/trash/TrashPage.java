package com.ibizdrive.trash;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * {@code GET /api/trash} 응답 envelope — A8.1 (docs/02 §7.11).
 *
 * <p>{@code nextCursor}는 다음 페이지가 있을 때만 노출 ({@code @JsonInclude(NON_NULL)}). 클라이언트는
 * 이 값을 그대로 다음 요청의 {@code cursor=} 파라미터로 echo back.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TrashPage(
    List<TrashItemDto> items,
    String nextCursor
) {
}
