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
 * <p>{@code workspace} + {@code navigationFolderId} 는 follow-up 트랙(2026-05-14)에서 추가 —
 * row click 시 frontend 가 `buildWorkspacePath(workspace, navigationFolderId)` + file 인 경우
 * `?file=resourceId` query 로 explorer 진입.
 * <ul>
 *   <li>file: {@code navigationFolderId = file.folder_id} (parent folder)</li>
 *   <li>folder: {@code navigationFolderId = folder.id} (self)</li>
 * </ul>
 *
 * <p>{@code folderPath} (breadcrumb) 는 본 PR scope 외 — row 시각화는 name + preset chip +
 * grantedBy.name 만으로 충분 (KISS, breadcrumb 표시는 별도 트랙).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MySharedWithMeItem(
    UUID permissionId,
    String resourceType,              // "file" | "folder"
    UUID resourceId,
    String name,
    String preset,                     // "read" | "upload" | "edit" | "admin"
    Instant grantedAt,
    Granter grantedBy,
    Workspace workspace,
    UUID navigationFolderId
) {

    public record Granter(UUID id, String name) {}

    public record Workspace(String kind, UUID id) {}   // kind: "department" | "team"
}
