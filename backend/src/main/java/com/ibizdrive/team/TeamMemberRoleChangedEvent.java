package com.ibizdrive.team;

import java.util.UUID;

/**
 * Team 멤버 역할 변경 도메인 이벤트 — Plan A2 Task T2.
 *
 * <p>{@link TeamService#changeRole} (Plan A2 T3, 미구현)가 멤버십 role을 갱신할 때 publish.
 * {@code TeamAuditListener}가 AFTER_COMMIT 단계에서 {@link com.ibizdrive.audit.AuditEventType#TEAM_MEMBER_ROLE_CHANGED}
 * audit_log를 작성하며, {@code beforeState = {"role": oldRole}}와
 * {@code afterState = {"role": newRole}} 두 필드를 모두 기록한다.
 *
 * <p>spec: docs/superpowers/plans/team-centric-pivot — §1.5.
 *
 * @param teamId   역할이 변경된 팀 UUID
 * @param userId   역할이 변경된 멤버 UUID
 * @param oldRole  변경 전 역할 ({@link TeamMembership.Role#OWNER} 또는 {@link TeamMembership.Role#MEMBER})
 * @param newRole  변경 후 역할
 * @param actorId  변경을 수행한 사용자 UUID
 */
public record TeamMemberRoleChangedEvent(
    UUID teamId,
    UUID userId,
    TeamMembership.Role oldRole,
    TeamMembership.Role newRole,
    UUID actorId
) {}
