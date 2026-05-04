package com.ibizdrive.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.auth.DuplicateEmailException;
import com.ibizdrive.auth.LoginAttemptTracker;
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
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
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
@Import({SecurityConfig.class, MethodSecurityConfig.class, AuthExceptionHandler.class})
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
}
