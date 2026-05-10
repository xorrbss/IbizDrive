package com.ibizdrive.team;

import com.ibizdrive.folder.Folder;
import com.ibizdrive.folder.FolderMutationService;
import com.ibizdrive.folder.ScopeType;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamServiceCreateTest {

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

        when(teamRepo.findActiveByNormalizedName(any())).thenReturn(Optional.empty());
        // Default save stub: return the same instance (TeamService captures merged return — see fix at TeamService:78).
        when(teamRepo.save(any(Team.class))).thenAnswer(inv -> inv.getArgument(0));
        // Default folderService stub: return a stub Folder with a fresh id.
        when(folderService.createRootForScope(any(), any(), any(), anyString()))
            .thenAnswer(inv -> stubRootFolder(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));
    }

    @Test
    void create_persistsTeamMembershipAndRootFolder_whenNameAvailable() {
        UUID creator = UUID.randomUUID();

        Team result = svc.create("Alpha", null, Team.Visibility.PRIVATE, creator);

        // Team saved
        ArgumentCaptor<Team> teamCaptor = ArgumentCaptor.forClass(Team.class);
        verify(teamRepo).save(teamCaptor.capture());
        Team savedTeam = teamCaptor.getValue();
        assertThat(savedTeam.getName()).isEqualTo("Alpha");
        assertThat(savedTeam.getCreatedBy()).isEqualTo(creator);
        assertThat(savedTeam.getVisibility()).isEqualTo(Team.Visibility.PRIVATE);

        // Root folder created via FolderMutationService.createRootForScope
        verify(folderService).createRootForScope(
            ScopeType.TEAM, savedTeam.getId(), creator, "Alpha");

        // attachRootFolder applied (rootFolderId now non-null)
        assertThat(result.getRootFolderId()).isNotNull();

        // Owner membership saved
        ArgumentCaptor<TeamMembership> memCaptor = ArgumentCaptor.forClass(TeamMembership.class);
        verify(memRepo).save(memCaptor.capture());
        TeamMembership ownerMem = memCaptor.getValue();
        assertThat(ownerMem.getTeamId()).isEqualTo(savedTeam.getId());
        assertThat(ownerMem.getUserId()).isEqualTo(creator);
        assertThat(ownerMem.getRole()).isEqualTo(TeamMembership.Role.OWNER);

        // TeamCreatedEvent published (only event — no TeamMemberAddedEvent for initial OWNER)
        ArgumentCaptor<TeamCreatedEvent> eventCaptor = ArgumentCaptor.forClass(TeamCreatedEvent.class);
        verify(events).publishEvent(eventCaptor.capture());
        TeamCreatedEvent published = eventCaptor.getValue();
        assertThat(published.teamId()).isEqualTo(savedTeam.getId());
        assertThat(published.createdBy()).isEqualTo(creator);
        assertThat(published.name()).isEqualTo("Alpha");
    }

    @Test
    void create_throwsConflict_whenActiveTeamWithSameNormalizedNameExists() {
        UUID creator = UUID.randomUUID();
        Team existing = new Team(UUID.randomUUID(), "Beta", "beta", null,
            Team.Visibility.PRIVATE, creator, OffsetDateTime.now());
        when(teamRepo.findActiveByNormalizedName("beta")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> svc.create("Beta", null, Team.Visibility.PRIVATE, creator))
            .isInstanceOf(TeamNameConflictException.class);

        Mockito.verify(teamRepo, Mockito.never()).save(any());
        Mockito.verify(folderService, Mockito.never()).createRootForScope(any(), any(), any(), anyString());
        Mockito.verify(memRepo, Mockito.never()).save(any());
        Mockito.verify(events, Mockito.never()).publishEvent(any(Object.class));
    }

    /** Build a mock Folder for FolderMutationService stub. */
    private Folder stubRootFolder(ScopeType scopeType, UUID scopeId, UUID ownerId) {
        Folder f = Mockito.mock(Folder.class);
        when(f.getId()).thenReturn(UUID.randomUUID());
        when(f.getScopeType()).thenReturn(scopeType);
        when(f.getScopeId()).thenReturn(scopeId);
        when(f.getOwnerId()).thenReturn(ownerId);
        when(f.getParentId()).thenReturn(null);
        return f;
    }
}
