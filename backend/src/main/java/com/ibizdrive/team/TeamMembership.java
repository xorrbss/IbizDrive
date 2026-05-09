package com.ibizdrive.team;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 팀 멤버십 JPA entity — Flyway V12 {@code team_memberships} 매핑.
 *
 * <p>spec docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §1.1.
 * 한 팀에 한 user 1행 (composite PK).
 *
 * <p>DB schema가 진실의 출처 (CLAUDE.md §3 원칙 6). {@code spring.jpa.hibernate.ddl-auto: validate}로
 * 컬럼 불일치 시 부팅 실패.
 *
 * <p><b>role 저장 전략</b>:
 * V12 CHECK 제약은 uppercase 문자열({@code 'OWNER'}, {@code 'MEMBER'})만 허용한다.
 * enum 상수명이 그대로 DB 값과 일치하므로 {@link Enumerated}({@link EnumType#STRING})를 직접 사용
 * — Team.visibility의 lowercase 변환 패턴과 달리 raw String 컬럼이 필요 없다.
 *
 * <p><b>관계 매핑 정책</b>: {@code invitedBy}는 단순 {@code UUID} 컬럼 (nullable). DB 레벨 FK는
 * V12에서 강제됨. team_id/user_id는 {@link TeamMembershipId} composite key가 보유.
 */
@Entity
@Table(name = "team_memberships")
public class TeamMembership {

    /** V12 role CHECK가 허용하는 두 값. enum constant name이 곧 DB 값. */
    public enum Role {
        OWNER,
        MEMBER
    }

    @EmbeddedId
    private TeamMembershipId id;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private OffsetDateTime joinedAt;

    @Column(name = "invited_by", updatable = false)
    private UUID invitedBy;

    protected TeamMembership() {
        // JPA
    }

    /**
     * 신규 멤버십 생성. service layer가 호출 — 모든 식별자/시간은 호출자가 결정한다.
     *
     * @throws IllegalArgumentException teamId/userId/role/now 중 하나라도 null
     */
    public TeamMembership(UUID teamId, UUID userId, Role role, UUID invitedBy, OffsetDateTime now) {
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }
        // TeamMembershipId 생성자가 teamId/userId null 검증 담당.
        this.id = new TeamMembershipId(teamId, userId);
        this.role = role;
        this.invitedBy = invitedBy;
        this.joinedAt = now;
    }

    public TeamMembershipId getId() {
        return id;
    }

    /** composite key의 teamId 위임 접근자 — service/repository 사용 편의. */
    public UUID getTeamId() {
        return id.getTeamId();
    }

    /** composite key의 userId 위임 접근자 — service/repository 사용 편의. */
    public UUID getUserId() {
        return id.getUserId();
    }

    public Role getRole() {
        return role;
    }

    public OffsetDateTime getJoinedAt() {
        return joinedAt;
    }

    public UUID getInvitedBy() {
        return invitedBy;
    }

    /**
     * role 변경. 도메인 메서드는 입력 검증만 담당 — last-OWNER 가드는 service layer (Plan A2).
     *
     * @throws IllegalArgumentException newRole이 null
     */
    public void changeRole(Role newRole) {
        if (newRole == null) {
            throw new IllegalArgumentException("role must not be null");
        }
        this.role = newRole;
    }
}
