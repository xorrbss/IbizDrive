package com.ibizdrive.audit;

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
 * A2.5 — A1 인증 시나리오 후 ADMIN이 {@code /api/admin/audit?eventType=...}로 audit_log를
 * 조회하는 read API E2E 검증. {@link AuthAuditE2ETest}의 emission 흐름과 {@link AuditQueryService}
 * 필터링이 한 사이클로 연결되는지 확인.
 *
 * <p>시나리오:
 * <ol>
 *   <li>MEMBER 로그인 5회 wrong-PW + 1회 success + 1회 logout → audit_log에 7건</li>
 *   <li>ADMIN 로그인 + {@code GET /api/admin/audit?eventType=user.login.failed} → 5건 응답</li>
 *   <li>ADMIN 추가 검증: {@code eventType=user.login.success} → MEMBER + ADMIN 자신 포함</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class AuditQueryE2ETest {

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

    private static final String PW = "Sup3rSecret_Pw_12";
    private String memberEmail;
    private UUID memberId;
    private String adminEmail;
    private UUID adminId;

    @BeforeEach
    void seed() {
        rest.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory());
        jdbc.update("DELETE FROM audit_log");
        userRepository.deleteAll();

        // 짧은 UUID prefix로 unique email 보장 — RFC 5321 local-part 64자 제한 회피
        // (CI Hibernate Validator strict로 64자 초과 시 400 VALIDATION_ERROR)
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        memberId = UUID.randomUUID();
        memberEmail = "m-" + suffix + "@example.com";
        userRepository.save(new User(
            memberId, memberEmail, "Audit Member", passwordEncoder.encode(PW),
            Role.MEMBER, true, false, OffsetDateTime.now()
        ));

        adminId = UUID.randomUUID();
        adminEmail = "a-" + suffix + "@example.com";
        userRepository.save(new User(
            adminId, adminEmail, "Audit Admin", passwordEncoder.encode(PW),
            Role.ADMIN, true, false, OffsetDateTime.now()
        ));
    }

    @Test
    void admin_filters_failed_events_returns_five_after_member_scenario() {
        // 1) MEMBER가 5회 wrong-PW (audit_log에 5건의 user.login.failed)
        HttpHeaders memberCsrf = csrfHandshake();
        for (int i = 0; i < 5; i++) {
            ResponseEntity<Map> r = postJson("/api/auth/login",
                Map.of("email", memberEmail, "password", "wrong-" + i), memberCsrf);
            assertThat(r.getStatusCode())
                .as("wrong-pw iter=%d body=%s", i, r.getBody())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        // 사전 가드 — DB에 5건 들어 있는지
        Long failedRows = jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_log WHERE event_type = 'user.login.failed'", Long.class);
        assertThat(failedRows).isEqualTo(5L);

        // 2) ADMIN 로그인
        HttpHeaders adminCsrf = csrfHandshake();
        ResponseEntity<Map> adminLogin = postJson("/api/auth/login",
            Map.of("email", adminEmail, "password", PW), adminCsrf);
        assertThat(adminLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
        String adminSession = extractCookie(adminLogin, "SESSION");

        // 3) ADMIN이 /api/admin/audit?eventType=user.login.failed 조회 → 5건
        HttpHeaders adminGet = new HttpHeaders();
        adminGet.add(HttpHeaders.COOKIE,
            adminCsrf.getFirst(HttpHeaders.COOKIE) + "; SESSION=" + adminSession);

        ResponseEntity<Map> queryRes = rest.exchange(
            "/api/admin/audit?eventType=user.login.failed&pageSize=20",
            HttpMethod.GET, new HttpEntity<>(adminGet), Map.class);
        assertThat(queryRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(queryRes.getBody()).isNotNull();

        Number total = (Number) queryRes.getBody().get("total");
        assertThat(total.longValue()).as("filtered failed events").isEqualTo(5L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) queryRes.getBody().get("entries");
        assertThat(entries).hasSize(5);
        assertThat(entries).allSatisfy(e -> {
            assertThat(e.get("eventType")).isEqualTo("user.login.failed");
            assertThat(e.get("actorId")).isEqualTo(memberId.toString());
        });
    }

    @Test
    void member_query_self_scope_only_returns_own_failed_events() {
        // 1) 두 명의 MEMBER가 각각 wrong-PW — 본 테스트는 이미 1명만 시드. 2번째 시드.
        UUID otherId = UUID.randomUUID();
        String otherEmail = "other-self-scope@example.com";
        userRepository.save(new User(
            otherId, otherEmail, "Other Member", passwordEncoder.encode(PW),
            Role.MEMBER, true, false, OffsetDateTime.now()
        ));

        // memberEmail로 3회 wrong + otherEmail로 2회 wrong → audit_log: actor=member 3건, actor=other 2건
        HttpHeaders csrf = csrfHandshake();
        for (int i = 0; i < 3; i++) {
            postJson("/api/auth/login",
                Map.of("email", memberEmail, "password", "wrong-" + i), csrf);
        }
        HttpHeaders csrf2 = csrfHandshake();
        for (int i = 0; i < 2; i++) {
            postJson("/api/auth/login",
                Map.of("email", otherEmail, "password", "wrong-" + i), csrf2);
        }

        // 2) memberEmail로 로그인하여 SESSION 확보
        HttpHeaders memberCsrf = csrfHandshake();
        ResponseEntity<Map> loginRes = postJson("/api/auth/login",
            Map.of("email", memberEmail, "password", PW), memberCsrf);
        assertThat(loginRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        String memberSession = extractCookie(loginRes, "SESSION");

        // 3) MEMBER가 /api/admin/audit?eventType=user.login.failed → 자신의 3건만
        HttpHeaders memberGet = new HttpHeaders();
        memberGet.add(HttpHeaders.COOKIE,
            memberCsrf.getFirst(HttpHeaders.COOKIE) + "; SESSION=" + memberSession);

        ResponseEntity<Map> queryRes = rest.exchange(
            "/api/admin/audit?eventType=user.login.failed&pageSize=20",
            HttpMethod.GET, new HttpEntity<>(memberGet), Map.class);
        assertThat(queryRes.getStatusCode()).isEqualTo(HttpStatus.OK);

        Number total = (Number) queryRes.getBody().get("total");
        assertThat(total.longValue()).as("MEMBER scope=self → 자신 3건만").isEqualTo(3L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) queryRes.getBody().get("entries");
        assertThat(entries).allSatisfy(e -> assertThat(e.get("actorId")).isEqualTo(memberId.toString()));
    }

    @Test
    void anonymous_request_to_audit_endpoint_returns_401() {
        ResponseEntity<Map> r = rest.getForEntity("/api/admin/audit", Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ─────────────────────────── helpers ───────────────────────────

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
