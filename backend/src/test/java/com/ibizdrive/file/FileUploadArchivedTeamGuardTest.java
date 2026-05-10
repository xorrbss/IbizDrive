package com.ibizdrive.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.storage.StorageClient;
import com.ibizdrive.team.TeamArchiveGuard;
import com.ibizdrive.team.TeamArchivedException;
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

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * T5 — {@link FileUploadService} + {@link FileVersionMutationService} TEAM_ARCHIVED 가드 회귀 보장.
 *
 * <p>spec docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §2.2/§5.4 — archived 팀 scope의
 * 콘텐츠는 read-only. 두 write 진입점(upload/restoreVersion) 각각:
 * <ul>
 *   <li><b>archived case</b> — Team.archivedAt 설정 후 진입점 호출 → {@link TeamArchivedException} 검증.</li>
 *   <li><b>active case (smoke)</b> — Team active일 때 동일 진입점 통과 검증.</li>
 * </ul>
 *
 * <p>DEPARTMENT scope 회귀는 {@link FileUploadServiceTest} / {@link FileVersionMutationServiceTest}가
 * 이미 커버 (모든 fixture가 department scope).
 *
 * <p>{@link FileArchivedTeamGuardTest}와 동일한 Testcontainers + DataJpaTest 슬라이스. fixture는 raw JDBC로
 * minimal team/folder/file 행을 채우고 active/archived 분기는 {@code teams.archived_at} 직접 UPDATE로 시뮬.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import(FileUploadArchivedTeamGuardTest.TestConfig.class)
class FileUploadArchivedTeamGuardTest {

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

        @Bean StorageClient storageClient() {
            return mock(StorageClient.class);
        }

        @Bean FileUploadService fileUploadService(FileRepository fileRepo,
                                                  FileVersionRepository versionRepo,
                                                  FolderRepository folderRepo,
                                                  StorageClient storage,
                                                  AuditService audit,
                                                  ObjectMapper mapper,
                                                  TeamRepository teamRepo) {
            return new FileUploadService(fileRepo, versionRepo, folderRepo, storage, audit, mapper,
                new TeamArchiveGuard(teamRepo));
        }

