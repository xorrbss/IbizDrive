package com.ibizdrive.search;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ibizdrive.file.FileItem;
import com.ibizdrive.folder.Folder;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code GET /api/search} 결과 항목 — A9.1 (docs/02 §7.8, ADR #33).
 *
 * <p>polymorphic discriminator: {@code type} = {@code "file"} | {@code "folder"}. 타입별 nullable
 * 필드는 {@code @JsonInclude(NON_NULL)}로 응답에서 제거 — 프론트는 discriminator를 분기 키로 사용.
 *
 * <ul>
 *   <li>{@code type="file"}: {@code folderId}, {@code sizeBytes}, {@code mimeType} 노출
 *       ({@code parentId}는 미노출)</li>
 *   <li>{@code type="folder"}: {@code parentId} 노출 (root면 null) — 나머지 file 전용 필드 미노출</li>
 * </ul>
 *
 * <p>{@code name}은 정규화 전 원본 (display). 정렬/cursor key는 {@code updatedAt}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchResultDto(
    String type,
    UUID id,
    String name,
    UUID parentId,
    UUID folderId,
    Long sizeBytes,
    String mimeType,
    Instant updatedAt
) {

    public static SearchResultDto fromFile(FileItem file) {
        return new SearchResultDto(
            "file",
            file.getId(),
            file.getName(),
            null,
            file.getFolderId(),
            file.getSizeBytes(),
            file.getMimeType(),
            file.getUpdatedAt()
        );
    }

    public static SearchResultDto fromFolder(Folder folder) {
        return new SearchResultDto(
            "folder",
            folder.getId(),
            folder.getName(),
            folder.getParentId(),
            null,
            null,
            null,
            folder.getUpdatedAt()
        );
    }
}
