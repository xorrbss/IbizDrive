package com.ibizdrive.admin;

import com.ibizdrive.common.error.ResourceNotFoundException;
import com.ibizdrive.team.Team;
import com.ibizdrive.team.TeamMembership;
import com.ibizdrive.team.TeamMembershipId;
import com.ibizdrive.team.TeamMembershipRepository;
import com.ibizdrive.team.TeamNameConflictException;
import com.ibizdrive.team.TeamRepository;
import com.ibizdrive.team.TeamService;
import com.ibizdrive.team.TeamUpdatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AdminTeamService} 단위 테스트 — T8 admin-teams (design-refresh-admin).
 */
class AdminTeamServiceTest {

    private static final UUID ACTOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TEAM_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OWNER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID OTHER_USER = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private TeamRepository teamRepo;
    private TeamMembershipRepository memRepo;
    private TeamService teamSvc;
    private ApplicationEventPublisher events;
    private AdminTeamService service;

    @BeforeEach
    void setUp() {
        teamRepo = mock(TeamRepository.class);
        memRepo = mock(TeamMembershipRepository.class);
        teamSvc = mock(TeamService.class);
        events = mock(ApplicationEventPublisher.class);
        service = new AdminTeamService(teamRepo, memRepo, teamSvc, events);
    }

    private Team newTeam() {
        return new Team(
            TEAM_ID, "Engineering", "engineering", null,
            "#5B7FCC", OWNER_ID,
            Team.Visibility.PRIVATE, OWNER_ID, OffsetDateTime.now()
        );
    }

    // ============================================================
    // list
    // ============================================================

    @Test
    void list_returnsAllTeams_withMemberCounts() {
        Team t1 = newTeam();
        Team t2 = new Team(
            UUID.randomUUID(), "Design", "design", "디자인 팀",
            "#C16A8B", OWNER_ID,
            Team.Visibility.INTERNAL, OWNER_ID, OffsetDateTime.now().minusDays(3)
        );
        when(teamRepo.findAll(any(Sort.class))).thenReturn(List.of(t1, t2));
        when(memRepo.countByTeamId(t1.getId())).thenReturn(5L);
        when(memRepo.countByTeamId(t2.getId())).thenReturn(8L);

        List<AdminTeamSummaryResponse> result = service.list();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(t1.getId());
        assertThat(result.get(0).memberCount()).isEqualTo(5);
        assertThat(result.get(0).color()).isEqualTo("#5B7FCC");
        assertThat(result.get(1).id()).isEqualTo(t2.getId());
        assertThat(result.get(1).memberCount()).isEqualTo(8);
        assertThat(result.get(1).color()).isEqualTo("#C16A8B");
    }

    // ============================================================
    // detail
    // ============================================================

    @Test
    void detail_returnsTeamDetail() {
        Team t = newTeam();
        when(teamRepo.findById(TEAM_ID)).thenReturn(Optional.of(t));
        when(memRepo.countByTeamId(TEAM_ID)).thenReturn(5L);

        AdminTeamDetailResponse result = service.detail(TEAM_ID);

        assertThat(result.id()).isEqualTo(TEAM_ID);
        assertThat(result.name()).isEqualTo("Engineering");
        assertThat(result.color()).isEqualTo("#5B7FCC");
        assertThat(result.leadId()).isEqualTo(OWNER_ID);
        assertThat(result.memberCount()).isEqualTo(5);
        assertThat(result.archived()).isFalse();
    }

