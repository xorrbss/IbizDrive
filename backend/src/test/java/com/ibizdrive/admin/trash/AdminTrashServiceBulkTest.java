package com.ibizdrive.admin.trash;

import com.ibizdrive.file.FileMutationService;
import com.ibizdrive.file.FileNameConflictException;
import com.ibizdrive.file.FileNotFoundException;
import com.ibizdrive.file.FileRepository;
import com.ibizdrive.folder.FolderMutationService;
import com.ibizdrive.folder.FolderNotFoundException;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.folder.FolderRestoreConflictException;
import com.ibizdrive.trash.TrashItemType;
import com.ibizdrive.trash.TrashPurgeService;
import com.ibizdrive.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link AdminTrashService#bulk} 단위 테스트 — Wave 2 T9 follow-up (spec §3).
 *
 * <p>검증 범위:
 * <ul>
 *   <li>action 검증 (restore/purge 외 → IAE → 글로벌 핸들러 400)</li>
 *   <li>cap 검증 (0 또는 201+ → IAE)</li>
 *   <li>부분 실패 모델: 단건 service 예외 → failed[]에 wire 문자열로 누적</li>
 *   <li>fan-out: file → fileMutationService.restore / TrashPurgeService.purgeFile,
 *       folder → folderMutationService.restore / TrashPurgeService.purgeFolder</li>
 *   <li>idempotency: 동일 항목 두 번 → 두 번째는 단건 service 결과에 따라 succeeded/failed</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AdminTrashServiceBulkTest {

    @Mock private AdminTrashRepository adminRepo;
    @Mock private UserRepository userRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private FileRepository fileRepository;
    @Mock private FileMutationService fileMutationService;
    @Mock private FolderMutationService folderMutationService;
    @Mock private TrashPurgeService trashPurgeService;

    private AdminTrashService service;

    @BeforeEach
    void setUp() {
        service = new AdminTrashService(
            adminRepo, userRepository, folderRepository,
            fileMutationService, folderMutationService, trashPurgeService);
    }

    private static AdminTrashBulkRequestDto.Item file(UUID id) {
        return new AdminTrashBulkRequestDto.Item("file", id);
    }

    private static AdminTrashBulkRequestDto.Item folder(UUID id) {
        return new AdminTrashBulkRequestDto.Item("folder", id);
    }

    // ===== 1. action validation =====

    @Test
    void bulk_rejectsInvalidAction() {
        UUID actor = UUID.randomUUID();
        assertThatThrownBy(() -> service.bulk("delete", List.of(file(UUID.randomUUID())), actor))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("action");
    }

    @Test
    void bulk_rejectsNullAction() {
        UUID actor = UUID.randomUUID();
        assertThatThrownBy(() -> service.bulk(null, List.of(file(UUID.randomUUID())), actor))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ===== 2. cap validation =====

    @Test
    void bulk_rejectsEmptyItems() {
        UUID actor = UUID.randomUUID();
        assertThatThrownBy(() -> service.bulk("restore", List.of(), actor))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("1..200");
    }

    @Test
    void bulk_rejectsNullItems() {
        UUID actor = UUID.randomUUID();
        assertThatThrownBy(() -> service.bulk("restore", null, actor))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void bulk_rejectsOverCap() {
        UUID actor = UUID.randomUUID();
        // 201 items
        List<AdminTrashBulkRequestDto.Item> items = new java.util.ArrayList<>();
        for (int i = 0; i < 201; i++) items.add(file(UUID.randomUUID()));
        assertThatThrownBy(() -> service.bulk("restore", items, actor))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("1..200");
    }

    // ===== 3. restore fan-out =====

    @Test
    void bulk_restore_dispatchesFileAndFolder() {
        UUID actor = UUID.randomUUID();
        UUID f1 = UUID.randomUUID();
        UUID fd1 = UUID.randomUUID();

        AdminTrashBulkResponseDto res = service.bulk("restore",
            List.of(file(f1), folder(fd1)), actor);

        verify(fileMutationService).restore(eq(f1), eq(actor));
        verify(folderMutationService).restore(eq(fd1), eq(actor));
        verify(trashPurgeService, never()).purgeFile(any(), any());
        verify(trashPurgeService, never()).purgeFolder(any(), any());

        assertThat(res.succeeded()).hasSize(2);
        assertThat(res.failed()).isEmpty();
    }

    // ===== 4. purge fan-out =====

    @Test
    void bulk_purge_dispatchesFileAndFolder() {
        UUID actor = UUID.randomUUID();
        UUID f1 = UUID.randomUUID();
        UUID fd1 = UUID.randomUUID();

        AdminTrashBulkResponseDto res = service.bulk("purge",
            List.of(file(f1), folder(fd1)), actor);

        verify(trashPurgeService).purgeFile(eq(f1), eq(actor));
        verify(trashPurgeService).purgeFolder(eq(fd1), eq(actor));
        verify(fileMutationService, never()).restore(any(), any());
        verify(folderMutationService, never()).restore(any(), any());

        assertThat(res.succeeded()).hasSize(2);
        assertThat(res.failed()).isEmpty();
    }

    // ===== 5. partial failure — NOT_FOUND =====

    @Test
    void bulk_restore_mapsNotFoundToFailed() {
        UUID actor = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        UUID present = UUID.randomUUID();

        doThrow(new FileNotFoundException("not found: " + missing))
            .when(fileMutationService).restore(eq(missing), any());

        AdminTrashBulkResponseDto res = service.bulk("restore",
            List.of(file(missing), file(present)), actor);

        assertThat(res.succeeded()).hasSize(1);
        assertThat(res.succeeded().get(0).id()).isEqualTo(present);
        assertThat(res.failed()).hasSize(1);
        assertThat(res.failed().get(0).id()).isEqualTo(missing);
        assertThat(res.failed().get(0).error()).isEqualTo("NOT_FOUND");
    }

    @Test
    void bulk_purge_mapsFolderNotFoundToFailed() {
        UUID actor = UUID.randomUUID();
        UUID missing = UUID.randomUUID();

        doThrow(new FolderNotFoundException("not found: " + missing))
            .when(trashPurgeService).purgeFolder(eq(missing), any());

        AdminTrashBulkResponseDto res = service.bulk("purge",
            List.of(folder(missing)), actor);

        assertThat(res.succeeded()).isEmpty();
        assertThat(res.failed()).hasSize(1);
        assertThat(res.failed().get(0).error()).isEqualTo("NOT_FOUND");
    }

    // ===== 6. partial failure — NAME_CONFLICT =====

    @Test
    void bulk_restore_mapsFileNameConflictToFailed() {
        UUID actor = UUID.randomUUID();
        UUID id = UUID.randomUUID();

        doThrow(new FileNameConflictException("conflict"))
            .when(fileMutationService).restore(eq(id), any());

        AdminTrashBulkResponseDto res = service.bulk("restore",
            List.of(file(id)), actor);

        assertThat(res.failed()).hasSize(1);
        assertThat(res.failed().get(0).error()).isEqualTo("NAME_CONFLICT");
    }

    @Test
    void bulk_restore_mapsFolderRestoreConflictToFailed() {
        UUID actor = UUID.randomUUID();
        UUID id = UUID.randomUUID();

        doThrow(new FolderRestoreConflictException("conflict"))
            .when(folderMutationService).restore(eq(id), any());

        AdminTrashBulkResponseDto res = service.bulk("restore",
            List.of(folder(id)), actor);

        assertThat(res.failed()).hasSize(1);
        assertThat(res.failed().get(0).error()).isEqualTo("NAME_CONFLICT");
    }

    @Test
    void bulk_restore_mapsFolderScopeMismatchToFailed() {
        // Plan E T4 — SCOPE_MISMATCH는 NAME_CONFLICT와 별개 wire code로 노출되어야 한다.
        // T3 reviewer 발견: 이전 catch 사이트는 둘을 NAME_CONFLICT로 silent misclassify.
        UUID actor = UUID.randomUUID();
        UUID id = UUID.randomUUID();

        doThrow(new FolderRestoreConflictException(
                FolderRestoreConflictException.Reason.SCOPE_MISMATCH, id, "scope mismatch"))
            .when(folderMutationService).restore(eq(id), any());

        AdminTrashBulkResponseDto res = service.bulk("restore",
            List.of(folder(id)), actor);

        assertThat(res.failed()).hasSize(1);
        assertThat(res.failed().get(0).error()).isEqualTo("SCOPE_MISMATCH");
    }

    // ===== 7. invalid item shape =====

    @Test
    void bulk_invalidTypeString_yieldsFailed() {
        UUID actor = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        AdminTrashBulkRequestDto.Item bad = new AdminTrashBulkRequestDto.Item("bogus", id);

        AdminTrashBulkResponseDto res = service.bulk("restore", List.of(bad), actor);

        assertThat(res.failed()).hasSize(1);
        assertThat(res.failed().get(0).error()).isEqualTo("INVALID_TYPE");
        verify(fileMutationService, never()).restore(any(), any());
        verify(folderMutationService, never()).restore(any(), any());
    }

    @Test
    void bulk_nullTypeOrId_yieldsFailedInvalidItem() {
        UUID actor = UUID.randomUUID();
        AdminTrashBulkRequestDto.Item bad = new AdminTrashBulkRequestDto.Item(null, UUID.randomUUID());

        AdminTrashBulkResponseDto res = service.bulk("restore", List.of(bad), actor);

        assertThat(res.failed()).hasSize(1);
        assertThat(res.failed().get(0).error()).isEqualTo("INVALID_ITEM");
    }

    // ===== 8. cap boundary 200 OK =====

    @Test
    void bulk_at200ItemsCap_runs() {
        UUID actor = UUID.randomUUID();
        List<AdminTrashBulkRequestDto.Item> items = new java.util.ArrayList<>();
        for (int i = 0; i < 200; i++) items.add(file(UUID.randomUUID()));

        AdminTrashBulkResponseDto res = service.bulk("restore", items, actor);

        assertThat(res.succeeded()).hasSize(200);
        verify(fileMutationService, org.mockito.Mockito.times(200)).restore(any(), eq(actor));
    }

    // ===== 9. type 매핑 =====

    @Test
    void bulk_itemType_inResponseUsesEnum() {
        UUID actor = UUID.randomUUID();
        UUID id = UUID.randomUUID();

        AdminTrashBulkResponseDto res = service.bulk("restore", List.of(file(id)), actor);

        assertThat(res.succeeded()).hasSize(1);
        assertThat(res.succeeded().get(0).type()).isEqualTo(TrashItemType.FILE);
    }
}
