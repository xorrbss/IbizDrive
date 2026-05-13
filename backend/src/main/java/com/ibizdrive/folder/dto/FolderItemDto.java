package com.ibizdrive.folder.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ibizdrive.file.FileItem;
import com.ibizdrive.folder.Folder;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code GET /api/folders/{id}/items} 응답 본문 항목 — 한 폴더에 들어있는 자식 폴더와 파일을 단일
 * 스키마로 합본하기 위한 wire 표현. frontend {@code FileItem} 타입과 1:1 mirror.
 *
 * <p>{@link #type} 식별자: {@code "file"} | {@code "folder"} — 문자열 literal 그대로 frontend 타입의
 * discriminated union과 일치 (docs/01 §2 unified item model).
 *
 * <p>{@link #mimeType}, {@link #size}는 폴더에 의미 없으므로 {@code null} — {@code @JsonInclude(NON_NULL)}이
 * 키 자체를 응답에서 생략한다 (계약: missing = N/A).
 *
 * <p>{@link #updatedBy}는 entity의 {@code ownerId} (UUID) 문자열 표현. 사용자 displayName join은 후속
 * Phase에서 별도 wire (현재 frontend도 string으로 그대로 노출 — Phase B 회귀 없음).
 *
 * <p>{@link #shareCount} (P2c) — 이 resource 자체에 부여된 active grant 수. 상속 grant 미포함.
 * 0이면 {@code null} → 키 omit. FE FileRow는 임계값 {@code > 1}에서만 배지 노출 (1건 단발 공유는
 * 시각적 노이즈로 의도적 hide). {@link com.ibizdrive.permission.PermissionRepository#countActiveByResources}
 * 의 계약과 동기.
 *
 * <p>{@link #itemsCount} (P2d) — 폴더 한정. 활성 자식(폴더+파일) 총 수. 파일 type 에서는 항상 {@code null}.
 * 폴더가 비어있어도 0을 그대로 반환(키 omit 아님) — FE FileRow는 {@code typeof === 'number'} 검사로
 * 폴더의 0개 표시도 허용한다. soft-deleted 자식은 제외.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FolderItemDto(
    UUID id,
    String type,
    String name,
    String mimeType,
    Long size,
    Instant updatedAt,
    String updatedBy,
    UUID parentId,
    ScopeRef scope,
    Integer shareCount,
    Integer itemsCount
) {
    public static FolderItemDto fromFolder(Folder f, Integer shareCount, Integer itemsCount) {
        return new FolderItemDto(
            f.getId(),
            "folder",
            f.getName(),
            null,
            null,
            f.getUpdatedAt(),
            f.getOwnerId() != null ? f.getOwnerId().toString() : null,
            f.getParentId(),
            ScopeRef.of(f.getScopeType(), f.getScopeId()),
            shareCount,
            itemsCount
        );
    }

    public static FolderItemDto fromFile(FileItem file, Integer shareCount) {
        // file에는 itemsCount 의미 없음 — 항상 null.
        return new FolderItemDto(
            file.getId(),
            "file",
            file.getName(),
            file.getMimeType(),
            file.getSizeBytes(),
            file.getUpdatedAt(),
            file.getOwnerId() != null ? file.getOwnerId().toString() : null,
            file.getFolderId(),
            ScopeRef.of(file.getScopeType(), file.getScopeId()),
            shareCount,
            null
        );
    }
}
