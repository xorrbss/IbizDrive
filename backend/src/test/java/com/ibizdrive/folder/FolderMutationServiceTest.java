package com.ibizdrive.folder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.audit.AuditEvent;
import com.ibizdrive.audit.AuditEventType;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.audit.AuditTargetType;
import com.ibizdrive.common.error.ResourceNotFoundException;
import com.ibizdrive.common.normalize.NormalizationException;
import com.ibizdrive.file.FileRepository;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * A4.6 — {@link FolderMutationService} 통합 슬라이스 (V5 schema + JPA + audit emit).
 *
 * <p>실제 Postgres ({@link Testcontainers})에 V5 마이그레이션을 적용하여 {@link FolderRepository}의 사전 충돌 검사,
 * INSERT 시점 V5 unique index 위반의 이중 가드, 그리고 lock query까지 entity 경로로 검증한다.
 *
 * <p>{@link AuditService}는 mock — service 단의 emission contract만 verify (eventType/targetType/targetId/actorId).
 * AuditService 자체의 REQUIRES_NEW + JSONB INSERT는 {@code AuditServiceTest}가 별도 검증.
 *
 * <p>Docker 미가용 환경에서는 {@code disabledWithoutDocker = true}로 자동 스킵.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import(FolderMutationServiceTest.TestConfig.class)
class FolderMutationServiceTest {

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
    @Autowired private AuditService auditService;
    @Autowired private JdbcTemplate jdbc;

    // ──────────────────────────────────────────────────────────────────
    // create
    // ──────────────────────────────────────────────────────────────────

    @Test
    void create_root_persistsAndEmitsAudit() {
        UUID owner = insertUser("c1@test", "c1");
        reset(auditService);

        Folder created = service.create(null, "Reports", owner, "standard", owner);

        assertThat(created.getId()).isNotNull();
        assertThat(created.getParentId()).isNull();
        assertThat(created.getName()).isEqualTo("Reports");
        assertThat(created.getNormalizedName()).isEqualTo("reports");
        assertThat(created.getOwnerId()).isEqualTo(owner);
        assertThat(folderRepository.findByIdAndDeletedAtIsNull(created.getId())).isPresent();

        verifyAuditEmitted(AuditEventType.FOLDER_CREATED, created.getId(), owner);
    }

    @Test
    void create_nested_persistsUnderParent() {
        UUID owner = insertUser("c2@test", "c2");
        Folder parent = service.create(null, "ParentC2", owner, "standard", owner);
        reset(auditService);

        Folder child = service.create(parent.getId(), "ChildC2", owner, "standard", owner);

        assertThat(child.getParentId()).isEqualTo(parent.getId());
        verifyAuditEmitted(AuditEventType.FOLDER_CREATED, child.getId(), owner);
    }

    @Test
    void create_duplicateRoot_throwsConflict() {
        UUID owner = insertUser("c3@test", "c3");
        service.create(null, "DupRoot", owner, "standard", owner);
        reset(auditService);

        assertThatThrownBy(() -> service.create(null, "DupRoot", owner, "standard", owner))
            .isInstanceOf(FolderNameConflictException.class);
        verify(auditService, never()).record(any());
    }

    @Test
    void create_duplicateUnderSameParent_throwsConflict() {
        UUID owner = insertUser("c4@test", "c4");
        Folder parent = service.create(null, "ParentC4", owner, "standard", owner);
        service.create(parent.getId(), "DupChild", owner, "standard", owner);
        reset(auditService);

        assertThatThrownBy(() ->
            service.create(parent.getId(), "DupChild", owner, "standard", owner))
            .isInstanceOf(FolderNameConflictException.class);
        verify(auditService, never()).record(any());
    }

    @Test
    void create_softDeletedParent_throwsNotFound() {
        UUID owner = insertUser("c5@test", "c5");
        Folder parent = service.create(null, "ParentC5", owner, "standard", owner);
        softDelete(parent.getId());
        reset(auditService);

        assertThatThrownBy(() ->
            service.create(parent.getId(), "ChildC5", owner, "standard", owner))
            .isInstanceOf(FolderNotFoundException.class);
        verify(auditService, never()).record(any());
    }

