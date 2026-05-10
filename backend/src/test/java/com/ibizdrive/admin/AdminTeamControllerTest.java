package com.ibizdrive.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.auth.LoginAttemptTracker;
import com.ibizdrive.common.error.AdminExceptionHandler;
import com.ibizdrive.common.error.AuthExceptionHandler;
import com.ibizdrive.common.error.GlobalExceptionHandler;
import com.ibizdrive.common.error.ResourceNotFoundException;
import com.ibizdrive.config.MethodSecurityConfig;
import com.ibizdrive.config.SecurityConfig;
import com.ibizdrive.team.TeamNameConflictException;
import com.ibizdrive.user.DbUserDetailsService;
import com.ibizdrive.user.IbizDriveUserDetails;
import com.ibizdrive.user.Role;
import com.ibizdrive.user.User;
import com.ibizdrive.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
 * {@link AdminTeamController} sliced WebMvcTest — T8 closure.
 *
 * <p>{@link AdminDepartmentControllerTest} 패턴 mirror. HTTP 매트릭스
 * (docs/02 §7.16.1):
 * 200 / 204 / 400 / 401 / 403 / 404 / 409 모두 검증.
 */
@WebMvcTest(controllers = AdminTeamController.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class,
    AuthExceptionHandler.class, AdminExceptionHandler.class, GlobalExceptionHandler.class})
class AdminTeamControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @MockBean
    private AdminTeamService adminTeamService;

    @MockBean
    private LoginAttemptTracker tracker;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private DbUserDetailsService dbUserDetailsService;

    @MockBean
    private PermissionEvaluator permissionEvaluator;

    private IbizDriveUserDetails adminPrincipal;
    private IbizDriveUserDetails memberPrincipal;

    private static final UUID ACTOR_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID TEAM_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID LEAD_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeEach
    void setUp() {
        User admin = new User(ACTOR_ID, "admin@example.com", "Admin",
            "{bcrypt}$2a$12$dummy", Role.ADMIN, true, false, OffsetDateTime.now());
        adminPrincipal = new IbizDriveUserDetails(admin);

        User member = new User(UUID.randomUUID(), "m@example.com", "Member",
            "{bcrypt}$2a$12$dummy", Role.MEMBER, true, false, OffsetDateTime.now());
        memberPrincipal = new IbizDriveUserDetails(member);
    }

    private AdminTeamSummaryResponse summary(String name) {
        return new AdminTeamSummaryResponse(
            TEAM_ID, name, null, "#5B7FCC", LEAD_ID, 5L, false, OffsetDateTime.now()
        );
    }

    private AdminTeamDetailResponse detail() {
        return new AdminTeamDetailResponse(
            TEAM_ID, "Eng", "엔지니어링 팀", "#5B7FCC", LEAD_ID,
            "private", null, 5L, false, null, null,
            ACTOR_ID, OffsetDateTime.now(), OffsetDateTime.now()
        );
    }

    // ===== GET /api/admin/teams =====

    @Test
    void list_adminAuthenticated_returns200WithList() throws Exception {
        when(adminTeamService.list()).thenReturn(List.of(summary("Eng"), summary("Sales")));

        mvc.perform(get("/api/admin/teams").with(user(adminPrincipal)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].name").value("Eng"))
            .andExpect(jsonPath("$[0].color").value("#5B7FCC"))
            .andExpect(jsonPath("$[0].memberCount").value(5))
            .andExpect(jsonPath("$[0].archived").value(false))
            .andExpect(jsonPath("$[1].name").value("Sales"));
    }

    @Test
    void list_unauthenticated_returns401() throws Exception {
        mvc.perform(get("/api/admin/teams"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(adminTeamService);
    }

    @Test
    void list_memberAuthenticated_returns403() throws Exception {
        mvc.perform(get("/api/admin/teams").with(user(memberPrincipal)))
            .andExpect(status().isForbidden());
        verifyNoInteractions(adminTeamService);
    }

    // ===== GET /api/admin/teams/{id} =====

    @Test
    void detail_adminAuthenticated_returns200WithDetail() throws Exception {
        when(adminTeamService.detail(eq(TEAM_ID))).thenReturn(detail());

        mvc.perform(get("/api/admin/teams/{id}", TEAM_ID).with(user(adminPrincipal)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(TEAM_ID.toString()))
            .andExpect(jsonPath("$.name").value("Eng"))
            .andExpect(jsonPath("$.description").value("엔지니어링 팀"))
            .andExpect(jsonPath("$.color").value("#5B7FCC"))
            .andExpect(jsonPath("$.leadId").value(LEAD_ID.toString()))
            .andExpect(jsonPath("$.visibility").value("private"))
            .andExpect(jsonPath("$.memberCount").value(5))
            .andExpect(jsonPath("$.archived").value(false));
    }

    @Test
    void detail_notFound_returns404() throws Exception {
        when(adminTeamService.detail(eq(TEAM_ID)))
            .thenThrow(new ResourceNotFoundException("team not found: " + TEAM_ID));

        mvc.perform(get("/api/admin/teams/{id}", TEAM_ID).with(user(adminPrincipal)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void detail_memberAuthenticated_returns403() throws Exception {
        mvc.perform(get("/api/admin/teams/{id}", TEAM_ID).with(user(memberPrincipal)))
            .andExpect(status().isForbidden());
        verifyNoInteractions(adminTeamService);
    }

    // ===== PATCH /api/admin/teams/{id} =====

    @Test
    void patch_renameOnly_returns200() throws Exception {
        when(adminTeamService.update(eq(TEAM_ID), eq("Backend"),
            eq(null), eq(null), eq(null), eq(ACTOR_ID))).thenReturn(detail());

        mvc.perform(patch("/api/admin/teams/{id}", TEAM_ID)
                .with(user(adminPrincipal)).with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("name", "Backend"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(TEAM_ID.toString()));

        verify(adminTeamService).update(eq(TEAM_ID), eq("Backend"),
            eq(null), eq(null), eq(null), eq(ACTOR_ID));
    }

    @Test
    void patch_emptyBody_returns400ValidationError() throws Exception {
        // 모든 필드 null/blank → AdminBadPatchException → 400 VALIDATION_ERROR
        mvc.perform(patch("/api/admin/teams/{id}", TEAM_ID)
                .with(user(adminPrincipal)).with(csrf())
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.details.field").value("body"));
        verifyNoInteractions(adminTeamService);
    }

    @Test
    void patch_invalidColorFormat_returns400Validation() throws Exception {
        // @Pattern 검증 실패 → Bean Validation → 400 VALIDATION_ERROR (controller가 service를 호출하지 않음).
        mvc.perform(patch("/api/admin/teams/{id}", TEAM_ID)
                .with(user(adminPrincipal)).with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("color", "blue"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(adminTeamService);
    }

    @Test
    void patch_notFound_returns404() throws Exception {
        when(adminTeamService.update(any(), any(), any(), any(), any(), any()))
            .thenThrow(new ResourceNotFoundException("team not found: " + TEAM_ID));

        mvc.perform(patch("/api/admin/teams/{id}", TEAM_ID)
                .with(user(adminPrincipal)).with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("name", "Anything"))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void patch_nameConflict_returns409TeamConflict() throws Exception {
        when(adminTeamService.update(any(), any(), any(), any(), any(), any()))
            .thenThrow(new TeamNameConflictException("Backend"));

        mvc.perform(patch("/api/admin/teams/{id}", TEAM_ID)
                .with(user(adminPrincipal)).with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("name", "Backend"))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("TEAM_CONFLICT"));
    }

    @Test
    void patch_leadIdNotMember_returns400ViaAdminBadPatch() throws Exception {
        UUID leadId = UUID.randomUUID();
        when(adminTeamService.update(any(), any(), any(), any(), eq(leadId), any()))
            .thenThrow(new AdminBadPatchException("leadId must be an existing team member: " + leadId));

        Map<String, Object> body = new HashMap<>();
        body.put("leadId", leadId.toString());

        mvc.perform(patch("/api/admin/teams/{id}", TEAM_ID)
                .with(user(adminPrincipal)).with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(body)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.details.field").value("body"));
    }

    @Test
    void patch_memberAuthenticated_returns403() throws Exception {
        mvc.perform(patch("/api/admin/teams/{id}", TEAM_ID)
                .with(user(memberPrincipal)).with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("name", "Backend"))))
            .andExpect(status().isForbidden());
        verifyNoInteractions(adminTeamService);
    }

    // ===== DELETE /api/admin/teams/{id} =====

    @Test
    void delete_adminAuthenticated_returns204() throws Exception {
        mvc.perform(delete("/api/admin/teams/{id}", TEAM_ID)
                .with(user(adminPrincipal)).with(csrf()))
            .andExpect(status().isNoContent());

        verify(adminTeamService).archive(eq(TEAM_ID), eq(ACTOR_ID));
    }

    @Test
    void delete_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("team not found"))
            .when(adminTeamService).archive(eq(TEAM_ID), any());

        mvc.perform(delete("/api/admin/teams/{id}", TEAM_ID)
                .with(user(adminPrincipal)).with(csrf()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void delete_memberAuthenticated_returns403() throws Exception {
        mvc.perform(delete("/api/admin/teams/{id}", TEAM_ID)
                .with(user(memberPrincipal)).with(csrf()))
            .andExpect(status().isForbidden());
        verifyNoInteractions(adminTeamService);
    }

    // ===== POST /api/admin/teams/{id}/restore =====

    @Test
    void restore_adminAuthenticated_returns204() throws Exception {
        mvc.perform(post("/api/admin/teams/{id}/restore", TEAM_ID)
                .with(user(adminPrincipal)).with(csrf()))
            .andExpect(status().isNoContent());

        verify(adminTeamService).restore(eq(TEAM_ID), eq(ACTOR_ID));
    }

    @Test
    void restore_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("team not found"))
            .when(adminTeamService).restore(eq(TEAM_ID), any());

        mvc.perform(post("/api/admin/teams/{id}/restore", TEAM_ID)
                .with(user(adminPrincipal)).with(csrf()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void restore_memberAuthenticated_returns403() throws Exception {
        mvc.perform(post("/api/admin/teams/{id}/restore", TEAM_ID)
                .with(user(memberPrincipal)).with(csrf()))
            .andExpect(status().isForbidden());
        verifyNoInteractions(adminTeamService);
    }
}
