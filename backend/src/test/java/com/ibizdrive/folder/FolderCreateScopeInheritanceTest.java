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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Plan A Task 24 — {@link FolderMutationService#create} scope inheritance + root-via-API rejection.
 *
 * <p>spec docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §1.2 (scope invariant), §1.3
 * (workspace lifecycle owns root creation).
 *
 * <p>"Fake workspace root"는 service의 새 {@code parentId required} 가드를 우회하기 위해 raw JDBC로
 * 직접 INSERT — 정상 운영에서는 {@code DepartmentService}/{@code TeamService} (Task 16/20)가 root를
 * 만든다. 본 테스트는 child 경로의 scope inheritance + null-parent 거부만 격리 검증.
 *
 * <p>{@link FolderMutationServiceTest}와 동일한 Testcontainers + DataJpaTest 슬라이스 — V13 NOT NULL +
 * CHECK 제약을 실제 Postgres에서 검증한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import(FolderCreateScopeInheritanceTest.TestConfig.class)
class FolderCreateScopeInheritanceTest {

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

        @Bean com.ibizdrive.team.TeamArchiveGuard teamArchiveGuard(com.ibizdrive.team.TeamRepository teamRepo) {
            return new com.ibizdrive.team.TeamArchiveGuard(teamRepo);
        }

        @Bean FolderMutationService folderMutationService(FolderRepository repo,
                                                          FileRepository fileRepo,
                                                          AuditService audit,
                                                          ObjectMapper mapper,
                                                          com.ibizdrive.team.TeamArchiveGuard guard) {
            return new FolderMutationService(repo, fileRepo, audit, mapper,
                new com.ibizdrive.trash.TrashRetentionProperties(30), guard);
        }
    }

    @Autowired private FolderMutationService service;
    @Autowired private FolderRepository folderRepository;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void childInheritsScopeFromParent() {
        UUID owner = insertUser("scope-inh1@test", "scope-inh1");
        UUID scopeId = UUID.randomUUID();
        UUID rootId = insertFakeRoot(owner, "department", scopeId);

        Folder child = service.create(rootId, "child", owner, "standard", owner);

        assertThat(child.getParentId()).isEqualTo(rootId);
        assertThat(child.getScopeType()).isEqualTo(ScopeType.DEPARTMENT);
        assertThat(child.getScopeId()).isEqualTo(scopeId);

        // entity getter 외에도 raw column으로 진실의 출처(DB) 확인.
        Folder fromDb = folderRepository.findByIdAndDeletedAtIsNull(child.getId()).orElseThrow();
        assertThat(fromDb.getScopeType()).isEqualTo(ScopeType.DEPARTMENT);
        assertThat(fromDb.getScopeId()).isEqualTo(scopeId);
    }

    @Test
    void rootCreationViaApiRejected() {
        UUID owner = insertUser("scope-inh2@test", "scope-inh2");

        assertThatThrownBy(() -> service.create(null, "would-be-root", owner, "standard", owner))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("parent_id required");
    }

    // ──────────────────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────────────────

    private UUID insertUser(String email, String displayName) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)", id, email, displayName);
        return id;
    }

    /**
     * service의 {@code parentId required} 가드를 우회하여 raw JDBC로 fake workspace root를 INSERT.
     * 정상 운영에서는 Department/TeamService가 root를 만든다 (Task 16/20).
     */
    private UUID insertFakeRoot(UUID ownerId, String scopeType, UUID scopeId) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update(
            "INSERT INTO folders(id, parent_id, name, normalized_name, slug, owner_id, audit_level, " +
            "scope_type, scope_id, created_at, updated_at) " +
            "VALUES (?, NULL, ?, ?, ?, ?, 'standard', ?, ?, ?, ?)",
            id, "root-" + id, "root-" + id, "root-" + id, ownerId, scopeType, scopeId, now, now
        );
        return id;
    }
}
