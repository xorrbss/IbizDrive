package com.ibizdrive.folder;

import com.ibizdrive.common.dto.RestoreRequest;
import com.ibizdrive.folder.dto.CreateFolderRequest;
import com.ibizdrive.folder.dto.FolderDto;
import com.ibizdrive.folder.dto.FolderItemDto;
import com.ibizdrive.folder.dto.FolderItemsResponse;
import com.ibizdrive.folder.dto.MoveFolderRequest;
import com.ibizdrive.folder.dto.RenameFolderRequest;
import com.ibizdrive.user.IbizDriveUserDetails;
import com.ibizdrive.user.Role;
import com.ibizdrive.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A4.7 — {@link FolderController} 직접 호출 단위 테스트 (PermissionControllerTest 패턴).
 *
 * <p>{@code @WebMvcTest} 슬라이스가 아니라 controller 메서드를 직접 호출 — controller가 가진 책임만 본다:
 * <ul>
 *   <li>service delegation 인자 전달 (parentId/null, name, ownerId=principal, auditLevel default, actorId=principal)</li>
 *   <li>DTO 매핑 + HTTP status (201/200)</li>
 *   <li>service에서 던진 예외 그대로 propagate (envelope 매핑은 GlobalExceptionHandler 책임)</li>
 * </ul>
 *
 * <p>{@code @PreAuthorize} 가드는 {@code IbizDrivePermissionEvaluator}/SpEL 인프라가 검증 — 본 테스트에서
 * 재검증하지 않는다 ({@code PermissionControllerTest}와 동일 분리).
 */
class FolderControllerTest {

    private static final UUID ACTOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PARENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID FOLDER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private FolderMutationService service;
    private FolderQueryService queryService;
    private FolderController controller;
    private IbizDriveUserDetails principal;

    @BeforeEach
    void setUp() {
        service = mock(FolderMutationService.class);
        queryService = mock(FolderQueryService.class);
        controller = new FolderController(service, queryService, mock(com.ibizdrive.folder.MovePreviewService.class));

        User u = new User(
            ACTOR, "admin@example.com", "Admin", "{bcrypt}$2a$12$dummy",
            Role.ADMIN, true, false, OffsetDateTime.now()
        );
        principal = new IbizDriveUserDetails(u);
    }

    // ── create ─────────────────────────────────────────────────────────

    @Test
    void create_nestedFolder_passesParentAndDelegates() {
        Folder created = newFolder(FOLDER_ID, PARENT_ID, "docs", "standard");
        when(service.create(eq(PARENT_ID), eq("docs"), eq(ACTOR), eq("standard"), eq(ACTOR)))
            .thenReturn(created);

        CreateFolderRequest body = new CreateFolderRequest(PARENT_ID, "docs", "standard");
        ResponseEntity<Map<String, FolderDto>> res = controller.create(body, principal);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        FolderDto dto = res.getBody().get("folder");
        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo(FOLDER_ID);
        assertThat(dto.parentId()).isEqualTo(PARENT_ID);
        assertThat(dto.name()).isEqualTo("docs");
        assertThat(dto.ownerId()).isEqualTo(ACTOR);
        assertThat(dto.auditLevel()).isEqualTo("standard");
    }

    @Test
    void create_rootFolder_passesNullParent() {
        Folder created = newFolder(FOLDER_ID, null, "root-team", "standard");
        when(service.create(isNull(), eq("root-team"), eq(ACTOR), eq("standard"), eq(ACTOR)))
            .thenReturn(created);

        CreateFolderRequest body = new CreateFolderRequest(null, "root-team", null);
        ResponseEntity<Map<String, FolderDto>> res = controller.create(body, principal);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // parentId is NULL → JsonInclude(NON_NULL)이 wire에서 키를 생략, record 자체는 null 유지
        assertThat(res.getBody().get("folder").parentId()).isNull();
        // auditLevel 본문 NULL → controller default "standard"가 service에 전달됐는지 확인
        verify(service).create(isNull(), eq("root-team"), eq(ACTOR), eq("standard"), eq(ACTOR));
    }

