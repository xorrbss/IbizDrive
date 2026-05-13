package com.ibizdrive.me.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * `GET /api/me/shared-with-me` 응답.
 *
 * <p>{@code nextCursor} 는 본 PR 미사용 (limit 만, cursor 페이지네이션은 follow-up). v1.x 후속 트랙에서
 * 추가 시 base64-encoded composite (granted_at, permission_id) 형태로 확장.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MySharedWithMeListResponse(
    List<MySharedWithMeItem> items,
    String nextCursor
) {

    public static MySharedWithMeListResponse of(List<MySharedWithMeItem> items) {
        return new MySharedWithMeListResponse(items, null);
    }
}
