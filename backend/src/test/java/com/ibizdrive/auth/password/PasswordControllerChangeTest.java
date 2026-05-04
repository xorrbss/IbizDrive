package com.ibizdrive.auth.password;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.auth.InvalidCredentialsException;
import com.ibizdrive.auth.LoginAttemptTracker;
import com.ibizdrive.common.error.AuthExceptionHandler;
import com.ibizdrive.config.SecurityConfig;
import com.ibizdrive.user.DbUserDetailsService;
import com.ibizdrive.user.IbizDriveUserDetails;
import com.ibizdrive.user.Role;
import com.ibizdrive.user.User;
import com.ibizdrive.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/auth/password/change} sliced WebMvcTest (P5).
 *
 * <p>검증 (5건):
 * <ol>
 *   <li>인증 + 유효 입력 + CSRF → 200, service.change 호출</li>
 *   <li>currentPassword 불일치 → 401 INVALID_CREDENTIALS</li>
 *   <li>약한 newPassword (7자) → 400 VALIDATION_ERROR</li>
 *   <li>blank currentPassword → 400 VALIDATION_ERROR</li>
 *   <li>미인증 → 401 (entry point)</li>
 * </ol>
 *
 * <p>{@code /change}는 forgot/reset과 달리 인증 + CSRF 모두 필수 (signup 패턴 미적용).
 */
@WebMvcTest(controllers = PasswordController.class)
@Import({SecurityConfig.class, AuthExceptionHandler.class})
class PasswordControllerChangeTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @MockBean
    private PasswordResetService passwordResetService;

    @MockBean
    private ForgotPasswordRateLimiter rateLimiter;  // controller 그래프 충족 — /change 미호출

    @MockBean
    private LoginAttemptTracker tracker;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private DbUserDetailsService dbUserDetailsService;

    private IbizDriveUserDetails principal;

    @BeforeEach
    void setUp() {
        User u = new User(
            UUID.randomUUID(),
            "alice@example.com",
            "Alice",
            "{bcrypt}$2a$12$existing",
            Role.MEMBER,
            true,
            false,
            OffsetDateTime.now()
        );
        principal = new IbizDriveUserDetails(u);
    }

    @Test
    void change_validInput_returns200AndCallsService() throws Exception {
        mvc.perform(post("/api/auth/password/change")
                .with(user(principal))
                .with(csrf())
                .contentType("application/json")
                .content(body("CurrentSecret123!", "NewSecret456!")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").exists());

        verify(passwordResetService).change(
            eq(principal.getUser()),
            eq("CurrentSecret123!"),
            eq("NewSecret456!"),
            any()  // sessionId (MockMvc 환경 nullable)
        );
    }

    @Test
    void change_wrongCurrentPassword_returns401() throws Exception {
        doThrow(new InvalidCredentialsException())
            .when(passwordResetService).change(any(), anyString(), anyString(), any());

        mvc.perform(post("/api/auth/password/change")
                .with(user(principal))
                .with(csrf())
                .contentType("application/json")
                .content(body("WrongPassword!", "NewSecret456!")))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.reason").value("INVALID_CREDENTIALS"));
    }

    /**
     * ADR #19 비밀번호 정책 5규칙 위반 → 400 VALIDATION_ERROR + details.rule.
     * (auth-password-policy 트랙, 2026-05-04)
     */
    @ParameterizedTest(name = "change rejects {1}-violating newPassword")
    @CsvSource({
        "'short7',        min_length",
        "'1234567890ab1234567890ab1234567890ab1234567890ab1234567890ab1234567890ab1234567890ab1234567890ab1234567890ab1234567890ab1234567890abc', max_length",
        "'123456789012',  missing_alpha",
        "'abcdefghijkl',  missing_digit",
        "'abcdef 12345',  whitespace"
    })
    void change_passwordPolicyViolation_returns400WithRule(String newPassword, String expectedRule) throws Exception {
        mvc.perform(post("/api/auth/password/change")
                .with(user(principal))
                .with(csrf())
                .contentType("application/json")
                .content(body("CurrentSecret123!", newPassword)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.details.field").value("newPassword"))
            .andExpect(jsonPath("$.details.rule").value(expectedRule));

        verifyNoInteractions(passwordResetService);
    }

    @Test
    void change_blankCurrentPassword_returns400Validation() throws Exception {
        mvc.perform(post("/api/auth/password/change")
                .with(user(principal))
                .with(csrf())
                .contentType("application/json")
                .content(body("", "NewSecret456!")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.details.field").value("currentPassword"));

        verifyNoInteractions(passwordResetService);
    }

    @Test
    void change_unauthenticated_returns401() throws Exception {
        // user(...) 미적용 — anyRequest().authenticated() 가드가 401로 차단.
        mvc.perform(post("/api/auth/password/change")
                .with(csrf())
                .contentType("application/json")
                .content(body("CurrentSecret123!", "NewSecret456!")))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(passwordResetService);
    }

    private String body(String currentPassword, String newPassword) throws Exception {
        return json.writeValueAsString(Map.of(
            "currentPassword", currentPassword,
            "newPassword", newPassword
        ));
    }
}
