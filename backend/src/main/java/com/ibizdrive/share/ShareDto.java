package com.ibizdrive.share;

import java.time.Instant;
import java.util.UUID;

/**
 * Wire-format DTO for {@link Share} — docs/02 §7.9 응답.
 *
 * <p>네 endpoint(POST /api/files/:id/share, GET /api/shares/by-me, /with-me, DELETE /api/shares/:id)
 * 모두 본 record 또는 그 list로 응답한다 (DELETE는 204 no body).
 *
 * <p>{@code revokedAt}/{@code revokedBy}는 active row에서는 항상 NULL (cursor 쿼리가 active만 반환).
 * 본 필드를 노출하는 이유는 향후 admin 감사 화면이 revoked share 이력을 보여줄 가능성에 대비.
 *
 * <p>permissionId 노출 — frontend가 share를 통한 권한 grant를 추적할 수 있도록 노출. revoke 시 이 grant도
 * 함께 사라진다는 의미를 UX에 표현 가능.
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
    UUID revokedBy
) {

    public static ShareDto from(Share share) {
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
            share.getRevokedBy()
        );
    }
}
