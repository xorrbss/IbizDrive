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

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.eq;
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

    private AdminTrashService service;

    @BeforeEach
    void setUp() {
        service = new AdminTrashService(adminRepo, userRepository, folderRepository);
    }

    // ===== 1. empty page =====

    @Test
    void list_returnsEmptyPage_whenBothSourcesEmpty() {
        when(adminRepo.findTrashedFilesAdminPage(any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of());
        when(adminRepo.findTrashedFoldersAdminPage(any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of());

        AdminTrashPage page = service.list(
            new AdminTrashFilters(null, null, null), null, null);

        assertThat(page.items()).isEmpty();
        assertThat(page.nextCursor()).isNull();
    }

    // ===== 2. q escape + wildcard wrap =====

    @Test
    void list_appliesQEscapeAndWildcardWrap() {
        when(adminRepo.findTrashedFilesAdminPage(any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of());
        when(adminRepo.findTrashedFoldersAdminPage(any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of());

        service.list(new AdminTrashFilters("  10%  ", null, null), null, null);

        ArgumentCaptor<String> qCaptor = ArgumentCaptor.forClass(String.class);
        verify(adminRepo).findTrashedFilesAdminPage(
            qCaptor.capture(), any(), any(), any(), anyInt());
        // trim → "10%"; lowercase → "10%"; escape % → "10\%"; wrap → "%10\%%"
        assertThat(qCaptor.getValue()).isEqualTo("%10\\%%");
    }

    // ===== 3. limit clamp =====

    @Test
    void list_clampsLimitTo100() {
        when(adminRepo.findTrashedFilesAdminPage(any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of());
        when(adminRepo.findTrashedFoldersAdminPage(any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of());

        service.list(new AdminTrashFilters(null, null, null), null, 500);

        ArgumentCaptor<Integer> limitCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(adminRepo).findTrashedFilesAdminPage(
            any(), any(), any(), any(), limitCaptor.capture());
        // clamped to 100, fetchSize = limit + 1 = 101
        assertThat(limitCaptor.getValue()).isEqualTo(101);
    }

    // ===== 4. q too long =====

    @Test
    void list_rejectsQTooLong() {
        String tooLong = "a".repeat(201);
        assertThatThrownBy(() -> service.list(
            new AdminTrashFilters(tooLong, null, null), null, null))
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

        when(adminRepo.findTrashedFilesAdminPage(any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of(file));
        when(adminRepo.findTrashedFoldersAdminPage(any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of(folder));
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(
            new User(ownerA, "a@x", "A", null, Role.MEMBER, true, false, OffsetDateTime.now()),
            new User(ownerB, "b@x", "B", null, Role.MEMBER, true, false, OffsetDateTime.now())
        ));
        when(folderRepository.findAllById(anyIterable())).thenReturn(List.of());

        AdminTrashPage page = service.list(
            new AdminTrashFilters(null, null, null), null, null);

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

        when(adminRepo.findTrashedFilesAdminPage(any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of(f1, f2, f3));
        when(adminRepo.findTrashedFoldersAdminPage(any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of());
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(
            new User(owner, "o@x", "O", null, Role.MEMBER, true, false, OffsetDateTime.now())
        ));
        when(folderRepository.findAllById(anyIterable())).thenReturn(List.of());

        AdminTrashPage page = service.list(
            new AdminTrashFilters(null, null, null), null, 2);

        assertThat(page.items()).hasSize(2);
        assertThat(page.nextCursor()).isNotNull().isNotBlank();
    }

    // ===== 7. type=FOLDER skips file repo =====

    @Test
    void list_skipsFiles_whenTypeIsFolder() {
        when(adminRepo.findTrashedFoldersAdminPage(any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of());

        service.list(
            new AdminTrashFilters(null, TrashItemType.FOLDER, null), null, null);

        verify(adminRepo, never()).findTrashedFilesAdminPage(
            any(), any(), any(), any(), anyInt());
        verify(adminRepo).findTrashedFoldersAdminPage(
            any(), any(), any(), any(), anyInt());
    }

    // ===== 8. owner email + parent name attached =====

    @Test
    void list_attachesOwnerEmailAndParentName() {
        UUID fileId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID parent = UUID.randomUUID();
        Instant deletedAt = Instant.parse("2026-05-04T00:00:00Z");

        FileItem file = mockFile(fileId, "report.pdf", deletedAt, owner, parent, 12345L);

        when(adminRepo.findTrashedFilesAdminPage(any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of(file));
        when(adminRepo.findTrashedFoldersAdminPage(any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of());
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(
            new User(owner, "owner@example.com", "Owner Name", null,
                Role.MEMBER, true, false, OffsetDateTime.now())
        ));
        Folder parentFolder = org.mockito.Mockito.mock(Folder.class);
        lenient().when(parentFolder.getId()).thenReturn(parent);
        lenient().when(parentFolder.getName()).thenReturn("Reports");
        when(folderRepository.findAllById(anyIterable())).thenReturn(List.of(parentFolder));

        AdminTrashPage page = service.list(
            new AdminTrashFilters(null, null, null), null, null);

        assertThat(page.items()).hasSize(1);
        AdminTrashItemDto dto = page.items().get(0);
        assertThat(dto.id()).isEqualTo(fileId);
        assertThat(dto.ownerId()).isEqualTo(owner);
        assertThat(dto.ownerEmail()).isEqualTo("owner@example.com");
        assertThat(dto.originalParentId()).isEqualTo(parent);
        assertThat(dto.originalParentName()).isEqualTo("Reports");
        assertThat(dto.sizeBytes()).isEqualTo(12345L);
        assertThat(dto.type()).isEqualTo(TrashItemType.FILE);
    }

    // ===== helpers =====

    private static FileItem mockFile(UUID id, String name, Instant deletedAt,
                                     UUID ownerId, UUID originalFolderId, long sizeBytes) {
        FileItem f = org.mockito.Mockito.mock(FileItem.class);
        lenient().when(f.getId()).thenReturn(id);
        lenient().when(f.getName()).thenReturn(name);
        lenient().when(f.getDeletedAt()).thenReturn(deletedAt);
        lenient().when(f.getPurgeAfter()).thenReturn(deletedAt.plusSeconds(30L * 86400));
        lenient().when(f.getOwnerId()).thenReturn(ownerId);
        lenient().when(f.getOriginalFolderId()).thenReturn(originalFolderId);
        lenient().when(f.getSizeBytes()).thenReturn(sizeBytes);
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
