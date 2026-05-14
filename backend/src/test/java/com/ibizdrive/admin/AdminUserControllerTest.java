package com.ibizdrive.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.auth.DuplicateEmailException;
import com.ibizdrive.auth.LoginAttemptTracker;
import com.ibizdrive.common.error.AdminExceptionHandler;
import com.ibizdrive.common.error.AuthExceptionHandler;
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

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
 * {@code POST /api/admin/users} sliced WebMvcTest — m-admin-entry-rewrite P6.
 *
 * <p>HTTP 매트릭스 (docs/02 §7.4):
 * <ol>
 *   <li>200 OK — ADMIN principal + 유효 입력 (응답 DTO에 tempPassword 미포함)</li>
 *   <li>400 VALIDATION_ERROR — invalid email 형식</li>
 *   <li>400 VALIDATION_ERROR — blank displayName</li>
 *   <li>400 VALIDATION_ERROR — null role</li>
 *   <li>401 — 미인증 (entry point)</li>
 *   <li>403 — MEMBER principal (@PreAuthorize 차단)</li>
 *   <li>409 CONFLICT/DUPLICATE_EMAIL — service throws DuplicateEmailException</li>
 *   <li>200 OK 응답 본문에 tempPassword 키 부재 (회귀 가드, docs/03 §2.8)</li>
 * </ol>
 *
 * <p>{@link MethodSecurityConfig} import — {@code @PreAuthorize}가 활성화되어야 403 매트릭스 검증 가능.
 * {@link PermissionEvaluator}는 본 endpoint가 사용하지 않으므로 {@code @MockBean}으로 충족만.
 */