    @Test
    void create_strictAuditLevel_propagatedToService() {
        Folder created = newFolder(FOLDER_ID, PARENT_ID, "secret", "strict");
        when(service.create(eq(PARENT_ID), eq("secret"), eq(ACTOR), eq("strict"), eq(ACTOR)))
            .thenReturn(created);

        CreateFolderRequest body = new CreateFolderRequest(PARENT_ID, "secret", "strict");
        controller.create(body, principal);

        verify(service).create(eq(PARENT_ID), eq("secret"), eq(ACTOR), eq("strict"), eq(ACTOR));
    }

    @Test
    void create_serviceThrowsConflict_propagated() {
        when(service.create(any(), any(), any(), any(), any()))
            .thenThrow(new FolderNameConflictException("dup"));

        CreateFolderRequest body = new CreateFolderRequest(PARENT_ID, "docs", null);
        assertThatThrownBy(() -> controller.create(body, principal))
            .isInstanceOf(FolderNameConflictException.class);
    }

    // ── rename ─────────────────────────────────────────────────────────

    @Test
    void rename_returnsOk_andDelegates() {
        Folder renamed = newFolder(FOLDER_ID, PARENT_ID, "renamed", "standard");
        when(service.rename(eq(FOLDER_ID), eq("renamed"), eq(ACTOR))).thenReturn(renamed);

        RenameFolderRequest body = new RenameFolderRequest("renamed");
        ResponseEntity<Map<String, FolderDto>> res = controller.rename(FOLDER_ID, body, principal);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().get("folder").name()).isEqualTo("renamed");
        verify(service).rename(FOLDER_ID, "renamed", ACTOR);
    }

    @Test
    void rename_serviceThrowsNotFound_propagated() {
        when(service.rename(any(), any(), any()))
            .thenThrow(new FolderNotFoundException("missing"));

        RenameFolderRequest body = new RenameFolderRequest("x");
        assertThatThrownBy(() -> controller.rename(FOLDER_ID, body, principal))
            .isInstanceOf(FolderNotFoundException.class);
    }

    // ── move ───────────────────────────────────────────────────────────

    @Test
    void move_toAnotherParent_delegates() {
        UUID newParent = UUID.fromString("44444444-4444-4444-4444-444444444444");
        Folder moved = newFolder(FOLDER_ID, newParent, "docs", "standard");
        when(service.move(eq(FOLDER_ID), eq(newParent), eq(ACTOR), eq(false))).thenReturn(moved);

        MoveFolderRequest body = new MoveFolderRequest(newParent, null);
        ResponseEntity<Map<String, FolderDto>> res = controller.move(FOLDER_ID, body, principal);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().get("folder").parentId()).isEqualTo(newParent);
    }

    @Test
    void move_toRoot_passesNullTarget() {
        Folder moved = newFolder(FOLDER_ID, null, "docs", "standard");
        when(service.move(eq(FOLDER_ID), isNull(), eq(ACTOR), eq(false))).thenReturn(moved);

        MoveFolderRequest body = new MoveFolderRequest(null, null);
        ResponseEntity<Map<String, FolderDto>> res = controller.move(FOLDER_ID, body, principal);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().get("folder").parentId()).isNull();
        verify(service).move(FOLDER_ID, null, ACTOR, false);
    }

    @Test
    void move_serviceThrowsCycle_propagated() {
        when(service.move(any(), any(), any(), eq(false)))
            .thenThrow(new IllegalArgumentException("cannot move folder into its own descendant"));

        MoveFolderRequest body = new MoveFolderRequest(PARENT_ID, null);
        assertThatThrownBy(() -> controller.move(FOLDER_ID, body, principal))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("descendant");
    }

    @Test
    void move_allowCrossScope_true_delegatesWithFlag() {
        UUID newParent = UUID.fromString("44444444-4444-4444-4444-444444444444");
        Folder moved = newFolder(FOLDER_ID, newParent, "docs", "standard");
        when(service.move(eq(FOLDER_ID), eq(newParent), eq(ACTOR), eq(true))).thenReturn(moved);

        MoveFolderRequest body = new MoveFolderRequest(newParent, true);
        ResponseEntity<Map<String, FolderDto>> res = controller.move(FOLDER_ID, body, principal);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(service).move(FOLDER_ID, newParent, ACTOR, true);
    }

    // ── helpers ────────────────────────────────────────────────────────

    // -- delete / restore ---------------------------------------------------------------

    @Test
    void delete_returnsNoContent_andDelegates() {
        ResponseEntity<Void> res = controller.delete(FOLDER_ID, principal);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).delete(FOLDER_ID, ACTOR);
    }

    @Test
    void restore_returnsOk_andDelegates() {
        Folder restored = newFolder(FOLDER_ID, PARENT_ID, "docs", "standard");
        when(service.restore(eq(FOLDER_ID), eq(ACTOR), isNull())).thenReturn(restored);

        ResponseEntity<Map<String, FolderDto>> res = controller.restore(FOLDER_ID, null, principal);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().get("folder").id()).isEqualTo(FOLDER_ID);
        verify(service).restore(FOLDER_ID, ACTOR, null);
    }

    @Test
    void restore_withNewName_delegatesNewName() {
        Folder restored = newFolder(FOLDER_ID, PARENT_ID, "renamed", "standard");
        when(service.restore(eq(FOLDER_ID), eq(ACTOR), eq("renamed"))).thenReturn(restored);

        ResponseEntity<Map<String, FolderDto>> res = controller.restore(
            FOLDER_ID, new RestoreRequest("renamed"), principal);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(service).restore(FOLDER_ID, ACTOR, "renamed");
    }

    // ── items (Phase B P1) ────────────────────────────────────────────

    @Test
    void items_defaultParams_delegatesWithNameAsc() {
        FolderItemsResponse svcRes = new FolderItemsResponse(List.of(
            new FolderItemDto(FOLDER_ID, "folder", "docs", null, null, Instant.now(),
                ACTOR.toString(), PARENT_ID, null, null, null)
        ));
        when(queryService.loadItems(eq(FOLDER_ID), eq(SortKey.NAME), eq(SortDir.ASC)))
            .thenReturn(svcRes);

        ResponseEntity<FolderItemsResponse> res =
            controller.items(FOLDER_ID, SortKey.NAME, SortDir.ASC);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().items()).hasSize(1);
        verify(queryService).loadItems(FOLDER_ID, SortKey.NAME, SortDir.ASC);
    }

    @Test
    void items_explicitParams_passedThrough() {
        FolderItemsResponse svcRes = new FolderItemsResponse(List.of());
        when(queryService.loadItems(eq(FOLDER_ID), eq(SortKey.SIZE), eq(SortDir.DESC)))
            .thenReturn(svcRes);

        ResponseEntity<FolderItemsResponse> res =
            controller.items(FOLDER_ID, SortKey.SIZE, SortDir.DESC);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(queryService).loadItems(FOLDER_ID, SortKey.SIZE, SortDir.DESC);
    }

    @Test
    void items_propagatesNotFoundFromService() {
        when(queryService.loadItems(eq(FOLDER_ID), any(), any()))
            .thenThrow(new FolderNotFoundException("folder not found: " + FOLDER_ID));

        assertThatThrownBy(() -> controller.items(FOLDER_ID, SortKey.NAME, SortDir.ASC))
            .isInstanceOf(FolderNotFoundException.class);
    }

    private Folder newFolder(UUID id, UUID parentId, String name, String auditLevel) {
        Folder f = new Folder();
        f.setId(id);
        f.setParentId(parentId);
        f.setName(name);
        f.setNormalizedName(name);
        f.setSlug(name);
        f.setOwnerId(ACTOR);
        f.setAuditLevel(auditLevel);
        Instant now = Instant.now();
        f.setCreatedAt(now);
        f.setUpdatedAt(now);
        f.assignScope(com.ibizdrive.folder.ScopeType.DEPARTMENT, java.util.UUID.randomUUID());
        return f;
    }
}
