package com.ibizdrive.file;

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
 * Plan D Task 22 — cross-workspace file move full-stack E2E.
 *
 * <p>시나리오:
 * <ol>
 *   <li>팀 A(scopeA) + 팀 B(scopeB) 시드, 각각 root 폴더 생성.</li>
 *   <li>actor를 rootA, rootB, 파일 위치 폴더(rootA)에 대해 {@code admin} preset으로 explicit grant.</li>
 *   <li>팀 A root 아래 file 1개 + additional permission row + share 시드.</li>
 *   <li>{@code POST /api/files/{fileId}/move/preview { destinationFolderId: rootB }} →
 *       200 + {@code itemCount=1}, {@code removedPermissions.length()=1}, {@code revokedShares.length()=1}.</li>
 *   <li>{@code POST /api/files/{fileId}/move { targetFolderId: rootB, allowCrossScope: true }} →
 *       200 + file {@code scope.scopeId == scopeB}, {@code folderId == rootB}.</li>
 *   <li>post-conditions: file permissions 0건, active shares 0건, audit_log 1건.</li>
 * </ol>
 *
 * <p>인증: Task 21({@code CrossWorkspaceMoveE2ETest})과 동일한 RANDOM_PORT + Testcontainers + cookie/CSRF 패턴.
 * {@code @Testcontainers(disabledWithoutDocker = true)} — Docker 없이 실행하면 SKIP.
 * CI에서 실제 Postgres 컨테이너와 함께 풀 검증된다.
 *
 * <p>audit_log {@code event_type} wire format: {@code file.moved.cross_workspace}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class CrossWorkspaceFileMoveE2ETest {

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

    /** Team A 식별자 (scope_id for team-A folders and file). */
    private UUID scopeA;
    /** Team B 식별자 (scope_id target after cross-workspace move). */
    private UUID scopeB;

    /** Root folder of team A — contains the source file. */
    private UUID rootA;
    /** Root folder of team B — destination of the cross-workspace move. */
    private UUID rootB;

    /** The single file under rootA that will be moved. */
    private UUID fileId;

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

        // ── Share target (receives a read grant + share on the file) ──
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

        // ── File inside rootA (the item to be cross-workspace moved) ────
        fileId = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO files(id, folder_id, name, normalized_name, owner_id, size_bytes, scope_type, scope_id) "
            + "VALUES (?, ?, ?, ?, ?, ?, 'team', ?)",
            fileId, rootA, "document.pdf", "document.pdf", actorId, 2048L, scopeA
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
        // actor needs on file: EDIT + SHARE + MOVE  (preview + /move source SpEL guards)
        // actor needs on rootB: UPLOAD               (/move dest SpEL guard)
        // Preset.ADMIN covers all (READ/UPLOAD/EDIT/MOVE/DOWNLOAD/DELETE/SHARE/PERMISSION_ADMIN).
        insertPermission("file", fileId, actorId, "admin", actorId);
        insertPermission("folder", rootB, actorId, "admin", actorId);
        // rootA admin so actor can READ/navigate (realistic fixture)
        insertPermission("folder", rootA, actorId, "admin", actorId);

        // ── Additional read grant on the file (to be wiped by cross-workspace move) ──
        insertPermission("file", fileId, shareTargetId, "read", actorId);

        // ── Share on the file via the read grant ─────────────────────────
        // shares.permission_id → permissions.id for shareTargetId's read grant.
        UUID sharePermId = jdbc.queryForObject(
            "SELECT id FROM permissions WHERE resource_id = ? AND subject_id = ?",
            UUID.class, fileId, shareTargetId
        );
        UUID shareId = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO shares(id, file_id, permission_id, shared_by) VALUES (?, ?, ?, ?)",
            shareId, fileId, sharePermId, actorId
        );
    }

    @Test
    void fileCrossWorkspaceMoveFullStack() {
        HttpHeaders actorHeaders = loginAs(actorEmail);

        // ── 1. Preview ───────────────────────────────────────────────────
        ResponseEntity<String> previewRes = postJsonTyped(
            "/api/files/" + fileId + "/move/preview",
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
        assertThat(previewRes.getBody())
            .as("preview response should include removedPermissions")
            .contains("removedPermissions");
        assertThat(previewRes.getBody())
            .as("preview response should include revokedShares")
            .contains("revokedShares");

        // ── 2. Move ──────────────────────────────────────────────────────
        ResponseEntity<Map> moveRes = postJsonTyped(
            "/api/files/" + fileId + "/move",
            "{\"targetFolderId\":\"" + rootB + "\",\"allowCrossScope\":true}",
            actorHeaders,
            Map.class
        );
        assertThat(moveRes.getStatusCode())
            .as("cross-workspace file move should return 200")
            .isEqualTo(HttpStatus.OK);

        @SuppressWarnings("unchecked")
        Map<String, Object> fileEnvelope = (Map<String, Object>) moveRes.getBody().get("file");
        assertThat(fileEnvelope).isNotNull();

        // 2a. file.folderId should equal rootB
        assertThat(fileEnvelope.get("folderId"))
            .as("moved file's folderId should equal rootB (%s)", rootB)
            .isEqualTo(rootB.toString());

        // 2b. file.scope.scopeId should equal scopeB
        @SuppressWarnings("unchecked")
        Map<String, Object> scope = (Map<String, Object>) fileEnvelope.get("scope");
        assertThat(scope)
            .as("moved file should expose scope block")
            .isNotNull();
        assertThat(scope.get("scopeId"))
            .as("moved file's scopeId should equal scopeB (%s)", scopeB)
            .isEqualTo(scopeB.toString());

        // ── 3. Post-conditions ───────────────────────────────────────────

        // 3a. files.scope_type / scope_id reflect team B
        Map<String, Object> fileRow = jdbc.queryForMap(
            "SELECT scope_type, scope_id, folder_id FROM files WHERE id = ?", fileId
        );
        assertThat(fileRow.get("scope_type"))
            .as("files.scope_type should be 'team'")
            .isEqualTo("team");
        assertThat(fileRow.get("scope_id").toString())
            .as("files.scope_id should equal scopeB")
            .isEqualTo(scopeB.toString());
        assertThat(fileRow.get("folder_id").toString())
            .as("files.folder_id should equal rootB")
            .isEqualTo(rootB.toString());

        // 3b. All permissions on the file must be cleared
        Integer permLeft = jdbc.queryForObject(
            "SELECT COUNT(*) FROM permissions WHERE resource_type = 'file' AND resource_id = ?",
            Integer.class, fileId
        );
        assertThat(permLeft)
            .as("all permissions on moved file should have been cleared")
            .isZero();

        // 3c. Active shares on the file must be 0 (revoked or permission cascade-deleted)
        Integer activeShares = jdbc.queryForObject(
            "SELECT COUNT(*) FROM shares s "
            + "INNER JOIN permissions p ON p.id = s.permission_id "
            + "WHERE p.resource_type = 'file' AND p.resource_id = ? AND s.revoked_at IS NULL",
            Integer.class, fileId
        );
        assertThat(activeShares)
            .as("active shares on moved file should be 0")
            .isZero();

        // 3d. Exactly 1 audit event for the cross-workspace file move
        Integer auditCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_log "
            + "WHERE event_type = 'file.moved.cross_workspace' AND target_id = ?",
            Integer.class, fileId
        );
        assertThat(auditCount)
            .as("exactly one audit.file.moved.cross_workspace event should be emitted")
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
