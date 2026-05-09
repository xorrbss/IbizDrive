package com.ibizdrive.team;

import com.ibizdrive.folder.FolderMutationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamServiceRemoveTest {

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
    void remove_deletesMembershipAndPublishesEvent_whenUserIsMember() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        TeamMembershipId id = new TeamMembershipId(teamId, userId);
        TeamMembership membership = new TeamMembership(teamId, userId,
            TeamMembership.Role.MEMBER, null, OffsetDateTime.now());
        when(memRepo.findById(id)).thenReturn(Optional.of(membership));

        svc.remove(teamId, userId, actor);

        verify(memRepo).deleteById(id);

        ArgumentCaptor<TeamMemberRemovedEvent> eventCaptor =
            ArgumentCaptor.forClass(TeamMemberRemovedEvent.class);
        verify(events).publishEvent(eventCaptor.capture());
        TeamMemberRemovedEvent published = eventCaptor.getValue();
        assertThat(published.teamId()).isEqualTo(teamId);
        assertThat(published.userId()).isEqualTo(userId);
        assertThat(published.removedBy()).isEqualTo(actor);
    }

    @Test
    void remove_isNoOp_whenUserNotMember() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        TeamMembershipId id = new TeamMembershipId(teamId, userId);
        when(memRepo.findById(id)).thenReturn(Optional.empty());

        svc.remove(teamId, userId, actor);

        verify(memRepo, never()).deleteById(any(TeamMembershipId.class));
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void remove_throwsLastOwnerRequired_whenRemovingLastOwner() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        TeamMembershipId id = new TeamMembershipId(teamId, userId);
        TeamMembership membership = new TeamMembership(teamId, userId,
            TeamMembership.Role.OWNER, null, OffsetDateTime.now());
        when(memRepo.findById(id)).thenReturn(Optional.of(membership));
        when(memRepo.countByTeamIdAndRole(teamId, TeamMembership.Role.OWNER)).thenReturn(1L);

        assertThatThrownBy(() -> svc.remove(teamId, userId, actor))
            .isInstanceOf(LastOwnerRequiredException.class)
            .hasMessageContaining(teamId.toString());

        verify(memRepo, never()).deleteById(any(TeamMembershipId.class));
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void remove_succeedsWhenRemovingOwner_andOtherOwnersExist() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        TeamMembershipId id = new TeamMembershipId(teamId, userId);
        TeamMembership membership = new TeamMembership(teamId, userId,
            TeamMembership.Role.OWNER, null, OffsetDateTime.now());
        when(memRepo.findById(id)).thenReturn(Optional.of(membership));
        when(memRepo.countByTeamIdAndRole(teamId, TeamMembership.Role.OWNER)).thenReturn(2L);

        svc.remove(teamId, userId, actor);

        verify(memRepo).deleteById(id);

        ArgumentCaptor<TeamMemberRemovedEvent> eventCaptor =
            ArgumentCaptor.forClass(TeamMemberRemovedEvent.class);
        verify(events).publishEvent(eventCaptor.capture());
        TeamMemberRemovedEvent published = eventCaptor.getValue();
        assertThat(published.teamId()).isEqualTo(teamId);
        assertThat(published.userId()).isEqualTo(userId);
        assertThat(published.removedBy()).isEqualTo(actor);
    }

    @Test
    void remove_succeedsWhenRemovingMember_evenIfOnlyOneOwner() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        TeamMembershipId id = new TeamMembershipId(teamId, userId);
        TeamMembership membership = new TeamMembership(teamId, userId,
            TeamMembership.Role.MEMBER, null, OffsetDateTime.now());
        when(memRepo.findById(id)).thenReturn(Optional.of(membership));

        svc.remove(teamId, userId, actor);

        verify(memRepo).deleteById(id);
        verify(events).publishEvent(any(TeamMemberRemovedEvent.class));
    }
}
