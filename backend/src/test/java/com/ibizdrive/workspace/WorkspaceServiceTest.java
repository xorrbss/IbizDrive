package com.ibizdrive.workspace;

import com.ibizdrive.department.Department;
import com.ibizdrive.department.DepartmentRepository;
import com.ibizdrive.team.Team;
import com.ibizdrive.team.TeamMembership;
import com.ibizdrive.team.TeamMembershipRepository;
import com.ibizdrive.team.TeamRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class WorkspaceServiceTest {

    @Test
    void findForUser_returnsDepartmentAndTeams_whenUserHasBoth() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        UUID teamA = UUID.randomUUID();

        DepartmentRepository deptRepo = Mockito.mock(DepartmentRepository.class);
        TeamRepository teamRepo = Mockito.mock(TeamRepository.class);
        TeamMembershipRepository memRepo = Mockito.mock(TeamMembershipRepository.class);
        UserDepartmentLookup lookup = Mockito.mock(UserDepartmentLookup.class);

        when(lookup.departmentIdOf(userId)).thenReturn(Optional.of(deptId));
        Department dept = new Department(deptId, "Sales", OffsetDateTime.now());
        dept.attachRootFolder(UUID.randomUUID());
        when(deptRepo.findById(deptId)).thenReturn(Optional.of(dept));

        TeamMembership m = new TeamMembership(teamA, userId, TeamMembership.Role.MEMBER,
            null, OffsetDateTime.now());
        when(memRepo.findByUserId(userId)).thenReturn(List.of(m));

        Team t = new Team(teamA, "Alpha", "alpha", null,
            Team.Visibility.PRIVATE, userId, OffsetDateTime.now());
        t.attachRootFolder(UUID.randomUUID());
        when(teamRepo.findAllById(List.of(teamA))).thenReturn(List.of(t));

        WorkspaceService svc = new WorkspaceService(deptRepo, teamRepo, memRepo, lookup);
        WorkspaceListing listing = svc.findForUser(userId);

        assertThat(listing.department()).isPresent();
        assertThat(listing.department().get().id()).isEqualTo(deptId);
        assertThat(listing.teams()).hasSize(1);
        assertThat(listing.teams().get(0).id()).isEqualTo(teamA);
    }

    @Test
    void findForUser_returnsEmptyListing_whenUserHasNoDepartmentOrTeams() {
        UUID userId = UUID.randomUUID();
        DepartmentRepository deptRepo = Mockito.mock(DepartmentRepository.class);
        TeamRepository teamRepo = Mockito.mock(TeamRepository.class);
        TeamMembershipRepository memRepo = Mockito.mock(TeamMembershipRepository.class);
        UserDepartmentLookup lookup = Mockito.mock(UserDepartmentLookup.class);
        when(lookup.departmentIdOf(userId)).thenReturn(Optional.empty());
        when(memRepo.findByUserId(userId)).thenReturn(List.of());

        WorkspaceService svc = new WorkspaceService(deptRepo, teamRepo, memRepo, lookup);
        WorkspaceListing listing = svc.findForUser(userId);

        assertThat(listing.department()).isEmpty();
        assertThat(listing.teams()).isEmpty();
    }

    @Test
    void findForUser_excludesDepartment_whenRootFolderIdIsNull() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();

        DepartmentRepository deptRepo = Mockito.mock(DepartmentRepository.class);
        TeamRepository teamRepo = Mockito.mock(TeamRepository.class);
        TeamMembershipRepository memRepo = Mockito.mock(TeamMembershipRepository.class);
        UserDepartmentLookup lookup = Mockito.mock(UserDepartmentLookup.class);

        when(lookup.departmentIdOf(userId)).thenReturn(Optional.of(deptId));
        // Department with no rootFolderId attached — Plan A2 backfill scenario
        Department deptWithoutRoot = new Department(deptId, "PendingDept", OffsetDateTime.now());
        when(deptRepo.findById(deptId)).thenReturn(Optional.of(deptWithoutRoot));
        when(memRepo.findByUserId(userId)).thenReturn(List.of());

        WorkspaceService svc = new WorkspaceService(deptRepo, teamRepo, memRepo, lookup);
        WorkspaceListing listing = svc.findForUser(userId);

        assertThat(listing.department()).isEmpty();
        assertThat(listing.teams()).isEmpty();
    }
}
