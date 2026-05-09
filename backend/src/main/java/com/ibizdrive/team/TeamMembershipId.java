package com.ibizdrive.team;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * {@link TeamMembership} composite primary key — Flyway V12의
 * {@code PRIMARY KEY (team_id, user_id)} 매핑.
 *
 * <p>spec docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §1.1.
 *
 * <p>JPA composite key 요구사항:
 * <ul>
 *   <li>{@link Embeddable} + {@link Serializable}</li>
 *   <li>protected no-arg 생성자 (JPA 리플렉션용)</li>
 *   <li>value-based {@code equals}/{@code hashCode} — Hibernate가 PK 조회/캐시 키로 사용</li>
 * </ul>
 */
@Embeddable
public class TeamMembershipId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "team_id", nullable = false, updatable = false)
    private UUID teamId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    protected TeamMembershipId() {
        // JPA
    }

    public TeamMembershipId(UUID teamId, UUID userId) {
        if (teamId == null) {
            throw new IllegalArgumentException("teamId must not be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        this.teamId = teamId;
        this.userId = userId;
    }

    public UUID getTeamId() {
        return teamId;
    }

    public UUID getUserId() {
        return userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TeamMembershipId other)) {
            return false;
        }
        return Objects.equals(teamId, other.teamId) && Objects.equals(userId, other.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(teamId, userId);
    }
}
