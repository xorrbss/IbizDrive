package com.ibizdrive.share;

import java.time.Instant;
import java.util.UUID;

/**
 * Share revoke 이벤트 — A10.3, ADR #34.
 *
 * <p>{@link ShareCommandService#revokeShare}가 트랜잭션 내에서 share row snapshot 캡처 후
 * permissions row를 hard-delete (V6 ON DELETE CASCADE로 share row도 함께 사라짐) 직전에
 * publish. {@code ShareAuditListener}가 audit_log에 {@code share.revoked}로 기록.
 *
 * <p><b>이중 audit 회피</b> (ADR #34 결정 1): 본 흐름은 {@link com.ibizdrive.permission.PermissionService#revokePermission}을
 * 호출하지 않고 {@code PermissionRepository.deleteById}를 직접 호출 → {@code PermissionRevokedEvent}는 발행되지 않음.
 * 단일 {@code share.revoked} audit row만 남고, {@code metadata.permissionId}로 grant 추적 보존.
 *
 * <p>{@code originalSharedBy}는 share row의 {@code shared_by} (원래 공유한 사람) — actor와 다를 수 있다
 * (ADMIN이 다른 사용자가 만든 share를 revoke하는 케이스).
 */
public record ShareRevokedEvent(
    UUID actorId,
    UUID shareId,
    UUID fileId,
    UUID permissionId,
    UUID originalSharedBy,
    Instant originalCreatedAt,
    Instant originalExpiresAt,
    String originalMessage
) {
    public ShareRevokedEvent {
        if (shareId == null) throw new IllegalArgumentException("shareId is required");
        if (fileId == null) throw new IllegalArgumentException("fileId is required");
        if (permissionId == null) throw new IllegalArgumentException("permissionId is required");
        if (originalSharedBy == null) throw new IllegalArgumentException("originalSharedBy is required");
    }
}
