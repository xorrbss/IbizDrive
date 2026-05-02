package com.ibizdrive.auth.password;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.auth.LoginAttemptTracker;
import com.ibizdrive.common.error.AuthExceptionHandler;
import com.ibizdrive.config.SecurityConfig;
import com.ibizdrive.user.DbUserDetailsService;
import com.ibizdrive.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/auth/password/forgot} sliced WebMvcTest (P3).
 *
 * <p>검증 (4건):
 * <ol>
 *   <li>유효 email → 200 + message body, service.requestReset(email) 호출</li>
 *   <li>유효 email + CSRF 미제공 → 200 (forgot은 CSRF ignore — signup과 동일 정책)</li>
 *   <li>잘못된 email 형식 → 400 VALIDATION_ERROR, service 미호출</li>
 *   <li>blank email → 400 VALIDATION_ERROR, service 미호출</li>
 * </ol>
 */
@WebMvcTest(controllers = PasswordController.class)
@Import({SecurityConfig.class, AuthExceptionHandler.class})
class PasswordControllerForgotTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @MockBean
    private PasswordResetService passwordResetService;

    @MockBean
    private ForgotPasswordRateLimiter rateLimiter;  // ADR #44 — 본 슬라이스에서는 항상 통과로 stub

    @MockBean
    private LoginAttemptTracker tracker;            // SecurityConfig 그래프 충족

    @MockBean
    private UserRepository userRepository;          // SecurityConfig 그래프 충족

    @MockBean
    private DbUserDetailsService dbUserDetailsService;

    @BeforeEach
    void allowRateLimit() {
        when(rateLimiter.tryAcquire(anyString(), anyString())).thenReturn(true);
    }

    @Test
    void forgot_validEmail_returns200AndCallsService() throws Exception {
        mvc.perform(post("/api/auth/password/forgot")
                .contentType("application/json")
                .content(body("alice@example.com")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").exists());

        verify(passwordResetService).requestReset(eq("alice@example.com"));
    }

    @Test
    void forgot_withoutCsrf_returns200() throws Exception {
        // SecurityConfig가 /api/auth/password/forgot을 ignoringRequestMatchers에 포함 — CSRF 미제공도 통과.
        mvc.perform(post("/api/auth/password/forgot")
                .contentType("application/json")
                .content(body("alice@example.com")))
            .andExpect(status().isOk());
    }

    @Test
    void forgot_invalidEmail_returns400Validation() throws Exception {
        mvc.perform(post("/api/auth/password/forgot")
                .contentType("application/json")
                .content(body("not-an-email")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.details.field").value("email"));

        verifyNoInteractions(passwordResetService);
    }

    @Test
    void forgot_blankEmail_returns400Validation() throws Exception {
        mvc.perform(post("/api/auth/password/forgot")
                .contentType("application/json")
                .content(body("")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.details.field").value("email"));

        verifyNoInteractions(passwordResetService);
    }

    private String body(String email) throws Exception {
        return json.writeValueAsString(Map.of("email", email));
    }
}
