package com.ibizdrive.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.common.error.AuthExceptionHandler;
import com.ibizdrive.common.health.HealthController;
import com.ibizdrive.config.SecurityConfig;
import com.ibizdrive.permission.PermissionCacheKeyService;
import com.ibizdrive.user.DbUserDetailsService;
import com.ibizdrive.user.Role;
import com.ibizdrive.user.User;
import com.ibizdrive.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A1.3 — {@code POST /api/auth/login} 통합 테스트 (sliced).
 *
 * <p>검증 대상 (8건):
 * <ol>
 *   <li>정상 로그인 → 200 + body (계정 enumeration 방지 동일 응답 검증은 §3과 §5,6 함께)</li>
 *   <li>잘못된 PW → 401 INVALID_CREDENTIALS + 카운터 증가 (다음 호출에 영향)</li>
 *   <li>미존재 email → 401 INVALID_CREDENTIALS (timing-safe: BCrypt dummy verify)</li>
 *   <li>5회 실패 후 6회째 → 423 ACCOUNT_LOCKED + retryAfterSec</li>
 *   <li>비활성 계정 (`is_active=false`) → 401 (동일 응답)</li>
 *   <li>soft-deleted (`deleted_at IS NOT NULL`) → 401 (UserRepository에서 이미 필터)</li>
 *   <li>mustChangePassword=true → 200 + body.user.mustChangePassword=true</li>
 *   <li>CSRF 미제공 → 403</li>
 * </ol>
 *
 * <p>{@code @WebMvcTest} slice — DB/Postgres 불필요. {@link UserRepository} 모킹.
 * 실제 session(Spring Session JDBC) 동작은 A1.5 시나리오 테스트(@SpringBootTest)에서 검증.
 */
@WebMvcTest(controllers = {AuthController.class, CsrfTokenController.class, HealthController.class})
@Import({SecurityConfig.class, AuthService.class, LoginAttemptTracker.class, AuthExceptionHandler.class, PermissionCacheKeyService.class})
class LoginControllerIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LoginAttemptTracker tracker;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private DbUserDetailsService dbUserDetailsService; // SecurityConfig 컴포넌트 그래프 충족용

    private static final String EMAIL = "alice@example.com";
    private static final String VALID_PW = "Sup3rSecret_Pw_12";
    private User active;
    private String activeHash;

    @BeforeEach
    void seedFixtures() {
        // tracker는 @Component (singleton) — 테스트 간 잠금 잔여 제거.
        // 본 테스트 클래스가 사용하는 모든 키를 reset.
        tracker.recordSuccess(EMAIL);
        tracker.recordSuccess("ghost@example.com");
        tracker.recordSuccess("inactive@example.com");
        tracker.recordSuccess("deleted@example.com");
        tracker.recordSuccess("newhire@example.com");

        activeHash = passwordEncoder.encode(VALID_PW);
        active = new User(
            UUID.randomUUID(),
            EMAIL,
            "Alice",
            activeHash,
            Role.MEMBER,
            true,
            false,
            OffsetDateTime.now()
        );
        // 기본 stub: alice는 활성 사용자 — lenient (모든 테스트에서 호출되지 않을 수 있음)
        lenient().when(userRepository.findActiveByEmail(EMAIL)).thenReturn(Optional.of(active));
    }

    @Test
    void login_validCredentials_returns200WithBody() throws Exception {
        mvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType("application/json")
                .content(body(EMAIL, VALID_PW)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.email").value(EMAIL))
            .andExpect(jsonPath("$.user.mustChangePassword").value(false))
            .andExpect(jsonPath("$.roles[0]").value("MEMBER"))
            .andExpect(jsonPath("$.effectivePermissionsCacheKey").exists());
    }

    @Test
    void login_wrongPassword_returns401InvalidCredentials() throws Exception {
        mvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType("application/json")
                .content(body(EMAIL, "wrong-password")))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.reason").value("INVALID_CREDENTIALS"));
    }

    @Test
    void login_emailNotFound_returns401InvalidCredentials() throws Exception {
        when(userRepository.findActiveByEmail("ghost@example.com")).thenReturn(Optional.empty());

        mvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType("application/json")
                .content(body("ghost@example.com", "any-password")))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.reason").value("INVALID_CREDENTIALS"));
    }

    @Test
    void login_fiveFailures_thenSixthReturns423WithRetryAfter() throws Exception {
        // 5회 실패
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content(body(EMAIL, "wrong-password")))
                .andExpect(status().isUnauthorized());
        }

        // 6회째 — 정확한 PW여도 lockout 우선 → 423
        mvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType("application/json")
                .content(body(EMAIL, VALID_PW)))
            .andExpect(status().isLocked())
            .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"))
            .andExpect(jsonPath("$.retryAfterSec").isNumber());
    }

    @Test
    void login_inactiveAccount_returns401InvalidCredentials() throws Exception {
        User inactive = new User(
            UUID.randomUUID(),
            "inactive@example.com",
            "Inactive",
            activeHash,
            Role.MEMBER,
            false,                  // is_active=false
            false,
            OffsetDateTime.now()
        );
        when(userRepository.findActiveByEmail("inactive@example.com")).thenReturn(Optional.of(inactive));

        mvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType("application/json")
                .content(body("inactive@example.com", VALID_PW)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.reason").value("INVALID_CREDENTIALS"));
    }

    @Test
    void login_softDeleted_returns401InvalidCredentials() throws Exception {
        // soft-deleted는 UserRepository.findActiveByEmail이 이미 필터 — empty 반환
        when(userRepository.findActiveByEmail("deleted@example.com")).thenReturn(Optional.empty());

        mvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType("application/json")
                .content(body("deleted@example.com", VALID_PW)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.reason").value("INVALID_CREDENTIALS"));
    }

    @Test
    void login_mustChangePassword_returns200WithFlag() throws Exception {
        User mustChange = new User(
            UUID.randomUUID(),
            "newhire@example.com",
            "New Hire",
            activeHash,
            Role.MEMBER,
            true,
            true,                   // mustChangePassword=true
            OffsetDateTime.now()
        );
        when(userRepository.findActiveByEmail("newhire@example.com")).thenReturn(Optional.of(mustChange));

        mvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType("application/json")
                .content(body("newhire@example.com", VALID_PW)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.mustChangePassword").value(true));
    }

    @Test
    void login_withoutCsrf_returns403() throws Exception {
        mvc.perform(post("/api/auth/login")
                .contentType("application/json")
                .content(body(EMAIL, VALID_PW)))
            .andExpect(status().isForbidden());
    }

    private String body(String email, String password) throws Exception {
        return json.writeValueAsString(Map.of("email", email, "password", password));
    }
}
