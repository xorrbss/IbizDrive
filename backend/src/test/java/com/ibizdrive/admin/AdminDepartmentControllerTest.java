package com.ibizdrive.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.auth.LoginAttemptTracker;
import com.ibizdrive.common.error.AdminExceptionHandler;
import com.ibizdrive.common.error.AuthExceptionHandler;
import com.ibizdrive.common.error.GlobalExceptionHandler;
import com.ibizdrive.common.error.ResourceNotFoundException;
import com.ibizdrive.config.MethodSecurityConfig;
import com.ibizdrive.config.SecurityConfig;
import com.ibizdrive.department.Department;
import com.ibizdrive.department.DepartmentConflictException;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link AdminDepartmentController} sliced WebMvcTest — admin-department-crud (Wave 2 T4).
 *
 * <p>{@link AdminUserControllerTest} 패턴 mirror. HTTP 매트릭스 (docs/02 §7.x):
 * 200 / 400 / 401 / 403 / 404 / 409 모두 검증.
 */
@WebMvcTest(controllers = AdminDepartmentController.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class,
    AuthExceptionHandler.class, AdminExceptionHandler.class, GlobalExceptionHandler.class})
class AdminDepartmentControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @MockBean
    private AdminDepartmentService adminDepartmentService;

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
    private static final UUID DEPT_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    @BeforeEach
    void setUp() {
        User admin = new User(ACTOR_ID, "admin@example.com", "Admin",
            "{bcrypt}$2a$12$dummy", Role.ADMIN, true, false, OffsetDateTime.now());
        adminPrincipal = new IbizDriveUserDetails(admin);

        User member = new User(UUID.randomUUID(), "m@example.com", "Member",
            "{bcrypt}$2a$12$dummy", Role.MEMBER, true, false, OffsetDateTime.now());
        memberPrincipal = new IbizDriveUserDetails(member);
    }

    // ===== GET /api/admin/departments =====

    @Test
    void list_adminAuthenticated_returns200WithPage() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        Department a = new Department(UUID.fromString("11111111-1111-1111-1111-111111111111"), "Eng", now);
        Department b = new Department(UUID.fromString("22222222-2222-2222-2222-222222222222"), "Sales", now);
        Pageable pageable = PageRequest.of(0, 50);
        when(adminDepartmentService.list(eq(pageable), isNull()))
            .thenReturn(new PageImpl<>(List.of(a, b), pageable, 2));

        mvc.perform(get("/api/admin/departments").with(user(adminPrincipal)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.content[0].name").value("Eng"))
            .andExpect(jsonPath("$.content[0].isActive").value(true))
            .andExpect(jsonPath("$.content[1].name").value("Sales"))
            .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void list_passesQueryParam() throws Exception {
        Pageable pageable = PageRequest.of(0, 50);
        when(adminDepartmentService.list(eq(pageable), eq("eng")))
            .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        mvc.perform(get("/api/admin/departments").param("q", "eng").with(user(adminPrincipal)))
            .andExpect(status().isOk());

        verify(adminDepartmentService).list(eq(pageable), eq("eng"));
    }

    @Test
    void list_unauthenticated_returns401() throws Exception {
        mvc.perform(get("/api/admin/departments"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(adminDepartmentService);
    }

    @Test
    void list_memberAuthenticated_returns403() throws Exception {
        mvc.perform(get("/api/admin/departments").with(user(memberPrincipal)))
            .andExpect(status().isForbidden());
        verifyNoInteractions(adminDepartmentService);
    }

    // ===== POST /api/admin/departments =====

    @Test
    void create_adminAuthenticated_returns200() throws Exception {
        Department created = new Department(DEPT_ID, "Eng", OffsetDateTime.now());
        when(adminDepartmentService.create(eq("Eng"), eq(ACTOR_ID))).thenReturn(created);

        mvc.perform(post("/api/admin/departments")
                .with(user(adminPrincipal)).with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("name", "Eng"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(DEPT_ID.toString()))
            .andExpect(jsonPath("$.name").value("Eng"))
            .andExpect(jsonPath("$.isActive").value(true));

        verify(adminDepartmentService).create("Eng", ACTOR_ID);
    }

    @Test
    void create_blankName_returns400Validation() throws Exception {
        mvc.perform(post("/api/admin/departments")
                .with(user(adminPrincipal)).with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("name", ""))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.details.field").value("name"));
        verifyNoInteractions(adminDepartmentService);
    }

    @Test
    void create_unauthenticated_returns401() throws Exception {
        mvc.perform(post("/api/admin/departments").with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("name", "Eng"))))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(adminDepartmentService);
    }

    @Test
    void create_memberAuthenticated_returns403() throws Exception {
        mvc.perform(post("/api/admin/departments").with(user(memberPrincipal)).with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("name", "Eng"))))
            .andExpect(status().isForbidden());
        verifyNoInteractions(adminDepartmentService);
    }

    @Test
    void create_conflict_returns409() throws Exception {
        doThrow(new DepartmentConflictException())
            .when(adminDepartmentService).create(anyString(), any(UUID.class));

        mvc.perform(post("/api/admin/departments").with(user(adminPrincipal)).with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("name", "Dup"))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("DEPARTMENT_CONFLICT"));
    }

    // ===== PATCH /api/admin/departments/{id} =====

    @Test
    void patch_rename_returns200() throws Exception {
        Department renamed = new Department(DEPT_ID, "NewName", OffsetDateTime.now());
        when(adminDepartmentService.rename(eq(DEPT_ID), eq("NewName"), eq(ACTOR_ID))).thenReturn(renamed);

        mvc.perform(patch("/api/admin/departments/{id}", DEPT_ID).with(user(adminPrincipal)).with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("name", "NewName"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("NewName"));

        verify(adminDepartmentService).rename(DEPT_ID, "NewName", ACTOR_ID);
    }

    @Test
    void patch_deactivate_returns200() throws Exception {
        Department dept = new Department(DEPT_ID, "Eng", OffsetDateTime.now());
        dept.deactivate();
        when(adminDepartmentService.deactivate(eq(DEPT_ID), eq(ACTOR_ID))).thenReturn(dept);

        mvc.perform(patch("/api/admin/departments/{id}", DEPT_ID).with(user(adminPrincipal)).with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("isActive", false))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isActive").value(false));

        verify(adminDepartmentService).deactivate(DEPT_ID, ACTOR_ID);
    }

    @Test
    void patch_reactivate_returns200() throws Exception {
        Department dept = new Department(DEPT_ID, "Eng", OffsetDateTime.now());
        when(adminDepartmentService.reactivate(eq(DEPT_ID), eq(ACTOR_ID))).thenReturn(dept);

        mvc.perform(patch("/api/admin/departments/{id}", DEPT_ID).with(user(adminPrincipal)).with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("isActive", true))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isActive").value(true));

        verify(adminDepartmentService).reactivate(DEPT_ID, ACTOR_ID);
    }

    @Test
    void patch_emptyBody_returns400() throws Exception {
        mvc.perform(patch("/api/admin/departments/{id}", DEPT_ID).with(user(adminPrincipal)).with(csrf())
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.details.field").value("body"));
        verifyNoInteractions(adminDepartmentService);
    }

    @Test
    void patch_targetNotFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("department not found: " + DEPT_ID))
            .when(adminDepartmentService).rename(eq(DEPT_ID), anyString(), any(UUID.class));

        mvc.perform(patch("/api/admin/departments/{id}", DEPT_ID).with(user(adminPrincipal)).with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("name", "X"))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void patch_renameConflict_returns409() throws Exception {
        doThrow(new DepartmentConflictException())
            .when(adminDepartmentService).rename(eq(DEPT_ID), anyString(), any(UUID.class));

        mvc.perform(patch("/api/admin/departments/{id}", DEPT_ID).with(user(adminPrincipal)).with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("name", "Dup"))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("DEPARTMENT_CONFLICT"));
    }

    @Test
    void patch_unauthenticated_returns401() throws Exception {
        mvc.perform(patch("/api/admin/departments/{id}", DEPT_ID).with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("name", "X"))))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(adminDepartmentService);
    }

    @Test
    void patch_memberAuthenticated_returns403() throws Exception {
        mvc.perform(patch("/api/admin/departments/{id}", DEPT_ID).with(user(memberPrincipal)).with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("name", "X"))))
            .andExpect(status().isForbidden());
        verifyNoInteractions(adminDepartmentService);
    }
}
