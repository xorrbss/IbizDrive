package com.ibizdrive.workspace;

import com.ibizdrive.auth.LoginAttemptTracker;
import com.ibizdrive.common.error.AuthExceptionHandler;
import com.ibizdrive.common.error.GlobalExceptionHandler;
import com.ibizdrive.config.MethodSecurityConfig;
import com.ibizdrive.config.SecurityConfig;
import com.ibizdrive.department.Department;
import com.ibizdrive.team.Team;
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
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link WorkspaceController} sliced WebMvcTest — Plan A Task 15.
 *
 * <p>peer 패턴 {@link com.ibizdrive.admin.AdminDepartmentControllerTest}.
 * 인증/응답 셰이프 검증, 서비스는 mock.
 */
@WebMvcTest(controllers = WorkspaceController.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class,
    AuthExceptionHandler.class, GlobalExceptionHandler.class})
class WorkspaceControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private WorkspaceService workspaceService;

    @MockBean
    private DbUserDetailsService dbUserDetailsService; // SecurityConfig dep

    @MockBean
    private LoginAttemptTracker loginAttemptTracker; // SecurityConfig dep

    @MockBean
    private UserRepository userRepository; // SecurityConfig dep (peer pattern)

    @MockBean
    private PermissionEvaluator permissionEvaluator; // MethodSecurityConfig dep

    @Test
    void getMe_returnsDepartmentAndTeams_whenAuthenticated() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        UUID deptRoot = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID teamRoot = UUID.randomUUID();

        // Build a Department + DepartmentWorkspace
        Department dept = new Department(deptId, "Sales", OffsetDateTime.now());
        dept.attachRootFolder(deptRoot);

        // Build a Team + TeamWorkspace
        Team team = new Team(
            teamId, "Alpha", "alpha", null,
            Team.Visibility.PRIVATE, userId, OffsetDateTime.now());
        team.attachRootFolder(teamRoot);

        WorkspaceListing listing = new WorkspaceListing(
            Optional.of(new DepartmentWorkspace(dept)),
            List.of(new TeamWorkspace(team)));
        when(workspaceService.findForUser(any())).thenReturn(listing);

        IbizDriveUserDetails principal = principalForUser(userId);

        mvc.perform(get("/api/workspaces/me").with(user(principal)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.department.kind").value("DEPARTMENT"))
            .andExpect(jsonPath("$.department.id").value(deptId.toString()))
            .andExpect(jsonPath("$.department.name").value("Sales"))
            .andExpect(jsonPath("$.department.rootFolderId").value(deptRoot.toString()))
            .andExpect(jsonPath("$.teams").isArray())
            .andExpect(jsonPath("$.teams.length()").value(1))
            .andExpect(jsonPath("$.teams[0].kind").value("TEAM"))
            .andExpect(jsonPath("$.teams[0].id").value(teamId.toString()))
            .andExpect(jsonPath("$.teams[0].name").value("Alpha"))
            .andExpect(jsonPath("$.teams[0].rootFolderId").value(teamRoot.toString()));
    }

    @Test
    void getMe_returnsEmptyShape_whenUserHasNoWorkspaces() throws Exception {
        UUID userId = UUID.randomUUID();
        WorkspaceListing emptyListing = new WorkspaceListing(Optional.empty(), List.of());
        when(workspaceService.findForUser(any())).thenReturn(emptyListing);

        IbizDriveUserDetails principal = principalForUser(userId);

        mvc.perform(get("/api/workspaces/me").with(user(principal)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.department").doesNotExist())
            .andExpect(jsonPath("$.teams").isArray())
            .andExpect(jsonPath("$.teams.length()").value(0));
    }

    @Test
    void getMe_returns401_whenUnauthenticated() throws Exception {
        mvc.perform(get("/api/workspaces/me"))
            .andExpect(status().isUnauthorized());
    }

    private IbizDriveUserDetails principalForUser(UUID userId) {
        User u = new User(userId, "test@ibizdrive", "Test User",
            "{bcrypt}$2a$12$dummy", Role.MEMBER, true, false, OffsetDateTime.now());
        return new IbizDriveUserDetails(u);
    }
}
