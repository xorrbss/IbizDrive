package com.ibizdrive.share;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V6__shares.sql 스키마 + 제약 검증 (ADR #34, docs/02 §2.7).
 *
 * <p>핵심 회귀 가드:
 *   - shares 테이블 도입 + permission_id FK CASCADE
 *   - file_id / folder_id XOR CHECK (정확히 하나만 NOT NULL)
 *   - revoked_at / revoked_by pair-set CHECK
 *   - idx_shares_active partial index (revoked_at IS NULL)
 *   - V4 audit_log REVOKE 정책 무영향 (A2 회귀 가드)
 *
 * <p>{@link com.ibizdrive.folder.V5MigrationIT} 패턴 일관 — Testcontainers + DataJpaTest + Flyway.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class V6MigrationIT {

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

    @Autowired
    private JdbcTemplate jdbc;

    // -------------------- schema presence --------------------

    @Test
    void shares_table_exists_afterV6() {
        Boolean exists = jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM information_schema.tables " +
            "WHERE table_schema='public' AND table_name='shares')",
            Boolean.class
        );
        assertTrue(Boolean.TRUE.equals(exists), "shares 테이블 (V6, ADR #34)");
    }

    @Test
    void shares_indexes_exist() {
        Set<String> indexes = jdbc.queryForList(
            "SELECT indexname FROM pg_indexes WHERE schemaname='public' AND tablename='shares'",
            String.class
        ).stream().collect(Collectors.toSet());
        assertTrue(indexes.contains("idx_shares_active"),
            "idx_shares_active partial index (by-me 쿼리)");
        assertTrue(indexes.contains("idx_shares_permission"),
            "idx_shares_permission (with-me JOIN)");
    }

    // -------------------- XOR CHECK (file_id / folder_id) --------------------

    @Test
    void shares_targetXor_bothNull_violatesCheck() {
        UUID owner = insertUser("xor-both-null@test", "owner");
        UUID permission = insertPermission(owner);

        assertThrows(DataIntegrityViolationException.class,
            () -> jdbc.update(
                "INSERT INTO shares(id, file_id, folder_id, permission_id, shared_by) " +
                "VALUES (?, NULL, NULL, ?, ?)",
                UUID.randomUUID(), permission, owner
            ),
            "file_id / folder_id 둘 다 NULL이면 XOR CHECK 위반");
    }

    @Test
    void shares_targetXor_bothSet_violatesCheck() {
        UUID owner = insertUser("xor-both-set@test", "owner");
        UUID parent = insertFolder(owner);
        UUID file = insertFile(parent, owner);
        UUID permission = insertPermission(owner);

        assertThrows(DataIntegrityViolationException.class,
            () -> jdbc.update(
                "INSERT INTO shares(id, file_id, folder_id, permission_id, shared_by) " +
                "VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), file, parent, permission, owner
            ),
            "file_id + folder_id 둘 다 NOT NULL이면 XOR CHECK 위반");
    }

    @Test
    void shares_fileShare_inserts_successfully() {
        UUID owner = insertUser("file-share@test", "owner");
        UUID parent = insertFolder(owner);
        UUID file = insertFile(parent, owner);
        UUID permission = insertPermission(owner);

        UUID shareId = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO shares(id, file_id, permission_id, shared_by) VALUES (?, ?, ?, ?)",
            shareId, file, permission, owner
        );

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM shares WHERE id = ?", shareId);
        assertEquals(file, row.get("file_id"));
        assertNull(row.get("folder_id"));
        assertNull(row.get("revoked_at"));
        assertNotNull(row.get("created_at"));
    }

    // -------------------- revoked pair CHECK --------------------

    @Test
    void shares_revokedPair_onlyAtSet_violatesCheck() {
        UUID owner = insertUser("revoke-only-at@test", "owner");
        UUID parent = insertFolder(owner);
        UUID file = insertFile(parent, owner);
        UUID permission = insertPermission(owner);

        UUID shareId = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO shares(id, file_id, permission_id, shared_by) VALUES (?, ?, ?, ?)",
            shareId, file, permission, owner
        );

        assertThrows(DataIntegrityViolationException.class,
            () -> jdbc.update(
                "UPDATE shares SET revoked_at = NOW() WHERE id = ?",
                shareId
            ),
            "revoked_at만 set하고 revoked_by가 NULL이면 pair CHECK 위반");
    }

    @Test
    void shares_revokedPair_bothSet_succeeds() {
        UUID owner = insertUser("revoke-both@test", "owner");
        UUID parent = insertFolder(owner);
        UUID file = insertFile(parent, owner);
        UUID permission = insertPermission(owner);

        UUID shareId = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO shares(id, file_id, permission_id, shared_by) VALUES (?, ?, ?, ?)",
            shareId, file, permission, owner
        );

        assertDoesNotThrow(() -> jdbc.update(
            "UPDATE shares SET revoked_at = NOW(), revoked_by = ? WHERE id = ?",
            owner, shareId
        ), "revoked_at + revoked_by 모두 set이면 pair CHECK 통과");
    }

    // -------------------- permission_id ON DELETE CASCADE --------------------

    @Test
    void shares_cascade_whenPermissionDeleted() {
        UUID owner = insertUser("cascade@test", "owner");
        UUID parent = insertFolder(owner);
        UUID file = insertFile(parent, owner);
        UUID permission = insertPermission(owner);

        UUID shareId = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO shares(id, file_id, permission_id, shared_by) VALUES (?, ?, ?, ?)",
            shareId, file, permission, owner
        );

        jdbc.update("DELETE FROM permissions WHERE id = ?", permission);

        List<Map<String, Object>> remaining = jdbc.queryForList("SELECT id FROM shares WHERE id = ?", shareId);
        assertTrue(remaining.isEmpty(),
            "permission row가 삭제되면 share row도 CASCADE로 함께 삭제 (ADR #34 결정 1)");
    }

    // -------------------- A2 회귀 가드 (audit_log REVOKE 무영향) --------------------

    @Test
    void v6_doesNotAffectAuditLog_revokePolicy() {
        Boolean hasUpdate = jdbc.queryForObject(
            "SELECT has_table_privilege('app_user', 'audit_log', 'UPDATE')",
            Boolean.class
        );
        assertEquals(Boolean.FALSE, hasUpdate,
            "V6가 audit_log REVOKE 정책을 깨뜨리지 않아야 함 (A2 회귀 가드)");

        Boolean hasDelete = jdbc.queryForObject(
            "SELECT has_table_privilege('app_user', 'audit_log', 'DELETE')",
            Boolean.class
        );
        assertEquals(Boolean.FALSE, hasDelete,
            "V6가 audit_log DELETE 권한 부여하면 안 됨 (A2 회귀 가드)");
    }

    // ====================== helpers ======================

    private UUID insertUser(String email, String displayName) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)",
            id, email, displayName
        );
        return id;
    }

    private UUID insertFolder(UUID ownerId) {
        UUID id = UUID.randomUUID();
        String name = "f-" + id.toString().substring(0, 8);
        jdbc.update(
            "INSERT INTO folders(id, parent_id, name, normalized_name, slug, owner_id, scope_type, scope_id) " +
            "VALUES (?, NULL, ?, ?, ?, ?, 'department', ?)",
            id, name, name, name, ownerId, java.util.UUID.randomUUID()
        );
        return id;
    }

    private UUID insertFile(UUID folderId, UUID ownerId) {
        UUID id = UUID.randomUUID();
        String name = "file-" + id.toString().substring(0, 8) + ".bin";
        jdbc.update(
            "INSERT INTO files(id, folder_id, name, normalized_name, owner_id, size_bytes, scope_type, scope_id) " +
            "VALUES (?, ?, ?, ?, ?, ?, 'department', ?)",
            id, folderId, name, name, ownerId, 0L, java.util.UUID.randomUUID()
        );
        return id;
    }

    private UUID insertPermission(UUID ownerId) {
        UUID id = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();   // V5는 FK 없는 resource_id 사용 (resource_type/resource_id 매트릭스만 강제).
        jdbc.update(
            "INSERT INTO permissions(id, resource_type, resource_id, subject_type, subject_id, " +
            "preset, granted_by) VALUES (?, 'file', ?, 'user', ?, 'read', ?)",
            id, resourceId, ownerId, ownerId
        );
        return id;
    }
}
