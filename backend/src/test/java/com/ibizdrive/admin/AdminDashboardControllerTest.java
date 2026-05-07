package com.ibizdrive.admin;

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

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * admin-dashboard P1 — {@link AdminDashboardController} sliced WebMvcTest.
 *
 * <p>{@link AdminDepartmentControllerTest} 패턴 mirror — 200 / 401 / 403만 검증
 * (read-only single endpoint).
 */
@WebMvcTest(controllers = AdminDashboardController.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class,
    AuthExceptionHandler.class, AdminExceptionHandler.class, GlobalExceptionHandler.class})
class AdminDashboardControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AdminDashboardService adminDashboardService;

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

    private static final UUID ADMIN_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @BeforeEach
    void setUp() {
        User admin = new User(ADMIN_ID, "admin@example.com", "Admin",
            "{bcrypt}$2a$12$dummy", Role.ADMIN, true, false, OffsetDateTime.now());
        adminPrincipal = new IbizDriveUserDetails(admin);

        User member = new User(UUID.randomUUID(), "m@example.com", "Member",
            "{bcrypt}$2a$12$dummy", Role.MEMBER, true, false, OffsetDateTime.now());
        memberPrincipal = new IbizDriveUserDetails(member);
    }

    @Test
    void summary_adminAuthenticated_returns200WithEnvelope() throws Exception {
        AdminDashboardSummaryResponse.SummaryData data = new AdminDashboardSummaryResponse.SummaryData(
            new AdminDashboardSummaryResponse.Users(42L, 38L),
            new AdminDashboardSummaryResponse.Departments(7L, 7L),
            new AdminDashboardSummaryResponse.Folders(1234L),
            new AdminDashboardSummaryResponse.Files(9876L, 123L),
            new AdminDashboardSummaryResponse.Audit(456L),
            new AdminDashboardSummaryResponse.Storage(1234567890L)
        );
        when(adminDashboardService.getSummary()).thenReturn(data);

        mvc.perform(get("/api/admin/dashboard/summary").with(user(adminPrincipal)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.summary.users.total").value(42))
            .andExpect(jsonPath("$.summary.users.active").value(38))
            .andExpect(jsonPath("$.summary.departments.total").value(7))
            .andExpect(jsonPath("$.summary.departments.active").value(7))
            .andExpect(jsonPath("$.summary.folders.active").value(1234))
            .andExpect(jsonPath("$.summary.files.active").value(9876))
            .andExpect(jsonPath("$.summary.files.trashed").value(123))
            .andExpect(jsonPath("$.summary.audit.last24h").value(456))
            .andExpect(jsonPath("$.summary.storage.usedBytes").value(1234567890));
    }

    @Test
    void summary_unauthenticated_returns401() throws Exception {
        mvc.perform(get("/api/admin/dashboard/summary"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(adminDashboardService);
    }

    @Test
    void summary_memberAuthenticated_returns403() throws Exception {
        mvc.perform(get("/api/admin/dashboard/summary").with(user(memberPrincipal)))
            .andExpect(status().isForbidden());
        verifyNoInteractions(adminDashboardService);
    }
}
