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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/auth/password/reset} sliced WebMvcTest (P4).
 *
 * <p>검증 (5건):
 * <ol>
 *   <li>유효 토큰 + 정책 만족 PW → 200, service.reset 호출</li>
 *   <li>토큰 무효 → 400 INVALID_TOKEN</li>
 *   <li>blank token → 400 VALIDATION_ERROR</li>
 *   <li>약한 PW (7자) → 400 VALIDATION_ERROR</li>
 *   <li>CSRF 미제공 → 200 (reset endpoint은 CSRF ignore)</li>
 * </ol>
 */
@WebMvcTest(controllers = PasswordController.class)
@Import({SecurityConfig.class, AuthExceptionHandler.class})
class PasswordControllerResetTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @MockBean
    private PasswordResetService passwordResetService;

    @MockBean
    private LoginAttemptTracker tracker;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private DbUserDetailsService dbUserDetailsService;

    @Test
    void reset_validInput_returns200() throws Exception {
        mvc.perform(post("/api/auth/password/reset")
                .contentType("application/json")
                .content(body("token-abc-1234567890", "NewSecret123!")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").exists());

        verify(passwordResetService).reset(eq("token-abc-1234567890"), eq("NewSecret123!"));
    }

    @Test
    void reset_invalidToken_returns400InvalidToken() throws Exception {
        doThrow(new InvalidPasswordResetTokenException("expired"))
            .when(passwordResetService).reset(anyString(), anyString());

        mvc.perform(post("/api/auth/password/reset")
                .contentType("application/json")
                .content(body("expired-token-123456", "NewSecret123!")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void reset_blankToken_returns400Validation() throws Exception {
        mvc.perform(post("/api/auth/password/reset")
                .contentType("application/json")
                .content(body("", "NewSecret123!")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.details.field").value("token"));

        verifyNoInteractions(passwordResetService);
    }

    @Test
    void reset_weakPassword_returns400Validation() throws Exception {
        mvc.perform(post("/api/auth/password/reset")
                .contentType("application/json")
                .content(body("token-abc-1234567890", "short7c")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.details.field").value("newPassword"));

        verifyNoInteractions(passwordResetService);
    }

    @Test
    void reset_withoutCsrf_returns200() throws Exception {
        mvc.perform(post("/api/auth/password/reset")
                .contentType("application/json")
                .content(body("token-abc-1234567890", "NewSecret123!")))
            .andExpect(status().isOk());
    }

    private String body(String token, String newPassword) throws Exception {
        return json.writeValueAsString(Map.of(
            "token", token,
            "newPassword", newPassword
        ));
    }
}
