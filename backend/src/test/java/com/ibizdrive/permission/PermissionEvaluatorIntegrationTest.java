package com.ibizdrive.permission;

import com.ibizdrive.common.error.AuthExceptionHandler;
import com.ibizdrive.common.error.GlobalExceptionHandler;
import com.ibizdrive.config.MethodSecurityConfig;
import com.ibizdrive.config.SecurityConfig;
import com.ibizdrive.user.DbUserDetailsService;
import com.ibizdrive.user.IbizDriveUserDetails;
import com.ibizdrive.user.Role;
import com.ibizdrive.user.User;
import com.ibizdrive.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A3.2 — {@code @PreAuthorize} + {@link IbizDrivePermissionEvaluator} 통합 검증
 * (docs/03 §3.6 + ADR #26).
 *
 * <p>{@link TestPermissionController}의 가짜 endpoint 3종에 대해:
 * <ul>
 *   <li>{@code hasPermission(#id, 'folder', 'READ')} — ADMIN/AUDITOR 200, MEMBER 403</li>
 *   <li>{@code hasPermission(#id, 'folder', 'EDIT')} — ADMIN 200, AUDITOR/MEMBER 403</li>
 *   <li>{@code hasRole('ADMIN')} — ADMIN 200, AUDITOR/MEMBER 403</li>
 *   <li>익명 — 401</li>
 * </ul>
 *
 * <p>403 응답은 docs/03 §3.6 envelope ({@code error.code=PERMISSION_DENIED}, {@code details.required}/
 * {@code details.have})를 갖춘다.
 */
@WebMvcTest(controllers = TestPermissionController.class)
@Import({
    SecurityConfig.class,
    MethodSecurityConfig.class,
    PermissionService.class,
    IbizDrivePermissionEvaluator.class,
    AuthExceptionHandler.class,
    GlobalExceptionHandler.class
})
class PermissionEvaluatorIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private DbUserDetailsService dbUserDetailsService;

    // A4.3 — IbizDrivePermissionEvaluator가 PermissionResolver(→PermissionRepository)에 의존.
    // @WebMvcTest 슬라이스는 JPA bean을 띄우지 않으므로 mock으로 대체. 본 통합 테스트의
    // targetId는 모두 "abc" (비-UUID)라 resolver 경로 자체가 skip되어 default false도 호출되지 않음.
    @MockBean
    private PermissionResolver permissionResolver;

    // A4.4 — PermissionService 가 PermissionRepository 의존을 갖게 되었으므로 슬라이스 충족용 mock.
    // (PermissionResolver 가 같은 repo 를 쓰지만 PermissionService 도 별도로 주입받음 → 빈 그래프 충족)
    @MockBean
    private PermissionRepository permissionRepository;

    private IbizDriveUserDetails admin;
    private IbizDriveUserDetails auditor;
    private IbizDriveUserDetails member;

    @BeforeEach
    void setUp() {
        admin = principalOf("11111111-1111-1111-1111-111111111111", Role.ADMIN);
        auditor = principalOf("22222222-2222-2222-2222-222222222222", Role.AUDITOR);
        member = principalOf("33333333-3333-3333-3333-333333333333", Role.MEMBER);
    }

    @AfterEach
    void tearDown() {
        PermissionDenyContext.clear();
    }

    private static IbizDriveUserDetails principalOf(String id, Role role) {
        User u = new User(
            UUID.fromString(id),
            role.name().toLowerCase() + "@example.com",
            role.name(),
            "{bcrypt}$2a$12$dummyhash",
            role,
            true,
            false,
            OffsetDateTime.now()
        );
        return new IbizDriveUserDetails(u);
    }

    // ─── hasPermission READ ──────────────────────────────────────────────────

    @Test
    void admin_canRead() throws Exception {
        mvc.perform(get("/api/test/folders/abc").with(user(admin)))
            .andExpect(status().isOk());
    }

    @Test
    void auditor_canRead() throws Exception {
        mvc.perform(get("/api/test/folders/abc").with(user(auditor)))
            .andExpect(status().isOk());
    }

    @Test
    void member_cannotRead_returns403_withEnvelope() throws Exception {
        mvc.perform(get("/api/test/folders/abc").with(user(member)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"))
            .andExpect(jsonPath("$.error.message").exists())
            .andExpect(jsonPath("$.error.details.required[0]").value("READ"))
            .andExpect(jsonPath("$.error.details.have").isArray());
    }

    @Test
    void anonymous_returns401() throws Exception {
        mvc.perform(get("/api/test/folders/abc"))
            .andExpect(status().isUnauthorized());
    }

    // ─── hasPermission EDIT ──────────────────────────────────────────────────

    @Test
    void admin_canEdit() throws Exception {
        mvc.perform(get("/api/test/folders/abc/edit").with(user(admin)))
            .andExpect(status().isOk());
    }

    @Test
    void auditor_cannotEdit_returns403_haveContainsRead() throws Exception {
        mvc.perform(get("/api/test/folders/abc/edit").with(user(auditor)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"))
            .andExpect(jsonPath("$.error.details.required[0]").value("EDIT"))
            .andExpect(jsonPath("$.error.details.have[0]").value("READ"));
    }

    @Test
    void member_cannotEdit() throws Exception {
        mvc.perform(get("/api/test/folders/abc/edit").with(user(member)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.details.have").isEmpty());
    }

    // ─── hasRole ADMIN ───────────────────────────────────────────────────────

    @Test
    void admin_canPurge() throws Exception {
        mvc.perform(get("/api/test/admin/purge/abc").with(user(admin)))
            .andExpect(status().isOk());
    }

    @Test
    void auditor_cannotPurge() throws Exception {
        mvc.perform(get("/api/test/admin/purge/abc").with(user(auditor)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
    }

    @Test
    void member_cannotPurge() throws Exception {
        mvc.perform(get("/api/test/admin/purge/abc").with(user(member)))
            .andExpect(status().isForbidden());
    }
}
