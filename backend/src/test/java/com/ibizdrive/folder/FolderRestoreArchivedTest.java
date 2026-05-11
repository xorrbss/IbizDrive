package com.ibizdrive.folder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.file.FileRepository;
import com.ibizdrive.team.TeamArchiveGuard;
import com.ibizdrive.team.TeamArchivedException;
import com.ibizdrive.team.TeamRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
 * Plan E T4 — {@link FolderMutationService#restore} archive guard.
 *
 * <p>spec docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §2.2 / §5.4 — archived 팀
 * 콘텐츠는 read-only. 휴지통에서의 복원도 write 진입점이므로 차단해야 한다.
 *
 * <p>{@link FolderMutationServiceTest}와 동일한 Testcontainers + DataJpaTest 슬라이스 — V13 NOT NULL +
 * V12 teams 테이블을 실제 Postgres에서 사용해 {@link TeamArchiveGuard}가 정상 호출되는지 검증.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import(FolderRestoreArchivedTest.TestConfig.class)
class FolderRestoreArchivedTest {

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
                com.ibizdrive.trash.TrashPolicyTestSupport.stubReturning(30),
                mock(CrossWorkspaceMoveService.class),
                guard);
        }
    }

    @Autowired private FolderMutationService service;
    @Autowired private FolderRepository folderRepository;
    @Autowired private JdbcTemplate jdbc;
    @PersistenceContext private EntityManager em;

    @Test
    void restore_archivedTeamScope_throwsTeamArchived() {
        // 시나리오: team scope 폴더 휴지통 → team archive → restore 시도 → TEAM_ARCHIVED.
        UUID owner = insertUser("rstarc1@test", "rstarc1");
        UUID teamId = insertActiveTeam("ArchivedTeam1", "archivedteam1", owner);
        UUID rootId = insertFakeTeamRoot(owner, teamId);

        Folder folder = service.create(rootId, "ToRestoreArc1", owner, "standard", owner);
        service.delete(folder.getId(), owner);

        // active 동안에는 정상 동작 가능 — guard 미동작 분기 (active team).
        // 여기선 archive 상태로 만든 뒤 restore.
        archiveTeam(teamId, owner);

        assertThatThrownBy(() -> service.restore(folder.getId(), owner))
            .isInstanceOf(TeamArchivedException.class)
            .satisfies(ex -> assertThat(((TeamArchivedException) ex).getTeamId()).isEqualTo(teamId));
    }

    @Test
    void restore_activeTeamScope_succeeds() {
        // 회귀 가드: active team scope에서 restore는 정상 동작 (archive guard short-circuit 검증).
        UUID owner = insertUser("rstarc2@test", "rstarc2");
        UUID teamId = insertActiveTeam("ActiveTeam2", "activeteam2", owner);
        UUID rootId = insertFakeTeamRoot(owner, teamId);

        Folder folder = service.create(rootId, "ToRestoreArc2", owner, "standard", owner);
        service.delete(folder.getId(), owner);

        Folder restored = service.restore(folder.getId(), owner);

        assertThat(restored.getDeletedAt()).isNull();
        assertThat(folderRepository.findByIdAndDeletedAtIsNull(folder.getId())).isPresent();
    }

    // ──────────────────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────────────────

    private UUID insertUser(String email, String displayName) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)", id, email, displayName);
        return id;
    }

    private UUID insertActiveTeam(String name, String normalizedName, UUID createdBy) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO teams(id, name, normalized_name, visibility, created_by, lead_id, created_at, updated_at) " +
            "VALUES (?, ?, ?, 'private', ?, ?, NOW(), NOW())",
            id, name, normalizedName, createdBy, createdBy);
        return id;
    }

    /** team scope를 갖는 fake root folder. service.create의 root-거부 가드를 우회. */
    private UUID insertFakeTeamRoot(UUID ownerId, UUID teamId) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update(
            "INSERT INTO folders(id, parent_id, name, normalized_name, slug, owner_id, audit_level, " +
            "scope_type, scope_id, created_at, updated_at) " +
            "VALUES (?, NULL, ?, ?, ?, ?, 'standard', 'team', ?, ?, ?)",
            id, "team-root-" + id, "team-root-" + id, "team-root-" + id, ownerId, teamId, now, now
        );
        return id;
    }

    /** Team을 archived 상태로 전환. archived_at + archived_by NOT NULL 동시 update.
     *  raw JDBC UPDATE는 JPA L1 캐시를 무효화하지 못한다. service.restore가 TeamArchiveGuard를 통해
     *  Team을 lookup할 때 stale (active) 캐시 hit이 발생하면 guard가 short-circuit되어 예외 미발생.
     *  flush + clear로 영속성 컨텍스트를 비워 다음 lookup이 DB에서 신선한 archived row를 읽도록 강제.
     *  peer pattern: {@link com.ibizdrive.file.FileRestoreArchivedTest#softDeleteFile}. */
    private void archiveTeam(UUID teamId, UUID actorId) {
        jdbc.update(
            "UPDATE teams SET archived_at = NOW(), archived_by = ?, updated_at = NOW() WHERE id = ?",
            actorId, teamId
        );
        em.flush();
        em.clear();
    }
}
