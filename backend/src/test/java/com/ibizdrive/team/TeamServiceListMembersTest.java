package com.ibizdrive.team;

import com.ibizdrive.folder.FolderMutationService;
import com.ibizdrive.team.dto.TeamMemberResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class TeamServiceListMembersTest {

    private TeamRepository teamRepo;
    private TeamMembershipRepository memRepo;
    private FolderMutationService folderService;
    private ApplicationEventPublisher events;
    private TeamService svc;

    @BeforeEach
    void setUp() {
        teamRepo = Mockito.mock(TeamRepository.class);
        memRepo = Mockito.mock(TeamMembershipRepository.class);
        folderService = Mockito.mock(FolderMutationService.class);
        events = Mockito.mock(ApplicationEventPublisher.class);
        svc = new TeamService(teamRepo, memRepo, folderService, events);
    }

    @Test
    void listMembers_delegatesToRepository() {
        UUID teamId = UUID.randomUUID();
        TeamMemberResponse r1 = new TeamMemberResponse(UUID.randomUUID(), "Alice", "a@x.io",
            TeamMembership.Role.OWNER, OffsetDateTime.now());
        when(memRepo.findMembersWithUser(eq(teamId))).thenReturn(List.of(r1));

        List<TeamMemberResponse> result = svc.listMembers(teamId);

        assertThat(result).containsExactly(r1);
        Mockito.verify(memRepo).findMembersWithUser(teamId);
        Mockito.verifyNoMoreInteractions(memRepo, teamRepo, folderService, events);
    }

    @Test
    void listMembers_nullTeamId_throws() {
        assertThatThrownBy(() -> svc.listMembers(null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
