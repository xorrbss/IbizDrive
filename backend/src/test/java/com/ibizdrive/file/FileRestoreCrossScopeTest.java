package com.ibizdrive.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.folder.ScopeType;
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
 * Plan E T5 — {@link FileMutationService#restore} cross-scope mismatch.
 *
 * <p>spec docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §3.4 / §5.2 / §5.3 —
 * 휴지통 파일의 original folder가 다른 workspace로 이동된 경우 (cross-workspace 데이터 재배치 후
 * 발생 가능), 복원하면 자식이 원래 workspace를 떠나 §1.2 invariant 위반. 따라서
 * {@link FileRestoreConflictException.Reason#SCOPE_MISMATCH}로 차단 (envelope
 * {@code RESTORE_CONFLICT} HTTP 409 + body {@code reason='scope_mismatch'}).
 *
 * <p>peer pattern: {@link com.ibizdrive.folder.FolderRestoreCrossScopeTest} (T4).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import(FileRestoreCrossScopeTest.TestConfig.class)
class FileRestoreCrossScopeTest {

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
                new com.ibizdrive.trash.TrashRetentionProperties(30), guard);
        }
    }

    @Autowired private FileMutationService service;
    @Autowired private FileRepository fileRepository;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void restore_originalFolderMovedToDifferentScope_throwsScopeMismatch() {
        // 시나리오:
        //   1) folder (scope_id=A)에 file을 만든다 → file.scope=A.
        //   2) file을 휴지통으로 — originalFolderId=folder, target.scope=A 보존.
        //   3) folder의 scope_id를 raw로 B로 재배치 (cross-workspace 데이터 이동 시뮬레이션 —
        //      서비스 경유는 cross-scope를 막으므로 raw JDBC).
        //   4) restore(file) 시도 → originalFolder.scope(B) != target.scope(A) → SCOPE_MISMATCH.
        UUID owner = insertUser("frstcs1@test", "frstcs1");
        UUID scopeA = UUID.randomUUID();
        UUID scopeB = UUID.randomUUID();
        UUID folderId = insertFakeFolder(owner, "department", scopeA);

        FileItem file = insertFile(folderId, owner, "ChildFileCs1.txt");
        assertThat(file.getScopeId()).isEqualTo(scopeA);

        softDeleteFile(file.getId(), folderId);
        // file은 이제 deleted_at != NULL, original_folder_id=folderId, scope=A 그대로.

        // folder를 scope B로 강제 재배치 — partition 또는 admin migration 시뮬레이션.
        jdbc.update("UPDATE folders SET scope_id = ? WHERE id = ?", scopeB, folderId);

        assertThatThrownBy(() -> service.restore(file.getId(), owner))
            .isInstanceOf(FileRestoreConflictException.class)
            .satisfies(ex -> {
                FileRestoreConflictException conflict = (FileRestoreConflictException) ex;
                assertThat(conflict.getReason())
                    .isEqualTo(FileRestoreConflictException.Reason.SCOPE_MISMATCH);
                assertThat(conflict.getResourceId()).isEqualTo(file.getId());
                assertThat(conflict.getDetails())
                    .containsEntry("expectedScopeType", "department")
                    .containsEntry("expectedScopeId", scopeA.toString())
                    .containsEntry("actualScopeType", "department")
                    .containsEntry("actualScopeId", scopeB.toString());
            });
    }

    @Test
    void restore_originalFolderDifferentScopeType_throwsScopeMismatch() {
        // 시나리오: scope_type까지 바뀐 경우 (department → team migration). 같은 SCOPE_MISMATCH.
        UUID owner = insertUser("frstcs2@test", "frstcs2");
        UUID deptId = UUID.randomUUID();
        UUID teamId = insertActiveTeam("FileTeamCs2", "fileteamcs2", owner);
        UUID folderId = insertFakeFolder(owner, "department", deptId);

        FileItem file = insertFile(folderId, owner, "ChildFileCs2.txt");
        softDeleteFile(file.getId(), folderId);

        // folder의 scope_type/scope_id를 team으로 통째 변경.
        jdbc.update("UPDATE folders SET scope_type = 'team', scope_id = ? WHERE id = ?", teamId, folderId);

        assertThatThrownBy(() -> service.restore(file.getId(), owner))
            .isInstanceOf(FileRestoreConflictException.class)
            .satisfies(ex -> {
                FileRestoreConflictException conflict = (FileRestoreConflictException) ex;
                assertThat(conflict.getReason())
                    .isEqualTo(FileRestoreConflictException.Reason.SCOPE_MISMATCH);
                assertThat(conflict.getDetails())
                    .containsEntry("expectedScopeType", "department")
                    .containsEntry("actualScopeType", "team");
            });
    }

    @Test
    void restore_originalFolderSameScope_succeeds() {
        // 회귀 가드: 정상 케이스 — original folder가 동일 scope에 active로 남아있을 때 restore 성공.
        UUID owner = insertUser("frstcs3@test", "frstcs3");
        UUID scopeA = UUID.randomUUID();
        UUID folderId = insertFakeFolder(owner, "department", scopeA);

        FileItem file = insertFile(folderId, owner, "ChildFileCs3.txt");
        softDeleteFile(file.getId(), folderId);

        FileItem restored = service.restore(file.getId(), owner);

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

    private UUID insertActiveTeam(String name, String normalizedName, UUID createdBy) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO teams(id, name, normalized_name, visibility, created_by, created_at, updated_at) " +
            "VALUES (?, ?, ?, 'private', ?, NOW(), NOW())",
            id, name, normalizedName, createdBy);
        return id;
    }

    private UUID insertFakeFolder(UUID ownerId, String scopeType, UUID scopeId) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update(
            "INSERT INTO folders(id, parent_id, name, normalized_name, slug, owner_id, audit_level, " +
            "scope_type, scope_id, created_at, updated_at) " +
            "VALUES (?, NULL, ?, ?, ?, ?, 'standard', ?, ?, ?, ?)",
            id, "folder-" + id, "folder-" + id, "folder-" + id, ownerId, scopeType, scopeId, now, now
        );
        return id;
    }

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

    private void softDeleteFile(UUID fileId, UUID originalFolderId) {
        jdbc.update(
            "UPDATE files SET deleted_at = NOW(), purge_after = NOW() + INTERVAL '30 days', " +
            "original_folder_id = ? WHERE id = ?",
            originalFolderId, fileId
        );
    }
}