    @Test
    void detail_throwsOnMissingTeam() {
        when(teamRepo.findById(TEAM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(TEAM_ID))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    // ============================================================
    // update — name
    // ============================================================

    @Test
    void update_renameOnly_publishesEventWithNameField() {
        Team t = newTeam();
        when(teamRepo.findById(TEAM_ID)).thenReturn(Optional.of(t));
        when(teamRepo.findActiveByNormalizedName("backend")).thenReturn(Optional.empty());
        when(memRepo.countByTeamId(TEAM_ID)).thenReturn(3L);

        AdminTeamDetailResponse result = service.update(TEAM_ID, "Backend", null, null, null, ACTOR);

        assertThat(t.getName()).isEqualTo("Backend");
        assertThat(result.name()).isEqualTo("Backend");

        ArgumentCaptor<TeamUpdatedEvent> captor = ArgumentCaptor.forClass(TeamUpdatedEvent.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().changedFields()).isEqualTo("name");
        assertThat(captor.getValue().actorId()).isEqualTo(ACTOR);
    }

    @Test
    void update_renameToConflictingActiveName_throws() {
        Team t = newTeam();
        Team other = new Team(
            UUID.randomUUID(), "Backend", "backend", null,
            "#5B7FCC", OWNER_ID,
            Team.Visibility.PRIVATE, OWNER_ID, OffsetDateTime.now()
        );
        when(teamRepo.findById(TEAM_ID)).thenReturn(Optional.of(t));
        when(teamRepo.findActiveByNormalizedName("backend")).thenReturn(Optional.of(other));

        assertThatThrownBy(() ->
            service.update(TEAM_ID, "Backend", null, null, null, ACTOR)
        ).isInstanceOf(TeamNameConflictException.class);
        verify(events, never()).publishEvent(any(TeamUpdatedEvent.class));
    }

    // ============================================================
    // update — description / color / leadId
    // ============================================================

    @Test
    void update_descriptionBlank_normalizesToNull() {
        Team t = newTeam();
        t.updateDescription("기존 설명");
        when(teamRepo.findById(TEAM_ID)).thenReturn(Optional.of(t));
        when(memRepo.countByTeamId(TEAM_ID)).thenReturn(0L);

        service.update(TEAM_ID, null, "  ", null, null, ACTOR);

        assertThat(t.getDescription()).isNull();
        ArgumentCaptor<TeamUpdatedEvent> captor = ArgumentCaptor.forClass(TeamUpdatedEvent.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().changedFields()).isEqualTo("description");
    }

    @Test
    void update_colorChange_validHex() {
        Team t = newTeam();
        when(teamRepo.findById(TEAM_ID)).thenReturn(Optional.of(t));
        when(memRepo.countByTeamId(TEAM_ID)).thenReturn(0L);

        service.update(TEAM_ID, null, null, "#C16A8B", null, ACTOR);

        assertThat(t.getColor()).isEqualTo("#C16A8B");
        verify(events).publishEvent(any(TeamUpdatedEvent.class));
    }

    @Test
    void update_colorChange_invalidHex_throws() {
        Team t = newTeam();
        when(teamRepo.findById(TEAM_ID)).thenReturn(Optional.of(t));

        assertThatThrownBy(() ->
            service.update(TEAM_ID, null, null, "blue", null, ACTOR)
        ).isInstanceOf(IllegalArgumentException.class);
        verify(events, never()).publishEvent(any(TeamUpdatedEvent.class));
    }

    @Test
    void update_leadId_mustBeTeamMember() {
        Team t = newTeam();
        when(teamRepo.findById(TEAM_ID)).thenReturn(Optional.of(t));
        when(memRepo.findById(new TeamMembershipId(TEAM_ID, OTHER_USER)))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            service.update(TEAM_ID, null, null, null, OTHER_USER, ACTOR)
        ).isInstanceOf(AdminBadPatchException.class);
        verify(events, never()).publishEvent(any(TeamUpdatedEvent.class));
    }

    @Test
    void update_leadId_changesWhenMemberExists() {
        Team t = newTeam();
        when(teamRepo.findById(TEAM_ID)).thenReturn(Optional.of(t));
        when(memRepo.findById(new TeamMembershipId(TEAM_ID, OTHER_USER)))
            .thenReturn(Optional.of(new TeamMembership(
                TEAM_ID, OTHER_USER, TeamMembership.Role.MEMBER, OWNER_ID, OffsetDateTime.now()
            )));
        when(memRepo.countByTeamId(TEAM_ID)).thenReturn(2L);

        service.update(TEAM_ID, null, null, null, OTHER_USER, ACTOR);

        assertThat(t.getLeadId()).isEqualTo(OTHER_USER);
        ArgumentCaptor<TeamUpdatedEvent> captor = ArgumentCaptor.forClass(TeamUpdatedEvent.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().changedFields()).isEqualTo("leadId");
    }

    @Test
    void update_multipleFields_emitsCommaSeparatedChangedFields() {
        Team t = newTeam();
        when(teamRepo.findById(TEAM_ID)).thenReturn(Optional.of(t));
        when(teamRepo.findActiveByNormalizedName("design")).thenReturn(Optional.empty());
        when(memRepo.findById(new TeamMembershipId(TEAM_ID, OTHER_USER)))
            .thenReturn(Optional.of(new TeamMembership(
                TEAM_ID, OTHER_USER, TeamMembership.Role.MEMBER, OWNER_ID, OffsetDateTime.now()
            )));
        when(memRepo.countByTeamId(TEAM_ID)).thenReturn(2L);

        service.update(TEAM_ID, "Design", "디자인 팀", "#C9925A", OTHER_USER, ACTOR);

        ArgumentCaptor<TeamUpdatedEvent> captor = ArgumentCaptor.forClass(TeamUpdatedEvent.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().changedFields()).isEqualTo("name,description,color,leadId");
    }

    @Test
    void update_noEffectiveChange_doesNotPublishEvent() {
        Team t = newTeam();
        when(teamRepo.findById(TEAM_ID)).thenReturn(Optional.of(t));
        when(memRepo.countByTeamId(TEAM_ID)).thenReturn(0L);

        // 동일 leadId, 색상도 동일 → 변경 없음
        service.update(TEAM_ID, null, null, null, OWNER_ID, ACTOR);

        verify(events, never()).publishEvent(any(TeamUpdatedEvent.class));
    }

    @Test
    void update_throwsOnMissingTeam() {
        when(teamRepo.findById(TEAM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            service.update(TEAM_ID, "Backend", null, null, null, ACTOR)
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    // ============================================================
    // archive / restore — delegation
    // ============================================================

    @Test
    void archive_delegatesToTeamService() {
        Team t = newTeam();
        when(teamRepo.findById(TEAM_ID)).thenReturn(Optional.of(t));

        service.archive(TEAM_ID, ACTOR);

        verify(teamSvc).archive(eq(TEAM_ID), eq(ACTOR));
    }

    @Test
    void archive_throwsOnMissingTeam() {
        when(teamRepo.findById(TEAM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.archive(TEAM_ID, ACTOR))
            .isInstanceOf(ResourceNotFoundException.class);
        verify(teamSvc, never()).archive(any(), any());
    }

    @Test
    void restore_delegatesToTeamService() {
        Team t = newTeam();
        when(teamRepo.findById(TEAM_ID)).thenReturn(Optional.of(t));

        service.restore(TEAM_ID, ACTOR);

        verify(teamSvc).restore(eq(TEAM_ID), eq(ACTOR));
    }
}
