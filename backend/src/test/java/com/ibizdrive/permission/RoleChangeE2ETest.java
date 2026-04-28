package com.ibizdrive.permission;

import com.ibizdrive.user.Role;
import com.ibizdrive.user.User;
import com.ibizdrive.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A3.5 — {@link PermissionService#changeRole} → 다음 요청부터 권한 박탈 + audit
 * {@code permission.changed} 1건 + cache key 변화 회귀 가드 (docs/03 §3.4 + ADR #26).
 *
 * <p>시나리오: 같은 사용자가 ADMIN으로 로그인 → admin-only endpoint 200 → ADMIN이
 * 자기 role을 MEMBER로 강등 → 재로그인 → 같은 endpoint 403 + audit 1건 + login response
 * {@code effectivePermissionsCacheKey} hex prefix가 강등 전후 달라짐.
 *
 * <p><b>왜 재로그인이 필요한가</b>: Spring Session JDBC는 SESSION 안에 SecurityContext를
 * 직렬화 저장한다 ({@link IbizDriveUserDetails} 스냅샷). {@link PermissionService#changeRole}이
 * {@code users.role}을 갱신해도 기존 SESSION의 principal은 강등 전 role을 그대로 쥐고 있다 —
 * principal을 매 요청마다 재로드하지 않기 때문 (성능 + 세션 invalidation 정책 분리).
 * 따라서 권한 강등이 효력을 발휘하려면 클라이언트가 재로그인하여 새 SecurityContext를
 * 발급받아야 한다. 본 테스트는 이 모델을 명시적으로 검증한다.
 *
 * <p>{@link com.ibizdrive.audit.PermissionAuditListener}는 REQUIRES_NEW로 INSERT 하므로
 * 호출 측 트랜잭션 rollback과 무관하게 audit row가 보존된다 (ADR #25).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class RoleChangeE2ETest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired private TestRestTemplate rest;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PermissionService permissionService;

    private static final String PW = "Sup3rSecret_Pw_12";

    private String email;
    private UUID userId;

    @BeforeEach
    void seed() {
        rest.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory());
        jdbc.update("DELETE FROM audit_log");
        userRepository.deleteAll();

        userId = UUID.randomUUID();
        email = "rc-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        userRepository.save(new User(
            userId, email, "Role Change", passwordEncoder.encode(PW),
            Role.ADMIN, true, false, OffsetDateTime.now()
        ));
    }

    @Test
    void admin_demoted_to_member_loses_edit_and_emits_permission_changed() {
        // 1) ADMIN으로 로그인 — admin-only endpoint 통과 확인
        ResponseEntity<Map> login1 = login();
        assertThat(login1.getStatusCode()).isEqualTo(HttpStatus.OK);
        String adminCacheKey = (String) login1.getBody().get("effectivePermissionsCacheKey");
        assertThat(adminCacheKey).as("ADMIN cache key").matches("[0-9a-f]{16}");
        String session1 = extractCookie(login1, "SESSION");
        ResponseEntity<Map> editAsAdmin = sessionGet(session1, "/api/test/folders/abc/edit");
        assertThat(editAsAdmin.getStatusCode())
            .as("ADMIN /edit 200 (강등 전, body=%s)", editAsAdmin.getBody())
            .isEqualTo(HttpStatus.OK);

        // 2) self-demotion: ADMIN → MEMBER (PermissionService 직접 호출 — 권한 endpoint는 A4)
        permissionService.changeRole(userId, Role.MEMBER, userId);

        // 3) 재로그인 — 새 SecurityContext에 강등된 role 반영
        ResponseEntity<Map> login2 = login();
        assertThat(login2.getStatusCode()).isEqualTo(HttpStatus.OK);
        String memberCacheKey = (String) login2.getBody().get("effectivePermissionsCacheKey");
        assertThat(memberCacheKey).as("MEMBER cache key").matches("[0-9a-f]{16}");

        // cacheKey 회귀 가드 — ADR #26: role 변경 시 키가 달라져 frontend invalidate 트리거
        assertThat(memberCacheKey)
            .as("role 변경 후 cache key 변경 (frontend invalidate trigger)")
            .isNotEqualTo(adminCacheKey);

        // 4) 같은 endpoint를 새 SESSION으로 호출 — 403 PERMISSION_DENIED + envelope
        String session2 = extractCookie(login2, "SESSION");
        ResponseEntity<Map> editAsMember = sessionGet(session2, "/api/test/folders/abc/edit");
        assertThat(editAsMember.getStatusCode())
            .as("MEMBER /edit 403 (강등 후, body=%s)", editAsMember.getBody())
            .isEqualTo(HttpStatus.FORBIDDEN);
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) editAsMember.getBody().get("error");
        assertThat(error.get("code")).isEqualTo("PERMISSION_DENIED");

        // 5) audit_log: permission.changed 1건, before/after JSON, actor=target=self
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT actor_id, target_type, target_id, before_state::text AS bef, after_state::text AS aft " +
            "FROM audit_log WHERE event_type = 'permission.changed'");
        assertThat(rows).as("permission.changed 정확히 1건").hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("actor_id")).isEqualTo(userId);
        assertThat(row.get("target_type")).isEqualTo("user");
        assertThat(row.get("target_id")).isEqualTo(userId);
        // before/after는 PermissionAuditListener가 {"role":"X"} JSON 문자열로 직렬화 (ADR #24)
        assertThat((String) row.get("bef")).contains("\"role\"").contains("ADMIN");
        assertThat((String) row.get("aft")).contains("\"role\"").contains("MEMBER");
    }

    @Test
    void same_role_changeRole_is_noop_no_audit_emitted() {
        // ADMIN → ADMIN no-op 가드 — PermissionService.changeRole이 같은 role에 대해
        // event publish를 하지 않음을 회귀 가드 (PermissionService line 100~102).
        permissionService.changeRole(userId, Role.ADMIN, userId);

        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_log WHERE event_type = 'permission.changed'", Long.class);
        assertThat(count).as("같은 role 변경은 no-op").isEqualTo(0L);
    }

    // ─────────────────────────── helpers ───────────────────────────

    private ResponseEntity<Map> login() {
        HttpHeaders csrf = csrfHandshake();
        return postJson("/api/auth/login", Map.of("email", email, "password", PW), csrf);
    }

    private ResponseEntity<Map> sessionGet(String session, String path) {
        HttpHeaders h = new HttpHeaders();
        h.add(HttpHeaders.COOKIE, "SESSION=" + session);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(h), Map.class);
    }

    private HttpHeaders csrfHandshake() {
        ResponseEntity<Map> csrfRes = rest.getForEntity("/api/auth/csrf", Map.class);
        assertThat(csrfRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        String csrfToken = (String) csrfRes.getBody().get("csrfToken");
        String xsrfCookie = extractCookie(csrfRes, "XSRF-TOKEN");
        HttpHeaders h = new HttpHeaders();
        h.add("X-XSRF-TOKEN", csrfToken);
        h.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + xsrfCookie);
        return h;
    }

    private ResponseEntity<Map> postJson(String path, Object body, HttpHeaders csrfHeaders) {
        HttpHeaders h = new HttpHeaders();
        h.addAll(csrfHeaders);
        h.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, h), Map.class);
    }

    private String extractCookie(ResponseEntity<?> res, String name) {
        List<String> setCookies = res.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (setCookies == null) return null;
        String prefix = name + "=";
        for (String sc : setCookies) {
            if (sc.startsWith(prefix)) {
                String value = sc.substring(prefix.length());
                int semi = value.indexOf(';');
                return semi >= 0 ? value.substring(0, semi) : value;
            }
        }
        return null;
    }
}
