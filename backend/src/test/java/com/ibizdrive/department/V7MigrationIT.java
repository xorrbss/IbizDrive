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

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V7__departments_users_dept.sql 스키마 + 제약 검증 (A16, ADR #36, docs/02 §2.x).
 *
 * <p>핵심 회귀 가드:
 *   - departments 테이블 도입 + ltree extension
 *   - departments soft-delete 컬럼 + partial name index
 *   - users.department_id FK + partial index (is_active=TRUE AND department_id IS NOT NULL)
 *   - V4 audit_log REVOKE 정책 무영향 (A2 회귀 가드)
 *
 * <p>{@link com.ibizdrive.share.V6MigrationIT} 패턴 일관 — Testcontainers + DataJpaTest + Flyway.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class V7MigrationIT {

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
    void departments_table_exists_afterV7() {
        Boolean exists = jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM information_schema.tables " +
            "WHERE table_schema='public' AND table_name='departments')",
            Boolean.class
        );
        assertTrue(Boolean.TRUE.equals(exists), "departments 테이블 (V7, ADR #36)");
    }

    @Test
    void users_departmentId_column_exists_afterV7() {
        Boolean exists = jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM information_schema.columns " +
            "WHERE table_schema='public' AND table_name='users' AND column_name='department_id')",
            Boolean.class
        );
        assertTrue(Boolean.TRUE.equals(exists), "users.department_id 컬럼 (V7)");
    }

    @Test
    void departments_indexes_exist() {
        Set<String> indexes = jdbc.queryForList(
            "SELECT indexname FROM pg_indexes WHERE schemaname='public' AND tablename='departments'",
            String.class
        ).stream().collect(Collectors.toSet());
        assertTrue(indexes.contains("idx_departments_path"),
            "idx_departments_path GIST (ltree v1.x 트리 쿼리 대비)");
        assertTrue(indexes.contains("idx_departments_name"),
            "idx_departments_name partial (deleted_at IS NULL)");
    }

    @Test
    void users_department_index_exists() {
        Set<String> indexes = jdbc.queryForList(
            "SELECT indexname FROM pg_indexes WHERE schemaname='public' AND tablename='users'",
            String.class
        ).stream().collect(Collectors.toSet());
        assertTrue(indexes.contains("idx_users_department"),
            "idx_users_department partial index (A16.2 권한 평가 SQL fast lookup)");
    }

    @Test
    void ltree_extension_installed() {
        Boolean exists = jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname='ltree')",
            Boolean.class
        );
        assertTrue(Boolean.TRUE.equals(exists), "ltree extension installed");
    }

    // -------------------- insert + FK --------------------

    @Test
    void departments_insert_succeeds() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO departments(id, name) VALUES (?, ?)", id, "Engineering");

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM departments WHERE id = ?", id);
        assertEquals("Engineering", row.get("name"));
        assertNull(row.get("deleted_at"));
        assertNotNull(row.get("created_at"));
    }

    @Test
    void users_departmentId_fk_enforced() {
        UUID userId = UUID.randomUUID();
        UUID nonexistentDept = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)",
            userId, "fk-test@example.com", "FK Test");

        assertThrows(DataIntegrityViolationException.class,
            () -> jdbc.update("UPDATE users SET department_id = ? WHERE id = ?",
                nonexistentDept, userId),
            "users.department_id가 존재하지 않는 dept를 참조하면 FK 위반");
    }

    @Test
    void users_departmentId_assign_succeeds() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        jdbc.update("INSERT INTO departments(id, name) VALUES (?, ?)", deptId, "Sales");
        jdbc.update("INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)",
            userId, "assign-test@example.com", "Assign Test");

        jdbc.update("UPDATE users SET department_id = ? WHERE id = ?", deptId, userId);

        UUID found = jdbc.queryForObject(
            "SELECT department_id FROM users WHERE id = ?", UUID.class, userId);
        assertEquals(deptId, found);
    }

    // -------------------- A2 회귀 가드 (audit_log REVOKE 무영향) --------------------

    @Test
    void v7_doesNotAffectAuditLog_revokePolicy() {
        Boolean hasUpdate = jdbc.queryForObject(
            "SELECT has_table_privilege('app_user', 'audit_log', 'UPDATE')",
            Boolean.class
        );
        assertEquals(Boolean.FALSE, hasUpdate,
            "V7가 audit_log REVOKE 정책을 깨뜨리지 않아야 함 (A2 회귀 가드)");

        Boolean hasDelete = jdbc.queryForObject(
            "SELECT has_table_privilege('app_user', 'audit_log', 'DELETE')",
            Boolean.class
        );
        assertEquals(Boolean.FALSE, hasDelete,
            "V7가 audit_log DELETE 권한 부여하면 안 됨 (A2 회귀 가드)");
    }
}
