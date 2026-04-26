package com.ibizdrive.auth;

import com.ibizdrive.common.error.AuthExceptionHandler;
import com.ibizdrive.common.health.HealthController;
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
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A1.4 — {@code GET /api/auth/me}, {@code POST /api/auth/logout} 통합 테스트 (sliced).
 *
 * <p>검증 대상 (5건):
 * <ol>
 *   <li>{@code GET /me} 인증 → 200 + body schema (user/departments/roles/cacheKey)</li>
 *   <li>{@code GET /me} 미인증 → 401 (HttpStatusEntryPoint)</li>
 *   <li>{@code POST /logout} 인증 + CSRF → 204 + {@code Set-Cookie SESSION=; Max-Age=0}</li>
 *   <li>{@code POST /logout} 인증 + CSRF 미제공 → 403 (CsrfFilter 차단)</li>
 *   <li>{@code POST /logout} 미인증 + CSRF → 401 (인증 가드 차단)</li>
 * </ol>
 *
 * <p>{@code @WebMvcTest} slice — DB/Postgres 불필요. 인증된 principal은
 * {@link org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors#user(org.springframework.security.core.userdetails.UserDetails)}로 시뮬레이션.
 * 실제 세션 invalidate + cookie 만료 end-to-end는 A1.5 시나리오 테스트(@SpringBootTest)에서 검증.
 */
@WebMvcTest(controllers = {AuthController.class, CsrfTokenController.class, HealthController.class})
@Import({SecurityConfig.class, AuthService.class, LoginAttemptTracker.class, AuthExceptionHandler.class})
class AuthMeLogoutIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private DbUserDetailsService dbUserDetailsService; // SecurityConfig 그래프 충족용

    private IbizDriveUserDetails principal;

    @BeforeEach
    void setUp() {
        User u = new User(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "alice@example.com",
            "Alice",
            "{bcrypt}$2a$12$dummyhashvalueforauthmelogouttests1234567890abcdef",
            Role.MEMBER,
            true,
            false,
            OffsetDateTime.now()
        );
        principal = new IbizDriveUserDetails(u);
    }

    @Test
    void me_authenticated_returns200WithFullSchema() throws Exception {
        mvc.perform(get("/api/auth/me").with(user(principal)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.id").value("11111111-1111-1111-1111-111111111111"))
            .andExpect(jsonPath("$.user.email").value("alice@example.com"))
            .andExpect(jsonPath("$.user.name").value("Alice"))
            .andExpect(jsonPath("$.user.kind").value("human"))
            .andExpect(jsonPath("$.user.mustChangePassword").value(false))
            .andExpect(jsonPath("$.departments").isArray())
            .andExpect(jsonPath("$.roles[0]").value("MEMBER"))
            .andExpect(jsonPath("$.effectivePermissionsCacheKey")
                .value("11111111-1111-1111-1111-111111111111:MEMBER:v0"));
    }

    @Test
    void me_unauthenticated_returns401() throws Exception {
        mvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_authenticatedWithCsrf_returns204AndExpiresSessionCookie() throws Exception {
        mvc.perform(post("/api/auth/logout").with(user(principal)).with(csrf()))
            .andExpect(status().isNoContent())
            .andExpect(cookie().exists("SESSION"))
            .andExpect(cookie().maxAge("SESSION", 0))
            .andExpect(cookie().path("SESSION", "/"));
    }

    @Test
    void logout_authenticatedWithoutCsrf_returns403() throws Exception {
        mvc.perform(post("/api/auth/logout").with(user(principal)))
            .andExpect(status().isForbidden());
    }

    @Test
    void logout_unauthenticatedWithCsrf_returns401() throws Exception {
        mvc.perform(post("/api/auth/logout").with(csrf()))
            .andExpect(status().isUnauthorized());
    }
}
