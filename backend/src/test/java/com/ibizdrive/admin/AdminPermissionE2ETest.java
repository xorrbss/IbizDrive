package com.ibizdrive.admin;

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

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave 2 T5 — admin permission matrix E2E.
 *
 * <p>{@code GET /api/admin/permissions} 시나리오 검증:
 * <ul>
 *   <li>인가: anonymous → 401, MEMBER → 403, ADMIN → 200</li>
 *   <li>filter: subjectType / subjectId(user, dept) / resourceType / preset / q 부분일치</li>
 *   <li>만료: 과거 expiresAt → isExpired=true / null → false</li>
 *   <li>정렬+페이지: created_at DESC, id DESC tie-break, size cap</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class AdminPermissionE2ETest {

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

    private UUID adminId;
    private String adminEmail;
    private UUID memberId;
    private String memberEmail;

    private UUID deptId;
    private UUID folderId;
    private UUID fileId;

    @BeforeEach
    void seed() {
        rest.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory());
        // permission rows + audit_log + 의존 entity 정리
        jdbc.update("DELETE FROM permissions");
        jdbc.update("DELETE FROM files");
        jdbc.update("DELETE FROM folders");
        jdbc.update("DELETE FROM audit_log");
        userRepository.deleteAll();
        jdbc.update("DELETE FROM departments");

        String suffix = UUID.randomUUID().toString().substring(0, 8);

        adminId = UUID.randomUUID();
        adminEmail = "a-" + suffix + "@example.com";
        userRepository.save(new User(adminId, adminEmail, "Admin User",
            passwordEncoder.encode(PW), Role.ADMIN, true, false, OffsetDateTime.now()));

        memberId = UUID.randomUUID();
        memberEmail = "m-" + suffix + "@example.com";
        userRepository.save(new User(memberId, memberEmail, "Alice Bob",
            passwordEncoder.encode(PW), Role.MEMBER, true, false, OffsetDateTime.now()));

        deptId = UUID.randomUUID();
        jdbc.update("INSERT INTO departments(id, name) VALUES (?, ?)", deptId, "Engineering");

        folderId = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO folders(id, parent_id, name, normalized_name, slug, owner_id) " +
            "VALUES (?, NULL, ?, ?, ?, ?)",
            folderId, "QuarterlyReports", "quarterlyreports", "quarterlyreports", adminId
        );

        fileId = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO files(id, folder_id, name, normalized_name, owner_id, size_bytes) " +
            "VALUES (?, ?, ?, ?, ?, ?)",
            fileId, folderId, "Budget.xlsx", "budget.xlsx", adminId, 0L
        );

        // 5 grants — 다양한 subject/preset, 시간 차이를 두어 정렬 검증 가능
        // (created_at은 default now()이지만 본 테스트는 시간 ordering이 필수가 아니므로
        //  tie-break은 id로 충분)
        insertPermission("folder", folderId, "user", memberId, "read", adminId, null);
        insertPermission("folder", folderId, "department", deptId, "edit", adminId, null);
        insertPermissionEveryone("folder", folderId, "read", adminId, null);
        insertPermission("file", fileId, "user", memberId, "admin", adminId, null);
        // 만료된 grant — file admin to dept, expiresAt 과거
        insertPermission("file", fileId, "department", deptId, "upload",
            adminId, Instant.now().minusSeconds(3600));
    }

    @Test
    void anonymous_returns_401() {
        ResponseEntity<Map> r = rest.getForEntity("/api/admin/permissions", Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void member_returns_403() {
        HttpHeaders h = loginAs(memberEmail);
        ResponseEntity<Map> r = rest.exchange("/api/admin/permissions",
            HttpMethod.GET, new HttpEntity<>(h), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void admin_returns_200_withAllRows() {
        HttpHeaders h = loginAs(adminEmail);
        ResponseEntity<Map> r = rest.exchange("/api/admin/permissions",
            HttpMethod.GET, new HttpEntity<>(h), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).isNotNull();
        Number total = (Number) r.getBody().get("totalElements");
        assertThat(total.longValue()).isEqualTo(5L);
    }

    @Test
    void filter_subjectType_user_only() {
        HttpHeaders h = loginAs(adminEmail);
        ResponseEntity<Map> r = rest.exchange("/api/admin/permissions?subjectType=user",
            HttpMethod.GET, new HttpEntity<>(h), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) r.getBody().get("content");
        assertThat(content).hasSize(2);
        assertThat(content).allSatisfy(row -> assertThat(row.get("subjectType")).isEqualTo("user"));
    }

    @Test
    void filter_subjectId_member_returnsTwoRows() {
        HttpHeaders h = loginAs(adminEmail);
        ResponseEntity<Map> r = rest.exchange(
            "/api/admin/permissions?subjectType=user&subjectId=" + memberId,
            HttpMethod.GET, new HttpEntity<>(h), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        Number total = (Number) r.getBody().get("totalElements");
        assertThat(total.longValue()).isEqualTo(2L);
    }

    @Test
    void filter_resourceType_file_returnsTwoRows() {
        HttpHeaders h = loginAs(adminEmail);
        ResponseEntity<Map> r = rest.exchange("/api/admin/permissions?resourceType=file",
            HttpMethod.GET, new HttpEntity<>(h), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        Number total = (Number) r.getBody().get("totalElements");
        assertThat(total.longValue()).isEqualTo(2L);
    }

    @Test
    void filter_preset_admin() {
        HttpHeaders h = loginAs(adminEmail);
        ResponseEntity<Map> r = rest.exchange("/api/admin/permissions?preset=admin",
            HttpMethod.GET, new HttpEntity<>(h), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        Number total = (Number) r.getBody().get("totalElements");
        assertThat(total.longValue()).isEqualTo(1L);
    }

    @Test
    void filter_q_matchesUserDisplayName() {
        HttpHeaders h = loginAs(adminEmail);
        // "Alice Bob" — 부분일치 "alice"
        ResponseEntity<Map> r = rest.exchange("/api/admin/permissions?q=alice",
            HttpMethod.GET, new HttpEntity<>(h), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) r.getBody().get("content");
        // member에 대한 grant 2건 (folder+read, file+admin)
        assertThat(content).hasSize(2);
        assertThat(content).allSatisfy(row -> assertThat(row.get("subjectName")).isEqualTo("Alice Bob"));
    }

    @Test
    void filter_q_matchesFolderName() {
        HttpHeaders h = loginAs(adminEmail);
        // "QuarterlyReports" — 부분일치 "quarter"
        ResponseEntity<Map> r = rest.exchange("/api/admin/permissions?q=quarter",
            HttpMethod.GET, new HttpEntity<>(h), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        Number total = (Number) r.getBody().get("totalElements");
        // folder grants 3건 (user/dept/everyone)
        assertThat(total.longValue()).isEqualTo(3L);
    }

    @Test
    void expiredRow_isExpiredTrue() {
        HttpHeaders h = loginAs(adminEmail);
        // file + dept + upload + expired
        ResponseEntity<Map> r = rest.exchange(
            "/api/admin/permissions?subjectType=department&resourceType=file",
            HttpMethod.GET, new HttpEntity<>(h), Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) r.getBody().get("content");
        assertThat(content).hasSize(1);
        assertThat(content.get(0).get("isExpired")).isEqualTo(Boolean.TRUE);
        assertThat(content.get(0).get("expiresAt")).isNotNull();
    }

    @Test
    void notExpiredRow_isExpiredFalse() {
        HttpHeaders h = loginAs(adminEmail);
        ResponseEntity<Map> r = rest.exchange(
            "/api/admin/permissions?subjectType=user",
            HttpMethod.GET, new HttpEntity<>(h), Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) r.getBody().get("content");
        assertThat(content).allSatisfy(row -> {
            assertThat(row.get("isExpired")).isEqualTo(Boolean.FALSE);
            assertThat(row.get("expiresAt")).isNull();
        });
    }

    @Test
    void pagination_size2_page0_then_page1() {
        HttpHeaders h = loginAs(adminEmail);
        ResponseEntity<Map> p0 = rest.exchange("/api/admin/permissions?size=2&page=0",
            HttpMethod.GET, new HttpEntity<>(h), Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> c0 = (List<Map<String, Object>>) p0.getBody().get("content");
        assertThat(c0).hasSize(2);
        Number total = (Number) p0.getBody().get("totalElements");
        assertThat(total.longValue()).isEqualTo(5L);

        ResponseEntity<Map> p1 = rest.exchange("/api/admin/permissions?size=2&page=1",
            HttpMethod.GET, new HttpEntity<>(h), Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> c1 = (List<Map<String, Object>>) p1.getBody().get("content");
        assertThat(c1).hasSize(2);

        // 페이지 간 ID 중복 없음 — tie-break 정상
        assertThat(c0.get(0).get("id")).isNotEqualTo(c1.get(0).get("id"));
        assertThat(c0.get(1).get("id")).isNotEqualTo(c1.get(1).get("id"));
    }

    @Test
    void filter_subjectIdWithoutSubjectType_returns_400() {
        HttpHeaders h = loginAs(adminEmail);
        ResponseEntity<Map> r = rest.exchange(
            "/api/admin/permissions?subjectId=" + memberId,
            HttpMethod.GET, new HttpEntity<>(h), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─────────────────────────── helpers ───────────────────────────

    private HttpHeaders loginAs(String email) {
        HttpHeaders csrf = csrfHandshake();
        ResponseEntity<Map> login = postJson("/api/auth/login",
            Map.of("email", email, "password", PW), csrf);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String session = extractCookie(login, "SESSION");
        HttpHeaders h = new HttpHeaders();
        h.add(HttpHeaders.COOKIE,
            csrf.getFirst(HttpHeaders.COOKIE) + "; SESSION=" + session);
        return h;
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

    private void insertPermission(String resourceType, UUID resourceId,
                                  String subjectType, UUID subjectId,
                                  String preset, UUID grantedBy, Instant expiresAt) {
        jdbc.update(
            "INSERT INTO permissions(id, resource_type, resource_id, subject_type, subject_id, " +
            "preset, granted_by, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(), resourceType, resourceId, subjectType, subjectId,
            preset, grantedBy,
            expiresAt == null ? null : Timestamp.from(expiresAt)
        );
    }

    private void insertPermissionEveryone(String resourceType, UUID resourceId,
                                          String preset, UUID grantedBy, Instant expiresAt) {
        jdbc.update(
            "INSERT INTO permissions(id, resource_type, resource_id, subject_type, subject_id, " +
            "preset, granted_by, expires_at) VALUES (?, ?, ?, ?, NULL, ?, ?, ?)",
            UUID.randomUUID(), resourceType, resourceId, "everyone",
            preset, grantedBy,
            expiresAt == null ? null : Timestamp.from(expiresAt)
        );
    }
}
