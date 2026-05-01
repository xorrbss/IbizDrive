package com.ibizdrive.permission;

import java.time.Instant;
import java.util.UUID;

/**
 * Permission 자동 만료 이벤트 — {@code permissions-expired-cron} (docs/04 §13).
 *
 * <p>{@link PermissionService#expirePermission}이 {@code permissions.expires_at <= NOW()} row를
 * DELETE 직전에 publish. {@link com.ibizdrive.audit.PermissionAuditListener}가
 * {@code permission.expired} audit row INSERT — {@link PermissionRevokedEvent}({@code permission.revoked})와
 * 의미론 분리 (사용자/관리자 의도 vs 시스템 트리거).
 *
 * <p><b>actor 부재</b>: 시스템 트리거이므로 {@code actorId} 필드 자체를 두지 않음 (audit row의
 * {@code actor_id=NULL}로 매핑). {@link PermissionRevokedEvent}와 다르게 본 record는 actor 인자가 없다.
 *
 * <p>DELETE 의 특성상 row 가 사라지므로 listener 가 {@code before_state} JSON 본문을 만들 수 있도록
 * grant 시점의 모든 컬럼 (resource/subject/preset/expires_at/granted_by/created_at) 을 보유한다.
 */
public record PermissionExpiredEvent(
    UUID permissionId,
    String resourceType,
    UUID resourceId,
    String subjectType,
    UUID subjectId,
    Preset preset,
    UUID originalGrantedBy,
    Instant originalCreatedAt,
    Instant originalExpiresAt
) {
    public PermissionExpiredEvent {
        if (permissionId == null) throw new IllegalArgumentException("permissionId is required");
        if (resourceType == null) throw new IllegalArgumentException("resourceType is required");
        if (resourceId == null) throw new IllegalArgumentException("resourceId is required");
        if (subjectType == null) throw new IllegalArgumentException("subjectType is required");
        if (preset == null) throw new IllegalArgumentException("preset is required");
        if (originalGrantedBy == null) throw new IllegalArgumentException("originalGrantedBy is required");
        if (originalCreatedAt == null) throw new IllegalArgumentException("originalCreatedAt is required");
        if (originalExpiresAt == null) throw new IllegalArgumentException("originalExpiresAt is required");
    }
}
