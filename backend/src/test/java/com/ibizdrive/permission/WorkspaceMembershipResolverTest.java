package com.ibizdrive.permission;

import com.ibizdrive.folder.ScopeType;
import com.ibizdrive.team.TeamMembership;
import com.ibizdrive.team.TeamMembershipId;
import com.ibizdrive.team.TeamMembershipRepository;
import com.ibizdrive.workspace.UserDepartmentLookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Plan A Task 22 — WorkspaceMembershipResolver Mockito unit test.
 *
 * <p>spec §3.2 매핑 검증: department-member / team-MEMBER / team-OWNER / non-member.
 * 추가 edge case: wrong department / user with no department.
 */
class WorkspaceMembershipResolverTest {

    private UserDepartmentLookup lookup;
    private TeamMembershipRepository memRepo;
    private WorkspaceMembershipResolver resolver;

    @BeforeEach
    void setUp() {
        lookup = Mockito.mock(UserDepartmentLookup.class);
        memRepo = Mockito.mock(TeamMembershipRepository.class);
        resolver = new WorkspaceMembershipResolver(lookup, memRepo);
    }

    @Test
    void resolve_returnsReadAndUpload_forDepartmentMember() {
        UUID userId = UUID.randomUUID();
        UUID dept = UUID.randomUUID();
        when(lookup.departmentIdOf(userId)).thenReturn(Optional.of(dept));

        Set<Permission> perms = resolver.resolve(userId, ScopeType.DEPARTMENT, dept);

        assertThat(perms).containsExactlyInAnyOrder(Permission.READ, Permission.UPLOAD);
    }

    @Test
    void resolve_returnsEmpty_forUserInDifferentDepartment() {
        UUID userId = UUID.randomUUID();
        UUID userDept = UUID.randomUUID();
        UUID otherDept = UUID.randomUUID();
        when(lookup.departmentIdOf(userId)).thenReturn(Optional.of(userDept));

        Set<Permission> perms = resolver.resolve(userId, ScopeType.DEPARTMENT, otherDept);

        assertThat(perms).isEmpty();
    }

    @Test
    void resolve_returnsEmpty_forUserWithNoDepartment() {
        UUID userId = UUID.randomUUID();
        UUID dept = UUID.randomUUID();
        when(lookup.departmentIdOf(userId)).thenReturn(Optional.empty());

        Set<Permission> perms = resolver.resolve(userId, ScopeType.DEPARTMENT, dept);

        assertThat(perms).isEmpty();
    }

    @Test
    void resolve_returnsReadUploadEdit_forTeamMember() {
        UUID userId = UUID.randomUUID();
        UUID team = UUID.randomUUID();
        when(memRepo.findById(new TeamMembershipId(team, userId))).thenReturn(
            Optional.of(new TeamMembership(team, userId, TeamMembership.Role.MEMBER,
                null, OffsetDateTime.now())));

        Set<Permission> perms = resolver.resolve(userId, ScopeType.TEAM, team);

        assertThat(perms).containsExactlyInAnyOrder(
            Permission.READ, Permission.UPLOAD, Permission.EDIT);
    }

    @Test
    void resolve_returnsFullSet_forTeamOwner() {
        UUID userId = UUID.randomUUID();
        UUID team = UUID.randomUUID();
        when(memRepo.findById(new TeamMembershipId(team, userId))).thenReturn(
            Optional.of(new TeamMembership(team, userId, TeamMembership.Role.OWNER,
                null, OffsetDateTime.now())));

        Set<Permission> perms = resolver.resolve(userId, ScopeType.TEAM, team);

        assertThat(perms).containsExactlyInAnyOrder(
            Permission.READ, Permission.UPLOAD, Permission.EDIT,
            Permission.DELETE, Permission.SHARE);
    }

    @Test
    void resolve_returnsEmpty_forNonTeamMember() {
        UUID userId = UUID.randomUUID();
        UUID team = UUID.randomUUID();
        when(memRepo.findById(new TeamMembershipId(team, userId))).thenReturn(Optional.empty());

        Set<Permission> perms = resolver.resolve(userId, ScopeType.TEAM, team);

        assertThat(perms).isEmpty();
    }
}
