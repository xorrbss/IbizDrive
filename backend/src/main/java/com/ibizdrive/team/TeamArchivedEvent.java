package com.ibizdrive.team;

import java.util.UUID;

/**
 * Team 보관(archive) 도메인 이벤트.
 *
 * <p>{@link TeamService#archive} (Plan A2 T7)가 팀을 보관 상태로 전환할 때 publish.
 * {@code TeamAuditListener}가 AFTER_COMMIT 단계에서
 * {@link com.ibizdrive.audit.AuditEventType#TEAM_ARCHIVED} audit_log 행을 작성한다.
 * before/afterState 모두 null — archivedAt 타임스탬프는 Team 엔티티에 보존되므로
 * audit row에 별도 state 기록이 불필요하다.
 *
 * <p>spec: docs/superpowers/plans/team-centric-pivot — §1.5 + §2.2.
 *
 * @param teamId  보관된 팀 UUID
 * @param actorId 보관을 수행한 사용자 UUID
 */
public record TeamArchivedEvent(UUID teamId, UUID actorId) {}
