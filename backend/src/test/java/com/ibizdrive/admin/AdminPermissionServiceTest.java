package com.ibizdrive.admin;

import com.ibizdrive.department.Department;
import com.ibizdrive.department.DepartmentRepository;
import com.ibizdrive.file.FileItem;
import com.ibizdrive.file.FileRepository;
import com.ibizdrive.folder.Folder;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.permission.PermissionRepository;
import com.ibizdrive.permission.PermissionRow;
import com.ibizdrive.user.User;
import com.ibizdrive.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link AdminPermissionService} 단위 테스트 — wave2-t5-admin-permission-matrix.
 *
 * <p>검증 범위:
 * <ul>
 *   <li>filter 검증 (subjectType/resourceType/preset enum, subjectId-only 차단, everyone+id 차단)</li>
 *   <li>q 정규화 (trim/lowercase/escape/wrap) — repository 호출 captor 검증</li>
 *   <li>name resolution (user/dept/folder/file/everyone/role)</li>
 *   <li>isExpired 계산 (null / 미래 / 과거)</li>
 * </ul>
 *
 * <p>{@link PermissionRow}는 protected ctor — Mockito mock으로 stub.
 */
@ExtendWith(MockitoExtension.class)
class AdminPermissionServiceTest {

    @Mock private PermissionRepository permissionRepository;
    @Mock private UserRepository userRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private FileRepository fileRepository;

    private AdminPermissionService service;

    private static final Pageable PAGE = PageRequest.of(0, 20);

    @BeforeEach
    void setUp() {
        service = new AdminPermissionService(
            permissionRepository, userRepository, departmentRepository,
            folderRepository, fileRepository
        );
    }

    // ===== filter validation =====

    @Test
    void list_invalidSubjectType_throws400() {
        AdminPermissionService.Filters f = new AdminPermissionService.Filters(
            "invalid", null, null, null, null);
        assertThatThrownBy(() -> service.list(f, PAGE))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("subjectType");
        verifyNoInteractions(permissionRepository);
    }

    @Test
    void list_invalidResourceType_throws400() {
        AdminPermissionService.Filters f = new AdminPermissionService.Filters(
            null, null, "invalid", null, null);
        assertThatThrownBy(() -> service.list(f, PAGE))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("resourceType");
    }

    @Test
    void list_invalidPreset_throws400() {
        AdminPermissionService.Filters f = new AdminPermissionService.Filters(
            null, null, null, "deny", null);
        assertThatThrownBy(() -> service.list(f, PAGE))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("preset");
    }

    @Test
    void list_subjectIdWithoutSubjectType_throws400() {
        AdminPermissionService.Filters f = new AdminPermissionService.Filters(
            null, UUID.randomUUID(), null, null, null);
        assertThatThrownBy(() -> service.list(f, PAGE))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("subjectType is required");
    }

    @Test
    void list_everyoneWithSubjectId_throws400() {
        AdminPermissionService.Filters f = new AdminPermissionService.Filters(
            "everyone", UUID.randomUUID(), null, null, null);
        assertThatThrownBy(() -> service.list(f, PAGE))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("subjectId must be null when subjectType=everyone");
    }

    // ===== q 정규화 =====

    @Test
    void list_qNormalized_trimLowercaseEscapeWrap() {
        AdminPermissionService.Filters f = new AdminPermissionService.Filters(
            null, null, null, null, "  Hello_50%  ");
        when(permissionRepository.findAllForAdminPageable(
            isNull(), isNull(), isNull(), isNull(), any(), eq(PAGE)))
            .thenReturn(new PageImpl<>(List.of()));

        service.list(f, PAGE);

        ArgumentCaptor<String> qCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(permissionRepository).findAllForAdminPageable(
            isNull(), isNull(), isNull(), isNull(), qCaptor.capture(), eq(PAGE));
        // %\_50\%% — escape (\_, \%) and wrap
        assertThat(qCaptor.getValue()).isEqualTo("%hello\\_50\\%%");
    }

