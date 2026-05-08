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
 * Wave 1 — T2: {@code GET /api/admin/audit/export} E2E.
 *
 * <p>검증 시나리오:
 * <ol>
 *   <li>익명 호출 → 401</li>
 *   <li>MEMBER 로그인 후 호출 → 403 (@PreAuthorize 차단)</li>
 *   <li>MEMBER 5회 wrong-PW로 audit row 5건 생성 → AUDITOR 호출 시 CSV 본문에 5건 + BOM
 *       + Content-Type=text/csv 헤더</li>
 *   <li>ADMIN 호출 + {@code eventType=user.login.failed} 필터 → 동일 5건만</li>
 *   <li>export 성공 시 {@code audit.exported} audit row 1건 생성 (actor=호출자, target_type=audit,
 *       metadata에 filters/rowCount/format)</li>
 * </ol>
 *
 * <p>{@link AuditQueryE2ETest}와 동일 컨테이너/시드 패턴 — 헬퍼는 의도적으로 중복 (테스트 격리).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class AuditExportE2ETest {

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
    private String auditorEmail;
    private UUID auditorId;

    @BeforeEach
    void seed() {
        rest.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory());
        jdbc.update("DELETE FROM audit_log");
        userRepository.deleteAll();

        String suffix = UUID.randomUUID().toString().substring(0, 8);

        memberId = UUID.randomUUID();
        memberEmail = "m-" + suffix + "@example.com";
        userRepository.save(new User(
            memberId, memberEmail, "Export Member", passwordEncoder.encode(PW),
            Role.MEMBER, true, false, OffsetDateTime.now()
        ));

        adminId = UUID.randomUUID();
        adminEmail = "a-" + suffix + "@example.com";
        userRepository.save(new User(
            adminId, adminEmail, "Export Admin", passwordEncoder.encode(PW),
            Role.ADMIN, true, false, OffsetDateTime.now()
        ));

        auditorId = UUID.randomUUID();
        auditorEmail = "u-" + suffix + "@example.com";
        userRepository.save(new User(
            auditorId, auditorEmail, "Export Auditor", passwordEncoder.encode(PW),
            Role.AUDITOR, true, false, OffsetDateTime.now()
        ));
    }

    @Test
    void anonymous_request_returns_401() {
        ResponseEntity<String> r = rest.getForEntity("/api/admin/audit/export", String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void member_request_returns_403_via_preauthorize() {
        // MEMBER 로그인 → SESSION 확보 → /export 호출 → 403
        HttpHeaders csrf = csrfHandshake();
        ResponseEntity<Map> login = postJson("/api/auth/login",
            Map.of("email", memberEmail, "password", PW), csrf);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String session = extractCookie(login, "SESSION");

        HttpHeaders h = new HttpHeaders();
        h.add(HttpHeaders.COOKIE, csrf.getFirst(HttpHeaders.COOKIE) + "; SESSION=" + session);
        ResponseEntity<String> r = rest.exchange("/api/admin/audit/export",
            HttpMethod.GET, new HttpEntity<>(h), String.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void auditor_export_returns_csv_with_bom_and_rows() {
        // 1) MEMBER 5회 wrong-PW → audit_log에 user.login.failed 5건
        HttpHeaders memberCsrf = csrfHandshake();
        for (int i = 0; i < 5; i++) {
            postJson("/api/auth/login",
                Map.of("email", memberEmail, "password", "wrong-" + i), memberCsrf);
        }
        Long failedRows = jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_log WHERE event_type = 'user.login.failed'", Long.class);
        assertThat(failedRows).isEqualTo(5L);

        // 2) AUDITOR 로그인
        HttpHeaders auditorCsrf = csrfHandshake();
        ResponseEntity<Map> login = postJson("/api/auth/login",
            Map.of("email", auditorEmail, "password", PW), auditorCsrf);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String session = extractCookie(login, "SESSION");

        // 3) /export 호출 (필터 없음 — 전체)
        HttpHeaders h = new HttpHeaders();
        h.add(HttpHeaders.COOKIE, auditorCsrf.getFirst(HttpHeaders.COOKIE) + "; SESSION=" + session);
        ResponseEntity<byte[]> r = rest.exchange("/api/admin/audit/export",
            HttpMethod.GET, new HttpEntity<>(h), byte[].class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getHeaders().getContentType().toString())
            .startsWith("text/csv");
        assertThat(r.getHeaders().getContentDisposition().getFilename())
            .startsWith("audit_logs_").endsWith(".csv");
        assertThat(r.getHeaders().getFirst(AuditQueryController.HEADER_TRUNCATED))
            .as("row 수가 cap 미만이라 truncated 헤더 없음").isNull();

        String body = new String(r.getBody(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).startsWith("\uFEFF");
        // 헤더 + AUDITOR login.success 1건 + member 5건 = 최소 6 data rows (= 7 lines)
        String[] lines = body.split("\r\n");
        assertThat(lines).hasSizeGreaterThanOrEqualTo(7);
        assertThat(lines[0])
            .isEqualTo("\uFEFFid,occurredAt,eventType,actorId,actorName,resourceType,resourceId,resourceName,ip,metadata");
        long failedLines = java.util.Arrays.stream(lines)
            .filter(l -> l.contains(",user.login.failed,"))
            .count();
        assertThat(failedLines).isEqualTo(5L);
    }

    @Test
    void admin_export_with_event_type_filter_includes_only_matching_rows() {
        // 사전 시드: MEMBER 3회 wrong + ADMIN 로그인 1회 (login.success도 발생) — 필터로 wrong만 추출
        HttpHeaders memberCsrf = csrfHandshake();
        for (int i = 0; i < 3; i++) {
            postJson("/api/auth/login",
                Map.of("email", memberEmail, "password", "wrong-" + i), memberCsrf);
        }
        HttpHeaders adminCsrf = csrfHandshake();
        ResponseEntity<Map> login = postJson("/api/auth/login",
            Map.of("email", adminEmail, "password", PW), adminCsrf);
        String session = extractCookie(login, "SESSION");

        HttpHeaders h = new HttpHeaders();
        h.add(HttpHeaders.COOKIE, adminCsrf.getFirst(HttpHeaders.COOKIE) + "; SESSION=" + session);

        ResponseEntity<byte[]> r = rest.exchange(
            "/api/admin/audit/export?eventType=user.login.failed",
            HttpMethod.GET, new HttpEntity<>(h), byte[].class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = new String(r.getBody(), java.nio.charset.StandardCharsets.UTF_8);
        String[] lines = body.split("\r\n");
        // 헤더 1 + 데이터 3 = 4
        assertThat(lines).hasSize(4);
        assertThat(lines[1]).contains(",user.login.failed,");
        assertThat(lines[2]).contains(",user.login.failed,");
        assertThat(lines[3]).contains(",user.login.failed,");
    }

    @Test
    void successful_export_emits_audit_exported_row() {
        // 1) AUDITOR 로그인 후 export → audit_log에 login.success 1건 + audit.exported 1건 생성 기대
        HttpHeaders csrf = csrfHandshake();
        ResponseEntity<Map> login = postJson("/api/auth/login",
            Map.of("email", auditorEmail, "password", PW), csrf);
        String session = extractCookie(login, "SESSION");

        HttpHeaders h = new HttpHeaders();
        h.add(HttpHeaders.COOKIE, csrf.getFirst(HttpHeaders.COOKIE) + "; SESSION=" + session);
        ResponseEntity<byte[]> r = rest.exchange(
            "/api/admin/audit/export?eventType=user.login.success",
            HttpMethod.GET, new HttpEntity<>(h), byte[].class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 2) audit.exported row 검증
        // listener는 응답 commit 후 트리거되지만 같은 요청 스레드에서 동기 실행되므로
        // exchange 반환 시점엔 INSERT가 commit된 상태.
        Long exported = jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_log WHERE event_type = 'audit.exported'", Long.class);
        assertThat(exported).isEqualTo(1L);

        // metadata는 jsonb — Postgres가 ::text 변환 시 키를 재정렬하고 콜론 뒤 공백을
        // 추가하므로 텍스트 매칭 대신 ->/->>로 키 단위 추출 (AuthAuditE2ETest 패턴).
        Map<String, Object> row = jdbc.queryForMap(
            "SELECT actor_id, target_type, " +
            "       metadata->>'format' AS format, " +
            "       (metadata->>'rowCount')::int AS row_count, " +
            "       (metadata->>'truncated')::boolean AS truncated, " +
            "       metadata->'filters'->>'eventType' AS event_type " +
            "FROM audit_log WHERE event_type = 'audit.exported' LIMIT 1");
        assertThat(row.get("actor_id")).isEqualTo(auditorId);
        assertThat(row.get("target_type")).isEqualTo("audit");
        assertThat(row.get("format")).isEqualTo("csv");
        assertThat(row.get("row_count")).isEqualTo(1);              // login.success 1건
        assertThat(row.get("truncated")).isEqualTo(false);
        assertThat(row.get("event_type")).isEqualTo("user.login.success");
    }

    @Test
    void auditor_export_json_returns_json_array_and_emits_format_json() throws Exception {
        // 1) MEMBER 2회 wrong-PW → audit_log에 user.login.failed 2건
        HttpHeaders memberCsrf = csrfHandshake();
        for (int i = 0; i < 2; i++) {
            postJson("/api/auth/login",
                Map.of("email", memberEmail, "password", "wrong-" + i), memberCsrf);
        }

        // 2) AUDITOR 로그인
        HttpHeaders auditorCsrf = csrfHandshake();
        ResponseEntity<Map> login = postJson("/api/auth/login",
            Map.of("email", auditorEmail, "password", PW), auditorCsrf);
        String session = extractCookie(login, "SESSION");

        HttpHeaders h = new HttpHeaders();
        h.add(HttpHeaders.COOKIE, auditorCsrf.getFirst(HttpHeaders.COOKIE) + "; SESSION=" + session);
        ResponseEntity<byte[]> r = rest.exchange(
            "/api/admin/audit/export?format=json",
            HttpMethod.GET, new HttpEntity<>(h), byte[].class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getHeaders().getContentType().toString())
            .startsWith("application/json");
        assertThat(r.getHeaders().getContentDisposition().getFilename())
            .startsWith("audit_logs_").endsWith(".json");

        // 응답 본문 — JSON 배열 검증
        String body = new String(r.getBody(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).startsWith("[").endsWith("]");
        com.fasterxml.jackson.databind.JsonNode arr =
            new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
        assertThat(arr.isArray()).isTrue();
        assertThat(arr.size()).isGreaterThanOrEqualTo(1);

        // audit_log metadata.format = json 검증
        Map<String, Object> row = jdbc.queryForMap(
            "SELECT metadata->>'format' AS format, " +
            "       (metadata->>'rowCount')::int AS row_count, " +
            "       (metadata->>'truncated')::boolean AS truncated " +
            "FROM audit_log WHERE event_type='audit.exported' ORDER BY occurred_at DESC LIMIT 1");
        assertThat(row.get("format")).isEqualTo("json");
        assertThat((Integer) row.get("row_count")).isGreaterThanOrEqualTo(1);
        assertThat(row.get("truncated")).isEqualTo(false);
    }

    @Test
    void invalid_format_returns_400_bad_request() {
        HttpHeaders csrf = csrfHandshake();
        ResponseEntity<Map> login = postJson("/api/auth/login",
            Map.of("email", adminEmail, "password", PW), csrf);
        String session = extractCookie(login, "SESSION");

        HttpHeaders h = new HttpHeaders();
        h.add(HttpHeaders.COOKIE, csrf.getFirst(HttpHeaders.COOKIE) + "; SESSION=" + session);
        ResponseEntity<Map> r = rest.exchange(
            "/api/admin/audit/export?format=xml",
            HttpMethod.GET, new HttpEntity<>(h), Map.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) r.getBody().get("error");
        assertThat(error).isNotNull();
        assertThat(error.get("code")).isEqualTo("BAD_REQUEST");
    }

    // ─────────────────────────── helpers ───────────────────────────

    private HttpHeaders csrfHandshake() {
        ResponseEntity<Map> csrfRes = rest.getForEntity("/api/auth/csrf", Map.class);
        assertThat(csrfRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        String csrfToken = (String) csrfRes.getBody().get("csrfToken");
        String xsrfCookie = extractCookie(csrfRes, "XSRF-TOKEN");
        HttpHeaders h = new HttpHeaders();
        h.add("X-CSRF-Token", csrfToken);
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
