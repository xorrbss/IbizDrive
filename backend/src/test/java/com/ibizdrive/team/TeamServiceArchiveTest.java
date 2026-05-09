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

class TeamServiceArchiveTest {

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
    void archive_marksTeamArchivedAndPublishesEvent_whenActive() {
        UUID teamId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        Team team = new Team(teamId, "Engineering", "engineering", null,
            Team.Visibility.PRIVATE, creatorId, OffsetDateTime.now());
        when(teamRepo.findById(teamId)).thenReturn(Optional.of(team));

        Team result = svc.archive(teamId, actorId);

        // Team should now be archived
        assertThat(result.isActive()).isFalse();
        assertThat(result.getArchivedBy()).isEqualTo(actorId);
        assertThat(result.getArchivedAt()).isNotNull();

        // save called once
        verify(teamRepo).save(team);

        // Correct event published with correct fields
        ArgumentCaptor<TeamArchivedEvent> eventCaptor = ArgumentCaptor.forClass(TeamArchivedEvent.class);
        verify(events).publishEvent(eventCaptor.capture());
        TeamArchivedEvent published = eventCaptor.getValue();
        assertThat(published.teamId()).isEqualTo(teamId);
        assertThat(published.actorId()).isEqualTo(actorId);
    }

    @Test
    void archive_isNoOp_whenAlreadyArchived() {
        UUID teamId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        Team team = new Team(teamId, "Engineering", "engineering", null,
            Team.Visibility.PRIVATE, creatorId, OffsetDateTime.now());
        team.archive(actorId, OffsetDateTime.now());
        when(teamRepo.findById(teamId)).thenReturn(Optional.of(team));

        svc.archive(teamId, actorId);

        // save and publishEvent must NOT be called — idempotent no-op
        verify(teamRepo, never()).save(any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void archive_throwsResourceNotFound_whenTeamMissing() {
        UUID teamId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        when(teamRepo.findById(teamId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> svc.archive(teamId, actorId))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining(teamId.toString());

        verify(teamRepo, never()).save(any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void archive_throwsIllegalArgument_whenTeamIdNull() {
        UUID actorId = UUID.randomUUID();

        assertThatThrownBy(() -> svc.archive(null, actorId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("teamId");

        verify(teamRepo, never()).findById(any());
        verify(teamRepo, never()).save(any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void archive_throwsIllegalArgument_whenActorIdNull() {
        UUID teamId = UUID.randomUUID();

        assertThatThrownBy(() -> svc.archive(teamId, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("actorId");

        verify(teamRepo, never()).findById(any());
        verify(teamRepo, never()).save(any());
        verify(events, never()).publishEvent(any(Object.class));
    }
}