    @Test
    void list_blankQ_nulledOut() {
        AdminPermissionService.Filters f = new AdminPermissionService.Filters(
            null, null, null, null, "   ");
        when(permissionRepository.findAllForAdminPageable(
            isNull(), isNull(), isNull(), isNull(), isNull(), eq(PAGE)))
            .thenReturn(new PageImpl<>(List.of()));

        service.list(f, PAGE);

        org.mockito.Mockito.verify(permissionRepository).findAllForAdminPageable(
            isNull(), isNull(), isNull(), isNull(), isNull(), eq(PAGE));
    }

    // ===== name resolution + isExpired =====

    @Test
    void list_userSubject_resolvesDisplayName_isExpiredFalseWhenFuture() {
        UUID rowId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        UUID grantedById = UUID.randomUUID();
        Instant future = Instant.now().plusSeconds(3600);

        PermissionRow row = mockRow(rowId, "user", userId, "folder", folderId,
            "read", grantedById, future, Instant.now());

        when(permissionRepository.findAllForAdminPageable(
            any(), any(), any(), any(), any(), eq(PAGE)))
            .thenReturn(new PageImpl<>(List.of(row)));

        User u = new User(userId, "u@x", "Alice", null,
            com.ibizdrive.user.Role.MEMBER, true, false,
            java.time.OffsetDateTime.now());
        User g = new User(grantedById, "g@x", "Granter", null,
            com.ibizdrive.user.Role.ADMIN, true, false,
            java.time.OffsetDateTime.now());
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(u, g));

        Folder folder = org.mockito.Mockito.mock(Folder.class);
        lenient().when(folder.getId()).thenReturn(folderId);
        lenient().when(folder.getName()).thenReturn("Reports");
        when(folderRepository.findAllById(anyIterable())).thenReturn(List.of(folder));

        Page<AdminPermissionRowResponse> page = service.list(
            new AdminPermissionService.Filters(null, null, null, null, null), PAGE);