@WebMvcTest(controllers = AdminUserController.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class, AuthExceptionHandler.class, AdminExceptionHandler.class})
class AdminUserControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @MockBean
    private AdminUserService adminUserService;

    @MockBean
    private LoginAttemptTracker tracker;       // SecurityConfig 그래프 충족

    @MockBean
    private UserRepository userRepository;     // SecurityConfig 그래프 충족

    @MockBean
    private DbUserDetailsService dbUserDetailsService;

    @MockBean
    private PermissionEvaluator permissionEvaluator; // MethodSecurityConfig 그래프 충족

    @MockBean
    private com.ibizdrive.approval.PendingApprovalService approvalService; // Phase 3b dual-approval 그래프 충족 — gate=false 기본이라 실제 호출 0

    private IbizDriveUserDetails adminPrincipal;
    private IbizDriveUserDetails memberPrincipal;

    private static final UUID ACTOR_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID CREATED_USER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @BeforeEach
    void setUp() {
        User admin = new User(ACTOR_ID, "admin@example.com", "Admin",
            "{bcrypt}$2a$12$dummy", Role.ADMIN, true, false, OffsetDateTime.now());
        adminPrincipal = new IbizDriveUserDetails(admin);

        User member = new User(UUID.randomUUID(), "member@example.com", "Member",
            "{bcrypt}$2a$12$dummy", Role.MEMBER, true, false, OffsetDateTime.now());
        memberPrincipal = new IbizDriveUserDetails(member);
    }

    @Test
    void invite_adminAuthenticated_returns200WithBodyWithoutTempPassword() throws Exception {
        User created = new User(CREATED_USER_ID, "bob@example.com", "Bob",
            "{bcrypt}$2a$12$created", Role.MEMBER, true, true, OffsetDateTime.now());
        when(adminUserService.invite(eq("bob@example.com"), eq("Bob"), eq(Role.MEMBER), eq(ACTOR_ID)))
            .thenReturn(created);

        mvc.perform(post("/api/admin/users")
                .with(user(adminPrincipal))
                .with(csrf())
                .contentType("application/json")
                .content(body("bob@example.com", "Bob", "MEMBER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(CREATED_USER_ID.toString()))
            .andExpect(jsonPath("$.email").value("bob@example.com"))
            .andExpect(jsonPath("$.displayName").value("Bob"))
            .andExpect(jsonPath("$.role").value("MEMBER"))
            .andExpect(jsonPath("$.mustChangePassword").value(true))
            // 회귀 가드 — 임시 PW가 응답에 노출되지 않음 (docs/03 §2.8)
            .andExpect(jsonPath("$.tempPassword").doesNotExist())
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist());

        verify(adminUserService).invite("bob@example.com", "Bob", Role.MEMBER, ACTOR_ID);
    }

    @Test
    void invite_invalidEmail_returns400Validation() throws Exception {
        mvc.perform(post("/api/admin/users")
                .with(user(adminPrincipal))
                .with(csrf())
                .contentType("application/json")
                .content(body("not-an-email", "Bob", "MEMBER")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.details.field").value("email"));

        verifyNoInteractions(adminUserService);
    }

    @Test
    void invite_blankDisplayName_returns400Validation() throws Exception {
        mvc.perform(post("/api/admin/users")
                .with(user(adminPrincipal))
                .with(csrf())
                .contentType("application/json")
                .content(body("bob@example.com", "", "MEMBER")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.details.field").value("displayName"));

        verifyNoInteractions(adminUserService);
    }

    @Test
    void invite_nullRole_returns400Validation() throws Exception {
        // role 키 자체를 누락. @NotNull 검증이 필드 단위로 발생.
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("email", "bob@example.com");
        payload.put("displayName", "Bob");
        // role 미포함

        mvc.perform(post("/api/admin/users")
                .with(user(adminPrincipal))
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(payload)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.details.field").value("role"));

        verifyNoInteractions(adminUserService);
    }

    @Test
    void invite_unauthenticated_returns401() throws Exception {
        mvc.perform(post("/api/admin/users")
                .with(csrf())
                .contentType("application/json")
                .content(body("bob@example.com", "Bob", "MEMBER")))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(adminUserService);
    }

    @Test
    void invite_memberAuthenticated_returns403() throws Exception {
        mvc.perform(post("/api/admin/users")
                .with(user(memberPrincipal))
                .with(csrf())
                .contentType("application/json")
                .content(body("bob@example.com", "Bob", "MEMBER")))
            .andExpect(status().isForbidden());

        verifyNoInteractions(adminUserService);
    }

    @Test
    void invite_duplicateEmail_returns409() throws Exception {
        doThrow(new DuplicateEmailException())
            .when(adminUserService).invite(anyString(), anyString(), any(Role.class), any(UUID.class));

        mvc.perform(post("/api/admin/users")
                .with(user(adminPrincipal))
                .with(csrf())
                .contentType("application/json")
                .content(body("dup@example.com", "Dup", "MEMBER")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONFLICT"))
            .andExpect(jsonPath("$.reason").value("DUPLICATE_EMAIL"));
    }

    private String body(String email, String displayName, String role) throws Exception {
        return json.writeValueAsString(Map.of(
            "email", email,
            "displayName", displayName,
            "role", role
        ));
    }

    // ===== GET /api/admin/users =====

    @Test
    void list_adminAuthenticated_returns200WithPage() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        User u1 = new User(UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "alice@example.com", "Alice", "{bcrypt}h", Role.ADMIN, true, false, now);
        User u2 = new User(UUID.fromString("22222222-2222-2222-2222-222222222222"),
            "bob@example.com", "Bob", "{bcrypt}h", Role.MEMBER, true, false, now);
        Pageable pageable = PageRequest.of(0, 50);
        when(adminUserService.list(eq(pageable), eq(null))).thenReturn(new PageImpl<>(List.of(u1, u2), pageable, 2));

        mvc.perform(get("/api/admin/users").with(user(adminPrincipal)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.content[0].email").value("alice@example.com"))
            .andExpect(jsonPath("$.content[0].role").value("ADMIN"))
            .andExpect(jsonPath("$.content[0].isActive").value(true))
            .andExpect(jsonPath("$.content[1].email").value("bob@example.com"))
            .andExpect(jsonPath("$.totalElements").value(2))
            // 회귀 가드 — 비밀번호 hash 응답 미노출 (docs/03 §2.8)
            .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
            .andExpect(jsonPath("$.content[0].password").doesNotExist());
    }

    @Test
    void list_pageSizeRespected() throws Exception {
        Pageable pageable = PageRequest.of(1, 10);
        when(adminUserService.list(eq(pageable), eq(null))).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        mvc.perform(get("/api/admin/users")
                .param("page", "1").param("size", "10")
                .with(user(adminPrincipal)))
            .andExpect(status().isOk());

        verify(adminUserService).list(eq(pageable), eq(null));
    }

    @Test
    void list_searchQueryPropagated() throws Exception {
        // admin-user-search-update — ?q= 파라미터가 service.list 두번째 인자로 전파
        Pageable pageable = PageRequest.of(0, 50);
        when(adminUserService.list(eq(pageable), eq("alice"))).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        mvc.perform(get("/api/admin/users").param("q", "alice").with(user(adminPrincipal)))
            .andExpect(status().isOk());

        verify(adminUserService).list(eq(pageable), eq("alice"));
    }

    @Test
    void list_unauthenticated_returns401() throws Exception {
        mvc.perform(get("/api/admin/users"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(adminUserService);
    }

    @Test
    void list_memberAuthenticated_returns403() throws Exception {
        mvc.perform(get("/api/admin/users").with(user(memberPrincipal)))
            .andExpect(status().isForbidden());
        verifyNoInteractions(adminUserService);
    }

    // ===== PATCH /api/admin/users/{id} =====

    @Test
    void patch_changeRole_returns200() throws Exception {
        UUID targetId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        User updated = new User(targetId, "x@example.com", "X", "{bcrypt}h",
            Role.AUDITOR, true, false, OffsetDateTime.now());
        when(adminUserService.changeRole(eq(targetId), eq(Role.AUDITOR), eq(ACTOR_ID))).thenReturn(updated);

        mvc.perform(patch("/api/admin/users/{id}", targetId)
                .with(user(adminPrincipal)).with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("role", "AUDITOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(targetId.toString()))
            .andExpect(jsonPath("$.role").value("AUDITOR"));

        verify(adminUserService).changeRole(targetId, Role.AUDITOR, ACTOR_ID);
    }

    @Test
    void patch_deactivate_returns200() throws Exception {
        UUID targetId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        User updated = new User(targetId, "x@example.com", "X", "{bcrypt}h",
            Role.MEMBER, false, false, OffsetDateTime.now());
        when(adminUserService.deactivate(eq(targetId), eq(ACTOR_ID))).thenReturn(updated);

        mvc.perform(patch("/api/admin/users/{id}", targetId)
                .with(user(adminPrincipal)).with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("isActive", false))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isActive").value(false));

        verify(adminUserService).deactivate(targetId, ACTOR_ID);
    }

    @Test
    void patch_emptyBody_returns400Validation() throws Exception {
        UUID targetId = UUID.randomUUID();
        mvc.perform(patch("/api/admin/users/{id}", targetId)
                .with(user(adminPrincipal)).with(csrf())
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.details.field").value("body"));
        verifyNoInteractions(adminUserService);
    }

    // admin-user-search-update — isActive=true 이제 reactivate로 처리 (Wave 1 — T1)
    @Test
    void patch_reactivate_returns200() throws Exception {
        UUID targetId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        User updated = new User(targetId, "react@example.com", "React", "{bcrypt}h",
            Role.MEMBER, true, false, OffsetDateTime.now());
        when(adminUserService.reactivate(eq(targetId), eq(ACTOR_ID))).thenReturn(updated);

        mvc.perform(patch("/api/admin/users/{id}", targetId)
                .with(user(adminPrincipal)).with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("isActive", true))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isActive").value(true));

        verify(adminUserService).reactivate(targetId, ACTOR_ID);
    }

    @Test
    void patch_changeDisplayName_returns200() throws Exception {
        UUID targetId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        User updated = new User(targetId, "rn@example.com", "Renamed", "{bcrypt}h",
            Role.MEMBER, true, false, OffsetDateTime.now());
        when(adminUserService.changeDisplayName(eq(targetId), eq("Renamed"), eq(ACTOR_ID))).thenReturn(updated);

        mvc.perform(patch("/api/admin/users/{id}", targetId)
                .with(user(adminPrincipal)).with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("displayName", "Renamed"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.displayName").value("Renamed"));

        verify(adminUserService).changeDisplayName(targetId, "Renamed", ACTOR_ID);
    }

    @Test
    void patch_blankDisplayName_returns400() throws Exception {
        // service의 IllegalArgumentException → GlobalExceptionHandler 400 매핑
        UUID targetId = UUID.fromString("77777777-7777-7777-7777-777777777777");
        doThrow(new IllegalArgumentException("displayName must not be blank"))
            .when(adminUserService).changeDisplayName(eq(targetId), anyString(), eq(ACTOR_ID));

        mvc.perform(patch("/api/admin/users/{id}", targetId)
                .with(user(adminPrincipal)).with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("displayName", "   "))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void patch_targetNotFound_returns404() throws Exception {
        UUID targetId = UUID.randomUUID();
        doThrow(new AdminUserNotFoundException(targetId.toString()))
            .when(adminUserService).changeRole(eq(targetId), any(Role.class), any(UUID.class));

        mvc.perform(patch("/api/admin/users/{id}", targetId)
                .with(user(adminPrincipal)).with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("role", "MEMBER"))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.reason").value("USER_NOT_FOUND"));
    }

    @Test
    void patch_selfDemote_returns403() throws Exception {
        doThrow(new AdminSelfProtectionException("self-demote forbidden"))
            .when(adminUserService).changeRole(eq(ACTOR_ID), any(Role.class), eq(ACTOR_ID));

        mvc.perform(patch("/api/admin/users/{id}", ACTOR_ID)
                .with(user(adminPrincipal)).with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("role", "MEMBER"))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
            .andExpect(jsonPath("$.reason").value("SELF_PROTECTION"));
    }

    @Test
    void patch_selfDeactivate_returns403() throws Exception {
        doThrow(new AdminSelfProtectionException("self-deactivate forbidden"))
            .when(adminUserService).deactivate(eq(ACTOR_ID), eq(ACTOR_ID));

        mvc.perform(patch("/api/admin/users/{id}", ACTOR_ID)
                .with(user(adminPrincipal)).with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("isActive", false))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
            .andExpect(jsonPath("$.reason").value("SELF_PROTECTION"));
    }

    @Test
    void patch_unauthenticated_returns401() throws Exception {
        mvc.perform(patch("/api/admin/users/{id}", UUID.randomUUID())
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("role", "MEMBER"))))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(adminUserService);
    }

    @Test
    void patch_memberAuthenticated_returns403() throws Exception {
        mvc.perform(patch("/api/admin/users/{id}", UUID.randomUUID())
                .with(user(memberPrincipal)).with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("role", "MEMBER"))))
            .andExpect(status().isForbidden());
        verifyNoInteractions(adminUserService);
    }

    // ── admin-user-lock-unlock: POST /:id/lock + DELETE /:id/lock ──────

    @Test
    void lock_adminPrincipal_returns200WithUpdatedUser() throws Exception {
        UUID targetId = UUID.randomUUID();
        User locked = new User(
            targetId, "lockme@example.com", "Lock Me",
            "{bcrypt}$2a$12$dummy",
            Role.MEMBER, true, false, OffsetDateTime.now()
        );
        locked.lock(OffsetDateTime.now());
        when(adminUserService.lockUser(eq(targetId), eq(ACTOR_ID))).thenReturn(locked);

        mvc.perform(post("/api/admin/users/{id}/lock", targetId)
                .with(user(adminPrincipal)).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(targetId.toString()))
            .andExpect(jsonPath("$.email").value("lockme@example.com"));
    }

    @Test
    void lock_selfLock_returns403SelfProtection() throws Exception {
        doThrow(new AdminSelfProtectionException("self-lock forbidden"))
            .when(adminUserService).lockUser(eq(ACTOR_ID), eq(ACTOR_ID));

        mvc.perform(post("/api/admin/users/{id}/lock", ACTOR_ID)
                .with(user(adminPrincipal)).with(csrf()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
            .andExpect(jsonPath("$.reason").value("SELF_PROTECTION"));
    }

    @Test
    void lock_targetNotFound_returns404() throws Exception {
        UUID ghost = UUID.randomUUID();
        doThrow(new AdminUserNotFoundException(ghost.toString()))
            .when(adminUserService).lockUser(eq(ghost), any(UUID.class));

        mvc.perform(post("/api/admin/users/{id}/lock", ghost)
                .with(user(adminPrincipal)).with(csrf()))
            .andExpect(status().isNotFound());
    }

    @Test
    void lock_unauthenticated_returns401() throws Exception {
        mvc.perform(post("/api/admin/users/{id}/lock", UUID.randomUUID())
                .with(csrf()))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(adminUserService);
    }

    @Test
    void lock_memberAuthenticated_returns403() throws Exception {
        mvc.perform(post("/api/admin/users/{id}/lock", UUID.randomUUID())
                .with(user(memberPrincipal)).with(csrf()))
            .andExpect(status().isForbidden());
        verifyNoInteractions(adminUserService);
    }

    @Test
    void unlock_adminPrincipal_returns204() throws Exception {
        UUID targetId = UUID.randomUUID();
        User unlocked = new User(
            targetId, "unlockme@example.com", "Unlock Me",
            "{bcrypt}$2a$12$dummy",
            Role.MEMBER, true, false, OffsetDateTime.now()
        );
        when(adminUserService.unlockUser(eq(targetId), eq(ACTOR_ID))).thenReturn(unlocked);

        mvc.perform(delete("/api/admin/users/{id}/lock", targetId)
                .with(user(adminPrincipal)).with(csrf()))
            .andExpect(status().isNoContent());
    }

    @Test
    void unlock_memberAuthenticated_returns403() throws Exception {
        mvc.perform(delete("/api/admin/users/{id}/lock", UUID.randomUUID())
                .with(user(memberPrincipal)).with(csrf()))
            .andExpect(status().isForbidden());
        verifyNoInteractions(adminUserService);
    }
}
