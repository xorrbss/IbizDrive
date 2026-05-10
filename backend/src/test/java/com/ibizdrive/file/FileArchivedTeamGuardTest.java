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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * T4 — {@link FileMutationService} TEAM_ARCHIVED 가드 회귀 보장.
 *
 * <p>spec docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §2.2/§5.4 — archived 팀 scope의
 * 콘텐츠는 read-only. 4개 write 진입점(rename/move/delete/restore) 각각:
 * <ul>
 *   <li><b>archived case</b> — Team.archivedAt 설정 후 진입점 호출 → {@link TeamArchivedException} 검증.</li>
 *   <li><b>active case (smoke)</b> — Team active일 때 동일 진입점 통과 검증.</li>
 * </ul>
 *
 * <p>DEPARTMENT scope 회귀는 {@link FileMutationServiceTest}가 이미 커버 (모든 fixture가 department scope).
 *
 * <p>{@link FileMutationServiceTest}와 동일한 Testcontainers + DataJpaTest 슬라이스. fixture는 raw JDBC로
 * minimal team/folder/file 행을 채우고 active/archived 분기는 {@code teams.archived_at} 직접 UPDATE로 시뮬.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import(FileArchivedTeamGuardTest.TestConfig.class)
class FileArchivedTeamGuardTest {

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

