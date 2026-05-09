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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamServiceInviteTest {

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
    void invite_addsMemberRow_whenUserNotYetMember() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID inviter = UUID.randomUUID();
        TeamMembershipId id = new TeamMembershipId(teamId, userId);

        when(memRepo.findById(id)).thenReturn(Optional.empty());

        TeamMembership result = svc.invite(teamId, userId, inviter);

        ArgumentCaptor<TeamMembership> memCaptor = ArgumentCaptor.forClass(TeamMembership.class);
        verify(memRepo).save(memCaptor.capture());
        TeamMembership saved = memCaptor.getValue();
        assertThat(saved.getTeamId()).isEqualTo(teamId);
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getRole()).isEqualTo(TeamMembership.Role.MEMBER);
        assertThat(saved.getInvitedBy()).isEqualTo(inviter);
        assertThat(result).isSameAs(saved);

        ArgumentCaptor<TeamMemberAddedEvent> eventCaptor =
            ArgumentCaptor.forClass(TeamMemberAddedEvent.class);
        verify(events).publishEvent(eventCaptor.capture());
        TeamMemberAddedEvent published = eventCaptor.getValue();
        assertThat(published.teamId()).isEqualTo(teamId);
        assertThat(published.userId()).isEqualTo(userId);
        assertThat(published.invitedBy()).isEqualTo(inviter);
    }

    @Test
    void invite_isIdempotent_whenUserAlreadyMember() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID inviter = UUID.randomUUID();
        TeamMembershipId id = new TeamMembershipId(teamId, userId);

        TeamMembership existing = new TeamMembership(teamId, userId,
            TeamMembership.Role.OWNER, null, OffsetDateTime.now());
        when(memRepo.findById(id)).thenReturn(Optional.of(existing));

        TeamMembership result = svc.invite(teamId, userId, inviter);

        assertThat(result).isSameAs(existing);
        verify(memRepo, never()).save(any());
        verify(events, never()).publishEvent(any(Object.class));
    }
}