    @Test
    void create_nonExistentParent_throwsNotFound() {
        UUID owner = insertUser("c6@test", "c6");
        UUID ghost = UUID.randomUUID();
        reset(auditService);

        assertThatThrownBy(() -> service.create(ghost, "X", owner, "standard", owner))
            .isInstanceOf(FolderNotFoundException.class);
    }

    @Test
    void create_softDeletedSameName_isAllowed() {
        UUID owner = insertUser("c7@test", "c7");
        Folder first = service.create(null, "Recyclable", owner, "standard", owner);
        softDelete(first.getId());
        reset(auditService);

        Folder second = service.create(null, "Recyclable", owner, "standard", owner);
        assertThat(second.getId()).isNotEqualTo(first.getId());
        verifyAuditEmitted(AuditEventType.FOLDER_CREATED, second.getId(), owner);
    }

    @Test
    void create_invalidAuditLevel_throwsBadRequest() {
        UUID owner = insertUser("c8@test", "c8");
        assertThatThrownBy(() -> service.create(null, "X", owner, "loose", owner))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_emptyName_throwsNormalizationException() {
        UUID owner = insertUser("c9@test", "c9");
        assertThatThrownBy(() -> service.create(null, "   ", owner, "standard", owner))
            .isInstanceOf(NormalizationException.class);
    }

    // ──────────────────────────────────────────────────────────────────
    // rename
    // ──────────────────────────────────────────────────────────────────

    @Test
    void rename_happy_persistsAndEmitsAudit() {
        UUID owner = insertUser("r1@test", "r1");
        Folder f = service.create(null, "OldR1", owner, "standard", owner);
        reset(auditService);

        Folder renamed = service.rename(f.getId(), "NewR1", owner);

        assertThat(renamed.getName()).isEqualTo("NewR1");
        assertThat(renamed.getNormalizedName()).isEqualTo("newr1");
        verifyAuditEmitted(AuditEventType.FOLDER_RENAMED, f.getId(), owner);
    }

    @Test
    void rename_sameNormalizedName_isNoOp() {
        UUID owner = insertUser("r2@test", "r2");
        Folder f = service.create(null, "SameR2", owner, "standard", owner);
        reset(auditService);

        Folder result = service.rename(f.getId(), "SameR2", owner);

        assertThat(result.getId()).isEqualTo(f.getId());
        verify(auditService, never()).record(any());
    }

    @Test
    void rename_conflict_throwsConflict() {
        UUID owner = insertUser("r3@test", "r3");
        service.create(null, "AlreadyR3", owner, "standard", owner);
        Folder target = service.create(null, "TargetR3", owner, "standard", owner);
        reset(auditService);

        assertThatThrownBy(() -> service.rename(target.getId(), "AlreadyR3", owner))
            .isInstanceOf(FolderNameConflictException.class);
        verify(auditService, never()).record(any());
    }

    @Test
    void rename_softDeletedTarget_throwsNotFound() {
        UUID owner = insertUser("r4@test", "r4");
        Folder f = service.create(null, "ToDelR4", owner, "standard", owner);
        softDelete(f.getId());
        reset(auditService);

        assertThatThrownBy(() -> service.rename(f.getId(), "Whatever", owner))
            .isInstanceOf(FolderNotFoundException.class);
        verify(auditService, never()).record(any());
    }

    @Test
    void rename_missingId_throwsNotFound() {
        UUID owner = insertUser("r5@test", "r5");
        assertThatThrownBy(() -> service.rename(UUID.randomUUID(), "X", owner))
            .isInstanceOf(FolderNotFoundException.class);
    }

    // ──────────────────────────────────────────────────────────────────
    // move
    // ──────────────────────────────────────────────────────────────────

    @Test
    void move_happy_changesParentAndEmitsAudit() {
        UUID owner = insertUser("m1@test", "m1");
        Folder src = service.create(null, "SrcM1", owner, "standard", owner);
        Folder dst = service.create(null, "DstM1", owner, "standard", owner);
        Folder child = service.create(src.getId(), "ChildM1", owner, "standard", owner);
        reset(auditService);

        Folder moved = service.move(child.getId(), dst.getId(), owner);

        assertThat(moved.getParentId()).isEqualTo(dst.getId());
        verifyAuditEmitted(AuditEventType.FOLDER_MOVED, child.getId(), owner);
    }

    @Test
    void move_toRoot_setsParentNull() {
        UUID owner = insertUser("m2@test", "m2");
        Folder parent = service.create(null, "ParentM2", owner, "standard", owner);
        Folder child = service.create(parent.getId(), "ChildM2", owner, "standard", owner);
        reset(auditService);

        Folder moved = service.move(child.getId(), null, owner);

        assertThat(moved.getParentId()).isNull();
        verifyAuditEmitted(AuditEventType.FOLDER_MOVED, child.getId(), owner);
    }

    @Test
    void move_sameParent_isNoOp() {
        UUID owner = insertUser("m3@test", "m3");
        Folder parent = service.create(null, "ParentM3", owner, "standard", owner);
        Folder child = service.create(parent.getId(), "ChildM3", owner, "standard", owner);
        reset(auditService);

        Folder result = service.move(child.getId(), parent.getId(), owner);

        assertThat(result.getId()).isEqualTo(child.getId());
        verify(auditService, never()).record(any());
    }

    @Test
    void move_intoSelf_throwsBadRequest() {
        UUID owner = insertUser("m4@test", "m4");
        Folder f = service.create(null, "SelfM4", owner, "standard", owner);
        reset(auditService);

        assertThatThrownBy(() -> service.move(f.getId(), f.getId(), owner))
            .isInstanceOf(IllegalArgumentException.class);
        verify(auditService, never()).record(any());
    }

    @Test
    void move_intoOwnDescendant_throwsBadRequest() {
        UUID owner = insertUser("m5@test", "m5");
        Folder grandparent = service.create(null, "GpM5", owner, "standard", owner);
        Folder parent = service.create(grandparent.getId(), "PM5", owner, "standard", owner);
        Folder child = service.create(parent.getId(), "CM5", owner, "standard", owner);
        reset(auditService);

        assertThatThrownBy(() -> service.move(grandparent.getId(), child.getId(), owner))
            .isInstanceOf(IllegalArgumentException.class);
        verify(auditService, never()).record(any());
    }

    @Test
    void move_conflictAtTargetParent_throwsConflict() {
        UUID owner = insertUser("m6@test", "m6");
        Folder src = service.create(null, "SrcM6", owner, "standard", owner);
        Folder dst = service.create(null, "DstM6", owner, "standard", owner);
        service.create(dst.getId(), "Common", owner, "standard", owner);
        Folder moving = service.create(src.getId(), "Common", owner, "standard", owner);
        reset(auditService);

        assertThatThrownBy(() -> service.move(moving.getId(), dst.getId(), owner))
            .isInstanceOf(FolderNameConflictException.class);
        verify(auditService, never()).record(any());
    }

    @Test
    void move_softDeletedTarget_throwsNotFound() {
        UUID owner = insertUser("m7@test", "m7");
        Folder src = service.create(null, "SrcM7", owner, "standard", owner);
        Folder dst = service.create(null, "DstM7", owner, "standard", owner);
        softDelete(src.getId());
        reset(auditService);

        assertThatThrownBy(() -> service.move(src.getId(), dst.getId(), owner))
            .isInstanceOf(FolderNotFoundException.class);
        verify(auditService, never()).record(any());
    }

    @Test
    void move_softDeletedNewParent_throwsNotFound() {
        UUID owner = insertUser("m8@test", "m8");
        Folder src = service.create(null, "SrcM8", owner, "standard", owner);
        Folder dst = service.create(null, "DstM8", owner, "standard", owner);
        softDelete(dst.getId());
        reset(auditService);

        assertThatThrownBy(() -> service.move(src.getId(), dst.getId(), owner))
            .isInstanceOf(FolderNotFoundException.class);
    }

    // ──────────────────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────────────────

    /** {@link AuditService#record}가 정확히 1회, 기대 eventType/target/actor로 호출됐는지 검증. */
    // -- delete / restore ---------------------------------------------------------------

    @Test
    void delete_activeFolder_softDeletesDescendantsAndEmitsAudit() {
        UUID owner = insertUser("d1@test", "d1");
        Folder parent = service.create(null, "ParentD1", owner, "standard", owner);
        Folder child = service.create(parent.getId(), "ChildD1", owner, "standard", owner);
        reset(auditService);

        service.delete(parent.getId(), owner);

        assertThat(folderRepository.findByIdAndDeletedAtIsNull(parent.getId())).isEmpty();
        assertThat(folderRepository.findByIdAndDeletedAtIsNull(child.getId())).isEmpty();
        assertThat(countDeleted(parent.getId())).isEqualTo(1);
        assertThat(countDeleted(child.getId())).isEqualTo(1);
        assertThat(originalParentId(child.getId())).isEqualTo(parent.getId());
        verifyAuditEmitted(AuditEventType.FOLDER_DELETED, parent.getId(), owner);
    }

    @Test
    void delete_setsDeletedBy_root_and_cascadeDescendants() {
        // V10 — cross-owner 시나리오 핵심 가드:
        //   * root entity (entity-level set)
        //   * cascade 후손 폴더 (softDeleteByIds JPQL)
        //   * cascade 후손 파일 (softDeleteByFolderIds JPQL)
        // 모두 동일 actorId가 deleted_by에 기록되어야 한다.
        UUID owner = insertUser("dby1-owner@test", "dby1-owner");
        UUID admin = insertUser("dby1-admin@test", "dby1-admin");
        Folder parent = service.create(null, "ParentDby1", owner, "standard", owner);
        Folder child = service.create(parent.getId(), "ChildDby1", owner, "standard", owner);
        // 후손 파일도 cascade 대상이 되도록 추가 (id만 사용; insert는 jdbc raw로 충분).
        UUID grandFile = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO files(id, folder_id, name, normalized_name, owner_id, size_bytes, scope_type, scope_id) " +
            "VALUES (?, ?, ?, ?, ?, ?, 'department', ?)",
            grandFile, child.getId(), "F.txt", "f.txt", owner, 0L, java.util.UUID.randomUUID()
        );
        reset(auditService);

        service.delete(parent.getId(), admin);

        assertThat(deletedBy(parent.getId(), "folders")).isEqualTo(admin);
        assertThat(deletedBy(child.getId(), "folders")).isEqualTo(admin);
        assertThat(deletedBy(grandFile, "files")).isEqualTo(admin);
    }

