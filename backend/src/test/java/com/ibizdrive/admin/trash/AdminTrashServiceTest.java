package com.ibizdrive.admin.trash;

import com.ibizdrive.file.FileItem;
import com.ibizdrive.folder.Folder;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.trash.TrashItemType;
import com.ibizdrive.user.Role;
import com.ibizdrive.user.User;
import com.ibizdrive.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AdminTrashService} 단위 테스트 — Wave 2 T9 Phase 2.
 *
 * <p>검증 범위 (plan §P2.2):
 * <ul>
 *   <li>q 검증/정규화 (length cap, trim/lowercase/LIKE escape/wildcard wrap)</li>
 *   <li>limit clamping (>MAX → MAX, fetchSize = limit+1)</li>
 *   <li>type 필터 라우팅 (FILE/FOLDER 단일 호출)</li>
 *   <li>merge sort (deletedAt DESC, id DESC)</li>
 *   <li>nextCursor 인코딩 (hasMore일 때만)</li>
 *   <li>batch lookup (owner email, originalParent name) — N+1 회피</li>
 * </ul>
 *
 * <p>{@code FileItem}/{@code Folder}는 protected JPA ctor → Mockito mock으로 stub
 * (AdminPermissionServiceTest 패턴 일치).
 */
@ExtendWith(MockitoExtension.class)
class AdminTrashServiceTest {

    @Mock private AdminTrashRepository adminRepo;
    @Mock private UserRepository userRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private com.ibizdrive.file.FileMutationService fileMutationService;
    @Mock private com.ibizdrive.folder.FolderMutationService folderMutationService;
    @Mock private com.ibizdrive.trash.TrashPurgeService trashPurgeService;

    private AdminTrashService service;

    @BeforeEach
    void setUp() {
        service = new AdminTrashService(
            adminRepo, userRepository, folderRepository,
            fileMutationService, folderMutationService, trashPurgeService);
    }

    // ===== 1. empty page =====

