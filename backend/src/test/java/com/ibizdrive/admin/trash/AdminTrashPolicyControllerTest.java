package com.ibizdrive.admin.trash;

import com.ibizdrive.auth.LoginAttemptTracker;
import com.ibizdrive.common.error.AdminExceptionHandler;
import com.ibizdrive.common.error.AuthExceptionHandler;
import com.ibizdrive.common.error.GlobalExceptionHandler;
import com.ibizdrive.config.MethodSecurityConfig;
import com.ibizdrive.config.SecurityConfig;
import com.ibizdrive.trash.TrashRetentionProperties;
import com.ibizdrive.user.DbUserDetailsService;
import com.ibizdrive.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link AdminTrashPolicyController} sliced WebMvcTest — wave2-trash-policy-viewer.
 *
 * <p>HTTP 매트릭스: 200 (admin) / 401 (anonymous) / 403 (member, auditor) — read-only이라
 * 400 케이스 없음.
 *
 * <p>{@link AdminTrashControllerTest} 패턴 동형 — `@Import`로 보안 설정 + 글로벌 예외 핸들러
 * 로드. {@link TrashRetentionProperties}는 {@code @TestConfiguration}으로 고정값 주입.
 */
@WebMvcTest(controllers = AdminTrashPolicyController.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class,
    AuthExceptionHandler.class, AdminExceptionHandler.class, GlobalExceptionHandler.class,
    AdminTrashPolicyControllerTest.TestConfig.class})
class AdminTrashPolicyControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean LoginAttemptTracker tracker;
    @MockBean UserRepository userRepository;
    @MockBean DbUserDetailsService dbUserDetailsService;
    @MockBean PermissionEvaluator permissionEvaluator;

    @TestConfiguration
    static class TestConfig {
        @Bean TrashRetentionProperties trashRetentionProperties() {
            // 운영자가 yml에서 30 → 14로 줄인 시나리오 — 동작 확인용 비-default 값.
            return new TrashRetentionProperties(14);
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void get_200_returnsRetentionDays() throws Exception {
        mockMvc.perform(get("/api/admin/trash/policy"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.retentionDays").value(14));
    }

    @Test
    void get_401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/admin/trash/policy"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "MEMBER")
    void get_403_forMember() throws Exception {
        mockMvc.perform(get("/api/admin/trash/policy"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "AUDITOR")
    void get_403_forAuditor() throws Exception {
        mockMvc.perform(get("/api/admin/trash/policy"))
            .andExpect(status().isForbidden());
    }
}
