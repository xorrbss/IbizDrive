package com.ibizdrive.folder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.file.FileRepository;
import com.ibizdrive.team.TeamArchiveGuard;
import com.ibizdrive.team.TeamRepository;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Plan E T4 — {@link FolderMutationService#restore} cross-scope mismatch.
 *
 * <p>spec docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §3.4 / §5.2 / §5.3 —
 * 휴지통 폴더의 original parent가 다른 workspace로 이동된 경우 (cross-workspace move 또는 데이터
 * 재배치 후 발생 가능), 복원하면 자식이 원래 workspace를 떠나 §1.2 invariant 위반. 따라서
 * {@link FolderRestoreConflictException.Reason#SCOPE_MISMATCH}로 차단 (envelope
 * {@code RESTORE_CONFLICT} HTTP 409 + body {@code reason='scope_mismatch'}).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import(FolderRestoreCrossScopeTest.TestConfig.class)
class FolderRestoreCrossScopeTest {

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

    @TestConfiguration
    static class TestConfig {
        @Bean ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean AuditService auditService() {
            return mock(AuditService.class);
        }

        @Bean TeamArchiveGuard teamArchiveGuard(TeamRepository teamRepo) {
            return new TeamArchiveGuard(teamRepo);
        }

        @Bean FolderMutationService folderMutationService(FolderRepository repo,
                                                          FileRepository fileRepo,
                                                          AuditService audit,
                                                          ObjectMapper mapper,
                                                          TeamArchiveGuard guard) {
            return new FolderMutationService(repo, fileRepo, audit, mapper,
                new com.ibizdrive.trash.TrashRetentionProperties(30), guard);
        }
    }

    @Autowired private FolderMutationService service;
    @Autowired private FolderRepository folderRepository;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void restore_originalParentMovedToDifferentScope_throwsScopeMismatch() {
        // 시나리오:
        //   1) rootA (scope_id=A)에 child를 만든다 → child.scope=A.
        //   2) child를 휴지통으로 — originalParentId=rootA, target.scope=A 보존.
        //   3) rootA의 scope_id를 raw로 B로 재배치 (cross-workspace 데이터 이동 시뮬레이션 —
        //      service.move 는 cross-scope를 막으므로 raw JDBC).
        //   4) restore(child) 시도 → originalParent.scope(B) != target.scope(A) → SCOPE_MISMATCH.
        UUID owner = insertUser("rstcs1@test", "rstcs1");
        UUID scopeA = UUID.randomUUID();
        UUID scopeB = UUID.randomUUID();
        UUID rootId = insertFakeRoot(owner, "department", scopeA);

        Folder child = service.create(rootId, "ChildCs1", owner, "standard", owner);
        assertThat(child.getScopeId()).isEqualTo(scopeA);

        service.delete(child.getId(), owner);
        // child는 이제 deleted_at != NULL, original_parent_id=rootId, scope=A 그대로.

        // rootA를 scope B로 강제 재배치 — partition 또는 admin migration 시뮬레이션.
        jdbc.update("UPDATE folders SET scope_id = ? WHERE id = ?", scopeB, rootId);

        assertThatThrownBy(() -> service.restore(child.getId(), owner))
            .isInstanceOf(FolderRestoreConflictException.class)
            .satisfies(ex -> {
                FolderRestoreConflictException conflict = (FolderRestoreConflictException) ex;
                assertThat(conflict.getReason())
                    .isEqualTo(FolderRestoreConflictException.Reason.SCOPE_MISMATCH);
                assertThat(conflict.getResourceId()).isEqualTo(child.getId());
                assertThat(conflict.getDetails())
                    .containsEntry("expectedScopeType", "department")
                    .containsEntry("expectedScopeId", scopeA.toString())
                    .containsEntry("actualScopeType", "department")
                    .containsEntry("actualScopeId", scopeB.toString());
            });
    }

    @Test
    void restore_originalParentDifferentScopeType_throwsScopeMismatch() {
        // 시나리오: scope_type까지 바뀐 경우 (department → team migration). 같은 SCOPE_MISMATCH.
        UUID owner = insertUser("rstcs2@test", "rstcs2");
        UUID deptId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID rootId = insertFakeRoot(owner, "department", deptId);

        Folder child = service.create(rootId, "ChildCs2", owner, "standard", owner);
        service.delete(child.getId(), owner);

        // root의 scope_type/scope_id를 team으로 통째 변경.
        jdbc.update("UPDATE folders SET scope_type = 'team', scope_id = ? WHERE id = ?", teamId, rootId);

        assertThatThrownBy(() -> service.restore(child.getId(), owner))
            .isInstanceOf(FolderRestoreConflictException.class)
            .satisfies(ex -> {
                FolderRestoreConflictException conflict = (FolderRestoreConflictException) ex;
                assertThat(conflict.getReason())
                    .isEqualTo(FolderRestoreConflictException.Reason.SCOPE_MISMATCH);
                assertThat(conflict.getDetails())
                    .containsEntry("expectedScopeType", "department")
                    .containsEntry("actualScopeType", "team");
            });
    }

    @Test
    void restore_originalParentSameScope_succeeds() {
        // 회귀 가드: 정상 케이스 — original parent가 동일 scope에 active로 남아있을 때 restore 성공.
        UUID owner = insertUser("rstcs3@test", "rstcs3");
        UUID scopeA = UUID.randomUUID();
        UUID rootId = insertFakeRoot(owner, "department", scopeA);

        Folder child = service.create(rootId, "ChildCs3", owner, "standard", owner);
        service.delete(child.getId(), owner);

        Folder restored = service.restore(child.getId(), owner);

        assertThat(restored.getDeletedAt()).isNull();
        assertThat(restored.getScopeId()).isEqualTo(scopeA);
    }

    // ──────────────────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────────────────

    private UUID insertUser(String email, String displayName) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)", id, email, displayName);
        return id;
    }

    private UUID insertFakeRoot(UUID ownerId, String scopeType, UUID scopeId) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update(
            "INSERT INTO folders(id, parent_id, name, normalized_name, slug, owner_id, audit_level, " +
            "scope_type, scope_id, created_at, updated_at) " +
            "VALUES (?, NULL, ?, ?, ?, ?, 'standard', ?, ?, ?, ?)",
            id, "root-" + id, "root-" + id, "root-" + id, ownerId, scopeType, scopeId, now, now
        );
        return id;
    }
}
