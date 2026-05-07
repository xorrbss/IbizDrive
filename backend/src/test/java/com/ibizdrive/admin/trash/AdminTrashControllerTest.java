package com.ibizdrive.admin.trash;

import com.ibizdrive.auth.LoginAttemptTracker;
import com.ibizdrive.common.error.AdminExceptionHandler;
import com.ibizdrive.common.error.AuthExceptionHandler;
import com.ibizdrive.common.error.GlobalExceptionHandler;
import com.ibizdrive.config.MethodSecurityConfig;
import com.ibizdrive.config.SecurityConfig;
import com.ibizdrive.trash.TrashItemType;
import com.ibizdrive.user.DbUserDetailsService;
import com.ibizdrive.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link AdminTrashController} sliced WebMvcTest — wave2-t9-admin-global-trash (spec §5.1).
 *
 * <p>HTTP 매트릭스: 200 (admin) / 401 (anonymous) / 403 (member, auditor) / 400 (filter 파싱 실패).
 *
 * <p>{@code @Import}로 보안 설정 + 글로벌 예외 핸들러 로드 — {@code AdminPermissionControllerTest}
 * 패턴 재사용. {@code IllegalArgumentException} → 400 매핑은 {@link GlobalExceptionHandler}가 담당.
 */
@WebMvcTest(controllers = AdminTrashController.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class,
    AuthExceptionHandler.class, AdminExceptionHandler.class, GlobalExceptionHandler.class})
class AdminTrashControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean AdminTrashService service;

    // SecurityConfig dependency chain — see AdminPermissionControllerTest.
    // SessionValidityFilter는 @Component → @WebMvcTest의 Filter 자동 스캔으로 포함.
    @MockBean LoginAttemptTracker tracker;
    @MockBean UserRepository userRepository;
    @MockBean DbUserDetailsService dbUserDetailsService;
    @MockBean PermissionEvaluator permissionEvaluator;

    @Test
    @WithMockUser(roles = "ADMIN")
    void list_200_forAdmin() throws Exception {
        when(service.list(any(), any(), any())).thenReturn(new AdminTrashPage(List.of(), null));

        mockMvc.perform(get("/api/admin/trash").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void list_401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/admin/trash"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "MEMBER")
    void list_403_forMember() throws Exception {
        mockMvc.perform(get("/api/admin/trash"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "AUDITOR")
    void list_403_forAuditor() throws Exception {
        mockMvc.perform(get("/api/admin/trash"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void list_passesFiltersAndCursorThrough() throws Exception {
        UUID ownerId = UUID.randomUUID();
        when(service.list(any(), any(), any())).thenReturn(new AdminTrashPage(List.of(), null));

        mockMvc.perform(get("/api/admin/trash")
                .param("q", "report")
                .param("type", "file")
                .param("ownerId", ownerId.toString())
                .param("cursor", "abc")
                .param("limit", "20"))
            .andExpect(status().isOk());

        verify(service).list(
            argThat(f -> "report".equals(f.q())
                && f.type() == TrashItemType.FILE
                && ownerId.equals(f.ownerId())),
            eq("abc"),
            eq(20)
        );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void list_400_whenInvalidType() throws Exception {
        mockMvc.perform(get("/api/admin/trash").param("type", "bogus"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void list_400_whenInvalidOwnerId() throws Exception {
        mockMvc.perform(get("/api/admin/trash").param("ownerId", "not-a-uuid"))
            .andExpect(status().isBadRequest());
    }
}
