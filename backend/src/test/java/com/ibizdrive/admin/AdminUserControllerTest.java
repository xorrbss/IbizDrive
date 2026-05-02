package com.ibizdrive.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.auth.DuplicateEmailException;
import com.ibizdrive.auth.LoginAttemptTracker;
import com.ibizdrive.common.error.AuthExceptionHandler;
import com.ibizdrive.common.error.GlobalExceptionHandler;
import com.ibizdrive.config.MethodSecurityConfig;
import com.ibizdrive.config.SecurityConfig;
import com.ibizdrive.permission.IbizDrivePermissionEvaluator;
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
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR #21 — {@code POST /api/admin/users} sliced WebMvcTest (P2).
 *
 * <p>가드 매트릭스 (5건 + 추가 validation):
 * <ol>
 *   <li>ADMIN 인증 + 유효 입력 + CSRF → 200 + 응답 DTO (tempPassword 키 부재)</li>
 *   <li>익명 → 401 (anyRequest authenticated)</li>
 *   <li>MEMBER 인증 → 403 PERMISSION_DENIED (@PreAuthorize hasRole('ADMIN'))</li>
 *   <li>ADMIN + invalid 입력 → 400 VALIDATION_ERROR (email 형식, displayName blank, role null)</li>
 *   <li>ADMIN + 중복 email → 409 CONFLICT/DUPLICATE_EMAIL (signup 매핑 재사용)</li>
 * </ol>
 *
 * <p>{@code @PreAuthorize} 평가는 {@link MethodSecurityConfig} + Spring Security 표준 RoleVoter가 담당하므로
 * 본 슬라이스는 {@link IbizDrivePermissionEvaluator}/{@code PermissionResolver}를 mock으로 대체하여
 * SpEL {@code hasPermission(...)} 미사용 경로(본 controller는 {@code hasRole('ADMIN')}만)를 가볍게 띄운다.
 */
@WebMvcTest(controllers = AdminUserController.class)
@Import({
    SecurityConfig.class,
    MethodSecurityConfig.class,
    AuthExceptionHandler.class,
    GlobalExceptionHandler.class
})
class AdminUserControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @MockBean
    private AdminUserService adminUserService;

    // SecurityFilterChain / MethodSecurityConfig 그래프 충족용 mocks (본 controller는 사용 안 함).
    @MockBean
    private LoginAttemptTracker tracker;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private DbUserDetailsService dbUserDetailsService;

    @MockBean
    private IbizDrivePermissionEvaluator permissionEvaluator;

    private IbizDriveUserDetails adminPrincipal;
    private IbizDriveUserDetails memberPrincipal;

    @BeforeEach
    void setUp() {
        adminPrincipal = principalOf("11111111-1111-1111-1111-111111111111", Role.ADMIN);
        memberPrincipal = principalOf("22222222-2222-2222-2222-222222222222", Role.MEMBER);
    }

    private static IbizDriveUserDetails principalOf(String id, Role role) {
        User u = new User(
            UUID.fromString(id),
            role.name().toLowerCase() + "@example.com",
            role.name(),
            "{bcrypt}$2a$12$dummyhash",
            role,
            true,
            false,
            OffsetDateTime.now()
        );
        return new IbizDriveUserDetails(u);
    }

    // ── 200 OK (ADMIN) ──────────────────────────────────────────────────

    @Test
    void invite_200_admin() throws Exception {
        UUID newUserId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        when(adminUserService.invite(eq("alice@example.com"), eq("Alice"), eq(Role.MEMBER), any()))
            .thenReturn(new AdminInviteUserResponse(
                newUserId, "alice@example.com", "Alice", Role.MEMBER, true
            ));

        mvc.perform(post("/api/admin/users")
                .with(user(adminPrincipal))
                .with(csrf())
                .contentType("application/json")
                .content(body("alice@example.com", "Alice", "MEMBER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(newUserId.toString()))
            .andExpect(jsonPath("$.email").value("alice@example.com"))
            .andExpect(jsonPath("$.displayName").value("Alice"))
            .andExpect(jsonPath("$.role").value("MEMBER"))
            .andExpect(jsonPath("$.mustChangePassword").value(true))
            // ADR #21 invariant — 임시 PW 키는 응답에 절대 없음.
            .andExpect(jsonPath("$.tempPassword").doesNotExist())
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist());

        // actorId는 인증된 ADMIN의 user id로 전달.
        verify(adminUserService).invite(
            eq("alice@example.com"), eq("Alice"), eq(Role.MEMBER),
            eq(adminPrincipal.getUser().getId())
        );
    }

    // ── 401 (익명) ───────────────────────────────────────────────────────

    @Test
    void invite_401_anonymous() throws Exception {
        mvc.perform(post("/api/admin/users")
                .with(csrf())
                .contentType("application/json")
                .content(body("alice@example.com", "Alice", "MEMBER")))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(adminUserService);
    }

    // ── 403 (MEMBER) ─────────────────────────────────────────────────────

    @Test
    void invite_403_member() throws Exception {
        mvc.perform(post("/api/admin/users")
                .with(user(memberPrincipal))
                .with(csrf())
                .contentType("application/json")
                .content(body("alice@example.com", "Alice", "MEMBER")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));

        verifyNoInteractions(adminUserService);
    }

    // ── 400 VALIDATION_ERROR ─────────────────────────────────────────────

    @Test
    void invite_400_invalidEmail() throws Exception {
        mvc.perform(post("/api/admin/users")
                .with(user(adminPrincipal))
                .with(csrf())
                .contentType("application/json")
                .content(body("not-an-email", "Alice", "MEMBER")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.details.field").value("email"));

        verifyNoInteractions(adminUserService);
    }

    @Test
    void invite_400_blankDisplayName() throws Exception {
        mvc.perform(post("/api/admin/users")
                .with(user(adminPrincipal))
                .with(csrf())
                .contentType("application/json")
                .content(body("alice@example.com", "   ", "MEMBER")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.details.field").value("displayName"));

        verifyNoInteractions(adminUserService);
    }

    @Test
    void invite_400_nullRole() throws Exception {
        // role 미제공 — Bean Validation @NotNull
        String reqBody = json.writeValueAsString(Map.of(
            "email", "alice@example.com",
            "displayName", "Alice"
        ));
        mvc.perform(post("/api/admin/users")
                .with(user(adminPrincipal))
                .with(csrf())
                .contentType("application/json")
                .content(reqBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.details.field").value("role"));

        verifyNoInteractions(adminUserService);
    }

    // ── 409 DUPLICATE_EMAIL ──────────────────────────────────────────────

    @Test
    void invite_409_duplicateEmail() throws Exception {
        doThrow(new DuplicateEmailException())
            .when(adminUserService).invite(any(), any(), any(), any());

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
