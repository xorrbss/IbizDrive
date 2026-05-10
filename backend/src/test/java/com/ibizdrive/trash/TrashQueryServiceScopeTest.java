package com.ibizdrive.trash;

import com.ibizdrive.file.FileRepository;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.folder.ScopeType;
import com.ibizdrive.permission.Permission;
import com.ibizdrive.permission.PermissionResolver;
import com.ibizdrive.permission.PermissionService;
import com.ibizdrive.permission.WorkspaceMembershipResolver;
import com.ibizdrive.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Plan E T2 — {@link TrashQueryService}의 workspace scope 가드 + repo wire 계약 검증.
 *
 * <p><b>커버리지</b>:
 * <ul>
 *   <li>workspace 비멤버 ⇒ {@link AccessDeniedException} (ADMIN 외)</li>
 *   <li>ADMIN bypass — membership resolver 호출 없이 통과</li>
 *   <li>repo는 {@link ScopeType#dbValue()} (lower-case) 문자열을 받는다 — silent 0-row 회귀 가드</li>
 *   <li>DEPT 멤버가 다른 부서 scopeId 요청 ⇒ AccessDeniedException</li>
 * </ul>
 *
 * <p>기존 cursor/sort/permission 후처리 흐름은 {@link TrashQueryServiceTest}가 보호.
 */
class TrashQueryServiceScopeTest {

    private static final UUID ACTOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DEPT_A = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID DEPT_B = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID TEAM_X = UUID.fromString("cccccccc-0000-0000-0000-000000000003");

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

    // ── membership guard ──────────────────────────────────────────────

    @Test
    void list_nonMember_throwsAccessDenied() {
        // membership resolver가 빈 집합 반환 = 비멤버
        when(membershipResolver.resolve(ACTOR, ScopeType.DEPARTMENT, DEPT_A))
            .thenReturn(EnumSet.noneOf(Permission.class));

        assertThatThrownBy(() ->
            service.list(ACTOR, Role.MEMBER, ScopeType.DEPARTMENT, DEPT_A, null, null, null)
        ).isInstanceOf(AccessDeniedException.class);

        // 가드 실패 — repo 호출 0회
        verifyNoInteractions(fileRepository, folderRepository, permissionResolver, permissionService);
    }

    @Test
    void list_deptMember_otherDeptScope_throwsAccessDenied() {
        // ACTOR는 DEPT_A 멤버지만 DEPT_B를 요청 — resolver는 DEPT_B에 대해 빈 집합 반환.
        when(membershipResolver.resolve(ACTOR, ScopeType.DEPARTMENT, DEPT_B))
            .thenReturn(EnumSet.noneOf(Permission.class));

        assertThatThrownBy(() ->
            service.list(ACTOR, Role.MEMBER, ScopeType.DEPARTMENT, DEPT_B, null, null, null)
        ).isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(fileRepository, folderRepository);
    }

    @Test
    void list_admin_bypassesMembershipCheck() {
        // ADMIN은 membership resolver를 거치지 않는다.
        when(permissionService.effectivePermissions(Role.ADMIN))
            .thenReturn(EnumSet.allOf(Permission.class));
        when(fileRepository.findTrashedPageByScope(any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of());
        when(folderRepository.findTrashedPageByScope(any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of());

        TrashPage page = service.list(ACTOR, Role.ADMIN, ScopeType.TEAM, TEAM_X, null, null, null);

        assertThat(page.items()).isEmpty();
        verifyNoInteractions(membershipResolver);
    }

    @Test
    void list_member_validScope_passesAndQueriesRepos() {
        // DEPT_A 멤버 — READ + UPLOAD 보유 (membership resolver 결과)
        when(membershipResolver.resolve(ACTOR, ScopeType.DEPARTMENT, DEPT_A))
            .thenReturn(EnumSet.of(Permission.READ, Permission.UPLOAD));
        when(permissionService.effectivePermissions(Role.MEMBER))
            .thenReturn(EnumSet.noneOf(Permission.class));
        when(fileRepository.findTrashedPageByScope(any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of());
        when(folderRepository.findTrashedPageByScope(any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of());

        TrashPage page = service.list(ACTOR, Role.MEMBER, ScopeType.DEPARTMENT, DEPT_A, null, null, null);

        assertThat(page.items()).isEmpty();
        // 가드 통과 후 두 repo 모두 호출됐는지
        verify(fileRepository).findTrashedPageByScope(any(), any(), any(), any(), anyInt());
        verify(folderRepository).findTrashedPageByScope(any(), any(), any(), any(), anyInt());
    }

    // ── repo wire contract: dbValue() (lower-case) ────────────────────

    @Test
    void list_passesDbValueLowercase_toFileRepo() {
        when(permissionService.effectivePermissions(Role.ADMIN))
            .thenReturn(EnumSet.allOf(Permission.class));
        when(fileRepository.findTrashedPageByScope(any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of());
        when(folderRepository.findTrashedPageByScope(any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of());

        service.list(ACTOR, Role.ADMIN, ScopeType.DEPARTMENT, DEPT_A, null, null, null);

        ArgumentCaptor<String> rawCaptor = ArgumentCaptor.forClass(String.class);
        verify(fileRepository).findTrashedPageByScope(
            rawCaptor.capture(), eq(DEPT_A), any(), any(), anyInt()
        );
        // T1 repo wire 계약 — name() 대문자가 아니라 dbValue() 소문자.
        assertThat(rawCaptor.getValue()).isEqualTo("department");
    }

    @Test
    void list_passesDbValueLowercase_toFolderRepo() {
        when(permissionService.effectivePermissions(Role.ADMIN))
            .thenReturn(EnumSet.allOf(Permission.class));
        when(fileRepository.findTrashedPageByScope(any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of());
        when(folderRepository.findTrashedPageByScope(any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of());

        service.list(ACTOR, Role.ADMIN, ScopeType.TEAM, TEAM_X, null, null, null);

        ArgumentCaptor<String> rawCaptor = ArgumentCaptor.forClass(String.class);
        verify(folderRepository).findTrashedPageByScope(
            rawCaptor.capture(), eq(TEAM_X), any(), any(), anyInt()
        );
        assertThat(rawCaptor.getValue()).isEqualTo("team");
    }
}
