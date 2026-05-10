package com.ibizdrive.folder;

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
 * Plan D Task 21 — cross-workspace folder move full-stack E2E.
 *
 * <p>시나리오:
 * <ol>
 *   <li>팀 A(scopeA) + 팀 B(scopeB) 시드, 각각 root 폴더 생성.</li>
 *   <li>actor를 양쪽 root 폴더 + childFolder에 대해 {@code admin} preset으로 explicit grant
 *       — Preset.ADMIN = all permissions except PURGE, covering MOVE/EDIT/SHARE/UPLOAD.</li>
 *   <li>팀 A root 아래 child → grandchild subtree + file + additional permission row + share 시드.</li>
 *   <li>{@code POST /api/folders/{child}/move/preview} 호출 → 200 + itemCount 검증.</li>
 *   <li>{@code POST /api/folders/{child}/move { targetParentId: rootB, allowCrossScope: true }} 호출 →
 *       200 + {@code folder.scope.scopeId == scopeB}.</li>
 *   <li>post-conditions: child 폴더의 permissions 0건, active shares 0건, audit_log 1건.</li>
 * </ol>
 *
 * <p>인증: 기존 E2E 패턴(RANDOM_PORT + Testcontainers + cookie/CSRF) 동일.
 * {@code @Testcontainers(disabledWithoutDocker = true)} — Docker 없이 실행하면 SKIP.
 * CI에서 실제 Postgres 컨테이너와 함께 풀 검증된다.
 *
 * <p>audit_log 컬럼: {@code event_type} (VARCHAR 50, wire format {@code folder.moved.cross_workspace}).
 * V3 migration 확인 완료.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class CrossWorkspaceMoveE2ETest {

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

    private UUID actorId;
    private String actorEmail;

    /** Team A 식별자 (scope_id for all team-A folders). */
    private UUID scopeA;
    /** Team B 식별자 (scope_id for all team-B folders). */
    private UUID scopeB;

    /** Root folder of team A. */
    private UUID rootA;
    /** Root folder of team B — destination of the cross-workspace move. */
    private UUID rootB;

    /** Child folder under rootA — the source folder that will be moved. */
    private UUID childFolder;

    @BeforeEach
    void seed() {
        rest.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory());

        // Clean in FK-safe order
        jdbc.update("DELETE FROM shares");
        jdbc.update("DELETE FROM permissions");
        jdbc.update("DELETE FROM files");
        jdbc.update("DELETE FROM folders");
        jdbc.update("DELETE FROM audit_log");
        jdbc.update("DELETE FROM team_memberships");
        jdbc.update("DELETE FROM teams");
        userRepository.deleteAll();

        String suffix = UUID.randomUUID().toString().substring(0, 8);

        // ── Actor ────────────────────────────────────────────────────────
        actorId = UUID.randomUUID();
        actorEmail = "actor-" + suffix + "@example.com";
        userRepository.save(new User(
            actorId, actorEmail, "Move Actor",
            passwordEncoder.encode(PW), Role.MEMBER, true, false, OffsetDateTime.now()
        ));

        // ── Share target (receives a read grant + share on childFolder) ──
        UUID shareTargetId = UUID.randomUUID();
        userRepository.save(new User(
            shareTargetId, "share-" + suffix + "@example.com", "Share Target",
            passwordEncoder.encode(PW), Role.MEMBER, true, false, OffsetDateTime.now()
        ));

        // ── Team A ──────────────────────────────────────────────────────
        scopeA = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO teams(id, name, normalized_name, visibility, created_by) VALUES (?, ?, ?, 'internal', ?)",
            scopeA, "Team Alpha", "teamalpha", actorId
        );

        rootA = UUID.randomUUID();
        insertFolder(rootA, null, "RootA", "roota", actorId, scopeA);

        childFolder = UUID.randomUUID();
        insertFolder(childFolder, rootA, "ChildFolder", "childfolder", actorId, scopeA);

        UUID grandchildFolder = UUID.randomUUID();
        insertFolder(grandchildFolder, childFolder, "GrandChild", "grandchild", actorId, scopeA);

        // File inside childFolder (contributes to itemCount in preview)
        UUID childFile = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO files(id, folder_id, name, normalized_name, owner_id, size_bytes, scope_type, scope_id) "
            + "VALUES (?, ?, ?, ?, ?, ?, 'team', ?)",
            childFile, childFolder, "report.pdf", "report.pdf", actorId, 1024L, scopeA
        );

        // ── Team B ──────────────────────────────────────────────────────
        scopeB = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO teams(id, name, normalized_name, visibility, created_by) VALUES (?, ?, ?, 'internal', ?)",
            scopeB, "Team Beta", "teambeta", actorId
        );

        rootB = UUID.randomUUID();
        insertFolder(rootB, null, "RootB", "rootb", actorId, scopeB);

        // ── Explicit grants ──────────────────────────────────────────────
        // actor needs:
        //   EDIT + SHARE + MOVE on childFolder  (preview + /move source SpEL guards)
        //   UPLOAD on rootB                      (/move dest SpEL guard)
        // Preset.ADMIN covers all (READ/UPLOAD/EDIT/MOVE/DOWNLOAD/DELETE/SHARE/PERMISSION_ADMIN).
        insertPermission("folder", childFolder, actorId, "admin", actorId);
        insertPermission("folder", rootB, actorId, "admin", actorId);
        // rootA admin so actor can READ/navigate (not required by SpEL but realistic fixture)
        insertPermission("folder", rootA, actorId, "admin", actorId);

        // ── Additional read grant on childFolder (to be wiped by cross-workspace move) ──
        insertPermission("folder", childFolder, shareTargetId, "read", actorId);

        // ── Share on childFolder via the read grant ──────────────────────
        // shares.permission_id → permissions.id for shareTargetId's read grant.
        UUID sharePermId = jdbc.queryForObject(
            "SELECT id FROM permissions WHERE resource_id = ? AND subject_id = ?",
            UUID.class, childFolder, shareTargetId
        );
        UUID shareId = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO shares(id, folder_id, permission_id, shared_by) VALUES (?, ?, ?, ?)",
            shareId, childFolder, sharePermId, actorId
        );
    }

    @Test
    void folderCrossWorkspaceMoveFullStack() {
        HttpHeaders actorHeaders = loginAs(actorEmail);

        // ── 1. Preview ───────────────────────────────────────────────────
        ResponseEntity<String> previewRes = postJsonTyped(
            "/api/folders/" + childFolder + "/move/preview",
            "{\"destinationFolderId\":\"" + rootB + "\"}",
            actorHeaders,
            String.class
        );
        assertThat(previewRes.getStatusCode())
            .as("preview should return 200")
            .isEqualTo(HttpStatus.OK);
        assertThat(previewRes.getBody())
            .as("preview response should include itemCount")
            .contains("itemCount");

        // ── 2. Move ──────────────────────────────────────────────────────
        ResponseEntity<Map> moveRes = postJsonTyped(
            "/api/folders/" + childFolder + "/move",
            "{\"targetParentId\":\"" + rootB + "\",\"allowCrossScope\":true}",
            actorHeaders,
            Map.class
        );
        assertThat(moveRes.getStatusCode())
            .as("cross-workspace move should return 200")
            .isEqualTo(HttpStatus.OK);

        @SuppressWarnings("unchecked")
        Map<String, Object> folderEnvelope = (Map<String, Object>) moveRes.getBody().get("folder");
        assertThat(folderEnvelope).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> scope = (Map<String, Object>) folderEnvelope.get("scope");
        assertThat(scope)
            .as("moved folder should expose scope block")
            .isNotNull();
        // ScopeRef DTO record: { type, id } — JSON key는 'id' (not 'scopeId'). spec §5.3 wire format.
        assertThat(scope.get("id"))
            .as("moved folder's scope.id should equal scopeB (%s)", scopeB)
            .isEqualTo(scopeB.toString());

        // ── 3. Post-conditions ───────────────────────────────────────────

        // 3a. All permissions on childFolder must be cleared
        Integer permLeft = jdbc.queryForObject(
            "SELECT COUNT(*) FROM permissions WHERE resource_type = 'folder' AND resource_id = ?",
            Integer.class, childFolder
        );
        assertThat(permLeft)
            .as("all permissions on moved childFolder should have been cleared")
            .isZero();

        // 3b. Active shares on childFolder must be 0 (revoked or permission cascade-deleted)
        Integer activeShares = jdbc.queryForObject(
            "SELECT COUNT(*) FROM shares s "
            + "INNER JOIN permissions p ON p.id = s.permission_id "
            + "WHERE p.resource_type = 'folder' AND p.resource_id = ? AND s.revoked_at IS NULL",
            Integer.class, childFolder
        );
        assertThat(activeShares)
            .as("active shares on moved childFolder should be 0")
            .isZero();

        // 3c. Exactly 1 audit event for the cross-workspace move
        Integer auditCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_log "
            + "WHERE event_type = 'folder.moved.cross_workspace' AND target_id = ?",
            Integer.class, childFolder
        );
        assertThat(auditCount)
            .as("exactly one audit.folder.moved.cross_workspace event should be emitted")
            .isEqualTo(1);
    }

    // ─────────────────────────── helpers ───────────────────────────

    private HttpHeaders loginAs(String email) {
        HttpHeaders csrf = csrfHandshake();
        ResponseEntity<Map> login = postJsonTyped(
            "/api/auth/login",
            "{\"email\":\"" + email + "\",\"password\":\"" + PW + "\"}",
            csrf, Map.class
        );
        assertThat(login.getStatusCode())
            .as("login should succeed for %s", email)
            .isEqualTo(HttpStatus.OK);
        String session = extractCookie(login, "SESSION");
        // Spring Security CSRF: POST 요청은 X-CSRF-Token 헤더 + XSRF-TOKEN cookie 둘 다 필요.
        // 헤더 빠지면 CsrfFilter가 403 — AuthAuditE2ETest.csrfWithSession 패턴과 동형.
        HttpHeaders h = new HttpHeaders();
        h.add("X-CSRF-Token", csrf.getFirst("X-CSRF-Token"));
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

    private <T> ResponseEntity<T> postJsonTyped(String path, String body,
                                                 HttpHeaders existingHeaders, Class<T> responseType) {
        HttpHeaders h = new HttpHeaders();
        h.addAll(existingHeaders);
        h.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, h), responseType);
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

    private void insertFolder(UUID id, UUID parentId, String name, String normalizedName,
                               UUID ownerId, UUID scopeId) {
        jdbc.update(
            "INSERT INTO folders(id, parent_id, name, normalized_name, slug, owner_id, scope_type, scope_id) "
            + "VALUES (?, ?, ?, ?, ?, ?, 'team', ?)",
            id, parentId, name, normalizedName, normalizedName, ownerId, scopeId
        );
    }

    private void insertPermission(String resourceType, UUID resourceId,
                                   UUID subjectId, String preset, UUID grantedBy) {
        jdbc.update(
            "INSERT INTO permissions(id, resource_type, resource_id, subject_type, subject_id, "
            + "preset, granted_by, expires_at) VALUES (?, ?, ?, 'user', ?, ?, ?, NULL)",
            UUID.randomUUID(), resourceType, resourceId, subjectId, preset, grantedBy
        );
    }
}
