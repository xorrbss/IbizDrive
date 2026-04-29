package com.ibizdrive.folder;

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

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V5__folders_files_permissions.sql 스키마 + 제약 검증 (ADR #27/#28/#29, docs/02 §2.3~§2.6).
 *
 * <p>Flyway가 V1~V5를 적용한 직후의 information_schema/pg_indexes 상태에서
 * 4테이블이 docs/02 §2.3/§2.4/§2.5/§2.6 명세를 충족하는지 RED → GREEN 검증.
 *
 * <p>본 테스트는 superuser connection으로 실행 (default Testcontainers 사용자).
 * 핵심 회귀 가드:
 *   - root parent UNIQUE COALESCE 보강 — Postgres NULL distinct 우회 차단 (ADR #27)
 *   - file_versions 테이블 도입 + DEFERRABLE FK (ADR #29 보장사항)
 *   - V4 audit_log REVOKE 정책 무영향 (A2 회귀 가드)
 *
 * <p>{@link com.ibizdrive.audit.AuditLogSchemaTest}와 동일한 Testcontainers 패턴.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class V5MigrationIT {

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
    void allFourTables_exist() {
        Set<String> tables = jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables " +
            "WHERE table_schema='public' AND table_name IN ('folders','files','file_versions','permissions')",
            String.class
        ).stream().collect(Collectors.toSet());
        assertEquals(Set.of("folders", "files", "file_versions", "permissions"), tables,
            "V5 후 4테이블이 모두 존재해야 함");
    }

    @Test
    void fileVersions_table_exists_evenThoughEntityIsDeferredToA5() {
        // ADR #29 — schema는 V5에서 도입, entity/repository/CRUD는 A5 이월.
        // schema가 없으면 A5에서 추가 마이그레이션 없이 entity 도입이 불가능해짐.
        Boolean exists = jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM information_schema.tables " +
            "WHERE table_schema='public' AND table_name='file_versions')",
            Boolean.class
        );
        assertTrue(Boolean.TRUE.equals(exists), "file_versions 테이블 (ADR #29 schema-only)");
    }

    @Test
    void files_currentVersionId_fk_isDeferrable() {
        // ADR #29 보장사항: files INSERT와 file_versions INSERT가 동일 트랜잭션 내 임의 순서로 가능해야 함.
        Boolean isDeferrable = jdbc.queryForObject(
            "SELECT condeferrable FROM pg_constraint WHERE conname = 'fk_files_current_version'",
            Boolean.class
        );
        assertTrue(Boolean.TRUE.equals(isDeferrable),
            "fk_files_current_version FK는 DEFERRABLE INITIALLY DEFERRED 여야 함");
    }

    // -------------------- folders UNIQUE (root COALESCE 보강) --------------------

    @Test
    void folders_sameParent_sameNormalizedName_violatesUnique() {
        UUID owner = insertUser("owner1@test", "owner1");
        UUID parent = insertFolder(null, "parent", "parent", owner);

        insertFolder(parent, "child", "child", owner);
        assertThrows(DataIntegrityViolationException.class,
            () -> insertFolder(parent, "child", "child", owner),
            "동일 parent_id + normalized_name 중복은 UNIQUE INDEX로 차단되어야 함");
    }

    @Test
    void folders_softDelete_thenSameNormalizedName_succeeds() {
        UUID owner = insertUser("owner2@test", "owner2");
        UUID parent = insertFolder(null, "parent2", "parent2", owner);

        UUID first = insertFolder(parent, "doc", "doc", owner);
        // soft delete: deleted_at + purge_after 동시 set (CHECK 제약 충족).
        jdbc.update(
            "UPDATE folders SET deleted_at = NOW(), purge_after = NOW() + INTERVAL '30 days' WHERE id = ?",
            first
        );
        assertDoesNotThrow(
            () -> insertFolder(parent, "doc", "doc", owner),
            "soft delete된 행은 partial unique index에서 제외되어 같은 이름 재생성 허용"
        );
    }

    @Test
    void folders_rootParent_null_sameName_violatesUnique_viaCoalesce() {
        // ADR #27 — root parent (parent_id IS NULL)은 ZERO_UUID로 COALESCE되어
        // Postgres가 NULL을 distinct 처리해 UNIQUE를 우회하는 동작을 차단해야 함.
        UUID owner = insertUser("owner3@test", "owner3");
        insertFolder(null, "root-name", "root-name", owner);
        assertThrows(DataIntegrityViolationException.class,
            () -> insertFolder(null, "root-name", "root-name", owner),
            "root parent NULL 두 행이 같은 normalized_name을 가지면 COALESCE UNIQUE 보강이 차단해야 함");
    }

    // -------------------- file_versions --------------------

    @Test
    void fileVersions_versionNumber_uniquePerFile() {
        UUID owner = insertUser("owner4@test", "owner4");
        UUID parent = insertFolder(null, "fparent", "fparent", owner);
        UUID file = insertFile(parent, "report.pdf", "report.pdf", owner);

        insertFileVersion(file, 1, owner);
        assertThrows(DataIntegrityViolationException.class,
            () -> insertFileVersion(file, 1, owner),
            "(file_id, version_number) UNIQUE 위반은 차단되어야 함");
    }

    // -------------------- A2 회귀 가드 (audit_log REVOKE 무영향) --------------------

    @Test
    void v5_doesNotAffectAuditLog_revokePolicy() {
        // V4가 audit_log에 REVOKE UPDATE/DELETE FROM app_user를 걸어둠.
        // V5가 audit_log 권한을 건드리지 않았는지 검증 — superuser로는 직접 권한 조회.
        // app_user role의 audit_log UPDATE 권한이 없어야 함 (false).
        Boolean hasUpdate = jdbc.queryForObject(
            "SELECT has_table_privilege('app_user', 'audit_log', 'UPDATE')",
            Boolean.class
        );
        assertEquals(Boolean.FALSE, hasUpdate,
            "V5가 audit_log REVOKE 정책을 깨뜨리지 않아야 함 (A2 회귀 가드)");

        Boolean hasDelete = jdbc.queryForObject(
            "SELECT has_table_privilege('app_user', 'audit_log', 'DELETE')",
            Boolean.class
        );
        assertEquals(Boolean.FALSE, hasDelete,
            "V5가 audit_log DELETE 권한 부여하면 안 됨 (A2 회귀 가드)");
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

    private UUID insertFolder(UUID parentId, String name, String normalizedName, UUID ownerId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO folders(id, parent_id, name, normalized_name, slug, owner_id) " +
            "VALUES (?, ?, ?, ?, ?, ?)",
            id, parentId, name, normalizedName, name, ownerId
        );
        return id;
    }

    private UUID insertFile(UUID folderId, String name, String normalizedName, UUID ownerId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO files(id, folder_id, name, normalized_name, owner_id, size_bytes) " +
            "VALUES (?, ?, ?, ?, ?, ?)",
            id, folderId, name, normalizedName, ownerId, 0L
        );
        return id;
    }

    private UUID insertFileVersion(UUID fileId, int versionNumber, UUID uploadedBy) {
        UUID id = UUID.randomUUID();
        UUID storageKey = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO file_versions(id, file_id, version_number, storage_key, size_bytes, " +
            "checksum_sha256, uploaded_by) VALUES (?, ?, ?, ?, ?, ?, ?)",
            id, fileId, versionNumber, storageKey, 0L,
            "0000000000000000000000000000000000000000000000000000000000000000",
            uploadedBy
        );
        return id;
    }
}
