package com.ibizdrive.folder;

import com.ibizdrive.file.FileItem;
import com.ibizdrive.file.FileRepository;
import com.ibizdrive.file.FileTestFixtures;
import com.ibizdrive.folder.dto.FolderItemDto;
import com.ibizdrive.folder.dto.FolderItemsResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Phase B P1 — {@link FolderQueryService#loadItems(UUID, SortKey, SortDir)} unit test.
 *
 * <p>커버리지 (6 case):
 * <ol>
 *   <li>NAME asc — 폴더 그룹 → 파일 그룹, 각 그룹 내 가나다순</li>
 *   <li>NAME desc — 그룹 순서 유지(폴더 먼저), 각 그룹 내 역순</li>
 *   <li>UPDATED_AT desc — 그룹 순서 유지, 각 그룹 최신순</li>
 *   <li>SIZE desc — 폴더 그룹은 size 컬럼 부재 → name asc fallback, 파일 그룹은 size 큰 순</li>
 *   <li>빈 폴더 → 빈 items</li>
 *   <li>parent 폴더 soft-deleted → {@link FolderNotFoundException}</li>
 * </ol>
 *
 * <p>FolderQueryServiceTest 패턴 답습 — repository mock, service에 합본/정렬 로직만 검증.
 * 권한 게이트는 controller 레이어 SpEL 책임이므로 본 service test 비대상.
 */
@ExtendWith(MockitoExtension.class)
class FolderQueryServiceItemsTest {

    @Mock private FolderRepository folderRepository;
    @Mock private FileRepository fileRepository;

    private FolderQueryService service() {
        return new FolderQueryService(folderRepository, fileRepository);
    }

    private static Folder folder(UUID id, UUID parentId, String name, Instant updatedAt) {
        Folder f = new Folder();
        f.setId(id);
        f.setParentId(parentId);
        f.setName(name);
        f.setSlug(name);
        f.setNormalizedName(name);
        f.setOwnerId(UUID.randomUUID());
        f.setAuditLevel("standard");
        f.setCreatedAt(updatedAt);
        f.setUpdatedAt(updatedAt);
        return f;
    }

    private static FileItem file(UUID id, UUID folderId, String name, long size, Instant updatedAt) {
        return FileTestFixtures.activeFile(id, folderId, UUID.randomUUID(), name, size, updatedAt);
    }

    private void mockParentExists(UUID parentId) {
        Folder parent = folder(parentId, null, "부모", Instant.parse("2026-01-01T00:00:00Z"));
        when(folderRepository.findByIdAndDeletedAtIsNull(parentId)).thenReturn(Optional.of(parent));
    }

    // ─────────────────── 1. NAME asc ───────────────────

    @Test
    void loadItems_nameAsc_foldersFirstThenFiles_alphabetical() {
        UUID parent = UUID.randomUUID();
        mockParentExists(parent);
        Instant t = Instant.parse("2026-05-01T00:00:00Z");
        UUID f1 = UUID.randomUUID(), f2 = UUID.randomUUID();
        UUID file1 = UUID.randomUUID(), file2 = UUID.randomUUID();
        when(folderRepository.findByParentIdAndDeletedAtIsNull(parent)).thenReturn(List.of(
            folder(f2, parent, "나폴더", t),
            folder(f1, parent, "가폴더", t)
        ));
        when(fileRepository.findByFolderIdAndDeletedAtIsNull(parent)).thenReturn(List.of(
            file(file2, parent, "보고서.pdf", 100, t),
            file(file1, parent, "계약서.pdf", 200, t)
        ));

        FolderItemsResponse res = service().loadItems(parent, SortKey.NAME, SortDir.ASC);

        assertThat(res.items()).extracting(FolderItemDto::type)
            .containsExactly("folder", "folder", "file", "file");
        assertThat(res.items()).extracting(FolderItemDto::name)
            .containsExactly("가폴더", "나폴더", "계약서.pdf", "보고서.pdf");
    }

    // ─────────────────── 2. NAME desc ───────────────────

    @Test
    void loadItems_nameDesc_foldersFirstThenFiles_reverseAlphabetical() {
        UUID parent = UUID.randomUUID();
        mockParentExists(parent);
        Instant t = Instant.parse("2026-05-01T00:00:00Z");
        when(folderRepository.findByParentIdAndDeletedAtIsNull(parent)).thenReturn(List.of(
            folder(UUID.randomUUID(), parent, "가폴더", t),
            folder(UUID.randomUUID(), parent, "나폴더", t)
        ));
        when(fileRepository.findByFolderIdAndDeletedAtIsNull(parent)).thenReturn(List.of(
            file(UUID.randomUUID(), parent, "계약서.pdf", 200, t),
            file(UUID.randomUUID(), parent, "보고서.pdf", 100, t)
        ));

        FolderItemsResponse res = service().loadItems(parent, SortKey.NAME, SortDir.DESC);

        assertThat(res.items()).extracting(FolderItemDto::type)
            .containsExactly("folder", "folder", "file", "file");
        assertThat(res.items()).extracting(FolderItemDto::name)
            .containsExactly("나폴더", "가폴더", "보고서.pdf", "계약서.pdf");
    }

    // ─────────────────── 3. UPDATED_AT desc ───────────────────

    @Test
    void loadItems_updatedAtDesc_foldersFirstThenFiles_newestFirstWithinGroup() {
        UUID parent = UUID.randomUUID();
        mockParentExists(parent);
        Instant older = Instant.parse("2026-04-01T00:00:00Z");
        Instant newer = Instant.parse("2026-05-01T00:00:00Z");
        UUID fOld = UUID.randomUUID(), fNew = UUID.randomUUID();
        UUID fileOld = UUID.randomUUID(), fileNew = UUID.randomUUID();
        when(folderRepository.findByParentIdAndDeletedAtIsNull(parent)).thenReturn(List.of(
            folder(fOld, parent, "오래된폴더", older),
            folder(fNew, parent, "최신폴더", newer)
        ));
        when(fileRepository.findByFolderIdAndDeletedAtIsNull(parent)).thenReturn(List.of(
            file(fileOld, parent, "old.pdf", 100, older),
            file(fileNew, parent, "new.pdf", 100, newer)
        ));

        FolderItemsResponse res = service().loadItems(parent, SortKey.UPDATED_AT, SortDir.DESC);

        assertThat(res.items()).extracting(FolderItemDto::id)
            .containsExactly(fNew, fOld, fileNew, fileOld);
    }

    // ─────────────────── 4. SIZE desc ───────────────────

    @Test
    void loadItems_sizeDesc_folderGroupFallsBackToNameAsc_fileGroupBySizeDesc() {
        UUID parent = UUID.randomUUID();
        mockParentExists(parent);
        Instant t = Instant.parse("2026-05-01T00:00:00Z");
        UUID smallFile = UUID.randomUUID(), bigFile = UUID.randomUUID();
        when(folderRepository.findByParentIdAndDeletedAtIsNull(parent)).thenReturn(List.of(
            folder(UUID.randomUUID(), parent, "나폴더", t),
            folder(UUID.randomUUID(), parent, "가폴더", t)
        ));
        when(fileRepository.findByFolderIdAndDeletedAtIsNull(parent)).thenReturn(List.of(
            file(smallFile, parent, "small.pdf", 100, t),
            file(bigFile, parent, "big.pdf", 9999, t)
        ));

        FolderItemsResponse res = service().loadItems(parent, SortKey.SIZE, SortDir.DESC);

        assertThat(res.items()).extracting(FolderItemDto::name)
            .containsExactly("가폴더", "나폴더", "big.pdf", "small.pdf");
    }

    // ─────────────────── 5. 빈 폴더 ───────────────────

    @Test
    void loadItems_emptyFolder_returnsEmptyItems() {
        UUID parent = UUID.randomUUID();
        mockParentExists(parent);
        when(folderRepository.findByParentIdAndDeletedAtIsNull(parent)).thenReturn(List.of());
        when(fileRepository.findByFolderIdAndDeletedAtIsNull(parent)).thenReturn(List.of());

        FolderItemsResponse res = service().loadItems(parent, SortKey.NAME, SortDir.ASC);

        assertThat(res.items()).isEmpty();
    }

    // ─────────────────── 6. parent soft-deleted ───────────────────

    @Test
    void loadItems_throwsFolderNotFoundWhenParentMissingOrSoftDeleted() {
        UUID parent = UUID.randomUUID();
        when(folderRepository.findByIdAndDeletedAtIsNull(parent)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().loadItems(parent, SortKey.NAME, SortDir.ASC))
            .isInstanceOf(FolderNotFoundException.class);
    }
}
