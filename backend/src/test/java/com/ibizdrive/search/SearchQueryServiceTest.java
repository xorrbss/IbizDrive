package com.ibizdrive.search;

import com.ibizdrive.file.FileItem;
import com.ibizdrive.file.FileRepository;
import com.ibizdrive.file.FileTestFixtures;
import com.ibizdrive.folder.Folder;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.folder.FolderTestFixtures;
import com.ibizdrive.permission.Permission;
import com.ibizdrive.permission.PermissionResolver;
import com.ibizdrive.permission.PermissionService;
import com.ibizdrive.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * A9.2 — {@link SearchQueryService} 단위 테스트. repository 모킹 + role/permission 평가 모킹으로
 * service 내부 (q normalize → minLen 검증 → repo LIKE → merge sort → READ 후처리 → cursor) 흐름 검증.
 */
class SearchQueryServiceTest {

    private static final UUID ACTOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PARENT = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private FileRepository fileRepository;
    private FolderRepository folderRepository;
    private PermissionService permissionService;
    private PermissionResolver permissionResolver;
    private SearchQueryService service;

    @BeforeEach
    void setUp() {
        fileRepository = mock(FileRepository.class);
        folderRepository = mock(FolderRepository.class);
        permissionService = mock(PermissionService.class);
        permissionResolver = mock(PermissionResolver.class);
        service = new SearchQueryService(
            fileRepository, folderRepository, permissionService, permissionResolver
        );
    }

    // ── q normalize + minLength ────────────────────────────────────────

