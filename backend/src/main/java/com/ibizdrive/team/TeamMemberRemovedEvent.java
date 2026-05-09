package com.ibizdrive.team;

import java.util.UUID;

/**
 * Team 멤버 제거 도메인 이벤트 — Plan A Task 18.
 *
 * <p>{@link TeamService#remove}가 멤버십 행 삭제 시 publish.
 * 멤버가 아닌 경우(no-op) 발행하지 않음.
 *
 * <p>{@code TeamAuditListener} (Plan A Task 28)가 AFTER_COMMIT 단계에서
 * {@code TEAM_MEMBER_REMOVED} audit_log 작성.
 *
 * <p>YAGNI: last-OWNER guard / role transfer는 Plan A2.
 */
public record TeamMemberRemovedEvent(UUID teamId, UUID userId, UUID removedBy) {}
