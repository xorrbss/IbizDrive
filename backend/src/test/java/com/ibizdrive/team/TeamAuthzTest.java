package com.ibizdrive.team;

import com.ibizdrive.user.IbizDriveUserDetails;
import com.ibizdrive.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class TeamAuthzTest {

    private TeamMembershipRepository memRepo;
    private TeamAuthz authz;

    @BeforeEach
    void setUp() {
        memRepo = Mockito.mock(TeamMembershipRepository.class);
        authz = new TeamAuthz(memRepo);
    }

    private IbizDriveUserDetails principal(UUID userId) {
        User u = Mockito.mock(User.class);
        when(u.getId()).thenReturn(userId);
        IbizDriveUserDetails uds = Mockito.mock(IbizDriveUserDetails.class);
        when(uds.getUser()).thenReturn(u);
        return uds;
    }

    @Test
    void isMember_trueWhenMembershipExists() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TeamMembership m = Mockito.mock(TeamMembership.class);
        when(memRepo.findById(new TeamMembershipId(teamId, userId))).thenReturn(Optional.of(m));

        assertThat(authz.isMember(teamId, principal(userId))).isTrue();
    }

    @Test
    void isMember_falseWhenNoMembership() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(memRepo.findById(new TeamMembershipId(teamId, userId))).thenReturn(Optional.empty());

        assertThat(authz.isMember(teamId, principal(userId))).isFalse();
    }

    @Test
    void isMember_falseWhenPrincipalNotIbizDriveUserDetails() {
        assertThat(authz.isMember(UUID.randomUUID(), "anonymous")).isFalse();
    }
}