    @Test
    void delete_missingFolder_throwsNotFound() {
        UUID owner = insertUser("d2@test", "d2");
        reset(auditService);

        assertThatThrownBy(() -> service.delete(UUID.randomUUID(), owner))
            .isInstanceOf(FolderNotFoundException.class);
        verify(auditService, never()).record(any());
    }

    @Test
    void restore_softDeletedFolder_reactivatesAndEmitsAudit() {
        UUID owner = insertUser("rs1@test", "rs1");
        Folder folder = service.create(null, "RestoreRs1", owner, "standard", owner);
        service.delete(folder.getId(), owner);
        reset(auditService);

        Folder restored = service.restore(folder.getId(), owner);

        assertThat(restored.getId()).isEqualTo(folder.getId());
        assertThat(folderRepository.findByIdAndDeletedAtIsNull(folder.getId())).isPresent();
        assertThat(countDeleted(folder.getId())).isZero();
        verifyAuditEmitted(AuditEventType.FOLDER_RESTORED, folder.getId(), owner);
    }

    @Test
    void restore_clearsDeletedBy() {
        // V10 — restore 시 deleted_by NULL 클리어 (CHECK 단방향: 활성 row는 deleted_by IS NULL).
        UUID owner = insertUser("rsby1-owner@test", "rsby1-owner");
        UUID admin = insertUser("rsby1-admin@test", "rsby1-admin");
        Folder folder = service.create(null, "RestoreRsby1", owner, "standard", owner);
        service.delete(folder.getId(), admin);
        assertThat(deletedBy(folder.getId(), "folders")).isEqualTo(admin);
        reset(auditService);

        Folder restored = service.restore(folder.getId(), owner);

        assertThat(restored.getDeletedBy()).isNull();
        assertThat(deletedBy(folder.getId(), "folders")).isNull();
    }

