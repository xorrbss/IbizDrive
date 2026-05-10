package com.ibizdrive.team;

import java.util.UUID;

/**
 * Team 메타데이터 갱신 도메인 이벤트 — T8 admin PATCH /api/admin/teams/{id}.
 *
 * <p>{@code AdminTeamService.update}가 name/description/color/leadId 중 하나 이상을 변경할 때
 * publish. {@code TeamAuditListener}가 AFTER_COMMIT 단계에서
 * {@link com.ibizdrive.audit.AuditEventType#TEAM_UPDATED} audit_log 행을 작성한다.
 *
 * <p>{@code changedFields}는 변경된 필드의 wire name 집합 (e.g. {@code "name,description,color"}).
 * 다중 필드 변경 시 단일 audit row 안에 묶어 표현. 실제 before/after 값은 audit row 메타에 옮기지 않고
 * Team 엔티티 현재 상태 + 호출자 입력으로 재구성 가능 (KISS).
 *
 * @param teamId        대상 팀 UUID
 * @param actorId       변경 수행자 UUID
 * @param changedFields 변경된 필드의 쉼표 구분 문자열 (e.g. "name,description")
 */
public record TeamUpdatedEvent(UUID teamId, UUID actorId, String changedFields) {}
