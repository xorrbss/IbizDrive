package com.ibizdrive.trash;

import com.ibizdrive.file.FileItem;
import com.ibizdrive.file.FileRepository;
import com.ibizdrive.file.FileTestFixtures;
import com.ibizdrive.folder.Folder;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.folder.FolderTestFixtures;
import com.ibizdrive.folder.ScopeType;
import com.ibizdrive.permission.Permission;
import com.ibizdrive.permission.PermissionResolver;
import com.ibizdrive.permission.PermissionService;
import com.ibizdrive.permission.WorkspaceMembershipResolver;
import com.ibizdrive.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * A8.1 — {@link TrashQueryService} 단위 테스트. repository 모킹 + role/permission 평가 모킹으로
 * service 내부의 (cursor decode → page query → merge sort → permission post-filter → nextCursor) 흐름을 검증.
 *
 * <p>본 테스트는 기존 list flow 회귀를 보호 — Plan E T2 신규 보장(scope param 필수, 멤버십 가드,
 * scope-filtered repo 호출)는 별도 {@link TrashQueryServiceScopeTest} 책임.
 */
class TrashQueryServiceTest {

    private static final UUID ACTOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PARENT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID SCOPE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private FileRepository fileRepository;
    private FolderRepository folderRepository;
    private PermissionService permissionService;
    private PermissionResolver permissionResolver;
    private WorkspaceMembershipResolver membershipResolver;
    private TrashQueryService service;

    @BeforeEach
    void setUp() {
        fileRepository = mock(FileRepository.class);
        folderRepository = mock(FolderRepository.class);
        permissionService = mock(PermissionService.class);
        permissionResolver = mock(PermissionResolver.class);
        membershipResolver = mock(WorkspaceMembershipResolver.class);
        service = new TrashQueryService(
            fileRepository, folderRepository, permissionService, permissionResolver, membershipResolver
        );
    }

    // ── empty + delegation ─────────────────────────────────────────────

