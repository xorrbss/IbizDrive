package com.ibizdrive.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.auth.dto.LoginResponse;
import com.ibizdrive.auth.dto.SignupRequest;
import com.ibizdrive.common.error.AuthExceptionHandler;
import com.ibizdrive.common.health.HealthController;
import com.ibizdrive.config.SecurityConfig;
import com.ibizdrive.permission.PermissionCacheKeyService;
import com.ibizdrive.user.DbUserDetailsService;
import com.ibizdrive.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR #41 — {@code POST /api/auth/signup} 통합 테스트 (sliced).
 *
 * <p>검증 대상 (6건):
 * <ol>
 *   <li>정상 입력 → 201 + LoginResponse body</li>
 *   <li>중복 email → 409 CONFLICT/DUPLICATE_EMAIL</li>
 *   <li>약한 password (7자) → 400 VALIDATION_ERROR</li>
 *   <li>잘못된 email 형식 → 400 VALIDATION_ERROR</li>
 *   <li>blank displayName → 400 VALIDATION_ERROR</li>
 *   <li>CSRF 미제공 + permitAll → 201 (signup endpoint은 CSRF ignore)</li>
 * </ol>
 *
 * <p>{@code @WebMvcTest} slice — DB 불필요. {@link SignupService}와 {@link AuthService}는 mock.
 * 실제 DB 트랜잭션 + audit_log INSERT는 추후 SpringBootTest 시나리오 테스트에서 검증.
 */
@WebMvcTest(controllers = {AuthController.class, CsrfTokenController.class, HealthController.class})
@Import({SecurityConfig.class, AuthExceptionHandler.class, PermissionCacheKeyService.class})
class AuthControllerSignupTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @MockBean
    private SignupService signupService;

    @MockBean
    private AuthService authService;

    @MockBean
    private LoginAttemptTracker tracker; // SecurityConfig 그래프 충족 (미사용)

    @MockBean
    private UserRepository userRepository; // SecurityConfig 그래프 충족

    @MockBean
    private DbUserDetailsService dbUserDetailsService;

    private static final LoginResponse OK_RESPONSE = new LoginResponse(
        new LoginResponse.UserInfo("11111111-1111-1111-1111-111111111111",
            "alice@example.com", "Alice", "human", false),
        List.of(),
        List.of("MEMBER"),
        "abcdef0123456789"
    );

    @Test
    void signup_validInput_returns201WithBody() throws Exception {
        when(signupService.signup(any(SignupRequest.class), any(), any())).thenReturn(OK_RESPONSE);

        mvc.perform(post("/api/auth/signup")
                .contentType("application/json")
                .content(body("alice@example.com", "Sup3rSecret_Pw_12", "Alice")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.user.email").value("alice@example.com"))
            .andExpect(jsonPath("$.user.name").value("Alice"))
            .andExpect(jsonPath("$.roles[0]").value("MEMBER"))
            .andExpect(jsonPath("$.effectivePermissionsCacheKey").exists());

        verify(signupService).signup(any(SignupRequest.class), any(), any());
    }

    @Test
    void signup_duplicateEmail_returns409() throws Exception {
        doThrow(new DuplicateEmailException())
            .when(signupService).signup(any(SignupRequest.class), any(), any());

        mvc.perform(post("/api/auth/signup")
                .contentType("application/json")
                .content(body("dup@example.com", "Sup3rSecret_Pw_12", "Dup")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONFLICT"))
            .andExpect(jsonPath("$.reason").value("DUPLICATE_EMAIL"));
    }

    @Test
    void signup_weakPassword_returns400Validation() throws Exception {
        mvc.perform(post("/api/auth/signup")
                .contentType("application/json")
                .content(body("alice@example.com", "short7c", "Alice")))   // 7자
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.details.field").value("password"));

        verifyNoInteractions(signupService);
    }

    @Test
    void signup_invalidEmail_returns400Validation() throws Exception {
        mvc.perform(post("/api/auth/signup")
                .contentType("application/json")
                .content(body("not-an-email", "Sup3rSecret_Pw_12", "Alice")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.details.field").value("email"));

        verifyNoInteractions(signupService);
    }

    @Test
    void signup_blankDisplayName_returns400Validation() throws Exception {
        mvc.perform(post("/api/auth/signup")
                .contentType("application/json")
                .content(body("alice@example.com", "Sup3rSecret_Pw_12", "")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.details.field").value("displayName"));

        verifyNoInteractions(signupService);
    }

    @Test
    void signup_withoutCsrf_returns201_csrfIsIgnoredOnSignup() throws Exception {
        // 다른 mutation endpoint는 CSRF 미제공 시 403이지만, /api/auth/signup은 SecurityConfig에서
        // ignoringRequestMatchers로 면제 (ADR #41 — 비로그인 호출자에게 사전 토큰 발급 부담 회피).
        when(signupService.signup(any(SignupRequest.class), any(), any())).thenReturn(OK_RESPONSE);

        mvc.perform(post("/api/auth/signup")
                .contentType("application/json")
                .content(body("alice@example.com", "Sup3rSecret_Pw_12", "Alice")))
            .andExpect(status().isCreated());
    }

    private String body(String email, String password, String displayName) throws Exception {
        return json.writeValueAsString(Map.of(
            "email", email,
            "password", password,
            "displayName", displayName
        ));
    }
}