    @Test
    void restore_cascadeDeletedChildWithoutActiveParent_throwsNotFound() {
        UUID owner = insertUser("rs3@test", "rs3");
        Folder parent = service.create(null, "ParentRs3", owner, "standard", owner);
        Folder child = service.create(parent.getId(), "ChildRs3", owner, "standard", owner);
        service.delete(parent.getId(), owner);
        reset(auditService);

        assertThatThrownBy(() -> service.restore(child.getId(), owner))
            .isInstanceOf(FolderNotFoundException.class);
        verify(auditService, never()).record(any());
    }

    @Test
    void restore_nameConflict_throwsRestoreConflict() {
        UUID owner = insertUser("rs2@test", "rs2");
        Folder first = service.create(null, "RestoreRs2", owner, "standard", owner);
        service.delete(first.getId(), owner);
        service.create(null, "RestoreRs2", owner, "standard", owner);
        reset(auditService);

        // newName 미지정 + 원본 충돌 → FolderRestoreConflictException (RESTORE_CONFLICT envelope).
        assertThatThrownBy(() -> service.restore(first.getId(), owner))
            .isInstanceOf(FolderRestoreConflictException.class);
        verify(auditService, never()).record(any());
    }

    @Test
    void restore_withNewName_renamesAndEmitsAudit() {
        UUID owner = insertUser("rs3@test", "rs3");
        Folder f = service.create(null, "OldFolderRs3", owner, "standard", owner);
        service.delete(f.getId(), owner);
        reset(auditService);

        Folder restored = service.restore(f.getId(), owner, "NewFolderRs3");

        assertThat(restored.getName()).isEqualTo("NewFolderRs3");
        assertThat(restored.getNormalizedName()).isEqualTo("newfolderrs3");
        assertThat(restored.getDeletedAt()).isNull();
        verifyAuditEmitted(AuditEventType.FOLDER_RESTORED, restored.getId(), owner);
    }

