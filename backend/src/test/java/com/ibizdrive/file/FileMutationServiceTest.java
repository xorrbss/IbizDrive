package com.ibizdrive.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.audit.AuditEvent;
import com.ibizdrive.audit.AuditEventType;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.audit.AuditTargetType;
import com.ibizdrive.common.normalize.NormalizationException;
import com.ibizdrive.folder.FolderNotFoundException;
import com.ibizdrive.folder.FolderRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * A4.8 — {@link FileMutationService} 통합 슬라이스. {@link FolderMutationServiceTest}와 동일한 패턴:
 * 실제 Postgres ({@link Testcontainers}) + V5 마이그레이션, {@link AuditService}만 mock해서
 * service 단의 emission contract와 entity 경로를 검증.
 *
 * <p>파일 INSERT는 service에 helper가 없으므로(create는 A4.9 tus 업로드에 종속) {@link FileRepository}
 * 직접 save로 fixture 구성. folder/owner FK는 {@code jdbc.update}로 minimal row만 채운다.
 *
 * <p>Docker 미가용 환경에서는 {@code disabledWithoutDocker = true}로 자동 스킵 — folder 테스트와 동일.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import(FileMutationServiceTest.TestConfig.class)
class FileMutationServiceTest {

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
                                                      ObjectMapper mapper) {
            return new FileMutationService(fileRepo, folderRepo, audit, mapper,
                new com.ibizdrive.trash.TrashRetentionProperties(30),
                mock(com.ibizdrive.folder.CrossWorkspaceMoveService.class));
        }
    }

    @Autowired private FileMutationService service;
    @Autowired private FileRepository fileRepository;
    @Autowired private AuditService auditService;
    @Autowired private JdbcTemplate jdbc;

    // ──────────────────────────────────────────────────────────────────
    // rename
    // ──────────────────────────────────────────────────────────────────

    @Test
    void rename_happy_persistsAndEmitsAudit() {
        UUID owner = insertUser("fr1@test", "fr1");
        UUID folder = insertFolder(owner, "FolderFR1");
        FileItem f = insertFile(folder, owner, "OldFR1.txt");
        reset(auditService);

        FileItem renamed = service.rename(f.getId(), "NewFR1.txt", owner);

        assertThat(renamed.getName()).isEqualTo("NewFR1.txt");
        assertThat(renamed.getNormalizedName()).isEqualTo("newfr1.txt");
        verifyAuditEmitted(AuditEventType.FILE_RENAMED, f.getId(), owner);
    }

    @Test
    void rename_sameNormalizedName_isNoOp() {
        UUID owner = insertUser("fr2@test", "fr2");
        UUID folder = insertFolder(owner, "FolderFR2");
        FileItem f = insertFile(folder, owner, "SameFR2.txt");
        reset(auditService);

        FileItem result = service.rename(f.getId(), "SameFR2.txt", owner);

        assertThat(result.getId()).isEqualTo(f.getId());
        verify(auditService, never()).record(any());
    }

    @Test
    void rename_conflict_throwsConflict() {
        UUID owner = insertUser("fr3@test", "fr3");
        UUID folder = insertFolder(owner, "FolderFR3");
        insertFile(folder, owner, "AlreadyFR3.txt");
        FileItem target = insertFile(folder, owner, "TargetFR3.txt");
        reset(auditService);

        assertThatThrownBy(() -> service.rename(target.getId(), "AlreadyFR3.txt", owner))
            .isInstanceOf(FileNameConflictException.class);
        verify(auditService, never()).record(any());
    }

    @Test
    void rename_softDeletedTarget_throwsNotFound() {
        UUID owner = insertUser("fr4@test", "fr4");
        UUID folder = insertFolder(owner, "FolderFR4");
        FileItem f = insertFile(folder, owner, "ToDelFR4.txt");
        softDeleteFile(f.getId(), folder);
        reset(auditService);

        assertThatThrownBy(() -> service.rename(f.getId(), "Whatever.txt", owner))
            .isInstanceOf(FileNotFoundException.class);
        verify(auditService, never()).record(any());
    }

    @Test
    void rename_missingId_throwsNotFound() {
        UUID owner = insertUser("fr5@test", "fr5");
        assertThatThrownBy(() -> service.rename(UUID.randomUUID(), "X.txt", owner))
            .isInstanceOf(FileNotFoundException.class);
    }

    @Test
    void rename_emptyName_throwsNormalizationException() {
        UUID owner = insertUser("fr6@test", "fr6");
        UUID folder = insertFolder(owner, "FolderFR6");
        FileItem f = insertFile(folder, owner, "OkFR6.txt");
        assertThatThrownBy(() -> service.rename(f.getId(), "   ", owner))
            .isInstanceOf(NormalizationException.class);
    }

    // ──────────────────────────────────────────────────────────────────
    // move
    // ──────────────────────────────────────────────────────────────────

    @Test
    void move_happy_changesFolderAndEmitsAudit() {
        UUID owner = insertUser("fm1@test", "fm1");
        UUID src = insertFolder(owner, "SrcFM1");
        UUID dst = insertFolder(owner, "DstFM1");
        FileItem f = insertFile(src, owner, "MoveFM1.txt");
        reset(auditService);

        FileItem moved = service.move(f.getId(), dst, owner);

        assertThat(moved.getFolderId()).isEqualTo(dst);
        verifyAuditEmitted(AuditEventType.FILE_MOVED, f.getId(), owner);
    }

    @Test
    void move_sameFolder_isNoOp() {
        UUID owner = insertUser("fm2@test", "fm2");
        UUID folder = insertFolder(owner, "FolderFM2");
        FileItem f = insertFile(folder, owner, "MoveFM2.txt");
        reset(auditService);

        FileItem result = service.move(f.getId(), folder, owner);

        assertThat(result.getId()).isEqualTo(f.getId());
        verify(auditService, never()).record(any());
    }

    @Test
    void move_nullTargetFolder_throwsBadRequest() {
        UUID owner = insertUser("fm3@test", "fm3");
        UUID folder = insertFolder(owner, "FolderFM3");
        FileItem f = insertFile(folder, owner, "FM3.txt");
        assertThatThrownBy(() -> service.move(f.getId(), null, owner))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void move_softDeletedTargetFolder_throwsFolderNotFound() {
        UUID owner = insertUser("fm4@test", "fm4");
        UUID src = insertFolder(owner, "SrcFM4");
        UUID dst = insertFolder(owner, "DstFM4");
        softDeleteFolder(dst);
        FileItem f = insertFile(src, owner, "FM4.txt");
        reset(auditService);

        assertThatThrownBy(() -> service.move(f.getId(), dst, owner))
            .isInstanceOf(FolderNotFoundException.class);
        verify(auditService, never()).record(any());
    }

    @Test
    void move_conflictAtTargetFolder_throwsConflict() {
        UUID owner = insertUser("fm5@test", "fm5");
        UUID src = insertFolder(owner, "SrcFM5");
        UUID dst = insertFolder(owner, "DstFM5");
        insertFile(dst, owner, "Common.txt");
        FileItem moving = insertFile(src, owner, "Common.txt");
        reset(auditService);

        assertThatThrownBy(() -> service.move(moving.getId(), dst, owner))
            .isInstanceOf(FileNameConflictException.class);
        verify(auditService, never()).record(any());
    }

    @Test
    void move_softDeletedFile_throwsFileNotFound() {
        UUID owner = insertUser("fm6@test", "fm6");
        UUID src = insertFolder(owner, "SrcFM6");
        UUID dst = insertFolder(owner, "DstFM6");
        FileItem f = insertFile(src, owner, "FM6.txt");
        softDeleteFile(f.getId(), src);
        reset(auditService);

        assertThatThrownBy(() -> service.move(f.getId(), dst, owner))
            .isInstanceOf(FileNotFoundException.class);
    }

    // ──────────────────────────────────────────────────────────────────
    // delete
    // ──────────────────────────────────────────────────────────────────

    @Test
    void delete_happy_softDeletesAndEmitsAudit() {
        UUID owner = insertUser("fd1@test", "fd1");
        UUID folder = insertFolder(owner, "FolderFD1");
        FileItem f = insertFile(folder, owner, "FD1.txt");
        reset(auditService);

        FileItem deleted = service.delete(f.getId(), owner);

        assertThat(deleted.getDeletedAt()).isNotNull();
        assertThat(deleted.getPurgeAfter()).isNotNull();
        assertThat(deleted.getOriginalFolderId()).isEqualTo(folder);
        // active 검색에서 누락
        assertThat(fileRepository.findByIdAndDeletedAtIsNull(f.getId())).isEmpty();
        verifyAuditEmitted(AuditEventType.FILE_DELETED, f.getId(), owner);
    }

    @Test
    void delete_alreadyDeleted_throwsNotFound() {
        UUID owner = insertUser("fd2@test", "fd2");
        UUID folder = insertFolder(owner, "FolderFD2");
        FileItem f = insertFile(folder, owner, "FD2.txt");
        softDeleteFile(f.getId(), folder);
        reset(auditService);

        assertThatThrownBy(() -> service.delete(f.getId(), owner))
            .isInstanceOf(FileNotFoundException.class);
        verify(auditService, never()).record(any());
    }

    @Test
    void delete_missingId_throwsNotFound() {
        UUID owner = insertUser("fd3@test", "fd3");
        assertThatThrownBy(() -> service.delete(UUID.randomUUID(), owner))
            .isInstanceOf(FileNotFoundException.class);
    }

    @Test
    void delete_setsDeletedBy_actorMayDifferFromOwner() {
        // V10 — cross-owner 시나리오 핵심 가드: admin(actor)이 owner와 다른 사용자가 소유한 파일을
        // 삭제할 때 deleted_by에 actor가 기록되어야 admin trash UI에서 deleter를 식별할 수 있다.
        UUID owner = insertUser("fdb1@test", "fdb1-owner");
        UUID admin = insertUser("fdb1-admin@test", "fdb1-admin");
        UUID folder = insertFolder(owner, "FolderFDB1");
        FileItem f = insertFile(folder, owner, "FDB1.txt");
        reset(auditService);

        FileItem deleted = service.delete(f.getId(), admin);

        assertThat(deleted.getDeletedBy()).isEqualTo(admin);
        assertThat(deleted.getOwnerId()).isEqualTo(owner);
    }

    @Test
    void delete_freesNameForReuse() {
        UUID owner = insertUser("fd4@test", "fd4");
        UUID folder = insertFolder(owner, "FolderFD4");
        FileItem first = insertFile(folder, owner, "Recyclable.txt");
        service.delete(first.getId(), owner);
        reset(auditService);

        // 같은 이름의 새 파일 INSERT 가능 — partial unique index가 deleted_at IS NULL만 강제.
        FileItem second = insertFile(folder, owner, "Recyclable.txt");
        assertThat(second.getId()).isNotEqualTo(first.getId());
    }

    // ──────────────────────────────────────────────────────────────────
    // restore
    // ──────────────────────────────────────────────────────────────────

    @Test
    void restore_happy_clearsTombstoneAndEmitsAudit() {
        UUID owner = insertUser("fs1@test", "fs1");
        UUID folder = insertFolder(owner, "FolderFS1");
        FileItem f = insertFile(folder, owner, "FS1.txt");
        service.delete(f.getId(), owner);
        reset(auditService);

        FileItem restored = service.restore(f.getId(), owner);

        assertThat(restored.getDeletedAt()).isNull();
        assertThat(restored.getPurgeAfter()).isNull();
        assertThat(restored.getOriginalFolderId()).isNull();
        assertThat(restored.getFolderId()).isEqualTo(folder);
        verifyAuditEmitted(AuditEventType.FILE_RESTORED, f.getId(), owner);
    }

    @Test
    void restore_clearsDeletedBy() {
        // V10 — restore 시 deleted_by NULL 클리어 (CHECK 단방향: 활성 row는 deleted_by IS NULL).
        UUID owner = insertUser("fsb1@test", "fsb1-owner");
        UUID admin = insertUser("fsb1-admin@test", "fsb1-admin");
        UUID folder = insertFolder(owner, "FolderFSB1");
        FileItem f = insertFile(folder, owner, "FSB1.txt");
        FileItem deleted = service.delete(f.getId(), admin);
        assertThat(deleted.getDeletedBy()).isEqualTo(admin);
        reset(auditService);

        FileItem restored = service.restore(f.getId(), owner);

        assertThat(restored.getDeletedBy()).isNull();
    }

    @Test
    void restore_activeFile_throwsNotFound() {
        UUID owner = insertUser("fs2@test", "fs2");
        UUID folder = insertFolder(owner, "FolderFS2");
        FileItem f = insertFile(folder, owner, "FS2.txt");
        reset(auditService);

        assertThatThrownBy(() -> service.restore(f.getId(), owner))
            .isInstanceOf(FileNotFoundException.class);
        verify(auditService, never()).record(any());
    }

    @Test
    void restore_originalFolderSoftDeleted_throwsNotFound() {
        UUID owner = insertUser("fs3@test", "fs3");
        UUID folder = insertFolder(owner, "FolderFS3");
        FileItem f = insertFile(folder, owner, "FS3.txt");
        service.delete(f.getId(), owner);
        softDeleteFolder(folder);
        reset(auditService);

        assertThatThrownBy(() -> service.restore(f.getId(), owner))
            .isInstanceOf(FileNotFoundException.class);
        verify(auditService, never()).record(any());
    }

    @Test
    void restore_conflictAtOriginalFolder_throwsRestoreConflict() {
        UUID owner = insertUser("fs4@test", "fs4");
        UUID folder = insertFolder(owner, "FolderFS4");
        FileItem f = insertFile(folder, owner, "Common.txt");
        service.delete(f.getId(), owner);
        // 같은 이름의 새 파일이 그 사이 INSERT 됨
        insertFile(folder, owner, "Common.txt");
        reset(auditService);

        // newName 미지정 + 원본 충돌 → FileRestoreConflictException (RESTORE_CONFLICT envelope, v1.x).
        assertThatThrownBy(() -> service.restore(f.getId(), owner))
            .isInstanceOf(FileRestoreConflictException.class);
        verify(auditService, never()).record(any());
    }

    @Test
    void restore_withNewName_renamesAndClearsTombstone() {
        UUID owner = insertUser("fs6@test", "fs6");
        UUID folder = insertFolder(owner, "FolderFS6");
        FileItem f = insertFile(folder, owner, "OldName.txt");
        service.delete(f.getId(), owner);
        reset(auditService);

        FileItem restored = service.restore(f.getId(), owner, "NewName.txt");

        assertThat(restored.getName()).isEqualTo("NewName.txt");
        assertThat(restored.getNormalizedName()).isEqualTo("newname.txt");
        assertThat(restored.getDeletedAt()).isNull();
        verifyAuditEmitted(AuditEventType.FILE_RESTORED, restored.getId(), owner);
    }

    @Test
    void restore_withNewName_conflict_throwsNameConflict() {
        UUID owner = insertUser("fs7@test", "fs7");
        UUID folder = insertFolder(owner, "FolderFS7");
        FileItem f = insertFile(folder, owner, "OriginalA.txt");
        service.delete(f.getId(), owner);
        // 새 이름이 충돌하는 활성 파일 INSERT
        insertFile(folder, owner, "Taken.txt");
        reset(auditService);

        // newName 지정 + 새 이름 충돌 → FileNameConflictException (RENAME_CONFLICT envelope).
        assertThatThrownBy(() -> service.restore(f.getId(), owner, "Taken.txt"))
            .isInstanceOf(FileNameConflictException.class);
        verify(auditService, never()).record(any());
    }

    @Test
    void restore_missingId_throwsNotFound() {
        UUID owner = insertUser("fs5@test", "fs5");
        assertThatThrownBy(() -> service.restore(UUID.randomUUID(), owner))
            .isInstanceOf(FileNotFoundException.class);
    }

    // ──────────────────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────────────────

    private void verifyAuditEmitted(AuditEventType expectedType, UUID expectedTargetId, UUID expectedActorId) {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService, times(1)).record(captor.capture());
        AuditEvent ev = captor.getValue();
        assertThat(ev.eventType()).isEqualTo(expectedType);
        assertThat(ev.targetType()).isEqualTo(AuditTargetType.FILE);
        assertThat(ev.targetId()).isEqualTo(expectedTargetId);
        assertThat(ev.actorId()).isEqualTo(expectedActorId);
    }

    private UUID insertUser(String email, String displayName) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)", id, email, displayName);
        return id;
    }

    /**
     * Folder FK용 minimal row. service.create 우회 — file 테스트는 folder mutation 검증 대상이 아님.
     * V5 schema의 {@code folders} 모든 NOT NULL 컬럼 (name/normalized_name/slug/owner_id/audit_level)을 채운다.
     */
    /** V13 — folders.scope_type/scope_id NOT NULL. fixture root는 fake department scope를 가진다. */
    private UUID insertFolder(UUID ownerId, String name) {
        UUID id = UUID.randomUUID();
        String normalized = name.toLowerCase();
        jdbc.update(
            "INSERT INTO folders(id, parent_id, name, normalized_name, slug, owner_id, audit_level, " +
            "scope_type, scope_id) " +
            "VALUES (?, NULL, ?, ?, ?, ?, 'standard', 'department', ?)",
            id, name, normalized, normalized, ownerId, UUID.randomUUID()
        );
        return id;
    }

    /**
     * {@link FileRepository#save}로 fixture INSERT — V5 NOT NULL 컬럼 모두 채움.
     * V13 — files.scope_type/scope_id 도 부모 folder의 scope를 상속해 채운다 (spec §1.2 invariant).
     */
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
        f.assignScope(com.ibizdrive.folder.ScopeType.fromDb((String) scope[0]), (UUID) scope[1]);
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

    private void softDeleteFolder(UUID folderId) {
        jdbc.update(
            "UPDATE folders SET deleted_at = NOW(), purge_after = NOW() + INTERVAL '30 days' WHERE id = ?",
            folderId
        );
    }

    /** Mockito {@code any()} import 충돌 회피용 wrapper. */
    private static AuditEvent any() {
        return org.mockito.ArgumentMatchers.any(AuditEvent.class);
    }
}
