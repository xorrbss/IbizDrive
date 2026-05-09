package com.ibizdrive.team;

import com.ibizdrive.folder.FolderMutationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        when(memRepo.existsById(id)).thenReturn(true);

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
        when(memRepo.existsById(id)).thenReturn(false);

        svc.remove(teamId, userId, actor);

        verify(memRepo, never()).deleteById(any(TeamMembershipId.class));
        verify(events, never()).publishEvent(any(Object.class));
    }
}
