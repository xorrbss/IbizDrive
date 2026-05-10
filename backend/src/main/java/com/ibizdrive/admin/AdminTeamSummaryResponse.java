package com.ibizdrive.admin;

import com.ibizdrive.team.Team;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Admin team list endpoint 응답 — T8 admin-teams.jsx TeamsListPanel.
 *
 * <p>{@code GET /api/admin/teams} 가 {@code List<AdminTeamSummaryResponse>}를 반환.
 * {@code memberCount} 는 admin layer에서 별도 query로 계산.
 *
 * @param id           team id
 * @param name         display name
 * @param description  optional team description (디자인 "한 줄로 설명")
 * @param color        7-char hex (#RRGGBB)
 * @param leadId       designated team lead user id
 * @param memberCount  팀 총 멤버 수
 * @param archived     archive 여부 ({@code archivedAt != null})
 * @param createdAt    팀 생성 시각
 */
public record AdminTeamSummaryResponse(
    UUID id,
    String name,
    String description,
    String color,
    UUID leadId,
    long memberCount,
    boolean archived,
    OffsetDateTime createdAt
) {
    public static AdminTeamSummaryResponse from(Team t, long memberCount) {
        return new AdminTeamSummaryResponse(
            t.getId(),
            t.getName(),
            t.getDescription(),
            t.getColor(),
            t.getLeadId(),
            memberCount,
            !t.isActive(),
            t.getCreatedAt()
        );
    }
}
