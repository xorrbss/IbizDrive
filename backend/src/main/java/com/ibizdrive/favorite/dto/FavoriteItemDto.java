package com.ibizdrive.favorite.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ibizdrive.file.FileItem;
import com.ibizdrive.folder.Folder;
import com.ibizdrive.folder.dto.ScopeRef;

import java.time.Instant;
import java.util.UUID;

/**
 * v1.x {@code GET /api/me/favorites} 응답의 단일 즐겨찾기 row.
 *
 * <p>file/folder 양립: {@code resourceType}으로 분기. {@code parentId}는 folder의 경우
 * 부모 폴더 id(root면 null), file의 경우 해당 file이 속한 폴더 id. frontend가 URL을 합성할 때
 * 사용 — file 클릭 시 {@code parentId}로 이동 + {@code ?file=resourceId} query, folder 클릭 시
 * {@code resourceId}로 이동.
 *
 * <p>{@code scope}는 workspace discriminator (department/team). frontend가
 * {@code /d/:slug/...} 또는 {@code /t/:slug/...} workspace 라우트로 라우팅하기 위해 필요.
 *
 * <p>{@code starredAt} = favorites 행의 created_at — 최신순 정렬 키 (지금은 server-side 정렬되어
 * 오므로 UI는 client-side 재정렬 불요).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FavoriteItemDto(
    String resourceType,
    UUID resourceId,
    String name,
    UUID parentId,
    ScopeRef scope,
    Instant starredAt
) {
    public static FavoriteItemDto fromFile(FileItem file, Instant starredAt) {
        return new FavoriteItemDto(
            "file",
            file.getId(),
            file.getName(),
            file.getFolderId(),
            ScopeRef.of(file.getScopeType(), file.getScopeId()),
            starredAt
        );
    }

    public static FavoriteItemDto fromFolder(Folder folder, Instant starredAt) {
        return new FavoriteItemDto(
            "folder",
            folder.getId(),
            folder.getName(),
            folder.getParentId(),
            ScopeRef.of(folder.getScopeType(), folder.getScopeId()),
            starredAt
        );
    }
}
