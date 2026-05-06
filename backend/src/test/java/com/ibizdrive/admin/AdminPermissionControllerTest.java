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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link AdminPermissionController} sliced WebMvcTest — wave2-t5-admin-permission-matrix.
 *
 * <p>HTTP 매트릭스: 200 (admin) / 401 (anonymous) / 403 (member) / 400 (filter 검증 실패).
 */
@WebMvcTest(controllers = AdminPermissionController.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class,
    AuthExceptionHandler.class, AdminExceptionHandler.class, GlobalExceptionHandler.class})
class AdminPermissionControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AdminPermissionService adminPermissionService;

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

    @BeforeEach
    void setUp() {
        User admin = new User(ACTOR_ID, "admin@x", "Admin", null,
            Role.ADMIN, true, false, OffsetDateTime.now());
        adminPrincipal = new IbizDriveUserDetails(admin);
        User member = new User(UUID.randomUUID(), "m@x", "Member", null,
            Role.MEMBER, true, false, OffsetDateTime.now());
        memberPrincipal = new IbizDriveUserDetails(member);
    }

    @Test
    void list_admin_returns200() throws Exception {
        Pageable pageable = PageRequest.of(0, 20);
        AdminPermissionRowResponse row = new AdminPermissionRowResponse(
            UUID.randomUUID(), "user", UUID.randomUUID(), "Alice",
            "folder", UUID.randomUUID(), "Reports", "read",
            UUID.randomUUID(), "Granter",
            Instant.parse("2026-01-01T00:00:00Z"), null, false);
        when(adminPermissionService.list(any(), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of(row), pageable, 1));

        mvc.perform(get("/api/admin/permissions").with(user(adminPrincipal)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].subjectName").value("Alice"))
            .andExpect(jsonPath("$.content[0].resourceName").value("Reports"))
            .andExpect(jsonPath("$.content[0].preset").value("read"))
            .andExpect(jsonPath("$.content[0].isExpired").value(false))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void list_unauthenticated_returns401() throws Exception {
        mvc.perform(get("/api/admin/permissions"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(adminPermissionService);
    }

    @Test
    void list_member_returns403() throws Exception {
        mvc.perform(get("/api/admin/permissions").with(user(memberPrincipal)))
            .andExpect(status().isForbidden());
        verifyNoInteractions(adminPermissionService);
    }

    @Test
    void list_invalidSubjectType_returns400() throws Exception {
        when(adminPermissionService.list(any(), any()))
            .thenThrow(new IllegalArgumentException("subjectType must be one of user|department|role|everyone"));

        mvc.perform(get("/api/admin/permissions")
                .param("subjectType", "invalid")
                .with(user(adminPrincipal)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void list_subjectIdWithoutSubjectType_returns400() throws Exception {
        when(adminPermissionService.list(any(), any()))
            .thenThrow(new IllegalArgumentException("subjectType is required when subjectId is provided"));

        mvc.perform(get("/api/admin/permissions")
                .param("subjectId", UUID.randomUUID().toString())
                .with(user(adminPrincipal)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void list_sizeCappedAt100() throws Exception {
        Pageable expected = PageRequest.of(0, 100);
        when(adminPermissionService.list(any(), eq(expected)))
            .thenReturn(new PageImpl<>(List.of(), expected, 0));

        mvc.perform(get("/api/admin/permissions").param("size", "500")
                .with(user(adminPrincipal)))
            .andExpect(status().isOk());

        org.mockito.Mockito.verify(adminPermissionService).list(any(), eq(expected));
    }
}
