package com.ibizdrive.audit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.InetAddress;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AuditService#record(AuditEvent)} 통합 테스트 (ADR #24).
 *
 * <p>핵심 검증:
 * <ol>
 *   <li>record()가 audit_log row를 INSERT, 모든 컬럼 정확히 채움 (event_type, actor_id, actor_ip,
 *       user_agent, target_type, target_id, before_state, after_state, metadata)
 *   <li>{@code @Transactional(propagation=REQUIRES_NEW)} 적용 — 호출자 트랜잭션이 rollback되어도
 *       audit row는 보존되어야 함 (감사 무결성)
 *   <li>actor_id가 null인 system 이벤트도 정상 INSERT (DB 컬럼 nullable)
 * </ol>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import(AuditServiceTest.TestConfig.class)
class AuditServiceTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        AuditService auditService(JdbcTemplate jdbc) {
            return new AuditService(jdbc);
        }
    }

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
    private AuditService auditService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager txManager;

    @Test
    void record_insertsRow_withAllFields() throws Exception {
        UUID actorId = seedUser("a1@example.com");
        UUID targetId = UUID.randomUUID();

        AuditEvent event = new AuditEvent(
            AuditEventType.FILE_UPLOADED,
            actorId,
            InetAddress.getByName("203.0.113.42"),
            "Mozilla/5.0 (test)",
            AuditTargetType.FILE,
            targetId,
            null,
            "{\"size\":1024}",
            "{\"folder\":\"sales\"}"
        );

        auditService.record(event);

        Map<String, Object> row = jdbc.queryForMap(
            "SELECT * FROM audit_log WHERE event_type='file.uploaded' ORDER BY id DESC LIMIT 1"
        );
        assertEquals("file.uploaded", row.get("event_type"));
        assertEquals(actorId, row.get("actor_id"));
        // PostgreSQL inet 타입은 host 주소(/32)일 때 mask 표기를 생략한다 — "203.0.113.42".
        // /N 표기는 명시적 CIDR 입력 시에만 보존된다.
        assertEquals("203.0.113.42", row.get("actor_ip").toString());
        assertEquals("Mozilla/5.0 (test)", row.get("user_agent"));
        assertEquals("file", row.get("target_type"));
        assertEquals(targetId, row.get("target_id"));
        assertNull(row.get("before_state"));
        assertNotNull(row.get("after_state"));
        assertNotNull(row.get("metadata"));
        assertNotNull(row.get("occurred_at"));
    }

    @Test
    void record_systemEvent_acceptsNullActor() {
        AuditEvent event = new AuditEvent(
            AuditEventType.SYSTEM_BACKUP_COMPLETED,
            null,         // actor_id null — 시스템 이벤트
            null,
            null,
            AuditTargetType.SYSTEM,
            null,
            null,
            null,
            "{\"backup_id\":\"b-001\"}"
        );

        assertDoesNotThrow(() -> auditService.record(event));

        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_log WHERE event_type='system.backup.completed'",
            Long.class
        );
        assertEquals(1L, count);
    }

    @Test
    void record_survivesCallerRollback_viaRequiresNew() {
        UUID actorId = seedUser("a2@example.com");
        long before = countAuditLogs();

        // 호출자 트랜잭션이 명시적으로 rollback되어도 record()의 REQUIRES_NEW가 audit row를 살려야 함.
        TransactionTemplate tt = new TransactionTemplate(txManager);
        try {
            tt.executeWithoutResult(status -> {
                auditService.record(new AuditEvent(
                    AuditEventType.FILE_DELETED,
                    actorId, null, null,
                    AuditTargetType.FILE, UUID.randomUUID(),
                    null, null, null
                ));
                status.setRollbackOnly();
                throw new RuntimeException("force rollback");
            });
            fail("expected runtime exception");
        } catch (RuntimeException expected) {
            // 호출자 트랜잭션은 rollback됨 — 정상.
        }

        long after = countAuditLogs();
        assertEquals(before + 1, after,
            "REQUIRES_NEW 트랜잭션이라 호출자 rollback에도 audit row가 보존되어야 함");
    }

    private long countAuditLogs() {
        Long c = jdbc.queryForObject("SELECT COUNT(*) FROM audit_log", Long.class);
        return c == null ? 0 : c;
    }

    /**
     * users row를 별도 트랜잭션(REQUIRES_NEW)에서 즉시 commit한다.
     *
     * <p>이유: {@code @DataJpaTest}는 각 테스트를 outer tx로 감싸 rollback한다. 그러나
     * {@link AuditService#record(AuditEvent)}는 {@code @Transactional(REQUIRES_NEW)}로
     * 별도 connection을 잡는다. seedUser를 outer tx 안에서 INSERT하면 commit 전이라
     * REQUIRES_NEW 쪽 connection은 그 row를 볼 수 없고 {@code actor_id → users.id} FK가
     * 위반된다 (PSQLException, SQLState 23503).
     *
     * <p>해결: seedUser도 REQUIRES_NEW로 즉시 commit. 컨테이너는 클래스 단위 lifecycle이라
     * 잔여 row가 누적되지만 각 테스트가 다른 email을 쓰고, audit_log 검증은 event_type 또는
     * before/after delta 기반이라 격리에 문제없음.
     */
    private UUID seedUser(String email) {
        UUID id = UUID.randomUUID();
        TransactionTemplate tt = new TransactionTemplate(txManager);
        tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tt.executeWithoutResult(status -> jdbc.update(
            "INSERT INTO users(id, email, display_name, password_hash) VALUES (?, ?, ?, ?)",
            id, email, "Test " + email, "{bcrypt}$dummy$"
        ));
        return id;
    }
}
