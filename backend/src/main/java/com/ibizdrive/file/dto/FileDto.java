package com.ibizdrive.file.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ibizdrive.file.FileItem;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code PATCH/POST /api/files[/...]} 응답 본문 — {@code 200 { file: FileDto }}.
 *
 * <p>{@link FileItem} entity의 wire 표현. {@code parentId}와 다르게 {@code folderId}는 NOT NULL —
 * 파일은 항상 폴더에 속한다 (V5 {@code files.folder_id NOT NULL}).
 *
 * <p>tombstone 컬럼({@code deletedAt}/{@code purgeAfter}/{@code originalFolderId}) 노출 정책:
 * <ul>
 *   <li>활성 상태(rename/move 응답): 모두 NULL → {@code @JsonInclude(NON_NULL)}이 키 생략.</li>
 *   <li>restore 응답: 복원 직후 모두 NULL → 마찬가지로 키 생략.</li>
 *   <li>delete 응답: 본 controller가 {@code 204 No Content}로 반환하므로 본 DTO 미사용.</li>
 * </ul>
 *
 * @see com.ibizdrive.folder.dto.FolderDto 동등 패턴
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FileDto(
    UUID id,
    UUID folderId,
    String name,
    UUID ownerId,
    long sizeBytes,
    String mimeType,
    UUID currentVersionId,
    Instant createdAt,
    Instant updatedAt
) {
    public static FileDto from(FileItem f) {
        return new FileDto(
            f.getId(),
            f.getFolderId(),
            f.getName(),
            f.getOwnerId(),
            f.getSizeBytes(),
            f.getMimeType(),
            f.getCurrentVersionId(),
            f.getCreatedAt(),
            f.getUpdatedAt()
        );
    }
}