    @Test
    void list_returnsEmptyPage_whenBothSourcesEmpty() {
        when(adminRepo.findTrashedFilesAdminPage(any(), any(), any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of());
        when(adminRepo.findTrashedFoldersAdminPage(any(), any(), any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of());

        AdminTrashPage page = service.list(
            new AdminTrashFilters(null, null, null, null, null), null, null);

        assertThat(page.items()).isEmpty();
        assertThat(page.nextCursor()).isNull();
    }

    // ===== 2. q escape + wildcard wrap =====

    @Test
    void list_appliesQEscapeAndWildcardWrap() {
        when(adminRepo.findTrashedFilesAdminPage(any(), any(), any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of());
        when(adminRepo.findTrashedFoldersAdminPage(any(), any(), any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of());

        service.list(new AdminTrashFilters("  10%  ", null, null, null, null), null, null);

        ArgumentCaptor<String> qCaptor = ArgumentCaptor.forClass(String.class);
        verify(adminRepo).findTrashedFilesAdminPage(
            qCaptor.capture(), any(), any(), any(), any(), any(), anyInt());
        // trim → "10%"; lowercase → "10%"; escape % → "10\%"; wrap → "%10\%%"
        assertThat(qCaptor.getValue()).isEqualTo("%10\\%%");
    }

    // ===== 3. limit clamp =====

    @Test
    void list_clampsLimitTo100() {
        when(adminRepo.findTrashedFilesAdminPage(any(), any(), any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of());
        when(adminRepo.findTrashedFoldersAdminPage(any(), any(), any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of());

        service.list(new AdminTrashFilters(null, null, null, null, null), null, 500);

        ArgumentCaptor<Integer> limitCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(adminRepo).findTrashedFilesAdminPage(
            any(), any(), any(), any(), any(), any(), limitCaptor.capture());
        // clamped to 100, fetchSize = limit + 1 = 101
        assertThat(limitCaptor.getValue()).isEqualTo(101);
    }

    // ===== 4. q too long =====

    @Test
    void list_rejectsQTooLong() {
        String tooLong = "a".repeat(201);
        assertThatThrownBy(() -> service.list(
            new AdminTrashFilters(tooLong, null, null, null, null), null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("q");
    }

    // ===== 5. merge sort by deletedAt DESC =====

    @Test
    void list_mergesFilesAndFoldersByDeletedAtDesc() {
        Instant earlier = Instant.parse("2026-05-01T10:00:00Z");
        Instant later = Instant.parse("2026-05-02T10:00:00Z");

        UUID fileId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();

        FileItem file = mockFile(fileId, "a.txt", earlier, ownerA, null, 100L);
        Folder folder = mockFolder(folderId, "B", later, ownerB, null);

        when(adminRepo.findTrashedFilesAdminPage(any(), any(), any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of(file));
        when(adminRepo.findTrashedFoldersAdminPage(any(), any(), any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of(folder));
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(
            new User(ownerA, "a@x", "A", null, Role.MEMBER, true, false, OffsetDateTime.now()),
            new User(ownerB, "b@x", "B", null, Role.MEMBER, true, false, OffsetDateTime.now())
        ));
        when(folderRepository.findAllById(anyIterable())).thenReturn(List.of());

        AdminTrashPage page = service.list(
            new AdminTrashFilters(null, null, null, null, null), null, null);

        assertThat(page.items()).hasSize(2);
        // later deletedAt first (DESC)
        assertThat(page.items().get(0).id()).isEqualTo(folderId);
        assertThat(page.items().get(0).type()).isEqualTo(TrashItemType.FOLDER);
        assertThat(page.items().get(1).id()).isEqualTo(fileId);
        assertThat(page.items().get(1).type()).isEqualTo(TrashItemType.FILE);
    }

    // ===== 6. nextCursor encoding when hasMore =====

    @Test
    void list_encodesNextCursor_whenHasMore() {
        UUID owner = UUID.randomUUID();
        // limit=2 → fetchSize=3 → return 3 files: hasMore=true
        FileItem f1 = mockFile(UUID.randomUUID(), "f1", Instant.parse("2026-05-03T00:00:00Z"), owner, null, 1L);
        FileItem f2 = mockFile(UUID.randomUUID(), "f2", Instant.parse("2026-05-02T00:00:00Z"), owner, null, 2L);
        FileItem f3 = mockFile(UUID.randomUUID(), "f3", Instant.parse("2026-05-01T00:00:00Z"), owner, null, 3L);

        when(adminRepo.findTrashedFilesAdminPage(any(), any(), any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of(f1, f2, f3));
        when(adminRepo.findTrashedFoldersAdminPage(any(), any(), any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of());
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(
            new User(owner, "o@x", "O", null, Role.MEMBER, true, false, OffsetDateTime.now())
        ));
        when(folderRepository.findAllById(anyIterable())).thenReturn(List.of());

        AdminTrashPage page = service.list(
            new AdminTrashFilters(null, null, null, null, null), null, 2);

        assertThat(page.items()).hasSize(2);
        assertThat(page.nextCursor()).isNotNull().isNotBlank();
    }

    // ===== 7. type=FOLDER skips file repo =====

    @Test
    void list_skipsFiles_whenTypeIsFolder() {
        when(adminRepo.findTrashedFoldersAdminPage(any(), any(), any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of());

        service.list(
            new AdminTrashFilters(null, TrashItemType.FOLDER, null, null, null), null, null);

        verify(adminRepo, never()).findTrashedFilesAdminPage(
            any(), any(), any(), any(), any(), any(), anyInt());
        verify(adminRepo).findTrashedFoldersAdminPage(
            any(), any(), any(), any(), any(), any(), anyInt());
    }

    // ===== 8. owner email + parent name attached =====

    @Test
    void list_attachesOwnerEmailAndParentName() {
        UUID fileId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID parent = UUID.randomUUID();
        Instant deletedAt = Instant.parse("2026-05-04T00:00:00Z");

        FileItem file = mockFile(fileId, "report.pdf", deletedAt, owner, parent, 12345L);

        when(adminRepo.findTrashedFilesAdminPage(any(), any(), any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of(file));
        when(adminRepo.findTrashedFoldersAdminPage(any(), any(), any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of());
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(
            new User(owner, "owner@example.com", "Owner Name", null,
                Role.MEMBER, true, false, OffsetDateTime.now())
        ));
        Folder parentFolder = org.mockito.Mockito.mock(Folder.class);
        lenient().when(parentFolder.getId()).thenReturn(parent);
        lenient().when(parentFolder.getName()).thenReturn("Reports");
        when(folderRepository.findAllById(anyIterable())).thenReturn(List.of(parentFolder));
        // root 부모 → CTE는 single segment "/Reports"를 반환.
        // singletonList — `List.of(Object[]...)`는 varargs로 풀려 `List<Object>`로 추론되어
        // 호출부에서 `Object[]` cast가 깨진다. element 1개일 때만 발생하는 함정.
        when(adminRepo.findFolderAncestorPaths(any(Collection.class))).thenReturn(
            Collections.singletonList(new Object[]{parent, "/Reports"}));

        AdminTrashPage page = service.list(
            new AdminTrashFilters(null, null, null, null, null), null, null);

        assertThat(page.items()).hasSize(1);
        AdminTrashItemDto dto = page.items().get(0);
        assertThat(dto.id()).isEqualTo(fileId);
        assertThat(dto.ownerId()).isEqualTo(owner);
        assertThat(dto.ownerEmail()).isEqualTo("owner@example.com");
        assertThat(dto.originalParentId()).isEqualTo(parent);
        assertThat(dto.originalParentName()).isEqualTo("Reports");
        assertThat(dto.originalParentPath()).isEqualTo("/Reports");
        assertThat(dto.sizeBytes()).isEqualTo(12345L);
        assertThat(dto.type()).isEqualTo(TrashItemType.FILE);
    }

    // ===== 9. 날짜 범위 pass-through =====

    @Test
    void list_passesDeletedRangeBoundsToRepo() {
        Instant from = Instant.parse("2026-05-01T00:00:00Z");
        Instant to = Instant.parse("2026-05-08T00:00:00Z");
        when(adminRepo.findTrashedFilesAdminPage(any(), any(), any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of());
        when(adminRepo.findTrashedFoldersAdminPage(any(), any(), any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of());

        service.list(new AdminTrashFilters(null, null, null, from, to), null, null);

        ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> toCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(adminRepo).findTrashedFilesAdminPage(
            any(), any(), fromCaptor.capture(), toCaptor.capture(), any(), any(), anyInt());
        assertThat(fromCaptor.getValue()).isEqualTo(from);
        assertThat(toCaptor.getValue()).isEqualTo(to);
    }

    // ===== 10. 거꾸로/동일 날짜 범위 거부 =====

    @Test
    void list_rejectsInvertedDeletedRange() {
        Instant from = Instant.parse("2026-05-08T00:00:00Z");
        Instant to = Instant.parse("2026-05-01T00:00:00Z");
        assertThatThrownBy(() -> service.list(
            new AdminTrashFilters(null, null, null, from, to), null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("deletedFrom");
    }

    @Test
    void list_rejectsEqualDeletedRange() {
        Instant same = Instant.parse("2026-05-01T00:00:00Z");
        assertThatThrownBy(() -> service.list(
            new AdminTrashFilters(null, null, null, same, same), null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ===== 11. V10 — deletedBy enrichment (cross-owner) =====

    @Test
    void list_attachesDeletedByEmail_whenDeleterDifferentFromOwner() {
        UUID fileId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID deleter = UUID.randomUUID();
        Instant deletedAt = Instant.parse("2026-05-04T00:00:00Z");

        FileItem file = mockFile(fileId, "doc.pdf", deletedAt, owner, null, 100L, deleter);

        when(adminRepo.findTrashedFilesAdminPage(any(), any(), any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of(file));
        when(adminRepo.findTrashedFoldersAdminPage(any(), any(), any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of());
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(
            new User(owner, "owner@x", "Owner", null, Role.MEMBER, true, false, OffsetDateTime.now()),
            new User(deleter, "admin@x", "Admin", null, Role.ADMIN, true, false, OffsetDateTime.now())
        ));
        when(folderRepository.findAllById(anyIterable())).thenReturn(List.of());

        AdminTrashPage page = service.list(
            new AdminTrashFilters(null, null, null, null, null), null, null);

        assertThat(page.items()).hasSize(1);
        AdminTrashItemDto dto = page.items().get(0);
        assertThat(dto.ownerId()).isEqualTo(owner);
        assertThat(dto.ownerEmail()).isEqualTo("owner@x");
        assertThat(dto.deletedById()).isEqualTo(deleter);
        assertThat(dto.deletedByEmail()).isEqualTo("admin@x");
    }

    // ===== 12. V10 — NULL deletedBy (V10 이전 row 또는 hard-delete된 deleter) =====

    @Test
    void list_deletedByNull_yieldsNullEmail() {
        UUID fileId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        Instant deletedAt = Instant.parse("2026-05-04T00:00:00Z");

        FileItem file = mockFile(fileId, "legacy.pdf", deletedAt, owner, null, 1L, null);

        when(adminRepo.findTrashedFilesAdminPage(any(), any(), any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of(file));
        when(adminRepo.findTrashedFoldersAdminPage(any(), any(), any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of());
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(
            new User(owner, "owner@x", "Owner", null, Role.MEMBER, true, false, OffsetDateTime.now())
        ));
        when(folderRepository.findAllById(anyIterable())).thenReturn(List.of());

        AdminTrashPage page = service.list(
            new AdminTrashFilters(null, null, null, null, null), null, null);

        AdminTrashItemDto dto = page.items().get(0);
        assertThat(dto.deletedById()).isNull();
        assertThat(dto.deletedByEmail()).isNull();
    }

    // ===== 13. V10 — owner+deleter 합류해도 single batch lookup =====

    @Test
    void list_unionsOwnerAndDeleterIdsIntoSingleBatchLookup() {
        UUID fileId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID deleter = UUID.randomUUID();
        FileItem file = mockFile(fileId, "x", Instant.parse("2026-05-04T00:00:00Z"),
            owner, null, 1L, deleter);

        when(adminRepo.findTrashedFilesAdminPage(any(), any(), any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of(file));
        when(adminRepo.findTrashedFoldersAdminPage(any(), any(), any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of());
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of());
        when(folderRepository.findAllById(anyIterable())).thenReturn(List.of());

        service.list(new AdminTrashFilters(null, null, null, null, null), null, null);

        // userRepository.findAllById는 owner+deleter를 합친 단일 호출이어야 한다 (N+1 회피).
        ArgumentCaptor<Iterable<UUID>> idsCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(userRepository).findAllById(idsCaptor.capture());
        assertThat(idsCaptor.getValue()).containsExactlyInAnyOrder(owner, deleter);
    }

    // ===== 14. folder subtree size — Wave 2 T9 follow-up =====

    @Test
    void list_populatesFolderSubtreeSize() {
        UUID folderId1 = UUID.randomUUID();
        UUID folderId2 = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        // 동일 deletedAt이면 id DESC 정렬 → 결과 순서가 id에 의존하므로 시간 차이를 두어 안정화.
        Folder f1 = mockFolder(folderId1, "Reports",
            Instant.parse("2026-05-04T00:00:00Z"), owner, null);
        Folder f2 = mockFolder(folderId2, "Empty",
            Instant.parse("2026-05-03T00:00:00Z"), owner, null);

        when(adminRepo.findTrashedFilesAdminPage(any(), any(), any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of());
        when(adminRepo.findTrashedFoldersAdminPage(any(), any(), any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of(f1, f2));
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(
            new User(owner, "o@x", "O", null, Role.MEMBER, true, false, OffsetDateTime.now())
        ));
        when(folderRepository.findAllById(anyIterable())).thenReturn(List.of());
        // CTE 결과: f1 subtree = 1500B, f2 = 0 (빈 폴더). Postgres SUM은 NUMERIC이지만
        // service에서 Number.longValue()로 받으므로 Long stub로 충분.
        when(adminRepo.findFolderSubtreeSizes(any(Collection.class))).thenReturn(List.of(
            new Object[]{folderId1, 1500L},
            new Object[]{folderId2, 0L}
        ));

        AdminTrashPage page = service.list(
            new AdminTrashFilters(null, null, null, null, null), null, null);

        Map<UUID, Long> sizesById = new HashMap<>();
        for (AdminTrashItemDto dto : page.items()) sizesById.put(dto.id(), dto.sizeBytes());
        assertThat(sizesById).containsEntry(folderId1, 1500L);
        assertThat(sizesById).containsEntry(folderId2, 0L);
    }

    // ===== 15. originalParentPath — full-path-resolve follow-up =====

    @Test
    void list_populatesOriginalParentPath_deepHierarchy() {
        UUID fileId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID parent = UUID.randomUUID();
        Instant deletedAt = Instant.parse("2026-05-04T00:00:00Z");

        FileItem file = mockFile(fileId, "spec.pdf", deletedAt, owner, parent, 100L);

        when(adminRepo.findTrashedFilesAdminPage(any(), any(), any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of(file));
        when(adminRepo.findTrashedFoldersAdminPage(any(), any(), any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of());
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(
            new User(owner, "o@x", "O", null, Role.MEMBER, true, false, OffsetDateTime.now())
        ));
        Folder parentFolder = org.mockito.Mockito.mock(Folder.class);
        lenient().when(parentFolder.getId()).thenReturn(parent);
        lenient().when(parentFolder.getName()).thenReturn("문서");
        when(folderRepository.findAllById(anyIterable())).thenReturn(List.of(parentFolder));
        // CTE가 root까지 거슬러 누적한 다중 segment 경로를 반환.
        when(adminRepo.findFolderAncestorPaths(any(Collection.class))).thenReturn(
            Collections.singletonList(new Object[]{parent, "/회사/팀A/문서"}));

        AdminTrashPage page = service.list(
            new AdminTrashFilters(null, null, null, null, null), null, null);

        AdminTrashItemDto dto = page.items().get(0);
        assertThat(dto.originalParentPath()).isEqualTo("/회사/팀A/문서");
        // path 계산이 실패해도 fallback 가능하도록 single-segment name은 별도 유지.
        assertThat(dto.originalParentName()).isEqualTo("문서");
    }

    @Test
    void list_skipsAncestorPathQuery_whenNoParents() {
        // 모든 항목이 root였던 경우 — parent set이 비어 있어 CTE 호출 자체가 없어야 한다
        // (Postgres IN ()는 문법 오류이며, subtree-size 동일 short-circuit 정합).
        UUID owner = UUID.randomUUID();
        FileItem rootFile = mockFile(UUID.randomUUID(), "f", Instant.parse("2026-05-01T00:00:00Z"),
            owner, null, 1L);

        when(adminRepo.findTrashedFilesAdminPage(any(), any(), any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of(rootFile));
        when(adminRepo.findTrashedFoldersAdminPage(any(), any(), any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of());
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(
            new User(owner, "o@x", "O", null, Role.MEMBER, true, false, OffsetDateTime.now())
        ));
        when(folderRepository.findAllById(anyIterable())).thenReturn(List.of());

        AdminTrashPage page = service.list(
            new AdminTrashFilters(null, null, null, null, null), null, null);

        verify(adminRepo, never()).findFolderAncestorPaths(any());
        assertThat(page.items().get(0).originalParentPath()).isNull();
    }

    @Test
    void list_skipsSubtreeQuery_whenNoFolders() {
        UUID owner = UUID.randomUUID();
        FileItem file = mockFile(UUID.randomUUID(), "f", Instant.parse("2026-05-01T00:00:00Z"),
            owner, null, 1L);

        when(adminRepo.findTrashedFilesAdminPage(any(), any(), any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of(file));
        when(adminRepo.findTrashedFoldersAdminPage(any(), any(), any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of());
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(
            new User(owner, "o@x", "O", null, Role.MEMBER, true, false, OffsetDateTime.now())
        ));
        when(folderRepository.findAllById(anyIterable())).thenReturn(List.of());

        service.list(new AdminTrashFilters(null, null, null, null, null), null, null);

        // Postgres IN ()는 문법 오류 — 빈 입력에서 query 호출 자체가 일어나면 안 된다.
        verify(adminRepo, never()).findFolderSubtreeSizes(any());
    }

    // ===== helpers =====

    private static FileItem mockFile(UUID id, String name, Instant deletedAt,
                                     UUID ownerId, UUID originalFolderId, long sizeBytes) {
        return mockFile(id, name, deletedAt, ownerId, originalFolderId, sizeBytes, null);
    }

    private static FileItem mockFile(UUID id, String name, Instant deletedAt,
                                     UUID ownerId, UUID originalFolderId, long sizeBytes,
                                     UUID deletedBy) {
        FileItem f = org.mockito.Mockito.mock(FileItem.class);
        lenient().when(f.getId()).thenReturn(id);
        lenient().when(f.getName()).thenReturn(name);
        lenient().when(f.getDeletedAt()).thenReturn(deletedAt);
        lenient().when(f.getPurgeAfter()).thenReturn(deletedAt.plusSeconds(30L * 86400));
        lenient().when(f.getOwnerId()).thenReturn(ownerId);
        lenient().when(f.getOriginalFolderId()).thenReturn(originalFolderId);
        lenient().when(f.getSizeBytes()).thenReturn(sizeBytes);
        lenient().when(f.getDeletedBy()).thenReturn(deletedBy);
        return f;
    }

    private static Folder mockFolder(UUID id, String name, Instant deletedAt,
                                     UUID ownerId, UUID originalParentId) {
        Folder fd = org.mockito.Mockito.mock(Folder.class);
        lenient().when(fd.getId()).thenReturn(id);
        lenient().when(fd.getName()).thenReturn(name);
        lenient().when(fd.getDeletedAt()).thenReturn(deletedAt);
        lenient().when(fd.getPurgeAfter()).thenReturn(deletedAt.plusSeconds(30L * 86400));
        lenient().when(fd.getOwnerId()).thenReturn(ownerId);
        lenient().when(fd.getOriginalParentId()).thenReturn(originalParentId);
        return fd;
    }
}
