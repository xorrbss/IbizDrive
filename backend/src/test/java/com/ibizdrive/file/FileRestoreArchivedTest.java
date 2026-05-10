package com.ibizdrive.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.folder.ScopeType;
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

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Plan E T5 — {@link FileMutationService#restore} archive guard.
 *
 * <p>spec docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §2.2 / §5.4 — archived 팀
 * 콘텐츠는 read-only. 휴지통 파일 복원도 write 진입점이므로 차단해야 한다.
 *
 * <p>{@link FileMutationServiceTest}와 동일한 Testcontainers + DataJpaTest 슬라이스 — V13 NOT NULL +
 * V12 teams 테이블을 실제 Postgres에서 사용해 {@link TeamArchiveGuard}가 정상 호출되는지 검증.
 *
 * <p>peer pattern: {@link com.ibizdrive.folder.FolderRestoreArchivedTest} (T4).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import(FileRestoreArchivedTest.TestConfig.class)
class FileRestoreArchivedTest {

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

        @Bean FileMutationService fileMutationService(FileRepository fileRepo,
                                                      FolderRepository folderRepo,
                                                      AuditService audit,
                                                      ObjectMapper mapper,
                                                      TeamArchiveGuard guard) {
            return new FileMutationService(fileRepo, folderRepo, audit, mapper,
                new com.ibizdrive.trash.TrashRetentionProperties(30),
                mock(com.ibizdrive.folder.CrossWorkspaceMoveService.class),
                guard);
        }
    }

    @Autowired private FileMutationService service;
    @Autowired private FileRepository fileRepository;
    @Autowired private JdbcTemplate jdbc;
    @PersistenceContext private EntityManager em;

    @Test
    void restore_archivedTeamScope_throwsTeamArchived() {
        // 시나리오: team scope 파일 휴지통 → team archive → restore 시도 → TEAM_ARCHIVED.
        UUID owner = insertUser("frstarc1@test", "frstarc1");
        UUID teamId = insertActiveTeam("ArchivedFileTeam1", "archivedfileteam1", owner);
        UUID folderId = insertFakeTeamRoot(owner, teamId);
        FileItem file = insertFile(folderId, owner, "ToRestoreArc1.txt");

        // soft-delete via raw JDBC — file delete service 경유는 별도 테스트 책임이므로 단축.
        softDeleteFile(file.getId(), folderId);

        // archive 상태로 만든 뒤 restore 시도 → guard가 단락.
        archiveTeam(teamId, owner);

        assertThatThrownBy(() -> service.restore(file.getId(), owner))
            .isInstanceOf(TeamArchivedException.class)
            .satisfies(ex -> assertThat(((TeamArchivedException) ex).getTeamId()).isEqualTo(teamId));
    }

    @Test
    void restore_activeTeamScope_succeeds() {
        // 회귀 가드: active team scope에서 restore는 정상 동작 (archive guard short-circuit 검증).
        UUID owner = insertUser("frstarc2@test", "frstarc2");
        UUID teamId = insertActiveTeam("ActiveFileTeam2", "activefileteam2", owner);
        UUID folderId = insertFakeTeamRoot(owner, teamId);
        FileItem file = insertFile(folderId, owner, "ToRestoreArc2.txt");

        softDeleteFile(file.getId(), folderId);

        FileItem restored = service.restore(file.getId(), owner);

        assertThat(restored.getDeletedAt()).isNull();
        assertThat(fileRepository.findByIdAndDeletedAtIsNull(file.getId())).isPresent();
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

    /** {@link FileRepository#save}로 fixture INSERT — V5 NOT NULL 컬럼 모두 채움. parent folder의 scope를 상속. */
    private FileItem insertFile(UUID folderId, UUID ownerId, String name) {
        FileItem f = new FileItem();
        f.setId(UUID.randomUUID());
        f.setFolderId(folderId);
        f.setName(name);
        f.setNormalizedName(name.toLowerCase());
        f.setOwnerId(ownerId);
        f.setSizeBytes(0L);
        Object[] scope = jdbc.queryForObject(
            "SELECT scope_type, scope_id FROM folders WHERE id = ?",
            (rs, rowNum) -> new Object[]{rs.getString("scope_type"), rs.getObject("scope_id", UUID.class)},
            folderId
        );
        f.assignScope(ScopeType.fromDb((String) scope[0]), (UUID) scope[1]);
        Instant now = Instant.now();
        f.setCreatedAt(now);
        f.setUpdatedAt(now);
        return fileRepository.saveAndFlush(f);
    }

    /** raw JDBC soft-delete — original_folder_id 보존. JPA L1 캐시는 raw UPDATE를 보지 못하므로
     *  service.restore의 lockByIdAndDeletedAtIsNotNull lookup이 stale entity (originalFolderId=NULL)를
     *  반환하지 않도록 flush + clear로 영속성 컨텍스트를 비운다. */
    private void softDeleteFile(UUID fileId, UUID originalFolderId) {
        jdbc.update(
            "UPDATE files SET deleted_at = NOW(), purge_after = NOW() + INTERVAL '30 days', " +
            "original_folder_id = ? WHERE id = ?",
            originalFolderId, fileId
        );
        em.flush();
        em.clear();
    }

    /** Team을 archived 상태로 전환. archived_at + archived_by NOT NULL 동시 update. */
    private void archiveTeam(UUID teamId, UUID actorId) {
        jdbc.update(
            "UPDATE teams SET archived_at = NOW(), archived_by = ?, updated_at = NOW() WHERE id = ?",
            actorId, teamId
        );
    }
}
