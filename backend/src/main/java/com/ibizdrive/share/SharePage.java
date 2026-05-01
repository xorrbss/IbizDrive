package com.ibizdrive.share;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * {@code GET /api/shares/by-me} / {@code GET /api/shares/with-me} 응답 envelope — A10.4 (docs/02 §7.9).
 *
 * <p>{@code nextCursor}는 다음 페이지가 있을 때만 노출 ({@code @JsonInclude(NON_NULL)}). 클라이언트는
 * 이 값을 그대로 다음 요청의 {@code cursor=} 파라미터로 echo back.
 *
 * <p>{@link com.ibizdrive.trash.TrashPage} 동형 — wire 형식 일관.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SharePage(
    List<ShareDto> items,
    String nextCursor
) {
}
