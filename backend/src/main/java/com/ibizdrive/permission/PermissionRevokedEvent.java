package com.ibizdrive.permission;

import java.time.Instant;
import java.util.UUID;

/**
 * Resource-level 권한 revoke 이벤트 — A4.4, ADR #26 close.
 *
 * <p>{@link PermissionService#revokePermission} 이 DELETE 직전에 row snapshot 을 캡처해 publish 하고,
 * {@link com.ibizdrive.audit.PermissionAuditListener} 가 audit_log 에 {@code permission.revoked} 로 기록한다.
 *
 * <p>snapshot 은 listener 가 {@code before_state} JSON 본문을 만드는 데 사용 — DELETE 후 row 가 사라지므로
 * 이벤트가 grant 시점의 모든 컬럼을 보유해야 한다 ({@link PermissionGrantedEvent} 와 동일 shape + permissionId).
 */
public record PermissionRevokedEvent(
    UUID actorId,
    UUID permissionId,
    String resourceType,
    UUID resourceId,
    String subjectType,
    UUID subjectId,
    Preset preset,
    Instant expiresAt
) {
    public PermissionRevokedEvent {
        if (permissionId == null) throw new IllegalArgumentException("permissionId is required");
        if (resourceType == null) throw new IllegalArgumentException("resourceType is required");
        if (resourceId == null) throw new IllegalArgumentException("resourceId is required");
        if (subjectType == null) throw new IllegalArgumentException("subjectType is required");
        if (preset == null) throw new IllegalArgumentException("preset is required");
    }
}
