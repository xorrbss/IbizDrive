package com.ibizdrive.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.file.dto.MoveFileRequest;
import com.ibizdrive.folder.CrossScopeMoveException;
import com.ibizdrive.folder.CrossWorkspaceMoveService;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.folder.ScopeType;
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
import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Plan D Task 17 — {@link FileMutationService#move(UUID, UUID, UUID, boolean)}
 * allowCrossScope 분기 통합 테스트 (mirror of Task 16 {@code FolderMoveAllowCrossScopeTest}).
 *
 * <p>실제 Postgres + Flyway 마이그레이션 위에서 세 가지 시나리오를 검증한다:
 * <ol>
 *   <li>{@code allowCrossScope=true}: cross-scope 이동이 성공하고 {@code scopeId}가 destination scope로
 *       변경된다 (CrossWorkspaceMoveService 위임 경로 — Task 18 구현 전에는 UnsupportedOperationException).
 *       Task 18 구현 후 이 테스트가 green이 된다.</li>
 *   <li>{@code allowCrossScope=false}: cross-scope 이동이 {@link CrossScopeMoveException}으로 차단.</li>
 *   <li>{@code allowCrossScope absent}: 3-arg 오버로드 (default false) → {@link CrossScopeMoveException}.</li>
 * </ol>
 *
 * <p>{@link PermissionResolver}는 mock — 권한 검사를 bypass해 서비스 분기 로직 자체에 집중.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import(FileMoveAllowCrossScopeTest.TestConfig.class)
class FileMoveAllowCrossScopeTest {

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
            // full permissions — 권한 검사 bypass.
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
        FileMutationService fileMutationService(FileRepository fileRepo,
                                                 FolderRepository folderRepo,
                                                 AuditService audit,
                                                 ObjectMapper mapper,
                                                 CrossWorkspaceMoveService crossWorkspaceMoveService,
                                                 com.ibizdrive.team.TeamRepository teamRepo) {
            return new FileMutationService(
                fileRepo, folderRepo, audit, mapper,
                com.ibizdrive.trash.TrashPolicyTestSupport.stubReturning(30),
                crossWorkspaceMoveService,
                new com.ibizdrive.team.TeamArchiveGuard(teamRepo)
            );
        }
    }

    @Autowired private FileMutationService service;
    @Autowired private FileRepository fileRepository;
    @Autowired private FolderRepository folderRepository;
    @Autowired private JdbcTemplate jdbc;

    // ── allowCrossScope=true ──────────────────────────────────────────

    @Test
    @Transactional
    void crossScopeMoveAllowedWhenAllowFlagTrue() {
        UUID actor = insertUser("fcs1@test", "fcs1");
        // scope A: department
        UUID scopeIdA = UUID.randomUUID();
        UUID folderA = insertFolder("FolderScopeA", actor, "department", scopeIdA);
        FileItem file = insertFile(folderA, actor, "report.pdf", "department", scopeIdA);

        // scope B: team
        UUID scopeIdB = UUID.randomUUID();
        UUID folderB = insertFolder("FolderScopeB", actor, "team", scopeIdB);

        // when: allowCrossScope=true → cross-workspace move succeeds
        FileItem moved = service.move(file.getId(), folderB, actor, true);

        // then: folderId changed, scopeId now matches destination
        assertThat(moved.getFolderId()).isEqualTo(folderB);
        assertThat(moved.getScopeType()).isEqualTo(ScopeType.TEAM);
        assertThat(moved.getScopeId()).isEqualTo(scopeIdB);
    }

    // ── allowCrossScope=false ─────────────────────────────────────────

    @Test
    @Transactional
    void crossScopeMoveStillRejectedWhenAllowFlagFalse() {
        UUID actor = insertUser("fcs2@test", "fcs2");
        UUID scopeIdA = UUID.randomUUID();
        UUID folderA = insertFolder("FolderScopeA2", actor, "department", scopeIdA);
        FileItem file = insertFile(folderA, actor, "doc2.pdf", "department", scopeIdA);

        UUID scopeIdB = UUID.randomUUID();
        UUID folderB = insertFolder("FolderScopeB2", actor, "team", scopeIdB);

        // when: explicit allowCrossScope=false → same-scope guard fires
        assertThatThrownBy(() -> service.move(file.getId(), folderB, actor, false))
            .isInstanceOf(CrossScopeMoveException.class);
    }

    // ── allowCrossScope absent (3-arg overload) ───────────────────────

    @Test
    @Transactional
    void crossScopeMoveStillRejectedWhenAllowFlagAbsent() {
        UUID actor = insertUser("fcs3@test", "fcs3");
        UUID scopeIdA = UUID.randomUUID();
        UUID folderA = insertFolder("FolderScopeA3", actor, "department", scopeIdA);
        FileItem file = insertFile(folderA, actor, "doc3.pdf", "department", scopeIdA);

        UUID scopeIdB = UUID.randomUUID();
        UUID folderB = insertFolder("FolderScopeB3", actor, "team", scopeIdB);

        // when: 3-arg overload (default false) → CrossScopeMoveException
        assertThatThrownBy(() -> service.move(file.getId(), folderB, actor))
            .isInstanceOf(CrossScopeMoveException.class);
    }

    // ── helpers ────────────────────────────────────────────────────────

    private UUID insertUser(String email, String displayName) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)", id, email, displayName);
        return id;
    }

    /**
     * 직접 JDBC로 폴더를 INSERT — scope_type/scope_id를 명시적으로 받아 cross-scope 시나리오 설정.
     */
    private UUID insertFolder(String name, UUID ownerId, String scopeType, UUID scopeId) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update(
            "INSERT INTO folders(id, parent_id, name, normalized_name, slug, owner_id, audit_level, " +
            "scope_type, scope_id, created_at, updated_at) " +
            "VALUES (?, NULL, ?, ?, ?, ?, 'standard', ?, ?, ?, ?)",
            id, name, name.toLowerCase(), name.toLowerCase(),
            ownerId, scopeType, scopeId, now, now
        );
        return id;
    }

    /**
     * FileRepository.save로 fixture INSERT.
     */
    private FileItem insertFile(UUID folderId, UUID ownerId, String name,
                                 String scopeType, UUID scopeId) {
        FileItem f = new FileItem();
        f.setId(UUID.randomUUID());
        f.setFolderId(folderId);
        f.setName(name);
        f.setNormalizedName(name.toLowerCase());
        f.setOwnerId(ownerId);
        f.setSizeBytes(0L);
        f.assignScope(ScopeType.fromDb(scopeType), scopeId);
        Instant now = Instant.now();
        f.setCreatedAt(now);
        f.setUpdatedAt(now);
        return fileRepository.saveAndFlush(f);
    }
}