        @Bean FileVersionMutationService fileVersionMutationService(FileRepository fileRepo,
                                                                    FileVersionRepository versionRepo,
                                                                    AuditService audit,
                                                                    ObjectMapper mapper,
                                                                    TeamRepository teamRepo) {
            return new FileVersionMutationService(fileRepo, versionRepo, audit, mapper,
                new TeamArchiveGuard(teamRepo));
        }
    }

    @Autowired private FileUploadService uploadService;
    @Autowired private FileVersionMutationService versionService;
    @Autowired private FileRepository fileRepository;
    @Autowired private FileVersionRepository fileVersionRepository;
    @Autowired private JdbcTemplate jdbc;

    // ──────────────────────────────────────────────────────────────────
    // upload
    // ──────────────────────────────────────────────────────────────────

    @Test
    void upload_archivedTeam_throwsTeamArchived() {
        UUID owner = insertUser("fua-up1@test", "fua-up1");
        UUID teamId = insertTeam("Alpha-up1", "alpha-up1", owner);
        UUID folder = insertTeamFolder(owner, teamId, "FolderUp1");
        archiveTeam(teamId, owner);

        byte[] body = "hello".getBytes();
        assertThatThrownBy(() -> uploadService.upload(folder, owner, "Up1.txt", "text/plain",
            body.length, new ByteArrayInputStream(body), null))
            .isInstanceOf(TeamArchivedException.class)
            .satisfies(ex -> assertThat(((TeamArchivedException) ex).getTeamId()).isEqualTo(teamId));
    }

    @Test
    void upload_activeTeam_succeeds() {
        UUID owner = insertUser("fua-up2@test", "fua-up2");
        UUID teamId = insertTeam("Alpha-up2", "alpha-up2", owner);
        UUID folder = insertTeamFolder(owner, teamId, "FolderUp2");

        byte[] body = "hello".getBytes();
        UploadResult result = uploadService.upload(folder, owner, "Up2.txt", "text/plain",
            body.length, new ByteArrayInputStream(body), null);

        assertThat(result.newFile()).isTrue();
        assertThat(result.file().getFolderId()).isEqualTo(folder);
        assertThat(result.file().getName()).isEqualTo("Up2.txt");
    }

    // ──────────────────────────────────────────────────────────────────
    // restoreVersion
    // ──────────────────────────────────────────────────────────────────

    @Test
    void restoreVersion_archivedTeam_throwsTeamArchived() {
        UUID owner = insertUser("fua-rv1@test", "fua-rv1");
        UUID teamId = insertTeam("Alpha-rv1", "alpha-rv1", owner);
        UUID folder = insertTeamFolder(owner, teamId, "FolderRv1");
        FileItem file = insertTeamFile(folder, owner, teamId, "RvFile1.txt");
        FileVersion v1 = insertVersion(file.getId(), 1, owner);
        FileVersion v2 = insertVersion(file.getId(), 2, owner);
        file.setCurrentVersionId(v2.getId());
        fileRepository.saveAndFlush(file);
        archiveTeam(teamId, owner);

        assertThatThrownBy(() -> versionService.restoreVersion(file.getId(), v1.getId(), owner))
            .isInstanceOf(TeamArchivedException.class)
            .satisfies(ex -> assertThat(((TeamArchivedException) ex).getTeamId()).isEqualTo(teamId));
    }

    @Test
    void restoreVersion_activeTeam_succeeds() {
        UUID owner = insertUser("fua-rv2@test", "fua-rv2");
        UUID teamId = insertTeam("Alpha-rv2", "alpha-rv2", owner);
        UUID folder = insertTeamFolder(owner, teamId, "FolderRv2");
        FileItem file = insertTeamFile(folder, owner, teamId, "RvFile2.txt");
        FileVersion v1 = insertVersion(file.getId(), 1, owner);
        FileVersion v2 = insertVersion(file.getId(), 2, owner);
        file.setCurrentVersionId(v2.getId());
        fileRepository.saveAndFlush(file);

        FileItem restored = versionService.restoreVersion(file.getId(), v1.getId(), owner);

        assertThat(restored.getCurrentVersionId()).isEqualTo(v1.getId());
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
            "INSERT INTO teams(id, name, normalized_name, visibility, created_by, lead_id, created_at, updated_at) " +
            "VALUES (?, ?, ?, 'private', ?, ?, NOW(), NOW())",
            id, name, normalizedName, createdBy, createdBy
        );
        return id;
    }

    private void archiveTeam(UUID teamId, UUID actorId) {
        jdbc.update(
            "UPDATE teams SET archived_at = NOW(), archived_by = ?, updated_at = NOW() WHERE id = ?",
            actorId, teamId
        );
    }

    /** team scope folder fixture — V13 NOT NULL scope_type/scope_id 충족. */
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

    /** team scope file fixture — file의 scope_type/scope_id를 부모 folder와 동일 (TEAM, teamId)로 채움. */
    private FileItem insertTeamFile(UUID folderId, UUID ownerId, UUID teamId, String name) {
        FileItem f = new FileItem();
        f.setId(UUID.randomUUID());
        f.setFolderId(folderId);
        f.setName(name);
        f.setNormalizedName(name.toLowerCase());
        f.setOwnerId(ownerId);
        f.setSizeBytes(0L);
        f.assignScope(com.ibizdrive.folder.ScopeType.TEAM, teamId);
        Instant now = Instant.now();
        f.setCreatedAt(now);
        f.setUpdatedAt(now);
        return fileRepository.saveAndFlush(f);
    }

    /** file_versions fixture — V5 NOT NULL 컬럼 모두 채움 (storage_key, checksum, scan_status 포함). */
    private FileVersion insertVersion(UUID fileId, int versionNumber, UUID uploaderId) {
        FileVersion v = new FileVersion();
        v.setId(UUID.randomUUID());
        v.setFileId(fileId);
        v.setVersionNumber(versionNumber);
        v.setStorageKey(UUID.randomUUID());
        v.setSizeBytes(0L);
        v.setChecksumSha256("0".repeat(64));
        v.setScanStatus(VersionScanStatus.PENDING);
        v.setUploadedBy(uploaderId);
        v.setUploadedAt(Instant.now());
        return fileVersionRepository.saveAndFlush(v);
    }
}