    @Test
    void list_emptyRepos_returnsEmptyPage() {
        when(permissionService.effectivePermissions(Role.ADMIN))
            .thenReturn(EnumSet.allOf(Permission.class));
        when(fileRepository.findTrashedPageByScope(any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of());
        when(folderRepository.findTrashedPageByScope(any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of());

        TrashPage page = service.list(ACTOR, Role.ADMIN, ScopeType.DEPARTMENT, SCOPE_ID, null, null, null);

        assertThat(page.items()).isEmpty();
        assertThat(page.nextCursor()).isNull();
    }

    // ── type filter ────────────────────────────────────────────────────

    @Test
    void list_typeFile_skipsFolderRepo() {
        when(permissionService.effectivePermissions(Role.ADMIN))
            .thenReturn(EnumSet.allOf(Permission.class));
        when(fileRepository.findTrashedPageByScope(any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of());

        service.list(ACTOR, Role.ADMIN, ScopeType.DEPARTMENT, SCOPE_ID, null, TrashItemType.FILE, null);

        verify(fileRepository).findTrashedPageByScope(any(), any(), any(), any(), anyInt());
        verifyNoInteractions(folderRepository);
    }

    @Test
    void list_typeFolder_skipsFileRepo() {
        when(permissionService.effectivePermissions(Role.ADMIN))
            .thenReturn(EnumSet.allOf(Permission.class));
        when(folderRepository.findTrashedPageByScope(any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of());

        service.list(ACTOR, Role.ADMIN, ScopeType.DEPARTMENT, SCOPE_ID, null, TrashItemType.FOLDER, null);

        verify(folderRepository).findTrashedPageByScope(any(), any(), any(), any(), anyInt());
        verifyNoInteractions(fileRepository);
    }

    // ── ADMIN sees all (ROLE path short-circuit) ───────────────────────

    @Test
    void list_admin_returnsAllItemsSortedByDeletedAtDesc() {
        Instant t0 = Instant.parse("2026-04-30T10:00:00Z");
        UUID fileId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID folderId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

        when(permissionService.effectivePermissions(Role.ADMIN))
            .thenReturn(EnumSet.allOf(Permission.class));
        when(fileRepository.findTrashedPageByScope(any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of(newFile(fileId, "doc.pdf", t0.minusSeconds(10))));
        when(folderRepository.findTrashedPageByScope(any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of(newFolder(folderId, "team", t0)));

        TrashPage page = service.list(ACTOR, Role.ADMIN, ScopeType.DEPARTMENT, SCOPE_ID, null, null, null);

        assertThat(page.items()).hasSize(2);
        // folder가 더 최근 → 먼저 등장
        assertThat(page.items().get(0).id()).isEqualTo(folderId);
        assertThat(page.items().get(0).type()).isEqualTo(TrashItemType.FOLDER);
        assertThat(page.items().get(1).id()).isEqualTo(fileId);
        assertThat(page.items().get(1).type()).isEqualTo(TrashItemType.FILE);
        assertThat(page.nextCursor()).isNull();
        // ADMIN ROLE 경로 — resource-level 체크 없음
        verifyNoInteractions(permissionResolver);
    }

    // ── MEMBER + permission post-filter ────────────────────────────────

    @Test
    void list_member_filtersByResourceLevelGrant() {
        Instant t0 = Instant.parse("2026-04-30T10:00:00Z");
        UUID grantedFile = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID deniedFile = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

        // MEMBER가 workspace 멤버라고 가정 — 묵시적 READ 권한 보유 (DEPT 멤버) → 가드 통과.
        when(membershipResolver.resolve(ACTOR, ScopeType.DEPARTMENT, SCOPE_ID))
            .thenReturn(EnumSet.of(Permission.READ, Permission.UPLOAD));
        when(permissionService.effectivePermissions(Role.MEMBER))
            .thenReturn(EnumSet.noneOf(Permission.class));
        when(fileRepository.findTrashedPageByScope(any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of(
                newFile(grantedFile, "ok.pdf", t0),
                newFile(deniedFile, "blocked.pdf", t0.minusSeconds(1))
            ));
        when(folderRepository.findTrashedPageByScope(any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of());
        when(permissionResolver.isGranted(eq(ACTOR), eq("file"), eq(grantedFile), eq(Permission.DELETE)))
            .thenReturn(true);
        when(permissionResolver.isGranted(eq(ACTOR), eq("file"), eq(deniedFile), eq(Permission.DELETE)))
            .thenReturn(false);

        TrashPage page = service.list(ACTOR, Role.MEMBER, ScopeType.DEPARTMENT, SCOPE_ID, null, null, null);

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).id()).isEqualTo(grantedFile);
    }

    // ── cursor pagination round-trip ───────────────────────────────────

    @Test
    void list_hasMore_emitsNextCursorMatchingLastItem() {
        // limit=1로 hasMore 유도 — file 2개, folder 0개
        Instant t0 = Instant.parse("2026-04-30T10:00:00Z");
        UUID a = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        UUID b = UUID.fromString("11111111-2222-3333-4444-555555555555");

        when(permissionService.effectivePermissions(Role.ADMIN))
            .thenReturn(EnumSet.allOf(Permission.class));
        when(fileRepository.findTrashedPageByScope(any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of(
                newFile(a, "a.pdf", t0),
                newFile(b, "b.pdf", t0.minusSeconds(1))
            ));
        when(folderRepository.findTrashedPageByScope(any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of());

        TrashPage page = service.list(ACTOR, Role.ADMIN, ScopeType.DEPARTMENT, SCOPE_ID,
            null, TrashItemType.FILE, 1);

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).id()).isEqualTo(a);
        assertThat(page.nextCursor()).isNotNull();

        // round-trip — decode 후 (a.deletedAt, a.id) 와 일치해야 함
        TrashCursor decoded = TrashCursor.decode(page.nextCursor());
        assertThat(decoded).isNotNull();
        assertThat(decoded.deletedAt()).isEqualTo(t0);
        assertThat(decoded.id()).isEqualTo(a);
    }

    @Test
    void list_invalidCursor_throwsIllegalArgument() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> service.list(ACTOR, Role.ADMIN, ScopeType.DEPARTMENT, SCOPE_ID,
                "!!not-base64!!", null, null)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    // ── originalParentPath enrichment (2026-05-11) ─────────────────────

    @Test
    void list_populatesOriginalParentPath_whenParentExists() {
        Instant t0 = Instant.parse("2026-04-30T10:00:00Z");
        UUID fileId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

        when(permissionService.effectivePermissions(Role.ADMIN))
            .thenReturn(EnumSet.allOf(Permission.class));
        when(fileRepository.findTrashedPageByScope(any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of(newFile(fileId, "doc.pdf", t0)));
        when(folderRepository.findTrashedPageByScope(any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of());
        // singletonList — `List.of(Object[]...)`는 varargs로 풀려 `List<Object>`로 추론되어
        // 호출부에서 `Object[]` cast가 깨진다. element 1개일 때만 발생하는 함정.
        when(folderRepository.findAncestorPaths(any(Collection.class)))
            .thenReturn(Collections.singletonList(new Object[]{PARENT, "/회사/팀A/문서"}));

        TrashPage page = service.list(ACTOR, Role.ADMIN, ScopeType.DEPARTMENT, SCOPE_ID, null, null, null);

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).originalParentId()).isEqualTo(PARENT);
        assertThat(page.items().get(0).originalParentPath()).isEqualTo("/회사/팀A/문서");
    }

    @Test
    void list_skipsAncestorPathQuery_whenAllItemsAreRoot() {
        // Postgres IN ()는 문법 오류 — parent set이 비어 있으면 CTE 호출 자체가 없어야 한다.
        Instant t0 = Instant.parse("2026-04-30T10:00:00Z");
        UUID folderId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

        when(permissionService.effectivePermissions(Role.ADMIN))
            .thenReturn(EnumSet.allOf(Permission.class));
        when(fileRepository.findTrashedPageByScope(any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of());
        when(folderRepository.findTrashedPageByScope(any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of(FolderTestFixtures.trashedFolder(folderId, null, ACTOR, "root-deleted", t0)));

        TrashPage page = service.list(ACTOR, Role.ADMIN, ScopeType.DEPARTMENT, SCOPE_ID, null, null, null);

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).originalParentId()).isNull();
        assertThat(page.items().get(0).originalParentPath()).isNull();
        verify(folderRepository, never()).findAncestorPaths(any());
    }

    @Test
    void list_nullsOriginalParentPath_whenChainResolutionFails() {
        // 부모 chain 종착 실패(데이터 corruption 시뮬레이션) — CTE가 결과 row 없이 반환되면
        // DTO path는 NULL로 전달되어 frontend가 폴백 텍스트로 표시한다.
        Instant t0 = Instant.parse("2026-04-30T10:00:00Z");
        UUID fileId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

        when(permissionService.effectivePermissions(Role.ADMIN))
            .thenReturn(EnumSet.allOf(Permission.class));
        when(fileRepository.findTrashedPageByScope(any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of(newFile(fileId, "orphan.pdf", t0)));
        when(folderRepository.findTrashedPageByScope(any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of());
        when(folderRepository.findAncestorPaths(any(Collection.class)))
            .thenReturn(List.of()); // chain 종착 실패 — 결과 누락

        TrashPage page = service.list(ACTOR, Role.ADMIN, ScopeType.DEPARTMENT, SCOPE_ID, null, null, null);

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).originalParentId()).isEqualTo(PARENT);
        assertThat(page.items().get(0).originalParentPath()).isNull();
    }

    // ── helpers ────────────────────────────────────────────────────────

    private static FileItem newFile(UUID id, String name, Instant deletedAt) {
        return FileTestFixtures.trashedFile(id, PARENT, ACTOR, name, deletedAt);
    }

    private static Folder newFolder(UUID id, String name, Instant deletedAt) {
        return FolderTestFixtures.trashedFolder(id, PARENT, ACTOR, name, deletedAt);
    }
}
