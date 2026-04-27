package com.ibizdrive.audit;

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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V3__audit_log.sql 스키마 검증 (ADR #24, docs/02 §2.8).
 *
 * <p>Flyway가 V1+V2+V3+V4를 적용한 직후의 information_schema/pg_indexes 상태에서
 * audit_log 테이블이 docs/02 §2.8의 명세를 충족하는지 RED → GREEN 검증.
 *
 * <p>본 테스트는 superuser connection으로 실행 (default Testcontainers 사용자).
 * append-only(REVOKE) 검증은 별도 {@link AuditLogAppendOnlyTest}가 담당.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class AuditLogSchemaTest {

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
    void auditLogTable_exists() {
        Boolean exists = jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM information_schema.tables " +
            "WHERE table_schema='public' AND table_name='audit_log')",
            Boolean.class
        );
        assertTrue(Boolean.TRUE.equals(exists), "audit_log 테이블이 V3 후 존재해야 함");
    }

    @Test
    void auditLogTable_hasAllRequiredColumns() {
        Set<String> columns = jdbc.queryForList(
            "SELECT column_name FROM information_schema.columns " +
            "WHERE table_schema='public' AND table_name='audit_log'",
            String.class
        ).stream().collect(Collectors.toSet());

        // docs/02 §2.8 명세 컬럼 (frontend types/audit.ts와 1:1 대응)
        assertEquals(
            Set.of("id", "occurred_at", "actor_id", "actor_ip", "user_agent",
                   "event_type", "target_type", "target_id",
                   "before_state", "after_state", "metadata"),
            columns,
            "audit_log 컬럼이 docs/02 §2.8 명세와 일치해야 함"
        );
    }

    @Test
    void targetType_check_acceptsAllSevenValues() {
        // CHECK 제약이 7개 값(audit 포함)을 모두 허용해야 함
        for (String t : List.of("file", "folder", "user", "permission", "share", "system", "audit")) {
            assertDoesNotThrow(
                () -> jdbc.update(
                    "INSERT INTO audit_log(event_type, target_type) VALUES (?, ?)",
                    "test.event", t
                ),
                "target_type=" + t + "는 CHECK 통과해야 함"
            );
        }
    }

    @Test
    void targetType_check_rejectsUnknownValue() {
        Exception ex = assertThrows(
            Exception.class,
            () -> jdbc.update(
                "INSERT INTO audit_log(event_type, target_type) VALUES ('test.event', 'invalid_type')"
            )
        );
        assertTrue(
            ex.getMessage().toLowerCase().contains("check") ||
                ex.getMessage().contains("audit_log_target_type_check"),
            "알 수 없는 target_type은 CHECK 위반으로 거부되어야 함, 실제 메시지: " + ex.getMessage()
        );
    }

    @Test
    void requiredIndexes_exist() {
        Set<String> indexes = jdbc.queryForList(
            "SELECT indexname FROM pg_indexes " +
            "WHERE schemaname='public' AND tablename='audit_log'",
            String.class
        ).stream().collect(Collectors.toSet());

        // PK 인덱스 + 4개 조회 인덱스 = 최소 5개
        assertTrue(indexes.contains("idx_audit_occurred_at"), "occurred_at DESC 인덱스 필수");
        assertTrue(indexes.contains("idx_audit_actor"), "actor_id 복합 인덱스 필수");
        assertTrue(indexes.contains("idx_audit_target"), "target 복합 인덱스 필수");
        assertTrue(indexes.contains("idx_audit_event"), "event_type 복합 인덱스 필수");
    }

    @Test
    void occurredAt_defaultsToNow() {
        jdbc.update("INSERT INTO audit_log(event_type, target_type) VALUES ('test.event', 'system')");
        Map<String, Object> row = jdbc.queryForMap(
            "SELECT occurred_at FROM audit_log WHERE event_type='test.event' LIMIT 1"
        );
        assertNotNull(row.get("occurred_at"), "occurred_at은 DEFAULT NOW()로 자동 채워져야 함");
    }
}
