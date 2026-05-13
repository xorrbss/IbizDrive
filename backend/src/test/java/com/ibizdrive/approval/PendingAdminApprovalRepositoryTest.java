package com.ibizdrive.approval;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V20 schema + CHECK 제약 + entity 매핑 + finder query 검증 — ADR #47 / docs/02 §2.11.
 *
 * <p>다른 repository slice ({@link com.ibizdrive.admin.CronPolicyRepositoryTest},
 * {@link com.ibizdrive.department.DepartmentRepositoryTest}) 동일 패턴. Phase 1 트랙은 service 부재
 * 라 본 테스트가 V20 schema의 진실의 출처.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class PendingAdminApprovalRepositoryTest {

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

    @Autowired private PendingAdminApprovalRepository repository;
    @Autowired private JdbcTemplate jdbc;

    private UUID requester;

    @BeforeEach
    void setUp() {
        requester = insertUser("requester@test", "Requester");
    }

    // ──────────────────────────────────────────────────────────────────
    // CHECK 제약 — DB가 진실의 출처 (CLAUDE.md §3 원칙 6)
    // ──────────────────────────────────────────────────────────────────

    @Test
    void check_statusEnum_rejectsUnknownValue() {
        assertThatThrownBy(() -> jdbc.update(
            "INSERT INTO pending_admin_approvals (id, action_type, payload_json, requested_by, status, expires_at) "
          + "VALUES (?, ?, ?::jsonb, ?, ?, NOW() + INTERVAL '7 days')",
            UUID.randomUUID(), "role_change", "{}", requester, "BOGUS"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void check_decidedAt_requestedMustBeNull() {
        // REQUESTED status면 decided_at은 NULL이어야 한다 — 강제 위반 시 CHECK fail.
        assertThatThrownBy(() -> jdbc.update(
            "INSERT INTO pending_admin_approvals (id, action_type, payload_json, requested_by, status, decided_at, expires_at) "
          + "VALUES (?, ?, ?::jsonb, ?, 'REQUESTED', NOW(), NOW() + INTERVAL '7 days')",
            UUID.randomUUID(), "role_change", "{}", requester))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void check_decidedAt_terminalMustBeNotNull() {
        // APPROVED status면 decided_at 필수. NULL 강제 시 CHECK fail.
        UUID secondary = insertUser("secondary@test", "Secondary");
        assertThatThrownBy(() -> jdbc.update(
            "INSERT INTO pending_admin_approvals (id, action_type, payload_json, requested_by, status, secondary_approver_id, decided_at, expires_at) "
          + "VALUES (?, ?, ?::jsonb, ?, 'APPROVED', ?, NULL, NOW() + INTERVAL '7 days')",
            UUID.randomUUID(), "role_change", "{}", requester, secondary))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void check_secondary_cancelledMustBeNull() {
        // CANCELLED는 secondary_approver_id가 NULL이어야 한다 (requested_by 본인 취소).
        UUID secondary = insertUser("secondary@test", "Secondary");
        assertThatThrownBy(() -> jdbc.update(
            "INSERT INTO pending_admin_approvals (id, action_type, payload_json, requested_by, status, secondary_approver_id, decided_at, expires_at) "
          + "VALUES (?, ?, ?::jsonb, ?, 'CANCELLED', ?, NOW(), NOW() + INTERVAL '7 days')",
            UUID.randomUUID(), "role_change", "{}", requester, secondary))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void check_secondary_approvedMustBeNotNull() {
        // APPROVED는 secondary_approver_id 필수.
        assertThatThrownBy(() -> jdbc.update(
            "INSERT INTO pending_admin_approvals (id, action_type, payload_json, requested_by, status, decided_at, expires_at) "
          + "VALUES (?, ?, ?::jsonb, ?, 'APPROVED', NOW(), NOW() + INTERVAL '7 days')",
            UUID.randomUUID(), "role_change", "{}", requester))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ──────────────────────────────────────────────────────────────────
    // entity 매핑 + JSONB
    // ──────────────────────────────────────────────────────────────────

    @Test
    void entity_savesAndReadsJsonbPayload() {
        PendingAdminApproval row = newRequested(
            "role_change",
            "{\"userId\":\"00000000-0000-0000-0000-000000000001\",\"fromRole\":\"MEMBER\",\"toRole\":\"ADMIN\",\"reason\":\"승급\"}");
        PendingAdminApproval saved = repository.saveAndFlush(row);

        PendingAdminApproval reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getActionType()).isEqualTo("role_change");
        assertThat(reloaded.getStatus()).isEqualTo(PendingApprovalStatus.REQUESTED);
        assertThat(reloaded.getPayloadJson()).contains("\"reason\":\"승급\"");
        assertThat(reloaded.getDecidedAt()).isNull();
        assertThat(reloaded.getSecondaryApproverId()).isNull();
    }

    // ──────────────────────────────────────────────────────────────────
    // finders — partial index 정합
    // ──────────────────────────────────────────────────────────────────

    @Test
    void findPendingByActionType_filtersByStatusRequested() {
        repository.saveAndFlush(newRequested("role_change", "{}"));
        repository.saveAndFlush(newRequested("trash_purge", "{}"));
        // terminal 상태 한 건 — pending 목록에서 제외되어야 한다.
        UUID secondary = insertUser("approver@test", "Approver");
        PendingAdminApproval approved = newRequested("role_change", "{}");
        approved.setStatus(PendingApprovalStatus.APPROVED);
        approved.setSecondaryApproverId(secondary);
        approved.setDecidedAt(OffsetDateTime.now(ZoneOffset.UTC));
        repository.saveAndFlush(approved);

        Page<PendingAdminApproval> rolePending =
            repository.findPendingByActionType("role_change", PageRequest.of(0, 10));
        assertThat(rolePending.getTotalElements()).isEqualTo(1);
        assertThat(rolePending.getContent().get(0).getActionType()).isEqualTo("role_change");

        Page<PendingAdminApproval> allPending =
            repository.findPendingByActionType(null, PageRequest.of(0, 10));
        assertThat(allPending.getTotalElements()).isEqualTo(2);
    }

    @Test
    void findPendingByRequester_returnsOnlyOwnRequested() {
        UUID otherRequester = insertUser("other@test", "Other");

        repository.saveAndFlush(newRequested("role_change", "{}"));                         // by requester
        repository.saveAndFlush(newRequestedBy(otherRequester, "role_change", "{}"));        // by other

        List<PendingAdminApproval> own = repository.findPendingByRequester(requester);
        assertThat(own).hasSize(1);
        assertThat(own.get(0).getRequestedBy()).isEqualTo(requester);
    }

    @Test
    void findExpiredPending_returnsOnlyExpiredRequested() {
        // expires_at = past → 후보. expires_at = future → 제외.
        PendingAdminApproval expired = newRequested("role_change", "{}");
        expired.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
        repository.saveAndFlush(expired);

        PendingAdminApproval fresh = newRequested("trash_purge", "{}");
        fresh.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));
        repository.saveAndFlush(fresh);

        List<PendingAdminApproval> candidates =
            repository.findExpiredPending(OffsetDateTime.now(ZoneOffset.UTC), PageRequest.of(0, 10));
        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getId()).isEqualTo(expired.getId());
    }

    @Test
    void lockById_returnsRowForUpdate() {
        PendingAdminApproval saved = repository.saveAndFlush(newRequested("role_change", "{}"));
        // 단순 lock fetch — pessimistic lock 동작 자체는 multi-tx test가 별도. 본 테스트는 query 정합만.
        PendingAdminApproval locked = repository.lockById(saved.getId()).orElseThrow();
        assertThat(locked.getId()).isEqualTo(saved.getId());
    }

    // ──────────────────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────────────────

    private PendingAdminApproval newRequested(String actionType, String payloadJson) {
        return newRequestedBy(requester, actionType, payloadJson);
    }

    private PendingAdminApproval newRequestedBy(UUID requestedBy, String actionType, String payloadJson) {
        PendingAdminApproval row = new PendingAdminApproval();
        row.setId(UUID.randomUUID());
        row.setActionType(actionType);
        row.setPayloadJson(payloadJson);
        row.setRequestedBy(requestedBy);
        row.setRequestedAt(OffsetDateTime.now(ZoneOffset.UTC));
        row.setStatus(PendingApprovalStatus.REQUESTED);
        row.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusDays(7));
        return row;
    }

    private UUID insertUser(String email, String displayName) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)",
            id, email, displayName);
        return id;
    }
}
