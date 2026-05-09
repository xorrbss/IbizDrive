package com.ibizdrive.team;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.audit.AuditEvent;
import com.ibizdrive.audit.AuditEventType;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.audit.AuditTargetType;
import com.ibizdrive.folder.Folder;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.folder.ScopeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamServiceCreateTest {

    private TeamRepository teamRepo;
    private TeamMembershipRepository memRepo;
    private FolderRepository folderRepo;
    private AuditService auditService;
    private ApplicationEventPublisher events;
    private TeamService svc;

    @BeforeEach
    void setUp() {
        teamRepo = Mockito.mock(TeamRepository.class);
        memRepo = Mockito.mock(TeamMembershipRepository.class);
        folderRepo = Mockito.mock(FolderRepository.class);
        auditService = Mockito.mock(AuditService.class);
        events = Mockito.mock(ApplicationEventPublisher.class);
        // ObjectMapper is real (cheap, deterministic, not worth mocking)
        svc = new TeamService(teamRepo, memRepo, folderRepo, auditService, events, new ObjectMapper());

        // Default: no name conflict
        when(teamRepo.findActiveByNormalizedName(any())).thenReturn(Optional.empty());
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

        // Root folder saved with TEAM scope
        ArgumentCaptor<Folder> folderCaptor = ArgumentCaptor.forClass(Folder.class);
        verify(folderRepo).save(folderCaptor.capture());
        Folder root = folderCaptor.getValue();
        assertThat(root.getParentId()).isNull();
        assertThat(root.getScopeType()).isEqualTo(ScopeType.TEAM);
        assertThat(root.getScopeId()).isEqualTo(savedTeam.getId());
        assertThat(root.getOwnerId()).isEqualTo(creator);

        // attachRootFolder called on team (rootFolderId now non-null + matches Folder)
        assertThat(result.getRootFolderId()).isNotNull();
        assertThat(result.getRootFolderId()).isEqualTo(root.getId());

        // Owner membership saved
        ArgumentCaptor<TeamMembership> memCaptor = ArgumentCaptor.forClass(TeamMembership.class);
        verify(memRepo).save(memCaptor.capture());
        TeamMembership ownerMem = memCaptor.getValue();
        assertThat(ownerMem.getTeamId()).isEqualTo(savedTeam.getId());
        assertThat(ownerMem.getUserId()).isEqualTo(creator);
        assertThat(ownerMem.getRole()).isEqualTo(TeamMembership.Role.OWNER);

        // Two audits emitted (TEAM_CREATED + TEAM_MEMBER_ADDED)
        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService, Mockito.times(2)).record(auditCaptor.capture());
        assertThat(auditCaptor.getAllValues()).extracting(AuditEvent::eventType)
            .containsExactly(AuditEventType.TEAM_CREATED, AuditEventType.TEAM_MEMBER_ADDED);
        assertThat(auditCaptor.getAllValues()).allSatisfy(e -> {
            assertThat(e.targetType()).isEqualTo(AuditTargetType.TEAM);
            assertThat(e.targetId()).isEqualTo(savedTeam.getId());
            assertThat(e.actorId()).isEqualTo(creator);
        });

        // Domain event published
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
            Team.Visibility.PRIVATE, creator, java.time.OffsetDateTime.now());
        when(teamRepo.findActiveByNormalizedName("beta")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> svc.create("Beta", null, Team.Visibility.PRIVATE, creator))
            .isInstanceOf(TeamNameConflictException.class);

        // No persistence side-effects on conflict
        Mockito.verify(teamRepo, Mockito.never()).save(any());
        Mockito.verify(folderRepo, Mockito.never()).save(any());
        Mockito.verify(memRepo, Mockito.never()).save(any());
        Mockito.verify(auditService, Mockito.never()).record(any());
        Mockito.verify(events, Mockito.never()).publishEvent(any(Object.class));
    }
}
