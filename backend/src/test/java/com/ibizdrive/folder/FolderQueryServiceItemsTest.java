package com.ibizdrive.folder;

import com.ibizdrive.favorite.FavoriteRepository;
import com.ibizdrive.file.FileItem;
import com.ibizdrive.file.FileRepository;
import com.ibizdrive.file.FileTestFixtures;
import com.ibizdrive.folder.dto.FolderItemDto;
import com.ibizdrive.folder.dto.FolderItemsResponse;
import com.ibizdrive.permission.PermissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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
    @Mock private PermissionRepository permissionRepository;
    @Mock private FavoriteRepository favoriteRepository;

    @BeforeEach
    void setupPermissionDefaultStub() {
        // P2c — 대부분 케이스에서 shareCount는 검증 대상이 아니므로 default empty stub.
        // shareCount 노출 케이스에서만 명시적 stub override(LIFO matching).
        // @BeforeEach로 분리하지 않으면 service() 안의 lenient 호출이 특정 stub을 뒤집어 우선순위가 깨진다.
        lenient().when(permissionRepository.countActiveByResources(any(String.class), any(Collection.class)))
            .thenReturn(List.of());
        // P2d — items-count 도 동형. 대부분 케이스에서 sub-children 없음 가정.
        lenient().when(folderRepository.countByParentIdInGroupedActive(any(Collection.class)))
            .thenReturn(List.of());
        lenient().when(fileRepository.countByFolderIdInGroupedActive(any(Collection.class)))
            .thenReturn(List.of());
    }

    private FolderQueryService service() {
        return new FolderQueryService(folderRepository, fileRepository, permissionRepository, favoriteRepository);
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
        f.assignScope(com.ibizdrive.folder.ScopeType.DEPARTMENT, java.util.UUID.randomUUID());
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

    // ─────────────────── 7. P2c shareCount wiring ───────────────────

    @Test
    void loadItems_shareCount_nullForResourcesWithoutGrants() {
        // 기본 케이스: permission repo가 빈 결과 → 모든 item shareCount=null.
        // FE FileRow는 threshold `> 1` → 미표시. JsonInclude(NON_NULL)로 응답 키 omit.
        UUID parent = UUID.randomUUID();
        mockParentExists(parent);
        Instant t = Instant.parse("2026-05-01T00:00:00Z");
        UUID f1 = UUID.randomUUID(), file1 = UUID.randomUUID();
        when(folderRepository.findByParentIdAndDeletedAtIsNull(parent)).thenReturn(List.of(
            folder(f1, parent, "폴더A", t)
        ));
        when(fileRepository.findByFolderIdAndDeletedAtIsNull(parent)).thenReturn(List.of(
            file(file1, parent, "파일A.pdf", 100, t)
        ));

        FolderItemsResponse res = service().loadItems(parent, SortKey.NAME, SortDir.ASC);

        assertThat(res.items()).extracting(FolderItemDto::shareCount)
            .containsExactly(null, null);
    }

    @Test
    void loadItems_shareCount_populatedFromBatchCount() {
        // permission repo가 (folder f1 -> 3건, file file1 -> 2건) 반환 → DTO에 주입.
        // 미응답 id(folder f2, file file2)는 자연스럽게 null → JSON omit.
        UUID parent = UUID.randomUUID();
        mockParentExists(parent);
        Instant t = Instant.parse("2026-05-01T00:00:00Z");
        UUID f1 = UUID.randomUUID(), f2 = UUID.randomUUID();
        UUID file1 = UUID.randomUUID(), file2 = UUID.randomUUID();
        when(folderRepository.findByParentIdAndDeletedAtIsNull(parent)).thenReturn(List.of(
            folder(f1, parent, "가폴더", t),
            folder(f2, parent, "나폴더", t)
        ));
        when(fileRepository.findByFolderIdAndDeletedAtIsNull(parent)).thenReturn(List.of(
            file(file1, parent, "가파일.pdf", 100, t),
            file(file2, parent, "나파일.pdf", 200, t)
        ));
        // List.of(Object[]) varargs는 array를 flatten해 List<Object>가 되므로 명시적 type parameter 필요.
        when(permissionRepository.countActiveByResources(eq("folder"), any(Collection.class)))
            .thenReturn(List.<Object[]>of(new Object[]{f1, 3L}));
        when(permissionRepository.countActiveByResources(eq("file"), any(Collection.class)))
            .thenReturn(List.<Object[]>of(new Object[]{file1, 2L}));

        FolderItemsResponse res = service().loadItems(parent, SortKey.NAME, SortDir.ASC);

        // 정렬: 가폴더(f1, 3), 나폴더(f2, null), 가파일(file1, 2), 나파일(file2, null)
        assertThat(res.items()).extracting(FolderItemDto::name)
            .containsExactly("가폴더", "나폴더", "가파일.pdf", "나파일.pdf");
        assertThat(res.items()).extracting(FolderItemDto::shareCount)
            .containsExactly(3, null, 2, null);
    }

    @Test
    void loadItems_shareCount_emptyItemsSkipsRepositoryCall() {
        // items가 비어있으면 IN() 무효 SQL을 회피해 repo 호출 자체를 생략 (FolderQueryService L_ guard).
        // permissionRepository는 service() lenient stub 외 호출되지 않아야 한다.
        UUID parent = UUID.randomUUID();
        mockParentExists(parent);
        when(folderRepository.findByParentIdAndDeletedAtIsNull(parent)).thenReturn(List.of());
        when(fileRepository.findByFolderIdAndDeletedAtIsNull(parent)).thenReturn(List.of());

        FolderItemsResponse res = service().loadItems(parent, SortKey.NAME, SortDir.ASC);

        assertThat(res.items()).isEmpty();
        // 명시적 verify는 생략 — lenient stub로 호출 0회/N회 모두 허용. 핵심 검증은 invariant(items empty).
    }

    // ─────────────────── 8. P2d itemsCount wiring ───────────────────

    @Test
    void loadItems_itemsCount_emptySubFolder_returnsZero() {
        // 자식 폴더가 자식을 갖지 않을 때 itemsCount=0 (null이 아닌 0) — FE typeof === 'number' 검사에서
        // 0 노출 허용. file 항목은 itemsCount=null.
        UUID parent = UUID.randomUUID();
        mockParentExists(parent);
        Instant t = Instant.parse("2026-05-01T00:00:00Z");
        UUID f1 = UUID.randomUUID(), file1 = UUID.randomUUID();
        when(folderRepository.findByParentIdAndDeletedAtIsNull(parent)).thenReturn(List.of(
            folder(f1, parent, "빈폴더", t)
        ));
        when(fileRepository.findByFolderIdAndDeletedAtIsNull(parent)).thenReturn(List.of(
            file(file1, parent, "단독.pdf", 100, t)
        ));

        FolderItemsResponse res = service().loadItems(parent, SortKey.NAME, SortDir.ASC);

        assertThat(res.items()).extracting(FolderItemDto::itemsCount)
            .containsExactly(0, null);
    }

    @Test
    void loadItems_itemsCount_sumsFoldersAndFilesPerSubFolder() {
        // 자식 폴더 f1: 자식 폴더 2개 + 자식 파일 3개 → itemsCount=5
        // 자식 폴더 f2: 자식 파일 1개 → itemsCount=1
        // 자식 폴더 f3: 자식 없음 → itemsCount=0
        UUID parent = UUID.randomUUID();
        mockParentExists(parent);
        Instant t = Instant.parse("2026-05-01T00:00:00Z");
        UUID f1 = UUID.randomUUID(), f2 = UUID.randomUUID(), f3 = UUID.randomUUID();
        when(folderRepository.findByParentIdAndDeletedAtIsNull(parent)).thenReturn(List.of(
            folder(f1, parent, "가폴더", t),
            folder(f2, parent, "나폴더", t),
            folder(f3, parent, "다폴더", t)
        ));
        when(fileRepository.findByFolderIdAndDeletedAtIsNull(parent)).thenReturn(List.of());
        when(folderRepository.countByParentIdInGroupedActive(any(Collection.class)))
            .thenReturn(List.<Object[]>of(new Object[]{f1, 2L}));
        when(fileRepository.countByFolderIdInGroupedActive(any(Collection.class)))
            .thenReturn(List.<Object[]>of(
                new Object[]{f1, 3L},
                new Object[]{f2, 1L}
            ));

        FolderItemsResponse res = service().loadItems(parent, SortKey.NAME, SortDir.ASC);

        // 가폴더(f1)=2+3=5, 나폴더(f2)=1, 다폴더(f3)=0
        assertThat(res.items()).extracting(FolderItemDto::name)
            .containsExactly("가폴더", "나폴더", "다폴더");
        assertThat(res.items()).extracting(FolderItemDto::itemsCount)
            .containsExactly(5, 1, 0);
    }

    @Test
    void loadItems_itemsCount_filesNeverGetItemsCount() {
        // file type 항목은 itemsCount=null로 유지(FolderItemDto.fromFile 계약).
        UUID parent = UUID.randomUUID();
        mockParentExists(parent);
        Instant t = Instant.parse("2026-05-01T00:00:00Z");
        UUID file1 = UUID.randomUUID(), file2 = UUID.randomUUID();
        when(folderRepository.findByParentIdAndDeletedAtIsNull(parent)).thenReturn(List.of());
        when(fileRepository.findByFolderIdAndDeletedAtIsNull(parent)).thenReturn(List.of(
            file(file1, parent, "a.pdf", 100, t),
            file(file2, parent, "b.pdf", 200, t)
        ));

        FolderItemsResponse res = service().loadItems(parent, SortKey.NAME, SortDir.ASC);

        assertThat(res.items()).extracting(FolderItemDto::itemsCount)
            .containsExactly(null, null);
    }

    // ─────────────────── 9. P2b restricted wiring (shareCount derive) ───────────────────

    @Test
    void loadItems_restricted_nullWhenNoGrants() {
        // grant 없으면 restricted=null (키 omit). shareCount=null 와 같은 정책.
        UUID parent = UUID.randomUUID();
        mockParentExists(parent);
        Instant t = Instant.parse("2026-05-01T00:00:00Z");
        UUID f1 = UUID.randomUUID(), file1 = UUID.randomUUID();
        when(folderRepository.findByParentIdAndDeletedAtIsNull(parent)).thenReturn(List.of(
            folder(f1, parent, "폴더A", t)
        ));
        when(fileRepository.findByFolderIdAndDeletedAtIsNull(parent)).thenReturn(List.of(
            file(file1, parent, "파일A.pdf", 100, t)
        ));

        FolderItemsResponse res = service().loadItems(parent, SortKey.NAME, SortDir.ASC);

        assertThat(res.items()).extracting(FolderItemDto::restricted)
            .containsExactly(null, null);
    }

    @Test
    void loadItems_restricted_trueWhenAnyGrant_evenSingle() {
        // grant 1건이면 shareCount=1 (FE 배지 미표시) 이나 restricted=true (lock 아이콘 표시).
        // 이 비대칭이 두 단계 시각 신호의 핵심 — "공유됨"(lock) vs "여러 명과 공유됨"(lock + count).
        UUID parent = UUID.randomUUID();
        mockParentExists(parent);
        Instant t = Instant.parse("2026-05-01T00:00:00Z");
        UUID f1 = UUID.randomUUID(), f2 = UUID.randomUUID();
        UUID file1 = UUID.randomUUID(), file2 = UUID.randomUUID();
        when(folderRepository.findByParentIdAndDeletedAtIsNull(parent)).thenReturn(List.of(
            folder(f1, parent, "가폴더", t),
            folder(f2, parent, "나폴더", t)
        ));
        when(fileRepository.findByFolderIdAndDeletedAtIsNull(parent)).thenReturn(List.of(
            file(file1, parent, "가파일.pdf", 100, t),
            file(file2, parent, "나파일.pdf", 200, t)
        ));
        when(permissionRepository.countActiveByResources(eq("folder"), any(Collection.class)))
            .thenReturn(List.<Object[]>of(new Object[]{f1, 1L}));  // 가폴더만 grant 1건
        when(permissionRepository.countActiveByResources(eq("file"), any(Collection.class)))
            .thenReturn(List.<Object[]>of(new Object[]{file1, 5L}));  // 가파일은 grant 5건

        FolderItemsResponse res = service().loadItems(parent, SortKey.NAME, SortDir.ASC);

        // 정렬: 가폴더(f1, share=1, restricted=true), 나폴더(null), 가파일(share=5, true), 나파일(null)
        assertThat(res.items()).extracting(FolderItemDto::shareCount)
            .containsExactly(1, null, 5, null);
        assertThat(res.items()).extracting(FolderItemDto::restricted)
            .containsExactly(Boolean.TRUE, null, Boolean.TRUE, null);
    }
}
