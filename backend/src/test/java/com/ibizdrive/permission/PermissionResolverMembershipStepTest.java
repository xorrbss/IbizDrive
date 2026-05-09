package com.ibizdrive.permission;

import com.ibizdrive.file.FileItem;
import com.ibizdrive.file.FileRepository;
import com.ibizdrive.folder.Folder;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.folder.ScopeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Plan A Task 23 — PermissionResolver의 workspace membership 단계 검증.
 *
 * <p>explicit/share grants는 없는 상태에서 membership만으로 grant되는지 확인.
 * 또한 explicit grant가 있을 때는 기존 경로가 우선 grant하는지(short-circuit) 확인.
 */
class PermissionResolverMembershipStepTest {

    private PermissionRepository permissionRepository;
    private WorkspaceMembershipResolver membershipResolver;
    private FolderRepository folderRepository;
    private FileRepository fileRepository;
    private PermissionResolver resolver;

    @BeforeEach
    void setUp() {
        permissionRepository = mock(PermissionRepository.class);
        membershipResolver = mock(WorkspaceMembershipResolver.class);
        folderRepository = mock(FolderRepository.class);
        fileRepository = mock(FileRepository.class);
        resolver = new PermissionResolver(permissionRepository, membershipResolver,
            folderRepository, fileRepository);

        // Default: no explicit grants
        when(permissionRepository.findEffective(any(), anyString(), any())).thenReturn(List.of());
    }

    @Test
    void isGranted_returnsTrue_whenMembershipProvidesPermission_forFolder() {
        UUID userId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        UUID scopeId = UUID.randomUUID();

        Folder folder = mock(Folder.class);
        when(folder.getScopeType()).thenReturn(ScopeType.TEAM);
        when(folder.getScopeId()).thenReturn(scopeId);
        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder));
        when(membershipResolver.resolve(userId, ScopeType.TEAM, scopeId))
            .thenReturn(EnumSet.of(Permission.READ, Permission.UPLOAD));

        assertThat(resolver.isGranted(userId, "folder", folderId, Permission.READ)).isTrue();
        assertThat(resolver.isGranted(userId, "folder", folderId, Permission.UPLOAD)).isTrue();
    }

    @Test
    void isGranted_returnsFalse_whenMembershipDoesNotIncludeRequired_forFolder() {
        UUID userId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        UUID scopeId = UUID.randomUUID();

        Folder folder = mock(Folder.class);
        when(folder.getScopeType()).thenReturn(ScopeType.DEPARTMENT);
        when(folder.getScopeId()).thenReturn(scopeId);
        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder));
        when(membershipResolver.resolve(userId, ScopeType.DEPARTMENT, scopeId))
            .thenReturn(EnumSet.of(Permission.READ, Permission.UPLOAD));

        // DELETE is not in dept member's default perms
        assertThat(resolver.isGranted(userId, "folder", folderId, Permission.DELETE)).isFalse();
    }

    @Test
    void isGranted_returnsTrue_whenMembershipProvidesPermission_forFile() {
        UUID userId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID scopeId = UUID.randomUUID();

        FileItem file = mock(FileItem.class);
        when(file.getScopeType()).thenReturn(ScopeType.TEAM);
        when(file.getScopeId()).thenReturn(scopeId);
        when(fileRepository.findById(fileId)).thenReturn(Optional.of(file));
        when(membershipResolver.resolve(userId, ScopeType.TEAM, scopeId))
            .thenReturn(EnumSet.of(Permission.READ));

        assertThat(resolver.isGranted(userId, "file", fileId, Permission.READ)).isTrue();
    }

    @Test
    void isGranted_returnsFalse_whenResourceNotFound() {
        UUID userId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        when(folderRepository.findById(folderId)).thenReturn(Optional.empty());

        assertThat(resolver.isGranted(userId, "folder", folderId, Permission.READ)).isFalse();
    }

    @Test
    void isGranted_returnsFalse_whenResourceTypeUnknown() {
        UUID userId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        assertThat(resolver.isGranted(userId, "unknown", resourceId, Permission.READ)).isFalse();
    }
}
