package com.ibizdrive.permission;

import com.ibizdrive.file.FileRepository;
import com.ibizdrive.folder.FolderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link PermissionResolver} 단위 테스트 — repository는 mock.
 *
 * <p>재귀 CTE/조상/만료/everyone 처리는 PermissionRepository 슬라이스 테스트에서 검증되므로
 * 여기서는 row → preset → permission union 평면화 로직만 검증.
 *
 * <p>Plan A Task 23 추가: membershipResolver/folderRepository/fileRepository mock 주입.
 * 기존 테스트는 membership 경로를 사용하지 않으므로 기본값(empty)으로 단락 방지.
 */
class PermissionResolverTest {

    private PermissionRepository repository;
    private WorkspaceMembershipResolver membershipResolver;
    private FolderRepository folderRepository;
    private FileRepository fileRepository;
    private PermissionResolver resolver;

    private final UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID resourceId = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeEach
    void setUp() {
        repository = mock(PermissionRepository.class);
        membershipResolver = mock(WorkspaceMembershipResolver.class);
        folderRepository = mock(FolderRepository.class);
        fileRepository = mock(FileRepository.class);
        resolver = new PermissionResolver(repository, membershipResolver, folderRepository, fileRepository);

        // 기존 테스트는 membership 경로를 검증하지 않음 — empty로 단락 방지 (false positive 없음).
        when(membershipResolver.resolve(any(), any(), any()))
            .thenReturn(EnumSet.noneOf(Permission.class));
        when(folderRepository.findById(any())).thenReturn(Optional.empty());
        when(fileRepository.findById(any())).thenReturn(Optional.empty());
    }

    @Test
    void grantsRead_whenReadPresetRowExists() {
        when(repository.findEffective(eq(userId), eq("folder"), eq(resourceId)))
            .thenReturn(List.of(rowWithPreset("read")));

        assertThat(resolver.isGranted(userId, "folder", resourceId, Permission.READ)).isTrue();
        assertThat(resolver.isGranted(userId, "folder", resourceId, Permission.DOWNLOAD)).isTrue();
        assertThat(resolver.isGranted(userId, "folder", resourceId, Permission.EDIT)).isFalse();
    }

    @Test
    void grantsEdit_viaEditPreset() {
        when(repository.findEffective(any(), any(), any()))
            .thenReturn(List.of(rowWithPreset("edit")));

        assertThat(resolver.isGranted(userId, "file", resourceId, Permission.EDIT)).isTrue();
        assertThat(resolver.isGranted(userId, "file", resourceId, Permission.MOVE)).isTrue();
        assertThat(resolver.isGranted(userId, "file", resourceId, Permission.DELETE)).isTrue();
        assertThat(resolver.isGranted(userId, "file", resourceId, Permission.SHARE)).isFalse();
    }

    @Test
    void deniesPurge_evenForAdminPreset_perPresetMatrix() {
        when(repository.findEffective(any(), any(), any()))
            .thenReturn(List.of(rowWithPreset("admin")));

        // ADMIN preset = complementOf(PURGE). PURGE는 어떤 preset에도 없음 (Preset.java:51).
        assertThat(resolver.isGranted(userId, "folder", resourceId, Permission.PURGE)).isFalse();
        assertThat(resolver.isGranted(userId, "folder", resourceId, Permission.PERMISSION_ADMIN)).isTrue();
    }

    @Test
    void unionsPermissionsAcrossMultipleRows() {
        // 부모 폴더에 'read' + 자기 자신에 'upload' grant — union은 READ + DOWNLOAD + UPLOAD.
        when(repository.findEffective(any(), any(), any()))
            .thenReturn(List.of(rowWithPreset("read"), rowWithPreset("upload")));

        assertThat(resolver.isGranted(userId, "file", resourceId, Permission.UPLOAD)).isTrue();
        assertThat(resolver.isGranted(userId, "file", resourceId, Permission.READ)).isTrue();
        assertThat(resolver.isGranted(userId, "file", resourceId, Permission.EDIT)).isFalse();
    }

    @Test
    void deniesWhenNoRows() {
        when(repository.findEffective(any(), any(), any())).thenReturn(List.of());

        assertThat(resolver.isGranted(userId, "folder", resourceId, Permission.READ)).isFalse();
    }

    @Test
    void deniesOnNullArgs() {
        assertThat(resolver.isGranted(null, "folder", resourceId, Permission.READ)).isFalse();
        assertThat(resolver.isGranted(userId, null, resourceId, Permission.READ)).isFalse();
        assertThat(resolver.isGranted(userId, "folder", null, Permission.READ)).isFalse();
        assertThat(resolver.isGranted(userId, "folder", resourceId, null)).isFalse();
    }

    private PermissionRow rowWithPreset(String preset) {
        PermissionRow row = new PermissionRow();
        row.setId(UUID.randomUUID());
        row.setResourceType("folder");
        row.setResourceId(resourceId);
        row.setSubjectType("user");
        row.setSubjectId(userId);
        row.setPreset(preset);
        row.setGrantedBy(userId);
        row.setCreatedAt(Instant.now());
        return row;
    }
}
