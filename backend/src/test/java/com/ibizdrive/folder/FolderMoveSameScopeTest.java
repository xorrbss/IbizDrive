package com.ibizdrive.folder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.file.FileRepository;
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
 * Plan A Task 25 — {@link FolderMutationService#move} same-scope guard.
 *
 * <p>spec docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §1.2 (scope invariant),
 * §5.6 (cross-workspace explicit move action — Plan D scope).
 *
 * <p>Plan A는 명시적 cross-workspace move action ({@code allowCrossScope: true})를 도입하지 않으며,
 * service.move는 same-scope만 무조건 허용. 다른 scope 부모로의 이동은
 * {@link CrossScopeMoveException} (HTTP 409 + {@code ERR_CROSS_SCOPE_MOVE}).
 *
 * <p>"Fake workspace root"는 raw JDBC INSERT — TeamService/DepartmentService 도입 전이라
 * service의 root-via-API 가드(Task 24)를 우회하기 위함. 정상 운영에서는 workspace lifecycle
 * (Task 16/20)이 root를 만든다.
 *
 * <p>{@link FolderCreateScopeInheritanceTest}와 동일한 Testcontainers + DataJpaTest 슬라이스 —
 * V13 NOT NULL + CHECK 제약을 실제 Postgres에서 검증.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import(FolderMoveSameScopeTest.TestConfig.class)
class FolderMoveSameScopeTest {

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

        @Bean FolderMutationService folderMutationService(FolderRepository repo,
                                                          FileRepository fileRepo,
                                                          AuditService audit,
                                                          ObjectMapper mapper) {
            return new FolderMutationService(repo, fileRepo, audit, mapper,
                new com.ibizdrive.trash.TrashRetentionProperties(30));
        }
    }

    @Autowired private FolderMutationService service;
    @Autowired private FolderRepository folderRepository;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void crossScopeMoveRejected() {
        // 두 개의 fake workspace root — 같은 type(department), 다른 scope_id로 cross-scope 시뮬레이션.
        UUID owner = insertUser("mvss1@test", "mvss1");
        UUID scopeA = UUID.randomUUID();
        UUID scopeB = UUID.randomUUID();
        UUID rootA = insertFakeRoot(owner, "department", scopeA);
        UUID rootB = insertFakeRoot(owner, "department", scopeB);

        // Task 24 invariant — child는 rootA의 scope를 그대로 상속.
        Folder childInA = service.create(rootA, "subA", owner, "standard", owner);
        assertThat(childInA.getScopeId()).isEqualTo(scopeA);

        // rootB는 scopeB이므로 childInA를 rootB 아래로 이동 시 cross-scope.
        assertThatThrownBy(() -> service.move(childInA.getId(), rootB, owner))
            .isInstanceOf(CrossScopeMoveException.class)
            .hasMessageContaining("ERR_CROSS_SCOPE_MOVE");
    }

    @Test
    void sameScopeMoveAllowed() {
        // 같은 scope 내에서의 이동은 허용 — guard에 영향받지 않는 happy path.
        UUID owner = insertUser("mvss2@test", "mvss2");
        UUID scopeId = UUID.randomUUID();
        UUID root = insertFakeRoot(owner, "department", scopeId);

        Folder src = service.create(root, "SrcMvSs", owner, "standard", owner);
        Folder dst = service.create(root, "DstMvSs", owner, "standard", owner);
        Folder child = service.create(src.getId(), "ChildMvSs", owner, "standard", owner);

        Folder moved = service.move(child.getId(), dst.getId(), owner);

        assertThat(moved.getParentId()).isEqualTo(dst.getId());
        assertThat(moved.getScopeId()).isEqualTo(scopeId);
    }

    // ──────────────────────────────────────────────────────────────────
    // helpers (FolderCreateScopeInheritanceTest와 동일 패턴)
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
        // folderRepository round-trip은 불필요 (id만 사용).
        return id;
    }
}
