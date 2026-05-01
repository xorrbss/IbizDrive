package com.ibizdrive.share;

import com.ibizdrive.permission.PermissionRow;

import java.time.Instant;
import java.util.UUID;

/**
 * Wire-format DTO for {@link Share} — docs/02 §7.9 응답.
 *
 * <p>다섯 endpoint(POST /api/files/:id/share, POST /api/folders/:id/share, GET /api/shares/by-me,
 * GET /api/shares/with-me, DELETE /api/shares/:id) 모두 본 record 또는 그 list로 응답한다 (DELETE는 204 no body).
 *
 * <p>{@code revokedAt}/{@code revokedBy}는 active row에서는 항상 NULL (cursor 쿼리가 active만 반환).
 * 본 필드를 노출하는 이유는 향후 admin 감사 화면이 revoked share 이력을 보여줄 가능성에 대비.
 *
 * <p>permissionId 노출 — frontend가 share를 통한 권한 grant를 추적할 수 있도록 노출. revoke 시 이 grant도
 * 함께 사라진다는 의미를 UX에 표현 가능.
 *
 * <p><strong>A13</strong> — {@code subjectType} / {@code subjectId} / {@code preset}는 {@code permissions}
 * row에서 join으로 채운다. {@code shares.permission_id} → {@code permissions.id} 1:1 매칭. {@code subjectId}는
 * {@code subjectType='everyone'}일 때 NULL (V5 CHECK). 이 3필드 덕분에 frontend는 별도 API 호출 없이
 * "누구에게 / 어떤 권한으로" 공유되었는지 한 row로 표시할 수 있다 (ShareDialog 기존공유 행 + SharesTable preset 컬럼).
 */
public record ShareDto(
    UUID id,
    UUID fileId,
    UUID folderId,
    UUID permissionId,
    UUID sharedBy,
    String message,
    Instant expiresAt,
    Instant createdAt,
    Instant revokedAt,
    UUID revokedBy,
    String subjectType,
    UUID subjectId,
    String preset
) {

    /**
     * Share + 매칭된 permission grant로 wire DTO를 생성한다.
     *
     * @param share share row (active 또는 revoked).
     * @param grant share.permissionId 와 1:1 매칭된 permission row. 호출자가 보장한다 (V6 FK).
     */
    public static ShareDto from(Share share, PermissionRow grant) {
        return new ShareDto(
            share.getId(),
            share.getFileId(),
            share.getFolderId(),
            share.getPermissionId(),
            share.getSharedBy(),
            share.getMessage(),
            share.getExpiresAt(),
            share.getCreatedAt(),
            share.getRevokedAt(),
            share.getRevokedBy(),
            grant.getSubjectType(),
            grant.getSubjectId(),
            grant.getPreset()
        );
    }
}
