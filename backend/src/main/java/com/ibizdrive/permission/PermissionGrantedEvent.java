package com.ibizdrive.permission;

import java.time.Instant;
import java.util.UUID;

/**
 * Resource-level 권한 grant 이벤트 — A4.4, ADR #26 close.
 *
 * <p>{@link PermissionService#grantPermission} 가 트랜잭션 경계 안에서 publish 하고,
 * {@link com.ibizdrive.audit.PermissionAuditListener} 가 audit_log 에 {@code permission.granted} 로 기록한다.
 * A2/A3 의 {@code RoleChangedEvent} 패턴을 동형 재사용 — 비즈니스 로직(grant INSERT)과 cross-cutting(감사 기록) 분리.
 *
 * <p>{@code permissionId} 는 새로 생성된 grant row 의 PK — listener 가 audit row 의 target_id 로 사용한다.
 * {@code expiresAt} 은 NULL 가능 (무기한 grant).
 *
 * <p>{@code subjectId} 는 {@code subjectType == 'everyone'} 일 때 NULL 허용 (DB CHECK 제약과 일치).
 */
public record PermissionGrantedEvent(
    UUID actorId,
    UUID permissionId,
    String resourceType,
    UUID resourceId,
    String subjectType,
    UUID subjectId,
    Preset preset,
    Instant expiresAt
) {
    public PermissionGrantedEvent {
        if (permissionId == null) throw new IllegalArgumentException("permissionId is required");
        if (resourceType == null) throw new IllegalArgumentException("resourceType is required");
        if (resourceId == null) throw new IllegalArgumentException("resourceId is required");
        if (subjectType == null) throw new IllegalArgumentException("subjectType is required");
        if (preset == null) throw new IllegalArgumentException("preset is required");
    }
}
