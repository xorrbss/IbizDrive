package com.ibizdrive.audit;

import com.ibizdrive.audit.dto.AuditLogEntryDto;
import com.ibizdrive.audit.dto.AuditLogPageDto;
import com.ibizdrive.common.error.AuthExceptionHandler;
import com.ibizdrive.config.SecurityConfig;
import com.ibizdrive.user.DbUserDetailsService;
import com.ibizdrive.user.IbizDriveUserDetails;
import com.ibizdrive.user.Role;
import com.ibizdrive.user.User;
import com.ibizdrive.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A2.3 — {@link AuditQueryController} {@code @WebMvcTest} slice (RED → GREEN).
 *
 * <p>검증 책임 분담:
 * <ul>
 *   <li>본 슬라이스: routing, security 가드, 쿼리 파라미터 → 서비스 인자 매핑, 응답 JSON shape.</li>
 *   <li>{@link AuditQueryServiceTest}: 실제 SQL 동작 (필터, 정렬, MEMBER scope, 페이지네이션) — Testcontainers.</li>
 * </ul>
 *
 * <p>{@link AuditQueryService}는 @MockBean — 인자 캡처로 controller가 올바른 페이로드를 service에
 * 전달하는지 검증. Service 자체 동작은 별도 통합 테스트가 책임.
 */
