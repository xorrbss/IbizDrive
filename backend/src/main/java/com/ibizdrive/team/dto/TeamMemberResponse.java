package com.ibizdrive.team.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ibizdrive.team.TeamMembership;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * {@code GET /api/teams/{teamId}/members} response item — Plan F T1.
 *
 * <p>JPQL constructor projection이 직접 인스턴스화하므로 record 컴팩트 생성자에 검증 없음 —
 * spec §3.4 참조.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TeamMemberResponse(
    UUID userId,
    String displayName,
    String email,
    TeamMembership.Role role,
    OffsetDateTime joinedAt
) {}
