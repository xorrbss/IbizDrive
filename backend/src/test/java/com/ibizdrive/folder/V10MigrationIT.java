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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V10__deleted_by.sql 스키마 + 제약 검증 (Wave 2 T9 follow-up).
 *
 * <p>{@link V5MigrationIT}와 동일한 raw {@code JdbcTemplate} 패턴 — entity 매핑이 아닌 schema-level
 * 제약(CHECK 단방향 + FK ON DELETE SET NULL + 컬럼 존재)을 직접 검증한다. entity 경로 회귀 가드는
 * {@link FolderRepositoryTest} + {@code FolderMutationServiceTest}/{@code FileMutationServiceTest}가
 * 담당.
 *
 * <p>핵심 회귀 가드:
 *   - {@code files_deleted_by_check} / {@code folders_deleted_by_check} 단방향 — 활성 row에 deleter
 *     채우는 것은 차단, trash row는 허용
 *   - {@code ON DELETE SET NULL} — deleter 사용자 hard-delete 후에도 trash row 보존, deleted_by만 NULL
 *   - 컬럼 type/nullable 정합 — Hibernate {@code ddl-auto=validate}만으로는 잡히지 않는 schema-only 가드
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class V10MigrationIT {

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

    // -------------------- column presence --------------------

    @Test
    void files_deletedBy_column_exists_uuid_nullable() {
        String dataType = jdbc.queryForObject(
            "SELECT data_type FROM information_schema.columns " +
            "WHERE table_schema='public' AND table_name='files' AND column_name='deleted_by'",
            String.class
        );
        assertEquals("uuid", dataType, "files.deleted_by 컬럼이 uuid 타입으로 존재해야 함");

        String isNullable = jdbc.queryForObject(
            "SELECT is_nullable FROM information_schema.columns " +
            "WHERE table_schema='public' AND table_name='files' AND column_name='deleted_by'",
            String.class
        );
        assertEquals("YES", isNullable, "files.deleted_by는 nullable (활성 row + 컷오프 이전 trash row)");
    }

    @Test
    void folders_deletedBy_column_exists_uuid_nullable() {
        String dataType = jdbc.queryForObject(
            "SELECT data_type FROM information_schema.columns " +
            "WHERE table_schema='public' AND table_name='folders' AND column_name='deleted_by'",
            String.class
        );
        assertEquals("uuid", dataType, "folders.deleted_by 컬럼이 uuid 타입으로 존재해야 함");
    }

    // -------------------- CHECK 단방향 --------------------

    @Test
    void files_activeRow_setDeletedBy_violatesCheck() {
        UUID owner = insertUser("v10-owner-1@test", "v10-owner-1");
        UUID folder = insertFolder(null, "v10-fparent-1", owner);
        UUID file = insertFile(folder, "v10-active.txt", owner);

        // 활성 row(deleted_at IS NULL)에 deleted_by를 채우려는 UPDATE는 CHECK로 차단되어야 함.
        assertThrows(DataIntegrityViolationException.class,
            () -> jdbc.update("UPDATE files SET deleted_by = ? WHERE id = ?", owner, file),
            "files_deleted_by_check: 활성 row에 deleted_by 채우는 것은 차단되어야 함");
    }

    @Test
    void folders_activeRow_setDeletedBy_violatesCheck() {
        UUID owner = insertUser("v10-owner-2@test", "v10-owner-2");
        UUID folder = insertFolder(null, "v10-fparent-2", owner);

        assertThrows(DataIntegrityViolationException.class,
            () -> jdbc.update("UPDATE folders SET deleted_by = ? WHERE id = ?", owner, folder),
            "folders_deleted_by_check: 활성 row에 deleted_by 채우는 것은 차단되어야 함");
    }

    @Test
    void files_trashRow_setDeletedBy_succeeds() {
        UUID owner = insertUser("v10-owner-3@test", "v10-owner-3");
        UUID deleter = insertUser("v10-deleter-3@test", "v10-deleter-3");
        UUID folder = insertFolder(null, "v10-fparent-3", owner);
        UUID file = insertFile(folder, "v10-trash.txt", owner);

        // soft-delete + deleter 동시 set — CHECK 통과.
        assertDoesNotThrow(
            () -> jdbc.update(
                "UPDATE files SET deleted_at = NOW(), purge_after = NOW() + INTERVAL '30 days', " +
                "deleted_by = ? WHERE id = ?", deleter, file),
            "trash row(deleted_at NOT NULL)는 deleted_by 허용");
    }

    // -------------------- FK ON DELETE SET NULL --------------------

    @Test
    void files_deletedBy_fk_isSetNull() {
        // pg_constraint.confdeltype = 'n' → ON DELETE SET NULL.
        // V5의 owner_id는 RESTRICT('r')와 다른 정책 — deleter 사용자 hard-delete가 trash row를
        // 막지 않도록 (삭제자 추적은 자료 무결성보다 보조 목적).
        String confdeltype = jdbc.queryForObject(
            "SELECT confdeltype FROM pg_constraint c " +
            "JOIN pg_class t ON c.conrelid = t.oid " +
            "WHERE t.relname = 'files' AND c.contype = 'f' " +
            "AND pg_get_constraintdef(c.oid) LIKE '%(deleted_by)%'",
            String.class
        );
        assertEquals("n", confdeltype, "files.deleted_by FK는 ON DELETE SET NULL이어야 함");
    }

    @Test
    void folders_deletedBy_fk_isSetNull() {
        String confdeltype = jdbc.queryForObject(
            "SELECT confdeltype FROM pg_constraint c " +
            "JOIN pg_class t ON c.conrelid = t.oid " +
            "WHERE t.relname = 'folders' AND c.contype = 'f' " +
            "AND pg_get_constraintdef(c.oid) LIKE '%(deleted_by)%'",
            String.class
        );
        assertEquals("n", confdeltype, "folders.deleted_by FK는 ON DELETE SET NULL이어야 함");
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

    private UUID insertFolder(UUID parentId, String name, UUID ownerId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO folders(id, parent_id, name, normalized_name, slug, owner_id) " +
            "VALUES (?, ?, ?, ?, ?, ?)",
            id, parentId, name, name, name, ownerId
        );
        return id;
    }

    private UUID insertFile(UUID folderId, String name, UUID ownerId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO files(id, folder_id, name, normalized_name, owner_id, size_bytes) " +
            "VALUES (?, ?, ?, ?, ?, ?)",
            id, folderId, name, name, ownerId, 0L
        );
        return id;
    }
}