@WebMvcTest(controllers = AuditQueryController.class)
@Import({SecurityConfig.class, AuthExceptionHandler.class})
class AuditQueryControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AuditQueryService queryService;

    @MockBean
    private AuditCsvWriter csvWriter;                  // /export 엔드포인트용 — 본 슬라이스는 호출 안 함

    @MockBean
    private UserRepository userRepository;             // SecurityConfig 그래프 충족

    @MockBean
    private DbUserDetailsService dbUserDetailsService; // SecurityConfig 그래프 충족

    private IbizDriveUserDetails member;
    private IbizDriveUserDetails admin;
    private IbizDriveUserDetails auditor;

    @BeforeEach
    void setUp() {
        member = principalOf("11111111-1111-1111-1111-111111111111", "Alice", Role.MEMBER);
        admin = principalOf("22222222-2222-2222-2222-222222222222", "Root",  Role.ADMIN);
        auditor = principalOf("33333333-3333-3333-3333-333333333333", "Audi", Role.AUDITOR);

        // 기본 stub: 빈 페이지 — 각 테스트가 필요 시 재정의.
        when(queryService.search(any(), anyInt(), anyInt(), any(), any()))
            .thenReturn(new AuditLogPageDto(List.of(), 0L, 1, 20));
    }

    private IbizDriveUserDetails principalOf(String id, String name, Role role) {
        User u = new User(
            UUID.fromString(id),
            name.toLowerCase() + "@example.com",
            name,
            "{bcrypt}$2a$12$dummyhash",
            role,
            true,
            false,
            OffsetDateTime.now()
        );
        return new IbizDriveUserDetails(u);
    }

    // ─── 1. 인증 가드 ─────────────────────────────────────────────────────────

    @Test
    void anonymous_returns401() throws Exception {
        mvc.perform(get("/api/admin/audit"))
            .andExpect(status().isUnauthorized());
    }

    // ─── 2. 권한 매트릭스 (트랙 결정 #4) ────────────────────────────────────────

    @Test
    void member_invokesService_withSelfScope() throws Exception {
        mvc.perform(get("/api/admin/audit").with(user(member)))
            .andExpect(status().isOk());

        ArgumentCaptor<UUID> viewerIdCap = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<Role> roleCap = ArgumentCaptor.forClass(Role.class);
        verify(queryService).search(any(), anyInt(), anyInt(), viewerIdCap.capture(), roleCap.capture());
        assertEquals(UUID.fromString("11111111-1111-1111-1111-111111111111"), viewerIdCap.getValue());
        assertEquals(Role.MEMBER, roleCap.getValue());
    }

    @Test
    void admin_invokesService_withAdminRole() throws Exception {
        mvc.perform(get("/api/admin/audit").with(user(admin)))
            .andExpect(status().isOk());

        ArgumentCaptor<Role> roleCap = ArgumentCaptor.forClass(Role.class);
        verify(queryService).search(any(), anyInt(), anyInt(), any(), roleCap.capture());
        assertEquals(Role.ADMIN, roleCap.getValue());
    }

    @Test
    void auditor_invokesService_withAuditorRole() throws Exception {
        mvc.perform(get("/api/admin/audit").with(user(auditor)))
            .andExpect(status().isOk());

        ArgumentCaptor<Role> roleCap = ArgumentCaptor.forClass(Role.class);
        verify(queryService).search(any(), anyInt(), anyInt(), any(), roleCap.capture());
        assertEquals(Role.AUDITOR, roleCap.getValue());
    }

    // ─── 3. 쿼리 파라미터 → 서비스 인자 매핑 ─────────────────────────────────────

    @Test
    void defaultPagination_isPage1Size20() throws Exception {
        mvc.perform(get("/api/admin/audit").with(user(admin))).andExpect(status().isOk());

        ArgumentCaptor<Integer> pageCap = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> sizeCap = ArgumentCaptor.forClass(Integer.class);
        verify(queryService).search(any(), pageCap.capture(), sizeCap.capture(), any(), any());
        assertEquals(1, pageCap.getValue());
        assertEquals(20, sizeCap.getValue());
    }

    @Test
    void customPaginationAndFilters_arePassedThrough() throws Exception {
        mvc.perform(get("/api/admin/audit")
                .with(user(admin))
                .param("page", "3")
                .param("pageSize", "50")
                .param("eventType", "file.uploaded")
                .param("actorQuery", "김")
                .param("fromDate", "2026-04-25")
                .param("toDate", "2026-04-26"))
            .andExpect(status().isOk());

        ArgumentCaptor<AuditQueryFilters> filtersCap = ArgumentCaptor.forClass(AuditQueryFilters.class);
        ArgumentCaptor<Integer> pageCap = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> sizeCap = ArgumentCaptor.forClass(Integer.class);
        verify(queryService).search(filtersCap.capture(), pageCap.capture(), sizeCap.capture(), any(), any());

        AuditQueryFilters f = filtersCap.getValue();
        assertEquals(LocalDate.of(2026, 4, 25), f.fromDate());
        assertEquals(LocalDate.of(2026, 4, 26), f.toDate());
        assertEquals("김", f.actorQuery());
        assertEquals("file.uploaded", f.eventType());
        assertEquals(3, pageCap.getValue());
        assertEquals(50, sizeCap.getValue());
    }

    @Test
    void targetTypeAndTargetId_areForwardedToService() throws Exception {
        UUID fileX = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        mvc.perform(get("/api/admin/audit")
                .with(user(member))
                .param("targetType", "file")
                .param("targetId", fileX.toString()))
            .andExpect(status().isOk());

        ArgumentCaptor<AuditQueryFilters> filtersCap = ArgumentCaptor.forClass(AuditQueryFilters.class);
        verify(queryService).search(filtersCap.capture(), anyInt(), anyInt(), any(), any());
        AuditQueryFilters f = filtersCap.getValue();
        assertEquals("file", f.targetType());
        assertEquals(fileX, f.targetId());
    }

    @Test
    void blankFilters_areNormalizedToNull() throws Exception {
        mvc.perform(get("/api/admin/audit")
                .with(user(admin))
                .param("eventType", "")
                .param("actorQuery", "   "))
            .andExpect(status().isOk());

        ArgumentCaptor<AuditQueryFilters> filtersCap = ArgumentCaptor.forClass(AuditQueryFilters.class);
        verify(queryService).search(filtersCap.capture(), anyInt(), anyInt(), any(), any());
        assertNull(filtersCap.getValue().eventType(), "빈 문자열 eventType은 null로");
        assertNull(filtersCap.getValue().actorQuery(), "공백 actorQuery는 null로");
    }

    // ─── 4. 응답 JSON shape (frontend AuditLogEntry 계약) ──────────────────────

    @Test
    void response_shape_matchesFrontendContract() throws Exception {
        AuditLogEntryDto entry = new AuditLogEntryDto(
            "42",
            OffsetDateTime.of(2026, 4, 25, 10, 30, 0, 0, ZoneOffset.UTC),
            "file.uploaded",
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "Alice",
            "file",
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
            null,
            "203.0.113.42",
            Map.of("size", 1024)
        );
        when(queryService.search(any(), anyInt(), anyInt(), any(), any()))
            .thenReturn(new AuditLogPageDto(List.of(entry), 1L, 1, 20));

        mvc.perform(get("/api/admin/audit").with(user(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entries[0].id").value("42"))
            .andExpect(jsonPath("$.entries[0].occurredAt").value("2026-04-25T10:30:00Z"))
            .andExpect(jsonPath("$.entries[0].eventType").value("file.uploaded"))
            .andExpect(jsonPath("$.entries[0].actorId").value("11111111-1111-1111-1111-111111111111"))
            .andExpect(jsonPath("$.entries[0].actorName").value("Alice"))
            .andExpect(jsonPath("$.entries[0].resourceType").value("file"))
            .andExpect(jsonPath("$.entries[0].resourceId").value("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
            .andExpect(jsonPath("$.entries[0].ip").value("203.0.113.42"))
            .andExpect(jsonPath("$.entries[0].metadata.size").value(1024))
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.pageSize").value(20));
    }
}
