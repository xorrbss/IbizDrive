package com.ibizdrive.auth.password;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.auth.LoginAttemptTracker;
import com.ibizdrive.common.error.AuthExceptionHandler;
import com.ibizdrive.config.SecurityConfig;
import com.ibizdrive.user.DbUserDetailsService;
import com.ibizdrive.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/auth/password/forgot} rate limit MockMvc 슬라이스 (auth-forgot-rate-limit P3).
 *
 * <p>검증 (6건):
 * <ol>
 *   <li>limiter.tryAcquire=true → 200 + 기존 message + service.requestReset 호출.</li>
 *   <li>limiter.tryAcquire=false → 429 + Retry-After 헤더 + body code=RATE_LIMIT_EXCEEDED + retryAfterSec.</li>
 *   <li>차단 시 service.requestReset 미호출.</li>
 *   <li>limiter는 lower-cased email + remoteAddr로 호출 (IP 추출 + 정규화 검증).</li>
 *   <li>X-Forwarded-For 헤더 제공 시 첫 값을 IP로 채택.</li>
 *   <li>차단 응답의 Retry-After 헤더와 body retryAfterSec 동일 값.</li>
 * </ol>
 */
@WebMvcTest(controllers = PasswordController.class)
@Import({SecurityConfig.class, AuthExceptionHandler.class})
class PasswordControllerForgotRateLimitTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @MockBean
    private PasswordResetService passwordResetService;

    @MockBean
    private ForgotPasswordRateLimiter rateLimiter;

    @MockBean
    private LoginAttemptTracker tracker;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private DbUserDetailsService dbUserDetailsService;

    @Test
    void forgot_limiterAllows_returns200AndCallsService() throws Exception {
        when(rateLimiter.tryAcquire(anyString(), anyString())).thenReturn(true);

        mvc.perform(post("/api/auth/password/forgot")
                .contentType("application/json")
                .content(body("alice@example.com")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").exists());

        verify(passwordResetService).requestReset("alice@example.com");
    }

    @Test
    void forgot_limiterBlocks_returns429WithRetryAfter() throws Exception {
        when(rateLimiter.tryAcquire(anyString(), anyString())).thenReturn(false);
        when(rateLimiter.getRetryAfterSeconds(anyString(), anyString())).thenReturn(42L);

        mvc.perform(post("/api/auth/password/forgot")
                .contentType("application/json")
                .content(body("alice@example.com")))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("Retry-After", "42"))
            .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
            .andExpect(jsonPath("$.retryAfterSec").value(42));
    }

    @Test
    void forgot_limiterBlocks_doesNotInvokeResetService() throws Exception {
        when(rateLimiter.tryAcquire(anyString(), anyString())).thenReturn(false);
        when(rateLimiter.getRetryAfterSeconds(anyString(), anyString())).thenReturn(60L);

        mvc.perform(post("/api/auth/password/forgot")
                .contentType("application/json")
                .content(body("alice@example.com")))
            .andExpect(status().isTooManyRequests());

        verifyNoInteractions(passwordResetService);
    }

    @Test
    void forgot_limiterCalledWithLowercasedEmailAndRemoteAddr() throws Exception {
        when(rateLimiter.tryAcquire(anyString(), anyString())).thenReturn(true);

        mvc.perform(post("/api/auth/password/forgot")
                .with(req -> { req.setRemoteAddr("10.0.0.1"); return req; })
                .contentType("application/json")
                .content(body("Alice@Example.COM")))
            .andExpect(status().isOk());

        verify(rateLimiter).tryAcquire("alice@example.com", "10.0.0.1");
    }

    @Test
    void forgot_xForwardedForHeader_takesFirstValue() throws Exception {
        when(rateLimiter.tryAcquire(anyString(), anyString())).thenReturn(true);

        mvc.perform(post("/api/auth/password/forgot")
                .with(req -> { req.setRemoteAddr("10.0.0.1"); return req; })
                .header("X-Forwarded-For", "203.0.113.7, 10.0.0.1")
                .contentType("application/json")
                .content(body("alice@example.com")))
            .andExpect(status().isOk());

        verify(rateLimiter).tryAcquire("alice@example.com", "203.0.113.7");
    }

    @Test
    void forgot_blockedResponse_retryAfterHeaderMatchesBody() throws Exception {
        when(rateLimiter.tryAcquire(anyString(), anyString())).thenReturn(false);
        when(rateLimiter.getRetryAfterSeconds(anyString(), anyString())).thenReturn(7L);

        mvc.perform(post("/api/auth/password/forgot")
                .contentType("application/json")
                .content(body("alice@example.com")))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("Retry-After", "7"))
            .andExpect(jsonPath("$.retryAfterSec").value(7));
    }

    private String body(String email) throws Exception {
        return json.writeValueAsString(Map.of("email", email));
    }
}
