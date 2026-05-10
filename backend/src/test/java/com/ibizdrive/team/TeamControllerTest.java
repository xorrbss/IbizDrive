package com.ibizdrive.team;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.auth.LoginAttemptTracker;
import com.ibizdrive.common.error.AuthExceptionHandler;
import com.ibizdrive.common.error.GlobalExceptionHandler;
import com.ibizdrive.config.MethodSecurityConfig;
import com.ibizdrive.config.SecurityConfig;
import com.ibizdrive.team.dto.TeamCreateRequest;
import com.ibizdrive.team.dto.TeamMemberInviteRequest;
import com.ibizdrive.team.dto.TeamMemberResponse;
import com.ibizdrive.team.dto.TeamMemberRoleUpdateRequest;
import com.ibizdrive.user.DbUserDetailsService;
import com.ibizdrive.user.IbizDriveUserDetails;
import com.ibizdrive.user.Role;
import com.ibizdrive.user.User;
import com.ibizdrive.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link TeamController} sliced WebMvcTest — Plan A Task 19.
 *
 * <p>peer = {@link com.ibizdrive.workspace.WorkspaceControllerTest}.
 * service + authz는 mock, 인증/응답 셰이프 + 403 가드만 검증.
 */
@WebMvcTest(controllers = TeamController.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class,
    AuthExceptionHandler.class, GlobalExceptionHandler.class})
class TeamControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TeamService teamService;

    @MockBean(name = "teamAuthz")
    private TeamAuthz teamAuthz;

    @MockBean
    private DbUserDetailsService dbUserDetailsService;

    @MockBean
    private LoginAttemptTracker loginAttemptTracker;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private PermissionEvaluator permissionEvaluator;

    @Test
    void createTeam_returns201_whenAuthenticated() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID rootFolderId = UUID.randomUUID();

        Team mockTeam = new Team(teamId, "MyTeam", "myteam", null,
            Team.Visibility.PRIVATE, userId, OffsetDateTime.now());
        mockTeam.attachRootFolder(rootFolderId);
        when(teamService.create(eq("MyTeam"), any(), eq(Team.Visibility.PRIVATE), eq(userId)))
            .thenReturn(mockTeam);

        IbizDriveUserDetails principal = principalForUser(userId);

        String body = objectMapper.writeValueAsString(
            new TeamCreateRequest("MyTeam", null, Team.Visibility.PRIVATE));

        mvc.perform(post("/api/teams")
                .with(user(principal)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(teamId.toString()))
            .andExpect(jsonPath("$.name").value("MyTeam"))
            .andExpect(jsonPath("$.visibility").value("PRIVATE"))
            .andExpect(jsonPath("$.rootFolderId").value(rootFolderId.toString()));
    }

    @Test
    void createTeam_returns401_whenUnauthenticated() throws Exception {
        String body = objectMapper.writeValueAsString(
            new TeamCreateRequest("MyTeam", null, Team.Visibility.PRIVATE));

        mvc.perform(post("/api/teams")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void createTeam_returns400_whenNameBlank() throws Exception {
        UUID userId = UUID.randomUUID();
        IbizDriveUserDetails principal = principalForUser(userId);

        String body = objectMapper.writeValueAsString(
            new TeamCreateRequest("", null, Team.Visibility.PRIVATE));

        mvc.perform(post("/api/teams")
                .with(user(principal)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());

        verify(teamService, never()).create(anyString(), any(), any(), any());
    }

    @Test
    void inviteMember_returns201_whenOwner() throws Exception {
        UUID actor = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID inviteeId = UUID.randomUUID();
        IbizDriveUserDetails principal = principalForUser(actor);

        when(teamAuthz.isOwner(eq(teamId), any())).thenReturn(true);

        String body = objectMapper.writeValueAsString(new TeamMemberInviteRequest(inviteeId));

        mvc.perform(post("/api/teams/" + teamId + "/members")
                .with(user(principal)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());

        verify(teamService).invite(teamId, inviteeId, actor);
    }

    @Test
    void inviteMember_returns403_whenNotOwner() throws Exception {
        UUID actor = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID inviteeId = UUID.randomUUID();
        IbizDriveUserDetails principal = principalForUser(actor);

        when(teamAuthz.isOwner(eq(teamId), any())).thenReturn(false);

        String body = objectMapper.writeValueAsString(new TeamMemberInviteRequest(inviteeId));

        mvc.perform(post("/api/teams/" + teamId + "/members")
                .with(user(principal)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());

        verify(teamService, never()).invite(any(), any(), any());
    }

    @Test
    void removeMember_returns204_whenOwnerOrSelf() throws Exception {
        UUID actor = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        IbizDriveUserDetails principal = principalForUser(actor);

        when(teamAuthz.isOwnerOrSelf(eq(teamId), eq(memberId), any())).thenReturn(true);

        mvc.perform(delete("/api/teams/" + teamId + "/members/" + memberId)
                .with(user(principal)).with(csrf()))
            .andExpect(status().isNoContent());

        verify(teamService).remove(teamId, memberId, actor);
    }

    @Test
    void removeMember_returns403_whenNotOwnerNorSelf() throws Exception {
        UUID actor = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        IbizDriveUserDetails principal = principalForUser(actor);

        when(teamAuthz.isOwnerOrSelf(eq(teamId), eq(memberId), any())).thenReturn(false);

        mvc.perform(delete("/api/teams/" + teamId + "/members/" + memberId)
                .with(user(principal)).with(csrf()))
            .andExpect(status().isForbidden());

        verify(teamService, never()).remove(any(), any(), any());
    }

    @Test
    void listMembers_returns200_whenMember() throws Exception {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        IbizDriveUserDetails principal = principalForUser(userId);
        TeamMemberResponse r1 = new TeamMemberResponse(
            userId, "Alice", "a@x.io",
            TeamMembership.Role.OWNER, OffsetDateTime.parse("2026-05-10T00:00:00Z"));

        when(teamAuthz.isMember(eq(teamId), any())).thenReturn(true);
        when(teamService.listMembers(eq(teamId))).thenReturn(List.of(r1));

        mvc.perform(get("/api/teams/" + teamId + "/members")
                .with(user(principal)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].userId").value(userId.toString()))
            .andExpect(jsonPath("$[0].displayName").value("Alice"))
            .andExpect(jsonPath("$[0].role").value("OWNER"));
    }

    @Test
    void listMembers_returns403_whenNonMember() throws Exception {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        IbizDriveUserDetails principal = principalForUser(userId);

        when(teamAuthz.isMember(eq(teamId), any())).thenReturn(false);

        mvc.perform(get("/api/teams/" + teamId + "/members")
                .with(user(principal)))
            .andExpect(status().isForbidden());

        verify(teamService, never()).listMembers(any());
    }

    @Test
    void changeRole_returns204_whenOwner() throws Exception {
        UUID teamId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        IbizDriveUserDetails principal = principalForUser(actorId);

        when(teamAuthz.isOwner(eq(teamId), any())).thenReturn(true);

        TeamMemberRoleUpdateRequest req = new TeamMemberRoleUpdateRequest(TeamMembership.Role.OWNER);

        mvc.perform(patch("/api/teams/" + teamId + "/members/" + targetUserId)
                .with(user(principal)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(req)))
            .andExpect(status().isNoContent());

        verify(teamService).changeRole(eq(teamId), eq(targetUserId),
            eq(TeamMembership.Role.OWNER), eq(actorId));
    }

    @Test
    void changeRole_returns400_whenLastOwner() throws Exception {
        UUID teamId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        IbizDriveUserDetails principal = principalForUser(actorId);

        when(teamAuthz.isOwner(eq(teamId), any())).thenReturn(true);
        doThrow(new LastOwnerRequiredException(teamId))
            .when(teamService).changeRole(any(), any(), any(), any());

        TeamMemberRoleUpdateRequest req = new TeamMemberRoleUpdateRequest(TeamMembership.Role.MEMBER);

        mvc.perform(patch("/api/teams/" + teamId + "/members/" + targetUserId)
                .with(user(principal)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(req)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("TEAM_OWNER_REQUIRED"));
    }

    @Test
    void changeRole_returns403_whenNonOwner() throws Exception {
        UUID teamId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        IbizDriveUserDetails principal = principalForUser(actorId);

        when(teamAuthz.isOwner(eq(teamId), any())).thenReturn(false);

        TeamMemberRoleUpdateRequest req = new TeamMemberRoleUpdateRequest(TeamMembership.Role.MEMBER);

        mvc.perform(patch("/api/teams/" + teamId + "/members/" + targetUserId)
                .with(user(principal)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(req)))
            .andExpect(status().isForbidden());

        verify(teamService, never()).changeRole(any(), any(), any(), any());
    }

    private IbizDriveUserDetails principalForUser(UUID userId) {
        User u = new User(userId, "test-" + userId + "@ibizdrive", "Test User",
            "{bcrypt}$2a$12$dummy", Role.MEMBER, true, false, OffsetDateTime.now());
        return new IbizDriveUserDetails(u);
    }
}
