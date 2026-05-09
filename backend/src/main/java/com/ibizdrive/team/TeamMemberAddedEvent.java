package com.ibizdrive.team;

import java.util.UUID;

/**
 * Team 멤버 추가 도메인 이벤트 — Plan A Task 17.
 *
 * <p>{@link TeamService#invite}가 신규 MEMBER 추가 시 publish.
 * 초기 OWNER membership(Task 16 create)에서는 발행하지 않음 — Task 28 listener는
 * {@link TeamCreatedEvent}만으로 "team + initial OWNER" 묶어 audit.
 *
 * <p>{@code TeamAuditListener} (Plan A Task 28)가 AFTER_COMMIT 단계에서
 * {@code TEAM_MEMBER_ADDED} audit_log 작성.
 */
public record TeamMemberAddedEvent(UUID teamId, UUID userId, UUID invitedBy) {}
