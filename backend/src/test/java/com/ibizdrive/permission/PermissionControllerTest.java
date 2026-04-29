package com.ibizdrive.permission;

import com.ibizdrive.common.error.ResourceNotFoundException;
import com.ibizdrive.file.FileItem;
import com.ibizdrive.file.FileRepository;
import com.ibizdrive.folder.Folder;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.permission.dto.GrantPermissionRequest;
import com.ibizdrive.permission.dto.PermissionDto;
import com.ibizdrive.user.IbizDriveUserDetails;
import com.ibizdrive.user.Role;
import com.ibizdrive.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A4.4 — {@link PermissionController} 직접 호출 단위 테스트.
 *
 * <p>{@code @WebMvcTest} 슬라이스 대신 controller 메서드를 직접 호출하여 routing 외 책임만 본다 —
 * <ul>
 *   <li>path segment 정규화 (folders→folder, files→file)</li>
 *   <li>리소스 존재 검증 (file/folder 모두 JPA repository)</li>
 *   <li>service delegation 인자 전달</li>
 *   <li>DTO 매핑 + 201/204 status</li>
 * </ul>
 *
 * <p>{@code @PreAuthorize} 가드는 {@link PermissionEvaluatorIntegrationTest} +
 * {@link PermissionEndpointE2ETest} 에서 이미 검증되므로 본 테스트에서 재검증하지 않는다.
 */
class PermissionControllerTest {

    private static final UUID ACTOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID RESOURCE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID SUBJECT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private PermissionService permissionService;
    private FileRepository fileRepository;
    private FolderRepository folderRepository;
    private PermissionController controller;
    private IbizDriveUserDetails principal;

    @BeforeEach
    void setUp() {
        permissionService = mock(PermissionService.class);
        fileRepository = mock(FileRepository.class);
        folderRepository = mock(FolderRepository.class);
        controller = new PermissionController(permissionService, fileRepository, folderRepository);

        User u = new User(
            ACTOR, "admin@example.com", "Admin", "{bcrypt}$2a$12$dummy",
            Role.ADMIN, true, false, OffsetDateTime.now()
        );
        principal = new IbizDriveUserDetails(u);
    }

    // ── grant: happy path ──────────────────────────────────────────────

    @Test
    void grant_folderPlural_normalizesAndPersists() {
        // folder 존재 — FolderRepository stub.
        when(folderRepository.findByIdAndDeletedAtIsNull(RESOURCE_ID))
            .thenReturn(Optional.of(mock(Folder.class)));

        UUID newPermId = UUID.randomUUID();
        when(permissionService.grantPermission(
            eq("folder"), eq(RESOURCE_ID), eq("user"), eq(SUBJECT_ID),
            eq(Preset.EDIT), any(), eq(ACTOR)
        )).thenReturn(newRow(newPermId, "folder", "user", SUBJECT_ID, "edit"));

        GrantPermissionRequest body = new GrantPermissionRequest(
            new GrantPermissionRequest.SubjectRef("user", SUBJECT_ID),
            "edit",
            null
        );
        ResponseEntity<Map<String, PermissionDto>> res =
            controller.grant("folders", RESOURCE_ID, body, principal);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        PermissionDto dto = res.getBody().get("permission");
        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo(newPermId);
        assertThat(dto.resourceType()).isEqualTo("folder");
        assertThat(dto.subjectType()).isEqualTo("user");
        assertThat(dto.preset()).isEqualTo("edit");
    }

    @Test
    void grant_fileSingular_lookupViaRepository() {
        FileItem fileEntity = mock(FileItem.class);
        when(fileRepository.findByIdAndDeletedAtIsNull(RESOURCE_ID))
            .thenReturn(Optional.of(fileEntity));

        when(permissionService.grantPermission(
            eq("file"), eq(RESOURCE_ID), eq("everyone"), eq(null),
            eq(Preset.READ), any(), eq(ACTOR)
        )).thenReturn(newRow(UUID.randomUUID(), "file", "everyone", null, "read"));

        GrantPermissionRequest body = new GrantPermissionRequest(
            new GrantPermissionRequest.SubjectRef("everyone", null),
            "read",
            null
        );
        ResponseEntity<Map<String, PermissionDto>> res =
            controller.grant("file", RESOURCE_ID, body, principal);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(folderRepository, never()).findByIdAndDeletedAtIsNull(any());
    }

    // ── grant: 404 paths ───────────────────────────────────────────────

    @Test
    void grant_folderMissing_throwsNotFound() {
        when(folderRepository.findByIdAndDeletedAtIsNull(RESOURCE_ID))
            .thenReturn(Optional.empty());

        GrantPermissionRequest body = new GrantPermissionRequest(
            new GrantPermissionRequest.SubjectRef("user", SUBJECT_ID), "read", null);

        assertThatThrownBy(() ->
            controller.grant("folders", RESOURCE_ID, body, principal))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("folder");

        verify(permissionService, never()).grantPermission(
            anyString(), any(), anyString(), any(), any(), any(), any());
    }

    @Test
    void grant_fileMissing_throwsNotFound() {
        when(fileRepository.findByIdAndDeletedAtIsNull(RESOURCE_ID))
            .thenReturn(Optional.empty());

        GrantPermissionRequest body = new GrantPermissionRequest(
            new GrantPermissionRequest.SubjectRef("user", SUBJECT_ID), "read", null);

        assertThatThrownBy(() ->
            controller.grant("files", RESOURCE_ID, body, principal))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("file");
    }

    // ── grant: 400 — bad inputs ────────────────────────────────────────

    @Test
    void grant_unsupportedResourcePath_throwsBadRequest() {
        GrantPermissionRequest body = new GrantPermissionRequest(
            new GrantPermissionRequest.SubjectRef("user", SUBJECT_ID), "read", null);

        assertThatThrownBy(() ->
            controller.grant("shares", RESOURCE_ID, body, principal))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void grant_unknownPresetWire_throwsBadRequest() {
        when(folderRepository.findByIdAndDeletedAtIsNull(RESOURCE_ID))
            .thenReturn(Optional.of(mock(Folder.class)));

        GrantPermissionRequest body = new GrantPermissionRequest(
            new GrantPermissionRequest.SubjectRef("user", SUBJECT_ID), "wizard", null);

        assertThatThrownBy(() ->
            controller.grant("folder", RESOURCE_ID, body, principal))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Preset");
    }

    // ── revoke ─────────────────────────────────────────────────────────

    @Test
    void revoke_returns204_andDelegates() {
        UUID permissionId = UUID.randomUUID();

        ResponseEntity<Void> res = controller.revoke(permissionId, principal);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(permissionService).revokePermission(permissionId, ACTOR);
    }

    // ── helpers ────────────────────────────────────────────────────────

    private PermissionRow newRow(UUID id, String resourceType,
                                 String subjectType, UUID subjectId, String preset) {
        PermissionRow r = new PermissionRow();
        r.setId(id);
        r.setResourceType(resourceType);
        r.setResourceId(RESOURCE_ID);
        r.setSubjectType(subjectType);
        r.setSubjectId(subjectId);
        r.setPreset(preset);
        r.setGrantedBy(ACTOR);
        r.setCreatedAt(Instant.now());
        return r;
    }
}