    @Test
    void search_minLengthViolation_throws() {
        assertThatThrownBy(() -> service.search(ACTOR, Role.ADMIN, "a", null, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("INVALID_SEARCH_QUERY");
    }

    @Test
    void search_blankQuery_throws() {
        assertThatThrownBy(() -> service.search(ACTOR, Role.ADMIN, "   ", null, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("INVALID_SEARCH_QUERY");
    }

    @Test
    void search_invalidType_throws() {
        assertThatThrownBy(() -> service.search(ACTOR, Role.ADMIN, "foo", "movie", null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("INVALID_SEARCH_QUERY");
    }

    // ── empty repos + ADMIN short-circuit ──────────────────────────────

    @Test
    void search_emptyRepos_returnsEmptyPage_zeroEstimate() {
        adminCanRead();
        when(fileRepository.searchByNormalizedName(anyString(), any(), any(), anyInt())).thenReturn(List.of());
        when(folderRepository.searchByNormalizedName(anyString(), any(), any(), anyInt())).thenReturn(List.of());
        when(fileRepository.countByNormalizedName(anyString())).thenReturn(0L);
        when(folderRepository.countByNormalizedName(anyString())).thenReturn(0L);

        SearchPage page = service.search(ACTOR, Role.ADMIN, "foo", null, null, null);

        assertThat(page.items()).isEmpty();
        assertThat(page.nextCursor()).isNull();
        assertThat(page.totalEstimate()).isZero();
    }

    // ── type filter ────────────────────────────────────────────────────

    @Test
    void search_typeFile_skipsFolderRepo() {
        adminCanRead();
        when(fileRepository.searchByNormalizedName(anyString(), any(), any(), anyInt())).thenReturn(List.of());
        when(fileRepository.countByNormalizedName(anyString())).thenReturn(0L);

        service.search(ACTOR, Role.ADMIN, "foo", "file", null, null);

        verify(fileRepository).searchByNormalizedName(anyString(), any(), any(), anyInt());
        verify(folderRepository, never()).searchByNormalizedName(anyString(), any(), any(), anyInt());
        verify(folderRepository, never()).countByNormalizedName(anyString());
    }

    @Test
    void search_typeFolder_skipsFileRepo() {
        adminCanRead();
        when(folderRepository.searchByNormalizedName(anyString(), any(), any(), anyInt())).thenReturn(List.of());
        when(folderRepository.countByNormalizedName(anyString())).thenReturn(0L);

        service.search(ACTOR, Role.ADMIN, "foo", "folder", null, null);

        verify(folderRepository).searchByNormalizedName(anyString(), any(), any(), anyInt());
        verify(fileRepository, never()).searchByNormalizedName(anyString(), any(), any(), anyInt());
    }

    // ── merge sort by updatedAt DESC ───────────────────────────────────

    @Test
    void search_typeAll_mergeSortsByUpdatedAtDesc() {
        Instant t0 = Instant.parse("2026-04-30T10:00:00Z");
        UUID fileId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID folderId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

        adminCanRead();
        when(fileRepository.searchByNormalizedName(anyString(), any(), any(), anyInt()))
            .thenReturn(List.of(newFile(fileId, "foo.pdf", t0.minusSeconds(10))));
        when(folderRepository.searchByNormalizedName(anyString(), any(), any(), anyInt()))
            .thenReturn(List.of(newFolder(folderId, "foo-team", t0)));
        when(fileRepository.countByNormalizedName(anyString())).thenReturn(1L);
        when(folderRepository.countByNormalizedName(anyString())).thenReturn(1L);

        SearchPage page = service.search(ACTOR, Role.ADMIN, "foo", null, null, null);

        assertThat(page.items()).hasSize(2);
        assertThat(page.items().get(0).id()).isEqualTo(folderId);
        assertThat(page.items().get(0).type()).isEqualTo("folder");
        assertThat(page.items().get(1).id()).isEqualTo(fileId);
        assertThat(page.items().get(1).type()).isEqualTo("file");
        assertThat(page.totalEstimate()).isEqualTo(2L);
        verifyNoInteractions(permissionResolver);
    }

    // ── MEMBER + READ post-filter ──────────────────────────────────────

    @Test
    void search_member_filtersByResourceLevelGrant() {
        Instant t0 = Instant.parse("2026-04-30T10:00:00Z");
        UUID granted = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID denied = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

        when(permissionService.effectivePermissions(Role.MEMBER))
            .thenReturn(EnumSet.noneOf(Permission.class));
        when(fileRepository.searchByNormalizedName(anyString(), any(), any(), anyInt()))
            .thenReturn(List.of(
                newFile(granted, "ok.pdf", t0),
                newFile(denied, "blocked.pdf", t0.minusSeconds(1))
            ));
        when(folderRepository.searchByNormalizedName(anyString(), any(), any(), anyInt())).thenReturn(List.of());
        when(fileRepository.countByNormalizedName(anyString())).thenReturn(2L);
        when(folderRepository.countByNormalizedName(anyString())).thenReturn(0L);
        when(permissionResolver.isGranted(eq(ACTOR), eq("file"), eq(granted), eq(Permission.READ)))
            .thenReturn(true);
        when(permissionResolver.isGranted(eq(ACTOR), eq("file"), eq(denied), eq(Permission.READ)))
            .thenReturn(false);

        SearchPage page = service.search(ACTOR, Role.MEMBER, "foo", null, null, null);

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).id()).isEqualTo(granted);
        // totalEstimate은 후처리 필터 전 — DB count 그대로 (ADR #33 trade-off)
        assertThat(page.totalEstimate()).isEqualTo(2L);
    }

    // ── cursor round-trip ──────────────────────────────────────────────

    @Test
    void search_hasMore_emitsNextCursorMatchingLastItem() {
        Instant t0 = Instant.parse("2026-04-30T10:00:00Z");
        UUID a = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        UUID b = UUID.fromString("11111111-2222-3333-4444-555555555555");

        adminCanRead();
        when(fileRepository.searchByNormalizedName(anyString(), any(), any(), anyInt()))
            .thenReturn(List.of(
                newFile(a, "foo.pdf", t0),
                newFile(b, "foo2.pdf", t0.minusSeconds(1))
            ));
        when(folderRepository.searchByNormalizedName(anyString(), any(), any(), anyInt())).thenReturn(List.of());
        when(fileRepository.countByNormalizedName(anyString())).thenReturn(2L);
        when(folderRepository.countByNormalizedName(anyString())).thenReturn(0L);

        SearchPage page = service.search(ACTOR, Role.ADMIN, "foo", "file", null, 1);

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).id()).isEqualTo(a);
        assertThat(page.nextCursor()).isNotNull();

        SearchCursor decoded = SearchCursor.decode(page.nextCursor());
        assertThat(decoded).isNotNull();
        assertThat(decoded.updatedAtEpochMs()).isEqualTo(t0.toEpochMilli());
        assertThat(decoded.type()).isEqualTo("file");
        assertThat(decoded.id()).isEqualTo(a);
    }

    @Test
    void search_cursorPage_returnsTotalEstimateMinusOne() {
        Instant t0 = Instant.parse("2026-04-30T10:00:00Z");
        adminCanRead();
        when(fileRepository.searchByNormalizedName(anyString(), any(), any(), anyInt())).thenReturn(List.of());
        when(folderRepository.searchByNormalizedName(anyString(), any(), any(), anyInt())).thenReturn(List.of());

        String cursorWire = SearchCursor.encode(t0.toEpochMilli(), "file", UUID.randomUUID());
        SearchPage page = service.search(ACTOR, Role.ADMIN, "foo", null, cursorWire, null);

        assertThat(page.totalEstimate()).isEqualTo(-1L);
        // count 쿼리 호출 안 됨 — cursor 페이지에서 비용 회피
        verify(fileRepository, never()).countByNormalizedName(anyString());
        verify(folderRepository, never()).countByNormalizedName(anyString());
    }

    @Test
    void search_invalidCursor_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.search(ACTOR, Role.ADMIN, "foo", null, "!!!not-base64!!!", null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ── LIKE escape ────────────────────────────────────────────────────

    @Test
    void escapeLike_escapesBackslashPercentUnderscore() {
        assertThat(SearchQueryService.escapeLike("foo")).isEqualTo("foo");
        assertThat(SearchQueryService.escapeLike("50%")).isEqualTo("50\\%");
        assertThat(SearchQueryService.escapeLike("a_b")).isEqualTo("a\\_b");
        assertThat(SearchQueryService.escapeLike("c\\d")).isEqualTo("c\\\\d");
        assertThat(SearchQueryService.escapeLike("%_\\")).isEqualTo("\\%\\_\\\\");
    }

    // ── helpers ────────────────────────────────────────────────────────

    private void adminCanRead() {
        when(permissionService.effectivePermissions(Role.ADMIN))
            .thenReturn(EnumSet.allOf(Permission.class));
    }

    private static FileItem newFile(UUID id, String name, Instant updatedAt) {
        // FileTestFixtures.trashedFile sets updatedAt = deletedAt parameter — service uses
        // getUpdatedAt only, so this fixture is fine for search service unit tests.
        return FileTestFixtures.trashedFile(id, PARENT, ACTOR, name, updatedAt);
    }

    private static Folder newFolder(UUID id, String name, Instant updatedAt) {
        return FolderTestFixtures.trashedFolder(id, PARENT, ACTOR, name, updatedAt);
    }
}
