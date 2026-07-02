package com.ibizdrive.search;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V26__search_trgm_indexes.sql 스키마 검증 — pg_trgm 확장 + 검색 GIN 인덱스 존재.
 *
 * <p>인덱스 실사용(EXPLAIN)은 planner가 소규모 데이터에서 seqscan을 선택하므로 검증하지 않는다 —
 * 존재/정의(partial 조건 포함)만 회귀 가드.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class SearchTrgmIndexSchemaTest {

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

    @Test
    void pgTrgmExtension_installed() {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM pg_extension WHERE extname = 'pg_trgm'", Integer.class);
        assertEquals(1, count, "V26이 pg_trgm 확장을 설치해야 함");
    }

    @Test
    void trgmIndexes_existWithPartialPredicate() {
        for (String[] target : new String[][] {
            {"files", "idx_files_normalized_name_trgm"},
            {"folders", "idx_folders_normalized_name_trgm"}}) {
            String def = jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE tablename = ? AND indexname = ?",
                String.class, target[0], target[1]);
            assertTrue(def != null && def.contains("gin"),
                target[1] + "은 GIN 인덱스여야 함: " + def);
            assertTrue(def.contains("gin_trgm_ops"),
                target[1] + "은 gin_trgm_ops opclass여야 함: " + def);
            assertTrue(def.contains("deleted_at IS NULL"),
                target[1] + "은 partial(deleted_at IS NULL)이어야 함: " + def);
        }
    }
}