        @Bean FileMutationService fileMutationService(FileRepository fileRepo,
                                                      FolderRepository folderRepo,
                                                      AuditService audit,
                                                      ObjectMapper mapper,
                                                      TeamRepository teamRepo) {
            return new FileMutationService(fileRepo, folderRepo, audit, mapper,
                new com.ibizdrive.trash.TrashRetentionProperties(30),
                org.mockito.Mockito.mock(com.ibizdrive.folder.CrossWorkspaceMoveService.class),
                new TeamArchiveGuard(teamRepo));
        }
    }

    @Autowired private FileMutationService service;
    @Autowired private FileRepository fileRepository;
    @Autowired private JdbcTemplate jdbc;
    @PersistenceContext private EntityManager em;

    // ──────────────────────────────────────────────────────────────────
    // rename
    // ──────────────────────────────────────────────────────────────────

    @Test
    void rename_archivedTeam_throwsTeamArchived() {
        UUID owner = insertUser("fa-rn1@test", "fa-rn1");
        UUID teamId = insertTeam("Alpha-rn1", "alpha-rn1", owner);
        UUID folder = insertTeamFolder(owner, teamId, "FolderRn1");
        FileItem f = insertTeamFile(folder, owner, teamId, "OrigRn1.txt");
        archiveTeam(teamId, owner);

        assertThatThrownBy(() -> service.rename(f.getId(), "NewRn1.txt", owner))
            .isInstanceOf(TeamArchivedException.class)
            .satisfies(ex -> assertThat(((TeamArchivedException) ex).getTeamId()).isEqualTo(teamId));
    }

    @Test
    void rename_activeTeam_succeeds() {
        UUID owner = insertUser("fa-rn2@test", "fa-rn2");
        UUID teamId = insertTeam("Alpha-rn2", "alpha-rn2", owner);
        UUID folder = insertTeamFolder(owner, teamId, "FolderRn2");
        FileItem f = insertTeamFile(folder, owner, teamId, "OrigRn2.txt");

        FileItem renamed = service.rename(f.getId(), "NewRn2.txt", owner);

        assertThat(renamed.getName()).isEqualTo("NewRn2.txt");
    }

    // ──────────────────────────────────────────────────────────────────
    // move
    // ──────────────────────────────────────────────────────────────────

    @Test
    void move_archivedTeam_throwsTeamArchived() {
        // 한 팀에 root는 1개만 허용(idx_folders_root_per_scope) — src/dst는 root 아래 child로 구성.
        UUID owner = insertUser("fa-mv1@test", "fa-mv1");
        UUID teamId = insertTeam("Alpha-mv1", "alpha-mv1", owner);
        UUID root = insertTeamFolder(owner, teamId, "RootMv1");
        UUID src = insertChildFolder(root, owner, teamId, "SrcMv1");
        UUID dst = insertChildFolder(root, owner, teamId, "DstMv1");
        FileItem f = insertTeamFile(src, owner, teamId, "MvFile1.txt");
        archiveTeam(teamId, owner);

        assertThatThrownBy(() -> service.move(f.getId(), dst, owner))
            .isInstanceOf(TeamArchivedException.class)
            .satisfies(ex -> assertThat(((TeamArchivedException) ex).getTeamId()).isEqualTo(teamId));
    }

    @Test
    void move_activeTeam_succeeds() {
        UUID owner = insertUser("fa-mv2@test", "fa-mv2");
        UUID teamId = insertTeam("Alpha-mv2", "alpha-mv2", owner);
        UUID root = insertTeamFolder(owner, teamId, "RootMv2");
        UUID src = insertChildFolder(root, owner, teamId, "SrcMv2");
        UUID dst = insertChildFolder(root, owner, teamId, "DstMv2");
        FileItem f = insertTeamFile(src, owner, teamId, "MvFile2.txt");

        FileItem moved = service.move(f.getId(), dst, owner);

        assertThat(moved.getFolderId()).isEqualTo(dst);
    }

    // ──────────────────────────────────────────────────────────────────
    // delete
    // ──────────────────────────────────────────────────────────────────

    @Test
    void delete_archivedTeam_throwsTeamArchived() {
        UUID owner = insertUser("fa-dl1@test", "fa-dl1");
        UUID teamId = insertTeam("Alpha-dl1", "alpha-dl1", owner);
        UUID folder = insertTeamFolder(owner, teamId, "FolderDl1");
        FileItem f = insertTeamFile(folder, owner, teamId, "DlFile1.txt");
        archiveTeam(teamId, owner);

        assertThatThrownBy(() -> service.delete(f.getId(), owner))
            .isInstanceOf(TeamArchivedException.class)
            .satisfies(ex -> assertThat(((TeamArchivedException) ex).getTeamId()).isEqualTo(teamId));
    }

    @Test
    void delete_activeTeam_succeeds() {
        UUID owner = insertUser("fa-dl2@test", "fa-dl2");
        UUID teamId = insertTeam("Alpha-dl2", "alpha-dl2", owner);
        UUID folder = insertTeamFolder(owner, teamId, "FolderDl2");
        FileItem f = insertTeamFile(folder, owner, teamId, "DlFile2.txt");

        FileItem deleted = service.delete(f.getId(), owner);

        assertThat(deleted.getDeletedAt()).isNotNull();
    }

    // ──────────────────────────────────────────────────────────────────
    // restore
    // ──────────────────────────────────────────────────────────────────

    @Test
    void restore_archivedTeam_throwsTeamArchived() {
        UUID owner = insertUser("fa-rs1@test", "fa-rs1");
        UUID teamId = insertTeam("Alpha-rs1", "alpha-rs1", owner);
        UUID folder = insertTeamFolder(owner, teamId, "FolderRs1");
        FileItem f = insertTeamFile(folder, owner, teamId, "RsFile1.txt");
        // soft-delete BEFORE archive — restore() requires a trashed file.
        service.delete(f.getId(), owner);
        archiveTeam(teamId, owner);

        assertThatThrownBy(() -> service.restore(f.getId(), owner))
            .isInstanceOf(TeamArchivedException.class)
            .satisfies(ex -> assertThat(((TeamArchivedException) ex).getTeamId()).isEqualTo(teamId));
    }

    @Test
    void restore_activeTeam_succeeds() {
        UUID owner = insertUser("fa-rs2@test", "fa-rs2");
        UUID teamId = insertTeam("Alpha-rs2", "alpha-rs2", owner);
        UUID folder = insertTeamFolder(owner, teamId, "FolderRs2");
        FileItem f = insertTeamFile(folder, owner, teamId, "RsFile2.txt");
        service.delete(f.getId(), owner);

        FileItem restored = service.restore(f.getId(), owner);

        assertThat(restored.getDeletedAt()).isNull();
        assertThat(restored.getFolderId()).isEqualTo(folder);
    }

    // ──────────────────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────────────────

    private UUID insertUser(String email, String displayName) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)", id, email, displayName);
        return id;
    }

    /** V12 schema — teams 행 하나(active 상태)를 raw JDBC로 INSERT. archive는 별도 UPDATE. */
    private UUID insertTeam(String name, String normalizedName, UUID createdBy) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO teams(id, name, normalized_name, visibility, created_by, created_at, updated_at) " +
            "VALUES (?, ?, ?, 'private', ?, NOW(), NOW())",
            id, name, normalizedName, createdBy
        );
        return id;
    }

    /**
     * raw JDBC UPDATE이므로 JPA L1 persistence context는 자동 갱신되지 않는다. setup 단계의
     * service.delete가 가드를 트리거해 Team을 캐시에 적재하면, 이후 가드 호출이 stale active 상태를
     * 반환한다(예: restore_archivedTeam). {@code em.flush() + em.clear()}로 캐시를 비워 다음 lookup이
     * DB를 재조회하도록 강제.
     */
    private void archiveTeam(UUID teamId, UUID actorId) {
        jdbc.update(
            "UPDATE teams SET archived_at = NOW(), archived_by = ?, updated_at = NOW() WHERE id = ?",
            actorId, teamId
        );
        em.flush();
        em.clear();
    }

    /** team scope root folder fixture — parent_id NULL. {@code idx_folders_root_per_scope}로 인해
     *  팀당 1개만 가능. 자식이 필요하면 {@link #insertChildFolder}를 사용. */
    private UUID insertTeamFolder(UUID ownerId, UUID teamId, String name) {
        UUID id = UUID.randomUUID();
        String normalized = name.toLowerCase();
        jdbc.update(
            "INSERT INTO folders(id, parent_id, name, normalized_name, slug, owner_id, audit_level, " +
            "scope_type, scope_id) " +
            "VALUES (?, NULL, ?, ?, ?, ?, 'standard', 'team', ?)",
            id, name, normalized, normalized, ownerId, teamId
        );
        return id;
    }

    /** team scope child folder — parent_id 명시. 같은 팀에 여러 폴더가 필요한 경우 root 1개 + children N개. */
    private UUID insertChildFolder(UUID parentId, UUID ownerId, UUID teamId, String name) {
        UUID id = UUID.randomUUID();
        String normalized = name.toLowerCase();
        jdbc.update(
            "INSERT INTO folders(id, parent_id, name, normalized_name, slug, owner_id, audit_level, " +
            "scope_type, scope_id) " +
            "VALUES (?, ?, ?, ?, ?, ?, 'standard', 'team', ?)",
            id, parentId, name, normalized, normalized, ownerId, teamId
        );
        return id;
    }

    /** team scope file fixture — file의 scope_type/scope_id를 부모 folder와 동일 (TEAM, teamId)로 채움. */
    private FileItem insertTeamFile(UUID folderId, UUID ownerId, UUID teamId, String name) {
        FileItem f = new FileItem();
        f.setId(UUID.randomUUID());
        f.setFolderId(folderId);
        f.setName(name);
        f.setNormalizedName(name.toLowerCase());
        f.setOwnerId(ownerId);
        f.setSizeBytes(0L);
        f.assignScope(ScopeType.TEAM, teamId);
        Instant now = Instant.now();
        f.setCreatedAt(now);
        f.setUpdatedAt(now);
        return fileRepository.saveAndFlush(f);
    }
}
