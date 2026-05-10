package com.ibizdrive.admin;

import com.ibizdrive.team.Team;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Admin team detail endpoint 응답 — T8 admin-teams.jsx TeamDetail (line 107~).
 *
 * <p>{@code GET /api/admin/teams/{id}} 가 단건 반환. 멤버 목록은 별도 endpoint
 * ({@code GET /api/teams/{id}/members}) — admin도 동일 endpoint 재사용.
 *
 * @param id            team id
 * @param name          display name
 * @param description   optional team description
 * @param color         7-char hex (#RRGGBB)
 * @param leadId        designated team lead user id
 * @param visibility    private | internal (V12 CHECK lowercase)
 * @param rootFolderId  팀 root folder UUID (Plan A1)
 * @param memberCount   팀 총 멤버 수
 * @param archived      archive 여부
 * @param archivedAt    archive 시각 (active 면 null)
 * @param archivedBy    archive 수행자 (active 면 null)
 * @param createdBy     팀 생성자
 * @param createdAt     팀 생성 시각
 * @param updatedAt     마지막 갱신 시각
 */
public record AdminTeamDetailResponse(
    UUID id,
    String name,
    String description,
    String color,
    UUID leadId,
    String visibility,
    UUID rootFolderId,
    long memberCount,
    boolean archived,
    OffsetDateTime archivedAt,
    UUID archivedBy,
    UUID createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public static AdminTeamDetailResponse from(Team t, long memberCount) {
        return new AdminTeamDetailResponse(
            t.getId(),
            t.getName(),
            t.getDescription(),
            t.getColor(),
            t.getLeadId(),
            t.getVisibility().dbValue(),
            t.getRootFolderId(),
            memberCount,
            !t.isActive(),
            t.getArchivedAt(),
            t.getArchivedBy(),
            t.getCreatedBy(),
            t.getCreatedAt(),
            t.getUpdatedAt()
        );
    }
}
