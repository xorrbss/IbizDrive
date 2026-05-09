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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamServiceRestoreTest {

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
    void restore_clearsArchivedAndPublishesEvent_whenArchived() {
        UUID teamId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        Team team = new Team(teamId, "Engineering", "engineering", null,
            Team.Visibility.PRIVATE, creatorId, OffsetDateTime.now());
        team.archive(creatorId, OffsetDateTime.now());
        when(teamRepo.findById(teamId)).thenReturn(Optional.of(team));
        when(teamRepo.findActiveByNormalizedName("engineering")).thenReturn(Optional.empty());

        Team result = svc.restore(teamId, actorId);

        // Team should now be active again
        assertThat(result.isActive()).isTrue();
        assertThat(result.getArchivedAt()).isNull();
        assertThat(result.getArchivedBy()).isNull();

        // save called once
        verify(teamRepo).save(team);

        // Correct event published with correct fields
        ArgumentCaptor<TeamRestoredEvent> eventCaptor = ArgumentCaptor.forClass(TeamRestoredEvent.class);
        verify(events).publishEvent(eventCaptor.capture());
        TeamRestoredEvent published = eventCaptor.getValue();
        assertThat(published.teamId()).isEqualTo(teamId);
        assertThat(published.actorId()).isEqualTo(actorId);
    }

    @Test
    void restore_isNoOp_whenAlreadyActive() {
        UUID teamId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        Team team = new Team(teamId, "Engineering", "engineering", null,
            Team.Visibility.PRIVATE, creatorId, OffsetDateTime.now());
        // team is already active (not archived)
        when(teamRepo.findById(teamId)).thenReturn(Optional.of(team));

        svc.restore(teamId, actorId);

        // save and publishEvent must NOT be called — idempotent no-op
        verify(teamRepo, never()).save(any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void restore_throwsTeamNameConflict_whenActiveDuplicateExists() {
        UUID teamId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        Team team = new Team(teamId, "Engineering", "engineering", null,
            Team.Visibility.PRIVATE, creatorId, OffsetDateTime.now());
        team.archive(creatorId, OffsetDateTime.now());

        Team otherTeam = new Team(UUID.randomUUID(), "Engineering", "engineering", null,
            Team.Visibility.PRIVATE, creatorId, OffsetDateTime.now());

        when(teamRepo.findById(teamId)).thenReturn(Optional.of(team));
        when(teamRepo.findActiveByNormalizedName("engineering")).thenReturn(Optional.of(otherTeam));

        assertThatThrownBy(() -> svc.restore(teamId, actorId))
            .isInstanceOf(TeamNameConflictException.class);

        // conflict path: no save, no event
        verify(teamRepo, never()).save(any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void restore_throwsResourceNotFound_whenTeamMissing() {
        UUID teamId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        when(teamRepo.findById(teamId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> svc.restore(teamId, actorId))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining(teamId.toString());

        verify(teamRepo, never()).save(any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void restore_throwsIllegalArgument_whenTeamIdNull() {
        UUID actorId = UUID.randomUUID();

        assertThatThrownBy(() -> svc.restore(null, actorId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("teamId");

        verify(teamRepo, never()).findById(any());
        verify(teamRepo, never()).save(any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void restore_throwsIllegalArgument_whenActorIdNull() {
        UUID teamId = UUID.randomUUID();

        assertThatThrownBy(() -> svc.restore(teamId, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("actorId");

        verify(teamRepo, never()).findById(any());
        verify(teamRepo, never()).save(any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void restore_succeeds_whenNoActiveDuplicate() {
        UUID teamId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        Team team = new Team(teamId, "Engineering", "engineering", null,
            Team.Visibility.PRIVATE, creatorId, OffsetDateTime.now());
        team.archive(creatorId, OffsetDateTime.now());
        when(teamRepo.findById(teamId)).thenReturn(Optional.of(team));
        // Explicitly return empty — no active duplicate
        when(teamRepo.findActiveByNormalizedName("engineering")).thenReturn(Optional.empty());

        Team result = svc.restore(teamId, actorId);

        // findActiveByNormalizedName was called
        verify(teamRepo).findActiveByNormalizedName("engineering");

        // restore proceeds: save + event
        verify(teamRepo).save(team);
        verify(events).publishEvent(any(TeamRestoredEvent.class));

        assertThat(result.isActive()).isTrue();
    }
}