        assertThat(page.getContent()).hasSize(1);
        AdminPermissionRowResponse r = page.getContent().get(0);
        assertThat(r.subjectName()).isEqualTo("Alice");
        assertThat(r.resourceName()).isEqualTo("Reports");
        assertThat(r.grantedByName()).isEqualTo("Granter");
        assertThat(r.isExpired()).isFalse();
    }

    @Test
    void list_deptSubject_fileResource_resolved() {
        UUID rowId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID grantedById = UUID.randomUUID();

        PermissionRow row = mockRow(rowId, "department", deptId, "file", fileId,
            "edit", grantedById, null, Instant.now());

        when(permissionRepository.findAllForAdminPageable(
            any(), any(), any(), any(), any(), eq(PAGE)))
            .thenReturn(new PageImpl<>(List.of(row)));

        User g = new User(grantedById, "g@x", "Granter", null,
            com.ibizdrive.user.Role.ADMIN, true, false,
            java.time.OffsetDateTime.now());
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(g));

        Department dept = new Department(deptId, "Engineering",
            java.time.OffsetDateTime.now());
        when(departmentRepository.findAllById(anyIterable())).thenReturn(List.of(dept));

        FileItem file = org.mockito.Mockito.mock(FileItem.class);
        lenient().when(file.getId()).thenReturn(fileId);
        lenient().when(file.getName()).thenReturn("budget.xlsx");
        when(fileRepository.findAllById(anyIterable())).thenReturn(List.of(file));

        Page<AdminPermissionRowResponse> page = service.list(
            new AdminPermissionService.Filters(null, null, null, null, null), PAGE);

        AdminPermissionRowResponse r = page.getContent().get(0);
        assertThat(r.subjectName()).isEqualTo("Engineering");
        assertThat(r.resourceName()).isEqualTo("budget.xlsx");
        assertThat(r.expiresAt()).isNull();
        assertThat(r.isExpired()).isFalse();
    }

    @Test
    void list_everyoneSubject_subjectName_jeonsa() {
        UUID rowId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        UUID grantedById = UUID.randomUUID();

        PermissionRow row = mockRow(rowId, "everyone", null, "folder", folderId,
            "read", grantedById, null, Instant.now());

        when(permissionRepository.findAllForAdminPageable(
            any(), any(), any(), any(), any(), eq(PAGE)))
            .thenReturn(new PageImpl<>(List.of(row)));

        User g = new User(grantedById, "g@x", "Granter", null,
            com.ibizdrive.user.Role.ADMIN, true, false,
            java.time.OffsetDateTime.now());
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(g));

        Folder folder = org.mockito.Mockito.mock(Folder.class);
        lenient().when(folder.getId()).thenReturn(folderId);
        lenient().when(folder.getName()).thenReturn("Public");
        when(folderRepository.findAllById(anyIterable())).thenReturn(List.of(folder));

        Page<AdminPermissionRowResponse> page = service.list(
            new AdminPermissionService.Filters(null, null, null, null, null), PAGE);

        AdminPermissionRowResponse r = page.getContent().get(0);
        assertThat(r.subjectId()).isNull();
        assertThat(r.subjectName()).isEqualTo("전사");
    }

    @Test
    void list_expiredRow_isExpiredTrue() {
        UUID rowId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        UUID grantedById = UUID.randomUUID();
        Instant past = Instant.now().minusSeconds(3600);

        PermissionRow row = mockRow(rowId, "user", userId, "folder", folderId,
            "read", grantedById, past, Instant.now().minusSeconds(7200));

        when(permissionRepository.findAllForAdminPageable(
            any(), any(), any(), any(), any(), eq(PAGE)))
            .thenReturn(new PageImpl<>(List.of(row)));

        User u = new User(userId, "u@x", "Alice", null,
            com.ibizdrive.user.Role.MEMBER, true, false,
            java.time.OffsetDateTime.now());
        User g = new User(grantedById, "g@x", "Granter", null,
            com.ibizdrive.user.Role.ADMIN, true, false,
            java.time.OffsetDateTime.now());
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(u, g));

        Folder folder = org.mockito.Mockito.mock(Folder.class);
        lenient().when(folder.getId()).thenReturn(folderId);
        lenient().when(folder.getName()).thenReturn("F");
        when(folderRepository.findAllById(anyIterable())).thenReturn(List.of(folder));

        AdminPermissionRowResponse r = service.list(
            new AdminPermissionService.Filters(null, null, null, null, null), PAGE
        ).getContent().get(0);

        assertThat(r.isExpired()).isTrue();
    }

    @Test
    void list_softDeletedSubject_subjectNameNull() {
        UUID rowId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        UUID grantedById = UUID.randomUUID();

        PermissionRow row = mockRow(rowId, "user", userId, "folder", folderId,
            "read", grantedById, null, Instant.now());

        when(permissionRepository.findAllForAdminPageable(
            any(), any(), any(), any(), any(), eq(PAGE)))
            .thenReturn(new PageImpl<>(List.of(row)));

        // user soft-deleted: only granter resolves
        User g = new User(grantedById, "g@x", "Granter", null,
            com.ibizdrive.user.Role.ADMIN, true, false,
            java.time.OffsetDateTime.now());
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(g));

        Folder folder = org.mockito.Mockito.mock(Folder.class);
        lenient().when(folder.getId()).thenReturn(folderId);
        lenient().when(folder.getName()).thenReturn("F");
        when(folderRepository.findAllById(anyIterable())).thenReturn(List.of(folder));

        AdminPermissionRowResponse r = service.list(
            new AdminPermissionService.Filters(null, null, null, null, null), PAGE
        ).getContent().get(0);

        assertThat(r.subjectName()).isNull();
        assertThat(r.grantedByName()).isEqualTo("Granter");
    }

    private static PermissionRow mockRow(UUID id, String subjectType, UUID subjectId,
                                         String resourceType, UUID resourceId,
                                         String preset, UUID grantedBy,
                                         Instant expiresAt, Instant createdAt) {
        PermissionRow row = org.mockito.Mockito.mock(PermissionRow.class);
        lenient().when(row.getId()).thenReturn(id);
        lenient().when(row.getSubjectType()).thenReturn(subjectType);
        lenient().when(row.getSubjectId()).thenReturn(subjectId);
        lenient().when(row.getResourceType()).thenReturn(resourceType);
        lenient().when(row.getResourceId()).thenReturn(resourceId);
        lenient().when(row.getPreset()).thenReturn(preset);
        lenient().when(row.getGrantedBy()).thenReturn(grantedBy);
        lenient().when(row.getExpiresAt()).thenReturn(expiresAt);
        lenient().when(row.getCreatedAt()).thenReturn(createdAt);
        return row;
    }
}
