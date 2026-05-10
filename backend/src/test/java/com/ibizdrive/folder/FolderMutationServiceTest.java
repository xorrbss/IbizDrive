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

import java.time.OffsetDateTime;
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

    // V13/Task 24 (team-centric pivot) — service.create는 child만 허용. root는 workspace lifecycle
    // (Task 16/20)이 만든다. 본 test는 root를 jdbc raw insert로 시뮬레이션 (insertRootFolder).
    // root-via-API 거부 가드는 FolderCreateScopeInheritanceTest가 격리 검증한다.

    @Test
    void create_nested_persistsUnderParent() {
        UUID owner = insertUser("c2@test", "c2");
        Folder parent = insertRootFolder("ParentC2", owner);
        reset(auditService);

        Folder child = service.create(parent.getId(), "ChildC2", owner, "standard", owner);

        assertThat(child.getParentId()).isEqualTo(parent.getId());
        verifyAuditEmitted(AuditEventType.FOLDER_CREATED, child.getId(), owner);
    }

    @Test
    void create_duplicateUnderSameParent_throwsConflict() {
        UUID owner = insertUser("c4@test", "c4");
        Folder parent = insertRootFolder("ParentC4", owner);
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
        Folder parent = insertRootFolder("ParentC5", owner);
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
    void create_softDeletedSameNameUnderSameParent_isAllowed() {
        // V13/Task 24: 원래는 root를 soft-delete 후 같은 이름으로 root 재생성을 검증했으나,
        // root-via-API가 차단되었으므로 child 단위로 동등 의미를 검증한다.
        UUID owner = insertUser("c7@test", "c7");
        Folder parent = insertRootFolder("ParentC7", owner);
        Folder first = service.create(parent.getId(), "Recyclable", owner, "standard", owner);
        softDelete(first.getId());
        reset(auditService);

        Folder second = service.create(parent.getId(), "Recyclable", owner, "standard", owner);
        assertThat(second.getId()).isNotEqualTo(first.getId());
        verifyAuditEmitted(AuditEventType.FOLDER_CREATED, second.getId(), owner);
    }

    @Test
    void create_invalidAuditLevel_throwsBadRequest() {
        UUID owner = insertUser("c8@test", "c8");
        Folder parent = insertRootFolder("ParentC8", owner);
        assertThatThrownBy(() -> service.create(parent.getId(), "X", owner, "loose", owner))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_emptyName_throwsNormalizationException() {
        UUID owner = insertUser("c9@test", "c9");
        Folder parent = insertRootFolder("ParentC9", owner);
        assertThatThrownBy(() -> service.create(parent.getId(), "   ", owner, "standard", owner))
            .isInstanceOf(NormalizationException.class);
    }

    // ──────────────────────────────────────────────────────────────────
    // rename
    // ──────────────────────────────────────────────────────────────────

    @Test
    void rename_happy_persistsAndEmitsAudit() {
        UUID owner = insertUser("r1@test", "r1");
        Folder root = insertRootFolder("RootR1", owner);
        Folder f = service.create(root.getId(), "OldR1", owner, "standard", owner);
        reset(auditService);

        Folder renamed = service.rename(f.getId(), "NewR1", owner);

        assertThat(renamed.getName()).isEqualTo("NewR1");
        assertThat(renamed.getNormalizedName()).isEqualTo("newr1");
        verifyAuditEmitted(AuditEventType.FOLDER_RENAMED, f.getId(), owner);
    }

    @Test
    void rename_sameNormalizedName_isNoOp() {
        UUID owner = insertUser("r2@test", "r2");
        Folder root = insertRootFolder("RootR2", owner);
        Folder f = service.create(root.getId(), "SameR2", owner, "standard", owner);
        reset(auditService);

        Folder result = service.rename(f.getId(), "SameR2", owner);

        assertThat(result.getId()).isEqualTo(f.getId());
        verify(auditService, never()).record(any());
    }

    @Test
    void rename_conflict_throwsConflict() {
        UUID owner = insertUser("r3@test", "r3");
        Folder root = insertRootFolder("RootR3", owner);
        service.create(root.getId(), "AlreadyR3", owner, "standard", owner);
        Folder target = service.create(root.getId(), "TargetR3", owner, "standard", owner);
        reset(auditService);

        assertThatThrownBy(() -> service.rename(target.getId(), "AlreadyR3", owner))
            .isInstanceOf(FolderNameConflictException.class);
        verify(auditService, never()).record(any());
    }

    @Test
    void rename_softDeletedTarget_throwsNotFound() {
        UUID owner = insertUser("r4@test", "r4");
        Folder root = insertRootFolder("RootR4", owner);
        Folder f = service.create(root.getId(), "ToDelR4", owner, "standard", owner);
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
        Folder root = insertRootFolder("RootM1", owner);
        Folder src = service.create(root.getId(), "SrcM1", owner, "standard", owner);
        Folder dst = service.create(root.getId(), "DstM1", owner, "standard", owner);
        Folder child = service.create(src.getId(), "ChildM1", owner, "standard", owner);
        reset(auditService);

        Folder moved = service.move(child.getId(), dst.getId(), owner);

        assertThat(moved.getParentId()).isEqualTo(dst.getId());
        verifyAuditEmitted(AuditEventType.FOLDER_MOVED, child.getId(), owner);
    }

    @Test
    void move_toRoot_throwsBadRequest() {
        // spec §1.3 — root 폴더는 workspace lifecycle만 생성/소유 (V13 partial unique
        // idx_folders_root_per_scope가 scope당 root 1개 강제). 일반 폴더의 null parent 이동은
        // service 진입에서 IllegalArgumentException으로 거부.
        UUID owner = insertUser("m2@test", "m2");
        Folder root = insertRootFolder("RootM2", owner);
        Folder parent = service.create(root.getId(), "ParentM2", owner, "standard", owner);
        Folder child = service.create(parent.getId(), "ChildM2", owner, "standard", owner);
        reset(auditService);

        assertThatThrownBy(() -> service.move(child.getId(), null, owner))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("newParentId is required");
        verify(auditService, never()).record(any());
    }

    @Test
    void move_sameParent_isNoOp() {
        UUID owner = insertUser("m3@test", "m3");
        Folder root = insertRootFolder("RootM3", owner);
        Folder parent = service.create(root.getId(), "ParentM3", owner, "standard", owner);
        Folder child = service.create(parent.getId(), "ChildM3", owner, "standard", owner);
        reset(auditService);

        Folder result = service.move(child.getId(), parent.getId(), owner);

        assertThat(result.getId()).isEqualTo(child.getId());
        verify(auditService, never()).record(any());
    }

    @Test
    void move_intoSelf_throwsBadRequest() {
        UUID owner = insertUser("m4@test", "m4");
        Folder root = insertRootFolder("RootM4", owner);
        Folder f = service.create(root.getId(), "SelfM4", owner, "standard", owner);
        reset(auditService);

        assertThatThrownBy(() -> service.move(f.getId(), f.getId(), owner))
            .isInstanceOf(IllegalArgumentException.class);
        verify(auditService, never()).record(any());
    }

    @Test
    void move_intoOwnDescendant_throwsBadRequest() {
        UUID owner = insertUser("m5@test", "m5");
        Folder root = insertRootFolder("RootM5", owner);
        Folder grandparent = service.create(root.getId(), "GpM5", owner, "standard", owner);
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
        Folder root = insertRootFolder("RootM6", owner);
        Folder src = service.create(root.getId(), "SrcM6", owner, "standard", owner);
        Folder dst = service.create(root.getId(), "DstM6", owner, "standard", owner);
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
        Folder root = insertRootFolder("RootM7", owner);
        Folder src = service.create(root.getId(), "SrcM7", owner, "standard", owner);
        Folder dst = service.create(root.getId(), "DstM7", owner, "standard", owner);
        softDelete(src.getId());
        reset(auditService);

        assertThatThrownBy(() -> service.move(src.getId(), dst.getId(), owner))
            .isInstanceOf(FolderNotFoundException.class);
        verify(auditService, never()).record(any());
    }

    @Test
    void move_softDeletedNewParent_throwsNotFound() {
        UUID owner = insertUser("m8@test", "m8");
        Folder root = insertRootFolder("RootM8", owner);
        Folder src = service.create(root.getId(), "SrcM8", owner, "standard", owner);
        Folder dst = service.create(root.getId(), "DstM8", owner, "standard", owner);
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
        Folder root = insertRootFolder("RootD1", owner);
        Folder parent = service.create(root.getId(), "ParentD1", owner, "standard", owner);
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
        Folder root = insertRootFolder("RootDby1", owner);
        Folder parent = service.create(root.getId(), "ParentDby1", owner, "standard", owner);
        Folder child = service.create(parent.getId(), "ChildDby1", owner, "standard", owner);
        // 후손 파일도 cascade 대상이 되도록 추가 (id만 사용; insert는 jdbc raw로 충분).
        // V13 — files.scope_type/scope_id NOT NULL. child의 scope를 그대로 상속해 invariant 충족.
        UUID grandFile = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO files(id, folder_id, name, normalized_name, owner_id, size_bytes, scope_type, scope_id) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            grandFile, child.getId(), "F.txt", "f.txt", owner, 0L,
            child.getScopeType().dbValue(), child.getScopeId()
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
        Folder root = insertRootFolder("RootRs1", owner);
        Folder folder = service.create(root.getId(), "RestoreRs1", owner, "standard", owner);
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
        Folder root = insertRootFolder("RootRsby1", owner);
        Folder folder = service.create(root.getId(), "RestoreRsby1", owner, "standard", owner);
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
        Folder root = insertRootFolder("RootRs3a", owner);
        Folder parent = service.create(root.getId(), "ParentRs3", owner, "standard", owner);
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
        Folder root = insertRootFolder("RootRs2", owner);
        Folder first = service.create(root.getId(), "RestoreRs2", owner, "standard", owner);
        service.delete(first.getId(), owner);
        service.create(root.getId(), "RestoreRs2", owner, "standard", owner);
        reset(auditService);

        // newName 미지정 + 원본 충돌 → FolderRestoreConflictException (RESTORE_CONFLICT envelope).
        assertThatThrownBy(() -> service.restore(first.getId(), owner))
            .isInstanceOf(FolderRestoreConflictException.class);
        verify(auditService, never()).record(any());
    }

    @Test
    void restore_withNewName_renamesAndEmitsAudit() {
        UUID owner = insertUser("rs3@test", "rs3");
        Folder root = insertRootFolder("RootRs3b", owner);
        Folder f = service.create(root.getId(), "OldFolderRs3", owner, "standard", owner);
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
        Folder root = insertRootFolder("RootRs4", owner);
        Folder f = service.create(root.getId(), "OriginalRs4", owner, "standard", owner);
        service.delete(f.getId(), owner);
        // 새 이름이 충돌하는 활성 폴더 INSERT
        service.create(root.getId(), "TakenRs4", owner, "standard", owner);
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

    /**
     * V13/Task 24 — service.create는 root 생성을 거부 (workspace lifecycle만 root를 만든다).
     * 본 테스트는 child 흐름 검증을 위해 fake workspace root가 필요하므로 raw JDBC로 INSERT한다.
     * scope_type/scope_id는 V13 NOT NULL을 충족하기 위해 임의의 department + random UUID로 채운다 —
     * 본 helper로 만든 root 아래 service.create로 만든 child는 이 scope를 그대로 상속한다 (Task 24).
     * 호출자는 같은 root id를 재사용해 sibling 관계를 만들 수 있다.
     */
    private Folder insertRootFolder(String name, UUID ownerId) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        UUID scopeId = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO folders(id, parent_id, name, normalized_name, slug, owner_id, audit_level, " +
            "scope_type, scope_id, created_at, updated_at) " +
            "VALUES (?, NULL, ?, ?, ?, ?, 'standard', 'department', ?, ?, ?)",
            id, name, name.toLowerCase(), name.toLowerCase(), ownerId, scopeId, now, now
        );
        return folderRepository.findByIdAndDeletedAtIsNull(id).orElseThrow();
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
