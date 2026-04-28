package com.ibizdrive.permission;

import com.ibizdrive.user.Role;

import java.util.UUID;

/**
 * 사용자 ROLE 변경 이벤트 — A3.4, ADR #24·#26.
 *
 * <p>{@link PermissionService#changeRole}가 트랜잭션 커밋 직전에 publish하고,
 * {@link com.ibizdrive.audit.PermissionAuditListener}가 audit_log에 {@code permission.changed}로 기록한다.
 * A2의 {@code AuthAuditListener} 패턴을 재사용 — 비즈니스 로직(권한 변경)과 cross-cutting(감사 기록) 분리.
 *
 * <p>{@code actorId}는 변경을 수행한 관리자 (자기 자신 변경 시 target과 동일 가능). 시스템 자동 변경은 null.
 * MVP는 ROLE 단위 매트릭스이므로 {@code from}/{@code to} 모두 {@link Role}.
 */
public record RoleChangedEvent(
    UUID actorId,
    UUID targetUserId,
    Role from,
    Role to
) {
    public RoleChangedEvent {
        if (targetUserId == null) throw new IllegalArgumentException("targetUserId is required");
        if (from == null) throw new IllegalArgumentException("from is required");
        if (to == null) throw new IllegalArgumentException("to is required");
    }
}
