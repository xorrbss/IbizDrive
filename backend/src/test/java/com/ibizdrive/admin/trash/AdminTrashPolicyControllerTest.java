package com.ibizdrive.admin.trash;

import com.ibizdrive.auth.LoginAttemptTracker;
import com.ibizdrive.common.error.AdminExceptionHandler;
import com.ibizdrive.common.error.AuthExceptionHandler;
import com.ibizdrive.common.error.GlobalExceptionHandler;
import com.ibizdrive.config.MethodSecurityConfig;
import com.ibizdrive.config.SecurityConfig;
import com.ibizdrive.trash.TrashPolicyService;
import com.ibizdrive.user.DbUserDetailsService;
import com.ibizdrive.user.IbizDriveUserDetails;
import com.ibizdrive.user.Role;
import com.ibizdrive.user.User;
import com.ibizdrive.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link AdminTrashPolicyController} sliced WebMvcTest — wave2-trash-policy-viewer +
 * trash-retention-mutation Phase B.
 *
 * <p>HTTP 매트릭스:
 * <ul>
 *   <li>GET 200 (admin) / 401 (anonymous) / 403 (member, auditor) — read-only.</li>
 *   <li>PUT 200 (admin valid) / 400 (range/null) / 401 / 403 (member, auditor).</li>
 * </ul>
 *
 * <p>{@link AdminTrashControllerTest} 패턴 동형 — `@Import`로 보안 설정 + 글로벌 예외 핸들러
 * 로드. {@link TrashPolicyService}는 {@code @TestConfiguration}으로 stub mock 주입.
 */
@WebMvcTest(controllers = AdminTrashPolicyController.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class,
    AuthExceptionHandler.class, AdminExceptionHandler.class, GlobalExceptionHandler.class,
    AdminTrashPolicyControllerTest.TestConfig.class})
class AdminTrashPolicyControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired TrashPolicyService trashPolicyService;

    @MockBean LoginAttemptTracker tracker;
    @MockBean UserRepository userRepository;
    @MockBean DbUserDetailsService dbUserDetailsService;
    @MockBean PermissionEvaluator permissionEvaluator;

    @BeforeEach
    void resetMocks() {
        reset(trashPolicyService);
        when(trashPolicyService.getRetentionDays()).thenReturn(14);
    }

    @TestConfiguration
    static class TestConfig {
        /** stub TrashPolicyService — 부팅 부수효과(@PostConstruct) 회피 + 단순 stub. */
        @Bean TrashPolicyService trashPolicyService() {
            return org.mockito.Mockito.mock(TrashPolicyService.class);
        }
    }

    private static final UUID ADMIN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static IbizDriveUserDetails admin() {
        User u = new User(ADMIN_ID, "admin@example.com", "Admin",
            "{bcrypt}$2a$12$dummy", Role.ADMIN, true, false, OffsetDateTime.now());
        return new IbizDriveUserDetails(u);
    }

    // ─────────────────────────── GET ───────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void get_200_returnsRetentionDays() throws Exception {
        mockMvc.perform(get("/api/admin/trash/policy"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.retentionDays").value(14));
    }

    @Test
    void get_401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/admin/trash/policy"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "MEMBER")
    void get_403_forMember() throws Exception {
        mockMvc.perform(get("/api/admin/trash/policy"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "AUDITOR")
    void get_403_forAuditor() throws Exception {
        mockMvc.perform(get("/api/admin/trash/policy"))
            .andExpect(status().isForbidden());
    }

    // ─────────────────────────── PUT (Phase B) ───────────────────────────

    @Test
    void put_200_admin_appliesAndReturns() throws Exception {
        when(trashPolicyService.updateRetentionDays(eq(21), eq(admin().getUser().getId()))).thenReturn(21);
        mockMvc.perform(put("/api/admin/trash/policy")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                    .user(admin()))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"days\":21}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.retentionDays").value(21));
        verify(trashPolicyService).updateRetentionDays(21, admin().getUser().getId());
    }

    @Test
    void put_400_whenDaysBelowMin() throws Exception {
        mockMvc.perform(put("/api/admin/trash/policy")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                    .user(admin()))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"days\":6}"))
            .andExpect(status().isBadRequest());
        verify(trashPolicyService, never()).updateRetentionDays(anyInt(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void put_400_whenDaysAboveMax() throws Exception {
        mockMvc.perform(put("/api/admin/trash/policy")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                    .user(admin()))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"days\":91}"))
            .andExpect(status().isBadRequest());
        verify(trashPolicyService, never()).updateRetentionDays(anyInt(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void put_400_whenDaysNull() throws Exception {
        mockMvc.perform(put("/api/admin/trash/policy")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                    .user(admin()))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
        verify(trashPolicyService, never()).updateRetentionDays(anyInt(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void put_401_whenUnauthenticated() throws Exception {
        mockMvc.perform(put("/api/admin/trash/policy")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"days\":14}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "AUDITOR")
    void put_403_forAuditor() throws Exception {
        mockMvc.perform(put("/api/admin/trash/policy")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"days\":14}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MEMBER")
    void put_403_forMember() throws Exception {
        mockMvc.perform(put("/api/admin/trash/policy")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"days\":14}"))
            .andExpect(status().isForbidden());
    }
}
