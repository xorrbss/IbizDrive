package com.ibizdrive.team;

import java.util.UUID;

/**
 * Team 복원(restore) 도메인 이벤트.
 *
 * <p>{@link TeamService#restore} (Plan A2 T7)가 보관된 팀을 활성 상태로 복원할 때 publish.
 * {@code TeamAuditListener}가 AFTER_COMMIT 단계에서
 * {@link com.ibizdrive.audit.AuditEventType#TEAM_RESTORED} audit_log 행을 작성한다.
 * before/afterState 모두 null — 복원 시각은 Team 엔티티의 archivedAt 필드 클리어로 표현되므로
 * audit row에 별도 state 기록이 불필요하다.
 *
 * <p>spec: docs/superpowers/plans/team-centric-pivot — §1.5 + §2.2.
 *
 * @param teamId  복원된 팀 UUID
 * @param actorId 복원을 수행한 사용자 UUID
 */
public record TeamRestoredEvent(UUID teamId, UUID actorId) {}
