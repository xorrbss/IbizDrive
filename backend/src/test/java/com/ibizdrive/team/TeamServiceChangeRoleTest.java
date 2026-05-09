package com.ibizdrive.team;

import com.ibizdrive.common.error.ResourceNotFoundException;
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

class TeamServiceChangeRoleTest {

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
    void changeRole_demotesOwnerAndPublishesEvent_whenAnotherOwnerExists() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        TeamMembershipId id = new TeamMembershipId(teamId, userId);
        TeamMembership membership = new TeamMembership(teamId, userId,
            TeamMembership.Role.OWNER, null, OffsetDateTime.now());
        when(memRepo.findById(id)).thenReturn(Optional.of(membership));
        when(memRepo.countByTeamIdAndRole(teamId, TeamMembership.Role.OWNER)).thenReturn(2L);

        TeamMembership result = svc.changeRole(teamId, userId, TeamMembership.Role.MEMBER, actor);

        assertThat(result.getRole()).isEqualTo(TeamMembership.Role.MEMBER);
        verify(memRepo).save(membership);

        ArgumentCaptor<TeamMemberRoleChangedEvent> eventCaptor =
            ArgumentCaptor.forClass(TeamMemberRoleChangedEvent.class);
        verify(events).publishEvent(eventCaptor.capture());
        TeamMemberRoleChangedEvent published = eventCaptor.getValue();
        assertThat(published.teamId()).isEqualTo(teamId);
        assertThat(published.userId()).isEqualTo(userId);
        assertThat(published.oldRole()).isEqualTo(TeamMembership.Role.OWNER);
        assertThat(published.newRole()).isEqualTo(TeamMembership.Role.MEMBER);
        assertThat(published.actorId()).isEqualTo(actor);
    }

    @Test
    void changeRole_isNoOp_whenRoleUnchanged() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        TeamMembershipId id = new TeamMembershipId(teamId, userId);
        TeamMembership membership = new TeamMembership(teamId, userId,
            TeamMembership.Role.MEMBER, null, OffsetDateTime.now());
        when(memRepo.findById(id)).thenReturn(Optional.of(membership));

        TeamMembership result = svc.changeRole(teamId, userId, TeamMembership.Role.MEMBER, actor);

        assertThat(result).isSameAs(membership);
        verify(memRepo, never()).save(any(TeamMembership.class));
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void changeRole_throwsResourceNotFound_whenMembershipMissing() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        TeamMembershipId id = new TeamMembershipId(teamId, userId);
        when(memRepo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            svc.changeRole(teamId, userId, TeamMembership.Role.MEMBER, actor))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("team=" + teamId)
            .hasMessageContaining("user=" + userId);

        verify(memRepo, never()).save(any(TeamMembership.class));
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void changeRole_throwsLastOwnerRequired_whenDemotingLastOwner() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        TeamMembershipId id = new TeamMembershipId(teamId, userId);
        TeamMembership membership = new TeamMembership(teamId, userId,
            TeamMembership.Role.OWNER, null, OffsetDateTime.now());
        when(memRepo.findById(id)).thenReturn(Optional.of(membership));
        when(memRepo.countByTeamIdAndRole(teamId, TeamMembership.Role.OWNER)).thenReturn(1L);

        assertThatThrownBy(() ->
            svc.changeRole(teamId, userId, TeamMembership.Role.MEMBER, actor))
            .isInstanceOf(LastOwnerRequiredException.class)
            .hasMessageContaining(teamId.toString());

        verify(memRepo, never()).save(any(TeamMembership.class));
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void changeRole_promotesMemberToOwnerAndPublishesEvent() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        TeamMembershipId id = new TeamMembershipId(teamId, userId);
        TeamMembership membership = new TeamMembership(teamId, userId,
            TeamMembership.Role.MEMBER, null, OffsetDateTime.now());
        when(memRepo.findById(id)).thenReturn(Optional.of(membership));

        TeamMembership result = svc.changeRole(teamId, userId, TeamMembership.Role.OWNER, actor);

        assertThat(result.getRole()).isEqualTo(TeamMembership.Role.OWNER);
        verify(memRepo).save(membership);

        ArgumentCaptor<TeamMemberRoleChangedEvent> eventCaptor =
            ArgumentCaptor.forClass(TeamMemberRoleChangedEvent.class);
        verify(events).publishEvent(eventCaptor.capture());
        TeamMemberRoleChangedEvent published = eventCaptor.getValue();
        assertThat(published.teamId()).isEqualTo(teamId);
        assertThat(published.userId()).isEqualTo(userId);
        assertThat(published.oldRole()).isEqualTo(TeamMembership.Role.MEMBER);
        assertThat(published.newRole()).isEqualTo(TeamMembership.Role.OWNER);
        assertThat(published.actorId()).isEqualTo(actor);
    }
}
