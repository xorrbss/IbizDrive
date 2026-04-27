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
 * A2.5 — A1 인증 이벤트가 실제 HTTP 흐름을 거쳐 audit_log에 persist되는지 E2E 검증.
 *
 * <p>{@link com.ibizdrive.auth.AuthService} / {@link com.ibizdrive.auth.AuthController}의
 * {@code publishEvent} → Spring {@link org.springframework.context.ApplicationEventMulticaster}
 * → {@link AuthAuditListener} → {@link AuditService#record} → audit_log INSERT 체인 전체를
 * 실제 Postgres + Spring Session JDBC + HttpClient5 환경에서 회귀 가드.
 *
 * <p>{@link AuthScenarioIntegrationTest}와 같은 {@code @SpringBootTest} 패턴을 채택하지만
 * MutableClock + LoginAttemptTracker @Primary 오버라이드는 동일 (lockout TTL은 본 테스트
 * 검증 범위 밖이므로 reset만 사용). audit_log 검증은 JdbcTemplate 직접 SELECT.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class AuthAuditE2ETest {

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

    /**
     * 테스트마다 고유 email을 사용 — {@link com.ibizdrive.auth.LoginAttemptTracker}는 production 빈을
     * 그대로 쓰므로(생성자 package-private) 같은 email로는 카운터가 누적됨. unique email로 회피.
     */
    private String email;
    private UUID userId;

    @BeforeEach
    void seed() {
        rest.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory());
        // Postgres 슈퍼유저 권한으로 정리 — V4 REVOKE는 app_user role에만 적용되므로 테스트 정리 가능.
        jdbc.update("DELETE FROM audit_log");
        userRepository.deleteAll();
        userId = UUID.randomUUID();
        // 짧은 UUID prefix로 unique email 보장 — RFC 5321 local-part 64자 제한을 회피하기 위해
        // method 이름 대신 UUID 8자만 사용. (CI Hibernate Validator strict로 64자 초과 시 400 응답)
        email = "e2e-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        userRepository.save(new User(
            userId, email, "Audit E2E", passwordEncoder.encode(PW),
            Role.MEMBER, true, false, OffsetDateTime.now()
        ));
    }

    @Test
    void successful_login_writes_user_login_success_with_actor_ip_ua() {
        HttpHeaders csrf = csrfHandshake();

        ResponseEntity<Map> loginRes = postJson("/api/auth/login",
            Map.of("email", email, "password", PW), csrf);
        assertThat(loginRes.getStatusCode())
            .as("login response (body=%s)", loginRes.getBody())
            .isEqualTo(HttpStatus.OK);

        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT event_type, actor_id, actor_ip::text AS actor_ip, user_agent, target_type, target_id " +
            "FROM audit_log WHERE event_type = 'user.login.success'");
        assertThat(rows).hasSize(1);
        Map<String, Object> r = rows.get(0);
        assertThat(r.get("event_type")).isEqualTo("user.login.success");
        assertThat(r.get("actor_id")).isEqualTo(userId);
        assertThat(r.get("target_type")).isEqualTo("user");
        assertThat(r.get("target_id")).isEqualTo(userId);
        assertThat((String) r.get("actor_ip")).as("loopback IP 기록").isNotBlank();
        assertThat((String) r.get("user_agent")).as("HttpClient5 UA 헤더 기록").isNotBlank();
    }

    @Test
    void wrong_password_writes_user_login_failed_with_reason_bad_password() {
        HttpHeaders csrf = csrfHandshake();

        ResponseEntity<Map> r = postJson("/api/auth/login",
            Map.of("email", email, "password", "wrong-pw"), csrf);
        assertThat(r.getStatusCode())
            .as("wrong-pw response (body=%s)", r.getBody())
            .isEqualTo(HttpStatus.UNAUTHORIZED);

        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT actor_id, metadata->>'reason' AS reason " +
            "FROM audit_log WHERE event_type = 'user.login.failed'");
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("actor_id")).as("기존 유저는 actor_id 채워짐").isEqualTo(userId);
        assertThat(row.get("reason")).isEqualTo("bad-password");
    }

    @Test
    void unknown_user_writes_user_login_failed_with_null_actor_and_user_not_found() {
        HttpHeaders csrf = csrfHandshake();

        ResponseEntity<Map> r = postJson("/api/auth/login",
            Map.of("email", "ghost@example.com", "password", "anything"), csrf);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT actor_id, metadata->>'reason' AS reason " +
            "FROM audit_log WHERE event_type = 'user.login.failed'");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("actor_id")).as("미존재 사용자는 actor_id null").isNull();
        assertThat(rows.get(0).get("reason")).isEqualTo("user-not-found");
    }

    @Test
    void five_wrong_then_locked_writes_five_failed_rows() {
        HttpHeaders csrf = csrfHandshake();

        for (int i = 0; i < 5; i++) {
            ResponseEntity<Map> r = postJson("/api/auth/login",
                Map.of("email", email, "password", "wrong-" + i), csrf);
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        // 6번째 요청은 lockout 차단 — 423 + AuthenticationFailureLockedEvent → user.login.failed metadata.reason=locked
        ResponseEntity<Map> r6 = postJson("/api/auth/login",
            Map.of("email", email, "password", PW), csrf);
        assertThat(r6.getStatusCode()).isEqualTo(HttpStatus.LOCKED);

        Long failedCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_log WHERE event_type = 'user.login.failed'", Long.class);
        assertThat(failedCount).as("5 bad-pw + 1 locked = 6").isEqualTo(6L);

        Long lockedReason = jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_log " +
            "WHERE event_type = 'user.login.failed' AND metadata->>'reason' = 'locked'",
            Long.class);
        assertThat(lockedReason).as("lockout publish 1건은 reason=locked").isEqualTo(1L);
    }

    @Test
    void logout_writes_user_logout_with_actor_id() {
        HttpHeaders csrf = csrfHandshake();
        // 1) 로그인
        ResponseEntity<Map> loginRes = postJson("/api/auth/login",
            Map.of("email", email, "password", PW), csrf);
        assertThat(loginRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        String session = extractCookie(loginRes, "SESSION");
        assertThat(session).isNotBlank();

        // 2) 로그아웃 (SESSION + CSRF 모두 보유)
        HttpHeaders h = csrfWithSession(csrf, session);
        ResponseEntity<Void> logoutRes = rest.exchange("/api/auth/logout", HttpMethod.POST,
            new HttpEntity<>(h), Void.class);
        assertThat(logoutRes.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT actor_id, target_id FROM audit_log WHERE event_type = 'user.logout'");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("actor_id")).isEqualTo(userId);
        assertThat(rows.get(0).get("target_id")).isEqualTo(userId);
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

    private HttpHeaders csrfWithSession(HttpHeaders csrf, String sessionCookie) {
        HttpHeaders h = new HttpHeaders();
        h.add("X-XSRF-TOKEN", csrf.getFirst("X-XSRF-TOKEN"));
        h.add(HttpHeaders.COOKIE, csrf.getFirst(HttpHeaders.COOKIE) + "; SESSION=" + sessionCookie);
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
