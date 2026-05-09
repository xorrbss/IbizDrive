package com.ibizdrive.team;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TeamMembership} 도메인 메서드 + composite key 단위 테스트 — Plan A Task 6.
 *
 * <p>입력 검증과 상태 전이만 다룬다. last-OWNER 가드/audit emit은 service layer 책임 (Plan A2).
 */
class TeamMembershipTest {

    @Test
    void roleSwitch() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TeamMembership membership = new TeamMembership(
            teamId, userId, TeamMembership.Role.MEMBER, null, OffsetDateTime.now()
        );

        assertThat(membership.getRole()).isEqualTo(TeamMembership.Role.MEMBER);

        membership.changeRole(TeamMembership.Role.OWNER);
        assertThat(membership.getRole()).isEqualTo(TeamMembership.Role.OWNER);
    }

    @Test
    void compositeIdEqualityIsValueBased() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        TeamMembershipId a = new TeamMembershipId(teamId, userId);
        TeamMembershipId b = new TeamMembershipId(teamId, userId);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        // sanity: 다른 user면 다른 키
        assertThat(a).isNotEqualTo(new TeamMembershipId(teamId, UUID.randomUUID()));
    }
}
