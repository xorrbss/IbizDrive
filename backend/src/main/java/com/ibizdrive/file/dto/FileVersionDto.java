package com.ibizdrive.file.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ibizdrive.file.FileVersion;
import com.ibizdrive.file.VersionScanStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code GET /api/files/:id/versions} 응답 배열 원소 — A5.2 (docs/02 §7.6).
 *
 * <p>{@link FileVersion} entity의 wire 표현. {@link com.ibizdrive.file.FileItem#getCurrentVersionId()
 * file.currentVersionId}와의 비교로 {@link #isCurrent}를 채운다 — DTO 자체는 file 컨텍스트를 모르므로
 * factory에 currentVersionId를 함께 전달한다.
 *
 * <p>JSON 키 표기: {@link FileDto}/{@link com.ibizdrive.folder.dto.FolderDto}와 동일한 camelCase.
 * docs/02 §7.6 본문 예시는 snake_case로 적혀있으나, 기존 API 계약(FileDto/FolderDto 등 모두 camelCase)
 * 일관성을 우선해 본 endpoint도 camelCase로 wire — A5.3 closure에서 docs §7.6 정합 patch 예정.
 *
 * <p>{@code scanStatus}는 wire에서 {@link VersionScanStatus#getDbValue()}와 동치인 lowercase 문자열로
 * 표현 (DB CHECK와 동일). enum {@code name()}을 그대로 노출하지 않고 명시적으로 dbValue를 직렬화.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FileVersionDto(
    UUID id,
    int versionNumber,
    long sizeBytes,
    String checksumSha256,
    String mimeType,
    String scanStatus,
    UUID uploadedBy,
    Instant uploadedAt,
    String comment,
    boolean isCurrent
) {
    public static FileVersionDto from(FileVersion v, UUID currentVersionId) {
        return new FileVersionDto(
            v.getId(),
            v.getVersionNumber(),
            v.getSizeBytes(),
            v.getChecksumSha256(),
            v.getMimeType(),
            v.getScanStatus() == null ? null : v.getScanStatus().getDbValue(),
            v.getUploadedBy(),
            v.getUploadedAt(),
            v.getComment(),
            currentVersionId != null && currentVersionId.equals(v.getId())
        );
    }
}
