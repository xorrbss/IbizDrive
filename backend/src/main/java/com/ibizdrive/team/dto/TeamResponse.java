package com.ibizdrive.team.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ibizdrive.team.Team;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Team API response — Plan A Task 19.
 *
 * <p>{@code archivedAt}/{@code description}는 nullable; null인 경우 JSON에서 omit
 * (peer DTO {@code WorkspaceMeResponse} 패턴).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TeamResponse(
    UUID id,
    String name,
    String description,
    Team.Visibility visibility,
    UUID rootFolderId,
    OffsetDateTime createdAt,
    OffsetDateTime archivedAt
) {
    public static TeamResponse of(Team t) {
        return new TeamResponse(
            t.getId(), t.getName(), t.getDescription(),
            t.getVisibility(), t.getRootFolderId(),
            t.getCreatedAt(), t.getArchivedAt());
    }
}