    @Test
    void restore_withNewName_conflict_throwsNameConflict() {
        UUID owner = insertUser("rs4@test", "rs4");
        Folder f = service.create(null, "OriginalRs4", owner, "standard", owner);
        service.delete(f.getId(), owner);
        // 새 이름이 충돌하는 활성 폴더 INSERT
        service.create(null, "TakenRs4", owner, "standard", owner);
        reset(auditService);

        // newName 지정 + 새 이름 충돌 → FolderNameConflictException (RENAME_CONFLICT envelope).
        assertThatThrownBy(() -> service.restore(f.getId(), owner, "TakenRs4"))
            .isInstanceOf(FolderNameConflictException.class);
        verify(auditService, never()).record(any());
    }

    private void verifyAuditEmitted(AuditEventType expectedType, UUID expectedTargetId, UUID expectedActorId) {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService, times(1)).record(captor.capture());
        AuditEvent ev = captor.getValue();
        assertThat(ev.eventType()).isEqualTo(expectedType);
        assertThat(ev.targetType()).isEqualTo(AuditTargetType.FOLDER);
        assertThat(ev.targetId()).isEqualTo(expectedTargetId);
        assertThat(ev.actorId()).isEqualTo(expectedActorId);
    }

    private UUID insertUser(String email, String displayName) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)", id, email, displayName);
        return id;
    }

    private void softDelete(UUID folderId) {
        jdbc.update(
            "UPDATE folders SET deleted_at = NOW(), purge_after = NOW() + INTERVAL '30 days' WHERE id = ?",
            folderId
        );
    }

    /** Mockito {@code any()} import 충돌 회피용 wrapper. */
    private int countDeleted(UUID folderId) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM folders WHERE id = ? AND deleted_at IS NOT NULL AND purge_after IS NOT NULL",
            Integer.class,
            folderId
        );
        return count == null ? 0 : count;
    }

    private UUID originalParentId(UUID folderId) {
        return jdbc.queryForObject(
            "SELECT original_parent_id FROM folders WHERE id = ?",
            (rs, rowNum) -> (UUID) rs.getObject(1),
            folderId
        );
    }

    /**
     * V10 — files/folders의 {@code deleted_by} 컬럼을 raw로 읽는다. cascade JPQL UPDATE는
     * persistence context의 entity instance를 refresh하지 않으므로 entity getter 대신 jdbc로 검증.
     */
    private UUID deletedBy(UUID id, String table) {
        return jdbc.queryForObject(
            "SELECT deleted_by FROM " + table + " WHERE id = ?",
            (rs, rowNum) -> (UUID) rs.getObject(1),
            id
        );
    }

    private static AuditEvent any() {
        return org.mockito.ArgumentMatchers.any(AuditEvent.class);
    }
}
