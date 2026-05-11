package com.ibizdrive.folder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.file.FileRepository;
import com.ibizdrive.permission.Permission;
import com.ibizdrive.permission.PermissionRepository;
import com.ibizdrive.permission.PermissionResolver;
import com.ibizdrive.share.ShareRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.util.EnumSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Plan D Task 16 — {@link FolderMutationService#move(UUID, UUID, UUID, boolean)}
 * allowCrossScope 분기 통합 테스트.
 *
 * <p>실제 Postgres + Flyway 마이그레이션 위에서 두 가지 시나리오를 검증한다:
 * <ol>
 *   <li>{@code allowCrossScope=true}: cross-scope 이동이 성공하고 {@code scopeId}가 destination scope로
 *       변경된다 (CrossWorkspaceMoveService 위임 경로).</li>
 *   <li>{@code allowCrossScope=false/absent}: cross-scope 이동이 {@link CrossScopeMoveException}으로
 *       차단된다 (기존 same-scope 가드 경로).</li>
 * </ol>
 *
 * <p>{@link PermissionResolver}는 mock — 권한 검사를 bypass해 서비스 분기 로직 자체에 집중.
 * 실제 권한 검증은 {@link CrossWorkspaceMoveServiceTest}가 별도 커버.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import(FolderMoveAllowCrossScopeTest.TestConfig.class)
class FolderMoveAllowCrossScopeTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.flyway.enabled", () -> "true");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        AuditService auditService() {
            return mock(AuditService.class);
        }

        @Bean
        PermissionResolver permissionResolver() {
            PermissionResolver resolver = mock(PermissionResolver.class);
            // 기본적으로 모든 폴더에 대해 full permissions 반환 — 권한 검사 bypass.
            when(resolver.resolveFor(any(), any(), any()))
                .thenReturn(EnumSet.allOf(Permission.class));
            return resolver;
        }

        @Bean
        ApplicationEventPublisher applicationEventPublisher() {
            return mock(ApplicationEventPublisher.class);
        }

        @Bean
        CrossWorkspaceMoveService crossWorkspaceMoveService(FolderRepository folderRepo,
                                                             FileRepository fileRepo,
                                                             PermissionResolver permissionResolver,
                                                             ApplicationEventPublisher eventPublisher,
                                                             PermissionRepository permRepo,
                                                             ShareRepository shareRepo) {
            return new CrossWorkspaceMoveService(
                folderRepo, fileRepo, permissionResolver,
                eventPublisher, permRepo, shareRepo
            );
        }

        @Bean
        FolderMutationService folderMutationService(FolderRepository repo,
                                                     FileRepository fileRepo,
                                                     AuditService audit,
                                                     ObjectMapper mapper,
                                                     CrossWorkspaceMoveService crossWorkspaceMoveService,
                                                     com.ibizdrive.team.TeamRepository teamRepo) {
            return new FolderMutationService(
                repo, fileRepo, audit, mapper,
                com.ibizdrive.trash.TrashPolicyTestSupport.stubReturning(30),
                crossWorkspaceMoveService,
                new com.ibizdrive.team.TeamArchiveGuard(teamRepo)
            );
        }
    }

    @Autowired private FolderMutationService service;
    @Autowired private FolderRepository folderRepository;
    @Autowired private JdbcTemplate jdbc;

    // ── allowCrossScope=true ──────────────────────────────────────────

    @Test
    @Transactional
    void crossScopeMoveAllowedWhenAllowFlagTrue() {
        UUID actor = insertUser("cs1@test", "cs1");
        // scope A: department type with scopeIdA
        UUID scopeIdA = UUID.randomUUID();
        Folder childA = insertFolder("ChildScopeA", null, actor, "department", scopeIdA);
        // scope B: team type with scopeIdB
        UUID scopeIdB = UUID.randomUUID();
        Folder rootB = insertFolder("RootScopeB", null, actor, "team", scopeIdB);

        // when: allowCrossScope=true → cross-workspace move succeeds
        Folder moved = service.move(childA.getId(), rootB.getId(), actor, true);

        // then: parent changed, scopeId now matches destination
        assertThat(moved.getParentId()).isEqualTo(rootB.getId());
        assertThat(moved.getScopeType()).isEqualTo(ScopeType.TEAM);
        assertThat(moved.getScopeId()).isEqualTo(scopeIdB);
    }

    // ── allowCrossScope=false (default) ──────────────────────────────

    @Test
    @Transactional
    void crossScopeMoveStillRejectedWhenAllowFlagFalse() {
        UUID actor = insertUser("cs2@test", "cs2");
        UUID scopeIdA = UUID.randomUUID();
        Folder childA = insertFolder("ChildScopeA2", null, actor, "department", scopeIdA);
        UUID scopeIdB = UUID.randomUUID();
        Folder rootB = insertFolder("RootScopeB2", null, actor, "team", scopeIdB);

        // when: explicit allowCrossScope=false → same-scope guard fires
        assertThatThrownBy(() -> service.move(childA.getId(), rootB.getId(), actor, false))
            .isInstanceOf(CrossScopeMoveException.class);
    }

    @Test
    @Transactional
    void crossScopeMoveStillRejectedWhenAllowFlagAbsent() {
        UUID actor = insertUser("cs3@test", "cs3");
        UUID scopeIdA = UUID.randomUUID();
        Folder childA = insertFolder("ChildScopeA3", null, actor, "department", scopeIdA);
        UUID scopeIdB = UUID.randomUUID();
        Folder rootB = insertFolder("RootScopeB3", null, actor, "team", scopeIdB);

        // when: 3-arg overload (default false) → CrossScopeMoveException
        assertThatThrownBy(() -> service.move(childA.getId(), rootB.getId(), actor))
            .isInstanceOf(CrossScopeMoveException.class);
    }

    // ── helpers ────────────────────────────────────────────────────────

    private UUID insertUser(String email, String displayName) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)", id, email, displayName);
        return id;
    }

    /**
     * 직접 JDBC로 폴더를 INSERT — service.create는 root 생성을 차단하므로 테스트 시드용 헬퍼.
     * scope_type/scope_id를 명시적으로 받아 cross-scope 시나리오를 설정한다.
     */
    private Folder insertFolder(String name, UUID parentId, UUID ownerId,
                                 String scopeType, UUID scopeId) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(java.time.Instant.now());
        jdbc.update(
            "INSERT INTO folders(id, parent_id, name, normalized_name, slug, owner_id, audit_level, " +
            "scope_type, scope_id, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, 'standard', ?, ?, ?, ?)",
            id, parentId, name, name.toLowerCase(), name.toLowerCase(),
            ownerId, scopeType, scopeId, now, now
        );
        return folderRepository.findByIdAndDeletedAtIsNull(id).orElseThrow();
    }
}
