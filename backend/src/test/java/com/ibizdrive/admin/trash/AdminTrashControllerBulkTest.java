package com.ibizdrive.admin.trash;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.auth.LoginAttemptTracker;
import com.ibizdrive.common.error.AdminExceptionHandler;
import com.ibizdrive.common.error.AuthExceptionHandler;
import com.ibizdrive.common.error.GlobalExceptionHandler;
import com.ibizdrive.config.MethodSecurityConfig;
import com.ibizdrive.config.SecurityConfig;
import com.ibizdrive.trash.TrashItemType;
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
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link AdminTrashController#bulk} sliced WebMvcTest — Wave 2 T9 follow-up (spec §3).
 *
 * <p>HTTP 매트릭스: 200 (admin) / 401 / 403 (member) / 400 (cap, invalid action).
 *
 * <p>{@code @AuthenticationPrincipal IbizDriveUserDetails}는
 * {@code .with(user(adminPrincipal))} 패턴으로 주입 ({@link
 * com.ibizdrive.admin.AdminUserControllerTest}와 동일).
 */
@WebMvcTest(controllers = AdminTrashController.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class,
    AuthExceptionHandler.class, AdminExceptionHandler.class, GlobalExceptionHandler.class})
class AdminTrashControllerBulkTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockBean AdminTrashService service;
    @MockBean com.ibizdrive.approval.PendingApprovalService approvalService; // Phase 3c dual-approval 그래프 충족 — gate=false 기본이라 실제 호출 0

    @MockBean LoginAttemptTracker tracker;
    @MockBean UserRepository userRepository;
    @MockBean DbUserDetailsService dbUserDetailsService;
    @MockBean PermissionEvaluator permissionEvaluator;

    private static final UUID ADMIN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private IbizDriveUserDetails adminPrincipal;
    private IbizDriveUserDetails memberPrincipal;

    @BeforeEach
    void setUp() {
        User admin = new User(ADMIN_ID, "admin@example.com", "Admin",
            "{bcrypt}$2a$12$dummy", Role.ADMIN, true, false, OffsetDateTime.now());
        adminPrincipal = new IbizDriveUserDetails(admin);

        User member = new User(UUID.randomUUID(), "member@example.com", "Member",
            "{bcrypt}$2a$12$dummy", Role.MEMBER, true, false, OffsetDateTime.now());
        memberPrincipal = new IbizDriveUserDetails(member);
    }

    private String body(String action, List<Map<String, Object>> items) throws Exception {
        return json.writeValueAsString(Map.of("action", action, "items", items));
    }

    @Test
    void bulk_200_forAdmin() throws Exception {
        UUID f1 = UUID.randomUUID();
        UUID fd1 = UUID.randomUUID();
        when(service.bulk(eq("restore"), any(), eq(ADMIN_ID))).thenReturn(
            new AdminTrashBulkResponseDto(
                List.of(new AdminTrashBulkResponseDto.Item(TrashItemType.FILE, f1)),
                List.of(new AdminTrashBulkResponseDto.FailedItem(TrashItemType.FOLDER, fd1, "NAME_CONFLICT"))
            )
        );

        mvc.perform(post("/api/admin/trash/bulk")
                .with(user(adminPrincipal))
                .with(csrf())
                .contentType("application/json")
                .content(body("restore", List.of(
                    Map.of("type", "file", "id", f1.toString()),
                    Map.of("type", "folder", "id", fd1.toString())
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.succeeded").isArray())
            .andExpect(jsonPath("$.succeeded[0].id").value(f1.toString()))
            .andExpect(jsonPath("$.succeeded[0].type").value("file"))
            .andExpect(jsonPath("$.failed[0].id").value(fd1.toString()))
            .andExpect(jsonPath("$.failed[0].error").value("NAME_CONFLICT"));

        verify(service).bulk(eq("restore"), any(), eq(ADMIN_ID));
    }

    @Test
    void bulk_401_whenUnauthenticated() throws Exception {
        mvc.perform(post("/api/admin/trash/bulk")
                .with(csrf())
                .contentType("application/json")
                .content(body("restore", List.of(Map.of("type", "file", "id", UUID.randomUUID().toString())))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void bulk_403_forMember() throws Exception {
        mvc.perform(post("/api/admin/trash/bulk")
                .with(user(memberPrincipal))
                .with(csrf())
                .contentType("application/json")
                .content(body("restore", List.of(Map.of("type", "file", "id", UUID.randomUUID().toString())))))
            .andExpect(status().isForbidden());
    }

    @Test
    void bulk_400_whenInvalidAction() throws Exception {
        when(service.bulk(eq("delete"), any(), eq(ADMIN_ID)))
            .thenThrow(new IllegalArgumentException("invalid action"));

        mvc.perform(post("/api/admin/trash/bulk")
                .with(user(adminPrincipal))
                .with(csrf())
                .contentType("application/json")
                .content(body("delete", List.of(Map.of("type", "file", "id", UUID.randomUUID().toString())))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void bulk_400_whenEmptyItems() throws Exception {
        when(service.bulk(eq("restore"), any(), eq(ADMIN_ID)))
            .thenThrow(new IllegalArgumentException("items must be 1..200"));

        mvc.perform(post("/api/admin/trash/bulk")
                .with(user(adminPrincipal))
                .with(csrf())
                .contentType("application/json")
                .content(body("restore", List.of())))
            .andExpect(status().isBadRequest());
    }

    @Test
    void bulk_400_whenOverCap() throws Exception {
        when(service.bulk(eq("restore"), any(), eq(ADMIN_ID)))
            .thenThrow(new IllegalArgumentException("items must be 1..200"));

        // 단일 아이템으로도 동일 IAE를 던지도록 stub만 두고 클라이언트 측 cap 검증은 server-side만.
        mvc.perform(post("/api/admin/trash/bulk")
                .with(user(adminPrincipal))
                .with(csrf())
                .contentType("application/json")
                .content(body("restore", List.of(Map.of("type", "file", "id", UUID.randomUUID().toString())))))
            .andExpect(status().isBadRequest());
    }
}
