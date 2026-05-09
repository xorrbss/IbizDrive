package com.ibizdrive.permission;

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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * V15__permissions_audit_team.sql 제약 확장 검증 — team-centric pivot Plan A Task 4.
 *
 * <p>{@link com.ibizdrive.team.V12MigrationIT}와 동일한 raw {@code JdbcTemplate} 패턴 — entity 매핑이 아닌
 * schema-level CHECK 제약 확장만 검증한다. permissions.subject_type / audit_log.target_type 모두 'team'
 * 값을 새로 허용해야 한다 (spec §1.4, §1.5).
 *
 * <p>핵심 회귀 가드:
 *   - {@code permissions_subject_type_check}이 'team'을 INSERT 가능하게 허용 (V5 enum + 'team')
 *   - {@code audit_log_target_type_check}이 'team'을 INSERT 가능하게 허용 (V9 enum + 'team')
 *   - 기존에 허용되던 값 ('user' subject, 'department' target)도 회귀 없이 INSERT 가능
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class V15CheckExtensionIT {

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

    // -------------------- new 'team' value acceptance --------------------

    @Test
    void permissionsAcceptsTeamSubject() {
        UUID granter = insertUser("v15-granter-1@test", "v15-granter-1");
        UUID teamSubjectId = UUID.randomUUID();   // FK 없음 (V5 permissions.subject_id 컬럼은 FK 미선언)
        UUID resourceId = UUID.randomUUID();      // resource_id 동일 — V5 permissions.resource_id 도 FK 미선언

        assertDoesNotThrow(
            () -> insertPermission("folder", resourceId, "team", teamSubjectId, "read", granter),
            "V15 이후 permissions.subject_type='team' INSERT는 CHECK 제약을 통과해야 함"
        );

        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM permissions WHERE subject_type = 'team' AND subject_id = ?",
            Integer.class, teamSubjectId
        );
        assertEquals(1, count, "team subject permission 1건이 영속되어야 함");
    }

    @Test
    void auditLogAcceptsTeamTarget() {
        UUID actor = insertUser("v15-actor-1@test", "v15-actor-1");
        UUID teamTargetId = UUID.randomUUID();

        assertDoesNotThrow(
            () -> insertAuditLog(actor, "team.created", "team", teamTargetId),
            "V15 이후 audit_log.target_type='team' INSERT는 CHECK 제약을 통과해야 함"
        );

        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_log WHERE target_type = 'team' AND target_id = ?",
            Integer.class, teamTargetId
        );
        assertEquals(1, count, "team target audit_log 1건이 영속되어야 함");
    }

    // -------------------- non-regression: existing values still accepted --------------------

    @Test
    void existingSubjectAndTargetValuesStillAccepted() {
        UUID actor = insertUser("v15-actor-2@test", "v15-actor-2");
        UUID userSubjectId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        UUID deptTargetId = UUID.randomUUID();

        // V5 baseline 'user' subject — 회귀 없이 INSERT 되어야 함.
        assertDoesNotThrow(
            () -> insertPermission("folder", resourceId, "user", userSubjectId, "read", actor),
            "기존 subject_type='user'도 V15 이후 회귀 없이 INSERT 가능해야 함"
        );

        // V9 baseline 'department' target — 회귀 없이 INSERT 되어야 함.
        assertDoesNotThrow(
            () -> insertAuditLog(actor, "department.created", "department", deptTargetId),
            "기존 target_type='department'도 V15 이후 회귀 없이 INSERT 가능해야 함"
        );
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

    private void insertPermission(
        String resourceType, UUID resourceId,
        String subjectType, UUID subjectId,
        String preset, UUID grantedBy
    ) {
        jdbc.update(
            "INSERT INTO permissions(id, resource_type, resource_id, subject_type, subject_id, preset, granted_by, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, NOW())",
            UUID.randomUUID(), resourceType, resourceId, subjectType, subjectId, preset, grantedBy
        );
    }

    private void insertAuditLog(UUID actorId, String eventType, String targetType, UUID targetId) {
        jdbc.update(
            "INSERT INTO audit_log(occurred_at, actor_id, event_type, target_type, target_id) " +
            "VALUES (NOW(), ?, ?, ?, ?)",
            actorId, eventType, targetType, targetId
        );
    }
}
