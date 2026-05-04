package com.ibizdrive.admin;

import com.ibizdrive.user.Role;

import java.util.UUID;

/**
 * Admin이 사용자의 시스템 ROLE을 변경 시 publish — admin-user-mgmt, ADR #21 후속.
 *
 * <p>{@link AdminAuditListener}가 구독해 {@code admin.role.changed} audit row INSERT 시
 * before/after metadata({@code oldRole}, {@code newRole})를 같이 기록.
 *
 * <p>{@code com.ibizdrive.permission.RoleChangedEvent}와 분리 — 후자는 dead code(현재 controller 미노출)이며
 * audit type도 {@code permission.changed}로 의미가 다르다. 본 이벤트는 admin-driven user role 변경
 * 전용으로 {@code admin.role.changed} enum에 매핑된다 (audit semantic alignment).
 *
 * @param userId   role이 변경된 user의 id (target)
 * @param actorId  변경을 수행한 관리자의 user id (audit actor)
 * @param oldRole  이전 role
 * @param newRole  새 role
 */
public record AdminRoleChangedEvent(UUID userId, UUID actorId, Role oldRole, Role newRole) {
}
