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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V13__folders_files_scope.sql 스키마 + 제약 검증 — team-centric pivot Plan A Task 2.
 *
 * <p>{@link V10MigrationIT}와 동일한 raw {@code JdbcTemplate} 패턴 — entity 매핑이 아닌 schema-level
 * 제약(NOT NULL 컬럼 + CHECK enum)을 직접 검증한다. entity 경로 회귀 가드는 후속 Task 7/8
 * (Folder/FileItem entity scope field) 이 담당.
 *
 * <p>핵심 회귀 가드 (spec §1.2 — green-field Scenario A: NOT NULL + CHECK 즉시 적용):
 *   - {@code folders.scope_type} / {@code folders.scope_id} NOT NULL
 *   - {@code chk_folders_scope_type} CHECK — 'department' / 'team' 외 값 차단
 *   - {@code files.scope_type} / {@code files.scope_id} 컬럼 존재
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class V13ScopeMigrationIT {

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
    void folders_scopeType_column_exists_notNull() {
        String dataType = jdbc.queryForObject(
            "SELECT data_type FROM information_schema.columns " +
            "WHERE table_schema='public' AND table_name='folders' AND column_name='scope_type'",
            String.class
        );
        assertEquals("character varying", dataType,
            "folders.scope_type 컬럼이 varchar 타입으로 존재해야 함");

        String isNullable = jdbc.queryForObject(
            "SELECT is_nullable FROM information_schema.columns " +
            "WHERE table_schema='public' AND table_name='folders' AND column_name='scope_type'",
            String.class
        );
        assertEquals("NO", isNullable,
            "folders.scope_type은 NOT NULL이어야 함 (Scenario A green-field)");
    }

    @Test
    void files_scope_columns_exist() {
        Integer scopeTypeCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns " +
            "WHERE table_schema='public' AND table_name='files' AND column_name='scope_type'",
            Integer.class
        );
        assertEquals(1, scopeTypeCount, "files.scope_type 컬럼이 존재해야 함");

        Integer scopeIdCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns " +
            "WHERE table_schema='public' AND table_name='files' AND column_name='scope_id'",
            Integer.class
        );
        assertEquals(1, scopeIdCount, "files.scope_id 컬럼이 존재해야 함");
    }

    // -------------------- CHECK enforcement --------------------

    @Test
    void folders_scopeType_check_rejectsInvalidValue() {
        UUID owner = insertUser("v13-owner-1@test", "v13-owner-1");

        // 'user' 같은 enum 외 값은 chk_folders_scope_type 으로 차단되어야 함.
        DataIntegrityViolationException ex = assertThrows(
            DataIntegrityViolationException.class,
            () -> insertFolderWithScope(owner, "v13-bad-scope", "user", UUID.randomUUID()),
            "chk_folders_scope_type: 'department'/'team' 외 값은 차단되어야 함"
        );
        assertTrue(ex.getMessage().contains("chk_folders_scope_type"),
            "예외 메시지에 CHECK 이름이 포함되어야 함: " + ex.getMessage());
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

    private UUID insertFolderWithScope(UUID ownerId, String name, String scopeType, UUID scopeId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO folders(id, name, normalized_name, slug, owner_id, scope_type, scope_id) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)",
            id, name, name, name, ownerId, scopeType, scopeId
        );
        return id;
    }
}
