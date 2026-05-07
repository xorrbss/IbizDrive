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
 * A3.5 — 권한 매트릭스 full E2E (docs/03 §3.5/§3.6 + ADR #26).
 *
 * <p>{@link TestPermissionController}의 가짜 endpoint 3종({@code hasPermission READ/EDIT},
 * {@code hasRole ADMIN})에 대해 ADMIN/AUDITOR/MEMBER × 3 권한 매트릭스를 실제 Postgres +
 * Spring Session JDBC + HttpClient5 + CSRF 흐름으로 통과 검증한다.
 *
 * <p>{@code PermissionEvaluatorIntegrationTest}는 {@code @WebMvcTest} 슬라이스에서 같은
 * 매트릭스를 검증하지만, 본 테스트는 {@code SecurityFilterChain} + Spring Session principal
 * 복원 + {@link com.ibizdrive.common.error.GlobalExceptionHandler} envelope이 실제 HTTP
 * 흐름에서 그린지 회귀 가드한다 (A2.5 패턴 동일: {@code @SpringBootTest} + Testcontainers
 * + {@code HttpComponentsClientHttpRequestFactory}로 SET-COOKIE 흐름 보존).
 *
 * <p><b>회귀 가드</b>: {@link Permission#PURGE}는 {@code hasPermission} SpEL 경로로 절대
 * 통과하지 않음 — {@code Preset.admin} 세트가 PURGE를 제외하기 때문 (docs/03 §3.2 line 333).
 * {@code @PreAuthorize("hasRole('ADMIN')")}로만 ADMIN이 통과하고 그 외는 403.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class PermissionEndpointE2ETest {

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

    private String adminEmail;
    private String auditorEmail;
    private String memberEmail;

    @BeforeEach
    void seed() {
        rest.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory());
        // V4 REVOKE는 app_user role에만 적용 — 슈퍼유저 JdbcTemplate으로 정리 가능.
        jdbc.update("DELETE FROM audit_log");
        userRepository.deleteAll();

        // unique email per @BeforeEach — LoginAttemptTracker는 production 빈을 그대로 쓰므로
        // 같은 email로는 카운터가 누적된다 (A2.5 AuthAuditE2ETest 패턴 동일).
        adminEmail = "admin-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        auditorEmail = "auditor-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        memberEmail = "member-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";

        userRepository.save(newUser(adminEmail, Role.ADMIN));
        userRepository.save(newUser(auditorEmail, Role.AUDITOR));
        userRepository.save(newUser(memberEmail, Role.MEMBER));
    }

    private User newUser(String email, Role role) {
        return new User(
            UUID.randomUUID(), email, role.name(), passwordEncoder.encode(PW),
            role, true, false, OffsetDateTime.now()
        );
    }

    // ─── hasPermission READ ──────────────────────────────────────────────────

    @Test
    void admin_canRead_folder() {
        ResponseEntity<Map> r = authedGet(adminEmail, "/api/test/folders/abc");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void auditor_canRead_folder() {
        ResponseEntity<Map> r = authedGet(auditorEmail, "/api/test/folders/abc");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void member_cannotRead_folder_returns403_envelope() {
        ResponseEntity<Map> r = authedGet(memberEmail, "/api/test/folders/abc");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertErrorCode(r, "PERMISSION_DENIED");
        assertRequiredHave(r, "READ", List.of());
    }

    // ─── hasPermission EDIT ──────────────────────────────────────────────────

    @Test
    void admin_canEdit_folder() {
        ResponseEntity<Map> r = authedGet(adminEmail, "/api/test/folders/abc/edit");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void auditor_cannotEdit_folder_haveContainsRead() {
        ResponseEntity<Map> r = authedGet(auditorEmail, "/api/test/folders/abc/edit");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertRequiredHave(r, "EDIT", List.of("READ"));
    }

    @Test
    void member_cannotEdit_folder() {
        ResponseEntity<Map> r = authedGet(memberEmail, "/api/test/folders/abc/edit");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertRequiredHave(r, "EDIT", List.of());
    }

    // ─── hasRole ADMIN (PURGE 회귀 가드) ─────────────────────────────────────
    //
    // 회귀 가드 핵심: PURGE는 hasPermission SpEL 경로로는 어떤 role도 통과하지 못한다.
    // Preset.admin이 PURGE를 의도적으로 제외하기 때문 (docs/03 §3.2 line 333). 호출 자체가
    // hasRole('ADMIN') 가드여야 ADMIN만 통과 — AUDITOR/MEMBER는 403, ADMIN은 200.

    @Test
    void admin_canPurge_viaHasRole() {
        ResponseEntity<Map> r = authedGet(adminEmail, "/api/test/admin/purge/abc");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void auditor_cannotPurge() {
        ResponseEntity<Map> r = authedGet(auditorEmail, "/api/test/admin/purge/abc");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertErrorCode(r, "PERMISSION_DENIED");
    }

    @Test
    void member_cannotPurge() {
        ResponseEntity<Map> r = authedGet(memberEmail, "/api/test/admin/purge/abc");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertErrorCode(r, "PERMISSION_DENIED");
    }

    // ─── 익명 401 (HttpStatusEntryPoint 일관성) ───────────────────────────────

    @Test
    void anonymous_returns401_onProtectedEndpoint() {
        ResponseEntity<Map> r = rest.getForEntity("/api/test/folders/abc", Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void anonymous_returns401_onPurgeEndpoint() {
        ResponseEntity<Map> r = rest.getForEntity("/api/test/admin/purge/abc", Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ─────────────────────────── helpers ───────────────────────────

    /**
     * CSRF handshake → login → 인증 SESSION 쿠키로 GET 한 번 — 매 테스트마다 fresh login.
     */
    private ResponseEntity<Map> authedGet(String email, String path) {
        HttpHeaders csrf = csrfHandshake();
        ResponseEntity<Map> loginRes = postJson("/api/auth/login",
            Map.of("email", email, "password", PW), csrf);
        assertThat(loginRes.getStatusCode())
            .as("seed login (email=%s, body=%s)", email, loginRes.getBody())
            .isEqualTo(HttpStatus.OK);
        String session = extractCookie(loginRes, "SESSION");
        assertThat(session).as("SESSION 쿠키 발급").isNotBlank();

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

    @SuppressWarnings("unchecked")
    private void assertErrorCode(ResponseEntity<Map> r, String expected) {
        Map<String, Object> body = r.getBody();
        assertThat(body).as("error envelope present").isNotNull();
        Map<String, Object> error = (Map<String, Object>) body.get("error");
        assertThat(error).as("error.* present").isNotNull();
        assertThat(error.get("code")).isEqualTo(expected);
    }

    @SuppressWarnings("unchecked")
    private void assertRequiredHave(ResponseEntity<Map> r, String required, List<String> have) {
        assertErrorCode(r, "PERMISSION_DENIED");
        Map<String, Object> body = r.getBody();
        Map<String, Object> error = (Map<String, Object>) body.get("error");
        Map<String, Object> details = (Map<String, Object>) error.get("details");
        assertThat(details).as("details.* present").isNotNull();
        assertThat((List<String>) details.get("required")).containsExactly(required);
        assertThat((List<String>) details.get("have")).containsExactlyElementsOf(have);
    }
}
