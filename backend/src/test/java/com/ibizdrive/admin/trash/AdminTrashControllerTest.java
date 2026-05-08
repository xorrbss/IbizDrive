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
import java.time.Instant;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
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

    @Test
    @WithMockUser(roles = "ADMIN")
    void list_passesDeletedDateRangeBoundaries() throws Exception {
        when(service.list(any(), any(), any())).thenReturn(new AdminTrashPage(List.of(), null));

        mockMvc.perform(get("/api/admin/trash")
                .param("deletedFrom", "2026-05-01")
                .param("deletedTo", "2026-05-07"))
            .andExpect(status().isOk());

        // 하한 inclusive: 2026-05-01T00:00:00Z
        // 상한 exclusive: 2026-05-08T00:00:00Z (입력 5/7의 다음 날 시작)
        verify(service).list(
            argThat(f -> Instant.parse("2026-05-01T00:00:00Z").equals(f.deletedFromMin())
                && Instant.parse("2026-05-08T00:00:00Z").equals(f.deletedToMax())),
            any(),
            any()
        );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void list_400_whenInvalidDeletedFrom() throws Exception {
        mockMvc.perform(get("/api/admin/trash").param("deletedFrom", "2026/05/01"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void list_400_whenInvalidDeletedTo() throws Exception {
        mockMvc.perform(get("/api/admin/trash").param("deletedTo", "not-a-date"))
            .andExpect(status().isBadRequest());
    }

    // ===== V10 — deletedById/deletedByEmail 응답 필드 =====

    @Test
    @WithMockUser(roles = "ADMIN")
    void list_serializesDeletedByFields() throws Exception {
        UUID itemId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID deleterId = UUID.randomUUID();
        Instant deletedAt = Instant.parse("2026-05-04T00:00:00Z");

        AdminTrashItemDto dto = new AdminTrashItemDto(
            itemId, "doc.pdf", TrashItemType.FILE,
            deletedAt, deletedAt.plusSeconds(30L * 86400),
            ownerId, "owner@example.com",
            null, null,
            12345L,
            deleterId, "admin@example.com"
        );
        when(service.list(any(), any(), any())).thenReturn(new AdminTrashPage(List.of(dto), null));

        mockMvc.perform(get("/api/admin/trash").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].deletedById").value(deleterId.toString()))
            .andExpect(jsonPath("$.items[0].deletedByEmail").value("admin@example.com"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void list_serializesNullDeletedBy_asJsonNull() throws Exception {
        UUID itemId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Instant deletedAt = Instant.parse("2026-05-04T00:00:00Z");

        AdminTrashItemDto dto = new AdminTrashItemDto(
            itemId, "legacy.pdf", TrashItemType.FILE,
            deletedAt, deletedAt.plusSeconds(30L * 86400),
            ownerId, "owner@example.com",
            null, null,
            1L,
            null, null
        );
        when(service.list(any(), any(), any())).thenReturn(new AdminTrashPage(List.of(dto), null));

        // AdminTrashItemDto는 @JsonInclude(NON_NULL) 미사용 — 명시적 null로 직렬화 (frontend는
        // nullable 키를 기대, originalParentId/originalParentName 패턴과 동일).
        mockMvc.perform(get("/api/admin/trash").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].deletedById").value(Matchers.nullValue()))
            .andExpect(jsonPath("$.items[0].deletedByEmail").value(Matchers.nullValue()));
    }
}
