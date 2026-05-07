package com.ibizdrive.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.auth.LoginAttemptTracker;
import com.ibizdrive.common.error.AdminExceptionHandler;
import com.ibizdrive.common.error.AuthExceptionHandler;
import com.ibizdrive.common.error.GlobalExceptionHandler;
import com.ibizdrive.config.MethodSecurityConfig;
import com.ibizdrive.config.SecurityConfig;
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

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link AdminStorageController} sliced WebMvcTest — admin-storage-overview.
 *
 * <p>{@link AdminDepartmentControllerTest} 패턴 mirror — 200 / 401 / 403 매트릭스 검증.
 */
@WebMvcTest(controllers = AdminStorageController.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class,
    AuthExceptionHandler.class, AdminExceptionHandler.class, GlobalExceptionHandler.class})
class AdminStorageControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @MockBean
    private AdminStorageService adminStorageService;

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

    @BeforeEach
    void setUp() {
        User admin = new User(UUID.randomUUID(), "admin@example.com", "Admin",
            "{bcrypt}$2a$12$dummy", Role.ADMIN, true, false, OffsetDateTime.now());
        adminPrincipal = new IbizDriveUserDetails(admin);

        User member = new User(UUID.randomUUID(), "m@example.com", "Member",
            "{bcrypt}$2a$12$dummy", Role.MEMBER, true, false, OffsetDateTime.now());
        memberPrincipal = new IbizDriveUserDetails(member);
    }

    // ===== GET /api/admin/storage/overview =====

    @Test
    void overview_admin_returns200WithEnvelope() throws Exception {
        Instant lastRun = Instant.parse("2026-05-06T14:30:00Z");
        when(adminStorageService.loadOverview()).thenReturn(
            new AdminStorageOverviewResponse.Overview(
                123L, 200L, 10_485_760L, 5L, 2_048L,
                new AdminStorageOverviewResponse.OrphanCleanupSummary(lastRun, 7)
            )
        );

        mvc.perform(get("/api/admin/storage/overview").with(user(adminPrincipal)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.overview.totalFiles").value(123))
            .andExpect(jsonPath("$.overview.totalVersions").value(200))
            .andExpect(jsonPath("$.overview.totalBytes").value(10_485_760))
            .andExpect(jsonPath("$.overview.trashedFiles").value(5))
            .andExpect(jsonPath("$.overview.trashedBytes").value(2_048))
            .andExpect(jsonPath("$.overview.orphanCleanup.lastRunAt").value("2026-05-06T14:30:00Z"))
            .andExpect(jsonPath("$.overview.orphanCleanup.lastDeletedCount").value(7));
    }

    @Test
    void overview_admin_orphanCleanupNull_returns200WithNullField() throws Exception {
        when(adminStorageService.loadOverview()).thenReturn(
            new AdminStorageOverviewResponse.Overview(0L, 0L, 0L, 0L, 0L, null)
        );

        mvc.perform(get("/api/admin/storage/overview").with(user(adminPrincipal)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.overview.totalFiles").value(0))
            .andExpect(jsonPath("$.overview.orphanCleanup").value(nullValue()));
    }

    @Test
    void overview_unauthenticated_returns401() throws Exception {
        mvc.perform(get("/api/admin/storage/overview"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(adminStorageService);
    }

    @Test
    void overview_memberAuthenticated_returns403() throws Exception {
        mvc.perform(get("/api/admin/storage/overview").with(user(memberPrincipal)))
            .andExpect(status().isForbidden());
        verifyNoInteractions(adminStorageService);
    }
}
