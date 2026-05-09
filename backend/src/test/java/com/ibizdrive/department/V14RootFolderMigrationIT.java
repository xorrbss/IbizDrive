package com.ibizdrive.department;

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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V14__departments_root_folder.sql 스키마 + 제약 검증 — team-centric pivot Plan A Task 3.
 *
 * <p>{@link com.ibizdrive.folder.V13ScopeMigrationIT}와 동일한 raw {@code JdbcTemplate} 패턴 —
 * entity 매핑이 아닌 schema-level 제약(컬럼 존재 + partial unique index)을 직접 검증한다.
 * Department.rootFolderId entity 회귀 가드는 후속 Task 9 가 담당.
 *
 * <p>핵심 회귀 가드 (spec §1.3):
 *   - {@code departments.root_folder_id} 컬럼이 folders(id) 참조로 추가
 *   - {@code idx_folders_root_per_scope} partial unique — 동일 (scope_type, scope_id) 활성 root 1개만 허용
 *   - 같은 partial index가 soft-deleted root는 제외 → workspace archive 후 재생성 시나리오 허용
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class V14RootFolderMigrationIT {

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
    void departments_rootFolderId_column_exists_referencingFolders() {
        Integer columnCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns " +
            "WHERE table_schema='public' AND table_name='departments' AND column_name='root_folder_id'",
            Integer.class
        );
        assertEquals(1, columnCount, "departments.root_folder_id 컬럼이 존재해야 함");

        // FK 무결성 검증: 실제로 folders(id) 참조 가능한지 INSERT/UPDATE 사이클로 확인.
        UUID owner = insertUser("v14-owner-1@test", "v14-owner-1");
        UUID deptId = insertDepartment("v14-dept-1");
        UUID rootId = insertRootFolder("v14-root-1", "department", deptId, owner);

        jdbc.update(
            "UPDATE departments SET root_folder_id = ? WHERE id = ?",
            rootId, deptId
        );

        UUID stored = jdbc.queryForObject(
            "SELECT root_folder_id FROM departments WHERE id = ?",
            UUID.class, deptId
        );
        assertNotNull(stored, "root_folder_id 가 영속되어야 함");
        assertEquals(rootId, stored, "departments.root_folder_id 가 folders(id) 참조로 저장되어야 함");
    }

    // -------------------- partial unique enforcement --------------------

    @Test
    void rootPerScope_blocksDuplicateActiveRootInSameScope() {
        UUID owner = insertUser("v14-owner-2@test", "v14-owner-2");
        UUID deptId = insertDepartment("v14-dept-2");

        // 첫 root 는 정상 INSERT.
        insertRootFolder("v14-root-a", "department", deptId, owner);

        // 동일 (scope_type='department', scope_id=deptId) + parent_id IS NULL + deleted_at IS NULL 인
        // 두 번째 root 는 idx_folders_root_per_scope partial unique 로 차단되어야 함.
        DataIntegrityViolationException ex = assertThrows(
            DataIntegrityViolationException.class,
            () -> insertRootFolder("v14-root-b", "department", deptId, owner),
            "idx_folders_root_per_scope: 동일 scope 활성 root 두 개는 차단되어야 함"
        );
        assertTrue(ex.getMessage().contains("idx_folders_root_per_scope"),
            "예외 메시지에 인덱스 이름이 포함되어야 함: " + ex.getMessage());
    }

    @Test
    void rootPerScope_allowsNewRootAfterPreviousSoftDeleted() {
        UUID owner = insertUser("v14-owner-3@test", "v14-owner-3");
        UUID deptId = insertDepartment("v14-dept-3");

        UUID firstRoot = insertRootFolder("v14-root-old", "department", deptId, owner);

        // soft-delete (deleted_at + purge_after 함께 set — V5 folders_deleted_purge_check 충족).
        Timestamp now = Timestamp.from(Instant.now());
        Timestamp purgeAfter = Timestamp.from(Instant.now().plusSeconds(86400));
        jdbc.update(
            "UPDATE folders SET deleted_at = ?, purge_after = ? WHERE id = ?",
            now, purgeAfter, firstRoot
        );

        // 같은 scope 의 새 root 가 허용되어야 함 — partial WHERE deleted_at IS NULL 이 정확히 적용됨을 증명.
        UUID secondRoot = insertRootFolder("v14-root-new", "department", deptId, owner);
        assertNotNull(secondRoot,
            "soft-deleted root 가 있을 때 같은 scope 의 새 root 는 partial index 에서 제외되어 허용");
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

    private UUID insertDepartment(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO departments(id, name) VALUES (?, ?)",
            id, name
        );
        return id;
    }

    private UUID insertRootFolder(String name, String scopeType, UUID scopeId, UUID ownerId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO folders(id, parent_id, name, normalized_name, slug, owner_id, scope_type, scope_id) " +
            "VALUES (?, NULL, ?, ?, ?, ?, ?, ?)",
            id, name, name, name, ownerId, scopeType, scopeId
        );
        return id;
    }
}
