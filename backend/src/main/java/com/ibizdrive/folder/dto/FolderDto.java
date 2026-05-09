package com.ibizdrive.folder.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ibizdrive.folder.Folder;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code POST/PATCH /api/folders[/...]} 응답 본문 — {@code 201/200 { folder: FolderDto }}.
 *
 * <p>{@link Folder} entity의 wire 표현. {@code parentId}는 root 폴더의 경우 NULL —
 * {@code @JsonInclude(NON_NULL)}이 응답에서 키 자체를 생략한다 (계약: NULL = root).
 *
 * <p>tombstone 컬럼({@code deletedAt}/{@code purgeAfter})과 {@code originalParentId}는 노출하지 않음 —
 * delete/restore endpoint는 본 세션 범위 외이며, mutation 응답에서는 활성 폴더만 다루므로 항상 NULL.
 *
 * <p>{@link #scope}는 team-centric pivot의 workspace discriminator — frontend가
 * {@code /d/:slug/...} 또는 {@code /t/:slug/...} 라우트로 라우팅할 수 있도록 노출 (spec §5.3, §5.5).
 * V13 이후 모든 활성 folder는 NOT NULL scope_type/scope_id를 갖지만, 방어적으로 entity가 scope를
 * 채우지 않은 경우 {@code null}이 되며 {@code @JsonInclude(NON_NULL)}이 키를 생략한다.
 *
 * @see com.ibizdrive.permission.dto.PermissionDto 동등 패턴
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FolderDto(
    UUID id,
    UUID parentId,
    String name,
    String slug,
    UUID ownerId,
    String auditLevel,
    ScopeRef scope,
    Instant createdAt,
    Instant updatedAt
) {
    public static FolderDto from(Folder f) {
        return new FolderDto(
            f.getId(),
            f.getParentId(),
            f.getName(),
            f.getSlug(),
            f.getOwnerId(),
            f.getAuditLevel(),
            ScopeRef.of(f.getScopeType(), f.getScopeId()),
            f.getCreatedAt(),
            f.getUpdatedAt()
        );
    }
}
