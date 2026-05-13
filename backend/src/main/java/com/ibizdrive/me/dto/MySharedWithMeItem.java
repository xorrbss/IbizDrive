package com.ibizdrive.me.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * User Home Dashboard SharedWithMeCard row — `GET /api/me/shared-with-me` 응답 item.
 *
 * <p>본인이 직접 USER subject 로 받은 active grant 1건의 시각화 형태. department/role/everyone
 * indirect grant 는 v1.1 — 본 응답에서 제외 (repository 가드).
 *
 * <p>{@code folderPath} 는 본 PR scope 외 — dashboard 의 row 시각화는 name + preset chip +
 * grantedBy.name 만으로 충분 (KISS, follow-up 트랙에서 확장 가능).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MySharedWithMeItem(
    UUID permissionId,
    String resourceType,              // "file" | "folder"
    UUID resourceId,
    String name,
    String preset,                     // "read" | "upload" | "edit" | "admin"
    Instant grantedAt,
    Granter grantedBy
) {

    public record Granter(UUID id, String name) {}
}
