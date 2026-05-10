package com.ibizdrive.folder;

import com.ibizdrive.file.FileController;
import com.ibizdrive.file.FileMutationService;
import com.ibizdrive.file.FileQueryService;
import com.ibizdrive.folder.dto.MovePreviewRequest;
import com.ibizdrive.folder.dto.MovePreviewResponse;
import com.ibizdrive.permission.Permission;
import com.ibizdrive.user.IbizDriveUserDetails;
import com.ibizdrive.user.Role;
import com.ibizdrive.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plan D Task 10 — {@link FolderController} 및 {@link FileController} {@code /move/preview}
 * 엔드포인트 단위 테스트 ({@code FolderControllerTest} 직접 호출 패턴).
 *
 * <p>controller 책임만 검증:
 * <ul>
 *   <li>service delegation 인자 전달 (folderId/fileId, destinationFolderId, actorId=principal)</li>
 *   <li>HTTP 200 + MovePreviewResponse 그대로 반환</li>
 *   <li>service 예외 propagate</li>
 * </ul>
 *
 * <p>{@code @PreAuthorize} SpEL 가드(EDIT+SHARE)는 {@code IbizDrivePermissionEvaluator}
 * 인프라 검증 범위 — 본 테스트에서 재검증하지 않는다 ({@code FolderControllerTest} 동일 분리).
 */
class MovePreviewControllerTest {

    private static final UUID ACTOR       = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FOLDER_ID   = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID FILE_ID     = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID DEST_ID     = UUID.fromString("55555555-5555-5555-5555-555555555555");

    // ── folder controller fixtures ────────────────────────────────────

    private FolderMutationService folderMutationService;
    private FolderQueryService folderQueryService;
    private MovePreviewService movePreviewService;
    private FolderController folderController;

    // ── file controller fixtures ──────────────────────────────────────

    private FileMutationService fileMutationService;
    private FileQueryService fileQueryService;
    private FileController fileController;

    private IbizDriveUserDetails principal;

    @BeforeEach
    void setUp() {
        movePreviewService = mock(MovePreviewService.class);

        folderMutationService = mock(FolderMutationService.class);
        folderQueryService = mock(FolderQueryService.class);
        folderController = new FolderController(folderMutationService, folderQueryService, movePreviewService);

        fileMutationService = mock(FileMutationService.class);
        fileQueryService = mock(FileQueryService.class);
        fileController = new FileController(fileMutationService, fileQueryService, movePreviewService);

        User u = new User(
            ACTOR, "user@example.com", "User", "{bcrypt}$2a$12$dummy",
            Role.MEMBER, true, false, OffsetDateTime.now()
        );
        principal = new IbizDriveUserDetails(u);
    }

    // ── folder /move/preview ──────────────────────────────────────────

    @Test
    void folderPreview_returnsImpactSummary_andDelegates() {
        MovePreviewResponse stub = new MovePreviewResponse(
            3, List.of(), List.of(), List.of(Permission.READ), null);
        when(movePreviewService.previewFolder(FOLDER_ID, DEST_ID, ACTOR)).thenReturn(stub);

        MovePreviewRequest req = new MovePreviewRequest(DEST_ID);
        ResponseEntity<MovePreviewResponse> res = folderController.movePreview(FOLDER_ID, req, principal);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        MovePreviewResponse body = res.getBody();
        assertThat(body).isNotNull();
        assertThat(body.itemCount()).isEqualTo(3);
        assertThat(body.removedPermissions()).isEmpty();
        assertThat(body.revokedShares()).isEmpty();
        assertThat(body.targetMembershipDefaults()).containsExactly(Permission.READ);
        assertThat(body.nameConflict()).isNull();

        verify(movePreviewService).previewFolder(FOLDER_ID, DEST_ID, ACTOR);
    }

    @Test
    void folderPreview_withNameConflict_returnsConflictName() {
        MovePreviewResponse stub = new MovePreviewResponse(
            1, List.of(), List.of(), List.of(), "report.pdf");
        when(movePreviewService.previewFolder(FOLDER_ID, DEST_ID, ACTOR)).thenReturn(stub);

        MovePreviewRequest req = new MovePreviewRequest(DEST_ID);
        ResponseEntity<MovePreviewResponse> res = folderController.movePreview(FOLDER_ID, req, principal);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().nameConflict()).isEqualTo("report.pdf");
    }

    @Test
    void folderPreview_serviceThrowsNotFound_propagated() {
        when(movePreviewService.previewFolder(any(), any(), any()))
            .thenThrow(new FolderNotFoundException("source not found"));

        MovePreviewRequest req = new MovePreviewRequest(DEST_ID);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> folderController.movePreview(FOLDER_ID, req, principal))
            .isInstanceOf(FolderNotFoundException.class);
    }

    @Test
    void folderPreview_serviceThrowsInvalidDestination_propagated() {
        when(movePreviewService.previewFolder(any(), any(), any()))
            .thenThrow(new InvalidMoveDestinationException("cycle"));

        MovePreviewRequest req = new MovePreviewRequest(DEST_ID);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> folderController.movePreview(FOLDER_ID, req, principal))
            .isInstanceOf(InvalidMoveDestinationException.class);
    }

    // ── file /move/preview ────────────────────────────────────────────

    @Test
    void filePreview_returnsImpactSummary_andDelegates() {
        MovePreviewResponse stub = new MovePreviewResponse(
            1, List.of(), List.of(), List.of(Permission.READ, Permission.EDIT), null);
        when(movePreviewService.previewFile(FILE_ID, DEST_ID, ACTOR)).thenReturn(stub);

        MovePreviewRequest req = new MovePreviewRequest(DEST_ID);
        ResponseEntity<MovePreviewResponse> res = fileController.movePreview(FILE_ID, req, principal);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        MovePreviewResponse body = res.getBody();
        assertThat(body).isNotNull();
        assertThat(body.itemCount()).isEqualTo(1);
        assertThat(body.removedPermissions()).isEmpty();
        assertThat(body.revokedShares()).isEmpty();
        assertThat(body.targetMembershipDefaults()).containsExactly(Permission.READ, Permission.EDIT);

        verify(movePreviewService).previewFile(FILE_ID, DEST_ID, ACTOR);
    }

    @Test
    void filePreview_withNameConflict_returnsConflictName() {
        MovePreviewResponse stub = new MovePreviewResponse(
            1, List.of(), List.of(), List.of(), "duplicate.docx");
        when(movePreviewService.previewFile(FILE_ID, DEST_ID, ACTOR)).thenReturn(stub);

        MovePreviewRequest req = new MovePreviewRequest(DEST_ID);
        ResponseEntity<MovePreviewResponse> res = fileController.movePreview(FILE_ID, req, principal);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().nameConflict()).isEqualTo("duplicate.docx");
    }

    @Test
    void filePreview_serviceThrowsNotFound_propagated() {
        when(movePreviewService.previewFile(any(), any(), any()))
            .thenThrow(new com.ibizdrive.file.FileNotFoundException("file not found"));

        MovePreviewRequest req = new MovePreviewRequest(DEST_ID);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> fileController.movePreview(FILE_ID, req, principal))
            .isInstanceOf(com.ibizdrive.file.FileNotFoundException.class);
    }
}
