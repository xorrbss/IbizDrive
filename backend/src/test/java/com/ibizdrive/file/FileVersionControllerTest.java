package com.ibizdrive.file;

import com.ibizdrive.permission.PermissionService;
import com.ibizdrive.permission.Preset;
import com.ibizdrive.user.IbizDriveUserDetails;
import com.ibizdrive.user.Role;
import com.ibizdrive.user.User;
import com.ibizdrive.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A5.2 — {@link FileVersionController} integration test with full Spring stack + Testcontainers.
 *
 * <p>{@code FolderControllerTest}는 service mock으로 controller 책임만 검증하지만, 본 endpoint는
 * (a) 권한 매트릭스(ADMIN/AUDITOR/MEMBER ± grant)와 (b) {@code is_current} 계산이 핵심 계약이므로
 * full integration ({@code @SpringBootTest} + 실제 evaluator + 실 DB)이 필요하다.
 *
 * <p>{@link com.ibizdrive.permission.PermissionEvaluatorIntegrationTest} (slice) +
 * {@link com.ibizdrive.permission.PermissionEndpointE2ETest} (RestTemplate)와 비교해 본 테스트는
 * MockMvc + {@code user()} post-processor를 사용해 login/CSRF flow는 우회하고 method security만 행사.
 *
 * <p>Docker 미가용 환경에서는 자동 스킵 ({@code disabledWithoutDocker=true}).
 *
 * <p>검증 매트릭스 (list / docs/02 §7.6 + ADR #29):
 * <ol>
 *   <li>ADMIN — 200 + DESC 정렬</li>
 *   <li>AUDITOR — 200 (Role-level READ 통과)</li>
 *   <li>MEMBER without grant — 403 PERMISSION_DENIED envelope</li>
 *   <li>MEMBER with file READ grant — 200</li>
 *   <li>존재하지 않는 fileId — 404 NOT_FOUND envelope</li>
 *   <li>soft-deleted file — 404 (휴지통 노출 차단)</li>
 *   <li>{@code isCurrent} 정확성 — file.currentVersionId와의 동등성</li>
 * </ol>
 *
 * <p>검증 매트릭스 (restore / M-RP.2.2 + ADR #39):
 * <ol>
 *   <li>ADMIN restore → 200 {@code { file: FileDto }}, currentVersionId 갱신, {@code VERSION_RESTORED} audit 1건</li>
 *   <li>이미 current인 version 재호출 → 200 멱등 + audit emit X (DB row 변경 없음)</li>
 *   <li>MEMBER without grant → 403 PERMISSION_DENIED ({@code required=[EDIT]})</li>
 *   <li>MEMBER with READ-only grant → 403 (EDIT 미보유)</li>
 *   <li>MEMBER with EDIT grant → 200</li>
 *   <li>cross-file version (다른 파일의 versionId) → 404 + audit emit X</li>
 *   <li>soft-deleted file → 404</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class FileVersionControllerTest {

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

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PermissionService permissionService;
    @Autowired private FileVersionRepository fileVersionRepository;

    private IbizDriveUserDetails admin;
    private IbizDriveUserDetails auditor;
    private IbizDriveUserDetails member;

    private UUID folderId;
    private UUID fileId;
    private UUID v1Id;
    private UUID v2Id;
    private UUID v3Id;

    @BeforeEach
    void seed() {
        // V5 FK fk_files_current_version은 DEFERRABLE INITIALLY DEFERRED — 각 SQL이 별도
        // 트랜잭션이므로 file_versions를 먼저 비우려면 files.current_version_id를 NULL로 끊어야 한다.
        jdbc.update("UPDATE files SET current_version_id = NULL");
        jdbc.update("DELETE FROM file_versions");
        jdbc.update("DELETE FROM files");
        jdbc.update("DELETE FROM folders");
        jdbc.update("DELETE FROM permissions");
        // V4 REVOKE는 app_user role에만 적용 — 기본 datasource(super)로는 정리 가능.
        jdbc.update("DELETE FROM audit_log");
        userRepository.deleteAll();

        admin = principalOf(insertUser("admin@test", "Admin", Role.ADMIN), Role.ADMIN, "admin@test");
        auditor = principalOf(insertUser("auditor@test", "Auditor", Role.AUDITOR), Role.AUDITOR, "auditor@test");
        member = principalOf(insertUser("member@test", "Member", Role.MEMBER), Role.MEMBER, "member@test");

        // V13 — folders/files scope NOT NULL. fixture root는 fake department scope를 가지며,
        // file은 동일 scope를 상속 (spec §1.2 invariant).
        folderId = UUID.randomUUID();
        UUID scopeId = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO folders(id, parent_id, name, normalized_name, slug, owner_id, audit_level, " +
            "scope_type, scope_id) " +
            "VALUES (?, NULL, 'f', 'f', 'f', ?, 'standard', 'department', ?)",
            folderId, admin.getUser().getId(), scopeId
        );

        fileId = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO files(id, folder_id, name, normalized_name, owner_id, size_bytes, " +
            "scope_type, scope_id) " +
            "VALUES (?, ?, 'doc.txt', 'doc.txt', ?, 0, 'department', ?)",
            fileId, folderId, admin.getUser().getId(), scopeId
        );

        // 3개 버전 INSERT (오름차순) — current는 v2(중간 버전)로 설정해 정렬 + isCurrent 분리 검증.
        v1Id = saveVersion(fileId, 1, admin.getUser().getId());
        v2Id = saveVersion(fileId, 2, admin.getUser().getId());
        v3Id = saveVersion(fileId, 3, admin.getUser().getId());
        jdbc.update("UPDATE files SET current_version_id = ? WHERE id = ?", v2Id, fileId);
    }

    // ─── 권한 매트릭스 ───────────────────────────────────────────────────────

    @Test
    void admin_listsVersions_descOrderAndCorrectIsCurrent() throws Exception {
        mvc.perform(get("/api/files/{fileId}/versions", fileId).with(user(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.versions", org.hamcrest.Matchers.hasSize(3)))
            // DESC 정렬: 첫 항목 = v3 (최신)
            .andExpect(jsonPath("$.versions[0].versionNumber").value(3))
            .andExpect(jsonPath("$.versions[1].versionNumber").value(2))
            .andExpect(jsonPath("$.versions[2].versionNumber").value(1))
            // isCurrent: v2만 true (currentVersionId = v2)
            .andExpect(jsonPath("$.versions[0].isCurrent").value(false))
            .andExpect(jsonPath("$.versions[1].isCurrent").value(true))
            .andExpect(jsonPath("$.versions[2].isCurrent").value(false));
    }

    @Test
    void auditor_listsVersions_succeeds() throws Exception {
        mvc.perform(get("/api/files/{fileId}/versions", fileId).with(user(auditor)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.versions", org.hamcrest.Matchers.hasSize(3)));
    }

    @Test
    void member_withoutGrant_returns403_envelope() throws Exception {
        mvc.perform(get("/api/files/{fileId}/versions", fileId).with(user(member)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"))
            .andExpect(jsonPath("$.error.details.required[0]").value("READ"));
    }

    @Test
    void member_withFileReadGrant_succeeds() throws Exception {
        permissionService.grantPermission(
            "file", fileId,
            "user", member.getUser().getId(),
            Preset.READ, null, admin.getUser().getId()
        );

        mvc.perform(get("/api/files/{fileId}/versions", fileId).with(user(member)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.versions", org.hamcrest.Matchers.hasSize(3)));
    }

    // ─── 404 매트릭스 ──────────────────────────────────────────────────────

    @Test
    void nonExistentFileId_returns404() throws Exception {
        UUID missing = UUID.randomUUID();
        mvc.perform(get("/api/files/{fileId}/versions", missing).with(user(admin)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void softDeletedFile_returns404() throws Exception {
        // soft-delete: deleted_at + purge_after 동시 set (V5 files_deleted_purge_check)
        jdbc.update(
            "UPDATE files SET deleted_at = NOW(), purge_after = NOW() + INTERVAL '30 days' " +
            "WHERE id = ?",
            fileId
        );

        mvc.perform(get("/api/files/{fileId}/versions", fileId).with(user(admin)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    // ─── isCurrent 정확성 (currentVersionId NULL 케이스) ──────────────────

    @Test
    void isCurrent_allFalse_whenCurrentVersionIdIsNull() throws Exception {
        jdbc.update("UPDATE files SET current_version_id = NULL WHERE id = ?", fileId);

        mvc.perform(get("/api/files/{fileId}/versions", fileId).with(user(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.versions[0].isCurrent").value(false))
            .andExpect(jsonPath("$.versions[1].isCurrent").value(false))
            .andExpect(jsonPath("$.versions[2].isCurrent").value(false));
    }

    // ─── M-RP.2.2 restore 매트릭스 ───────────────────────────────────────

    @Test
    void restore_admin_setsCurrent_returnsFileDto_emitsAudit() throws Exception {
        // current = v2, restore target = v1 (older)
        mvc.perform(post("/api/files/{fileId}/versions/{versionId}/restore", fileId, v1Id)
                .with(user(admin)).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.file.id").value(fileId.toString()))
            .andExpect(jsonPath("$.file.currentVersionId").value(v1Id.toString()));

        UUID dbCurrent = jdbc.queryForObject(
            "SELECT current_version_id FROM files WHERE id = ?", UUID.class, fileId);
        assertThat(dbCurrent).isEqualTo(v1Id);

        // denormalized 메타 동기화 — files.size_bytes/mime_type가 v1 값으로 갱신 (FileUploadService:214-217 invariant).
        // saveVersion 헬퍼는 size_bytes = 1024 * versionNumber → v1 = 1024.
        Long dbSize = jdbc.queryForObject(
            "SELECT size_bytes FROM files WHERE id = ?", Long.class, fileId);
        assertThat(dbSize).isEqualTo(1024L);
        String dbMime = jdbc.queryForObject(
            "SELECT mime_type FROM files WHERE id = ?", String.class, fileId);
        assertThat(dbMime).isEqualTo("text/plain");

        Integer auditCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_log " +
            "WHERE event_type = 'version.restored' AND target_id = ?",
            Integer.class, fileId);
        assertThat(auditCount).isEqualTo(1);

        String beforeJson = jdbc.queryForObject(
            "SELECT before_state::text FROM audit_log " +
            "WHERE event_type = 'version.restored' AND target_id = ?",
            String.class, fileId);
        String afterJson = jdbc.queryForObject(
            "SELECT after_state::text FROM audit_log " +
            "WHERE event_type = 'version.restored' AND target_id = ?",
            String.class, fileId);
        assertThat(beforeJson).contains(v2Id.toString());
        assertThat(afterJson).contains(v1Id.toString());
    }

    @Test
    void restore_alreadyCurrent_isIdempotent_noAudit() throws Exception {
        // current = v2, restore = v2 → no-op
        mvc.perform(post("/api/files/{fileId}/versions/{versionId}/restore", fileId, v2Id)
                .with(user(admin)).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.file.currentVersionId").value(v2Id.toString()));

        Integer auditCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_log " +
            "WHERE event_type = 'version.restored' AND target_id = ?",
            Integer.class, fileId);
        assertThat(auditCount).isEqualTo(0);
    }

    @Test
    void restore_memberWithoutGrant_returns403() throws Exception {
        mvc.perform(post("/api/files/{fileId}/versions/{versionId}/restore", fileId, v1Id)
                .with(user(member)).with(csrf()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"))
            .andExpect(jsonPath("$.error.details.required[0]").value("EDIT"));
    }

    @Test
    void restore_memberWithReadOnlyGrant_returns403() throws Exception {
        // READ-only는 EDIT 권한 없으므로 거부 — docs/03 §3 권한 매트릭스
        permissionService.grantPermission(
            "file", fileId,
            "user", member.getUser().getId(),
            Preset.READ, null, admin.getUser().getId()
        );

        mvc.perform(post("/api/files/{fileId}/versions/{versionId}/restore", fileId, v1Id)
                .with(user(member)).with(csrf()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
    }

    @Test
    void restore_memberWithEditGrant_succeeds() throws Exception {
        permissionService.grantPermission(
            "file", fileId,
            "user", member.getUser().getId(),
            Preset.EDIT, null, admin.getUser().getId()
        );

        mvc.perform(post("/api/files/{fileId}/versions/{versionId}/restore", fileId, v1Id)
                .with(user(member)).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.file.currentVersionId").value(v1Id.toString()));
    }

    @Test
    void restore_crossFileVersion_returns404_noAudit() throws Exception {
        // 다른 파일에 속한 version으로 본 파일의 current를 바꾸려는 우회 시도 차단.
        UUID otherFileId = UUID.randomUUID();
        // V13 — file은 부모 folder의 scope를 그대로 상속 (spec §1.2 invariant).
        jdbc.update(
            "INSERT INTO files(id, folder_id, name, normalized_name, owner_id, size_bytes, " +
            "scope_type, scope_id) " +
            "SELECT ?, ?, 'other.txt', 'other.txt', ?, 0, scope_type, scope_id " +
            "FROM folders WHERE id = ?",
            otherFileId, folderId, admin.getUser().getId(), folderId
        );
        UUID strayVersionId = saveVersion(otherFileId, 1, admin.getUser().getId());

        mvc.perform(post("/api/files/{fileId}/versions/{versionId}/restore", fileId, strayVersionId)
                .with(user(admin)).with(csrf()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        // current_version_id 변경 없음
        UUID dbCurrent = jdbc.queryForObject(
            "SELECT current_version_id FROM files WHERE id = ?", UUID.class, fileId);
        assertThat(dbCurrent).isEqualTo(v2Id);

        Integer auditCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_log " +
            "WHERE event_type = 'version.restored' AND target_id = ?",
            Integer.class, fileId);
        assertThat(auditCount).isEqualTo(0);
    }

    @Test
    void restore_softDeletedFile_returns404() throws Exception {
        jdbc.update(
            "UPDATE files SET deleted_at = NOW(), purge_after = NOW() + INTERVAL '30 days' " +
            "WHERE id = ?",
            fileId
        );

        mvc.perform(post("/api/files/{fileId}/versions/{versionId}/restore", fileId, v1Id)
                .with(user(admin)).with(csrf()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    // ====================== helpers ======================

    private UUID insertUser(String email, String displayName, Role role) {
        UUID id = UUID.randomUUID();
        User u = new User(
            id, email, displayName, passwordEncoder.encode("Sup3rSecret_Pw_12"),
            role, true, false, OffsetDateTime.now()
        );
        userRepository.save(u);
        return id;
    }

    private IbizDriveUserDetails principalOf(UUID id, Role role, String email) {
        User u = new User(
            id, email, role.name(), "{bcrypt}$2a$12$dummy",
            role, true, false, OffsetDateTime.now()
        );
        return new IbizDriveUserDetails(u);
    }

    private UUID saveVersion(UUID fileId, int versionNumber, UUID uploadedBy) {
        FileVersion v = new FileVersion();
        v.setId(UUID.randomUUID());
        v.setFileId(fileId);
        v.setVersionNumber(versionNumber);
        v.setStorageKey(UUID.randomUUID());
        v.setSizeBytes(1024L * versionNumber);
        v.setChecksumSha256("0".repeat(64));
        v.setMimeType("text/plain");
        v.setScanStatus(VersionScanStatus.CLEAN);
        v.setUploadedBy(uploadedBy);
        v.setUploadedAt(Instant.now());
        return fileVersionRepository.save(v).getId();
    }
}
