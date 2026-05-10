package com.ibizdrive.team;

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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V12__teams.sql 스키마 + 제약 검증 — team-centric pivot Plan A Task 1.
 *
 * <p>{@link com.ibizdrive.folder.V10MigrationIT}와 동일한 raw {@code JdbcTemplate} 패턴 — entity 매핑이
 * 아닌 schema-level 제약(컬럼 개수 + composite PK + partial unique index)을 직접 검증한다. entity 경로
 * 회귀 가드는 후속 Task 5/6 (Team/TeamMembership entity test)이 담당.
 *
 * <p>핵심 회귀 가드:
 *   - {@code teams} 컬럼 13개 (V12 11개 + V16 color/lead_id 2개)
 *   - {@code team_memberships} composite PK (team_id, user_id) — 한 팀당 한 user 1행
 *   - {@code idx_teams_name_active} partial unique — active row(archived_at IS NULL) 동일 이름 차단,
 *     archive 후 재생성은 허용 (별도 검증)
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class V12MigrationIT {

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
    void teams_table_has_thirteen_columns() {
        Integer cols = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns " +
            "WHERE table_schema='public' AND table_name='teams'",
            Integer.class);
        // V12 11개 + V16 color, lead_id 2개 = 13.
        // id, name, normalized_name, description, visibility, root_folder_id,
        // created_by, archived_at, archived_by, created_at, updated_at,
        // color, lead_id
        assertEquals(13, cols, "teams 테이블은 13개 컬럼을 가져야 함 (V12 11 + V16 2)");
    }

    @Test
    void team_memberships_has_composite_primary_key() {
        // PK constraint이 (team_id, user_id) 두 컬럼을 가리켜야 함.
        Integer pkCols = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.key_column_usage " +
            "WHERE table_schema='public' AND table_name='team_memberships' " +
            "AND constraint_name = 'team_memberships_pkey'",
            Integer.class);
        assertEquals(2, pkCols, "team_memberships PK는 (team_id, user_id) 2개 컬럼이어야 함");
    }

    // -------------------- partial unique index --------------------

    @Test
    void teams_active_name_unique_index_blocks_duplicates() {
        UUID owner = insertUser("v12-mig-1@test", "v12-mig-1");
        insertActiveTeam("Alpha", "alpha", owner);

        // PostgreSQL이 인덱스 이름을 메시지에 포함 — Spring은 DuplicateKeyException(DataIntegrityViolationException
        // 하위)으로 매핑. 상위 타입으로 받아 두 케이스 모두 커버.
        DataIntegrityViolationException ex = assertThrows(
            DataIntegrityViolationException.class,
            () -> insertActiveTeam("Alpha", "alpha", owner),
            "동일 active normalized_name은 idx_teams_name_active로 차단되어야 함");
        assertTrue(ex.getMessage().contains("idx_teams_name_active"),
            "예외 메시지에 인덱스 이름이 포함되어야 함: " + ex.getMessage());
    }

    @Test
    void teams_archived_name_can_be_reused() {
        UUID owner = insertUser("v12-mig-2@test", "v12-mig-2");
        UUID first = insertActiveTeam("Beta", "beta", owner);
        // archive — index가 partial(WHERE archived_at IS NULL)이므로 active set에서 빠짐.
        jdbc.update("UPDATE teams SET archived_at = NOW(), archived_by = ? WHERE id = ?", owner, first);

        // 동일 normalized_name 재사용 — block되지 않아야 함.
        UUID second = insertActiveTeam("Beta", "beta", owner);
        assertNotEquals(first, second, "archive 후 동일 normalized_name 재사용은 새 row를 생성해야 함");
    }

    // ====================== helpers ======================

    private UUID insertUser(String email, String displayName) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)",
            id, email, displayName);
        return id;
    }

    private UUID insertActiveTeam(String name, String normalizedName, UUID createdBy) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO teams(id, name, normalized_name, visibility, created_by, lead_id, created_at, updated_at) " +
            "VALUES (?, ?, ?, 'private', ?, ?, NOW(), NOW())",
            id, name, normalizedName, createdBy, createdBy);
        return id;
    }
}
