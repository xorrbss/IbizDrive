package com.ibizdrive.auth;

import com.ibizdrive.common.health.HealthController;
import com.ibizdrive.config.SecurityConfig;
import com.ibizdrive.user.DbUserDetailsService;
import com.ibizdrive.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A1.2 — {@link SecurityConfig} 본 wiring 통합 테스트 (sliced).
 *
 * <p>검증 대상:
 * <ul>
 *   <li>{@code GET /api/auth/csrf} (permitAll) → 200 + {@code XSRF-TOKEN} 쿠키 + JSON 토큰</li>
 *   <li>임의 {@code POST} (인증 + CSRF 모두 필요한 경로) — CSRF 누락 시 403</li>
 *   <li>{@code POST} + 정상 CSRF → 인증이 없으므로 401 (CSRF는 통과)</li>
 *   <li>{@code GET /api/auth/me} 미인증 → 401 (anyRequest authenticated)</li>
 *   <li>{@code GET /api/health} → 200 (permitAll, smoke)</li>
 * </ul>
 *
 * <p>{@code @WebMvcTest}로 controller slice + SecurityConfig만 로드 — DB/Testcontainers 불필요.
 * {@link DbUserDetailsService}와 {@link UserRepository}는 SecurityFilterChain 의존성 그래프에 들어가지
 * 않지만 component scan 범위에 들어가므로 mock으로 대체. 실제 DB 동작은 별도 통합 테스트(A1.3+)에서 검증.
 */
@WebMvcTest(controllers = {CsrfTokenController.class, HealthController.class})
@Import(SecurityConfig.class)
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private DbUserDetailsService dbUserDetailsService;

    @MockBean
    private UserRepository userRepository;

    @Test
    void getCsrf_returnsTokenAndCookie() throws Exception {
        mvc.perform(get("/api/auth/csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.csrfToken", notNullValue()))
            .andExpect(cookie().exists("XSRF-TOKEN"))
            .andExpect(cookie().httpOnly("XSRF-TOKEN", false));
    }

    @Test
    void postWithoutCsrf_returns403() throws Exception {
        // 임의 mutation 경로 — endpoint 매핑 전에 CSRF 필터가 처리.
        mvc.perform(post("/api/folders").contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void postWithValidCsrf_passesCsrfButReturns401WhenUnauthenticated() throws Exception {
        // CSRF 통과 후 인증 부재 → 401. CookieCsrfTokenRepository는 cookie 값 == header 값을 검증.
        // .with(csrf())는 spring-security-test가 토큰을 attribute에 주입하여 통과시킴.
        mvc.perform(post("/api/folders")
                .contentType("application/json")
                .content("{}")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getMe_unauthenticated_returns401() throws Exception {
        mvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getHealth_anonymous_returns200() throws Exception {
        // /api/health는 permitAll — A1.2 매처에서 유지되어야 함.
        mvc.perform(get("/api/health"))
            .andExpect(status().isOk());
    }
}
