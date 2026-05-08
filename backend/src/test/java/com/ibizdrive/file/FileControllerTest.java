package com.ibizdrive.file;

import com.ibizdrive.common.dto.RestoreRequest;
import com.ibizdrive.file.dto.FileDto;
import com.ibizdrive.file.dto.MoveFileRequest;
import com.ibizdrive.file.dto.RenameFileRequest;
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
 * A4.8 — {@link FileController} 직접 호출 단위 테스트 ({@code FolderControllerTest} 패턴).
 *
 * <p>{@code @WebMvcTest} 슬라이스가 아니라 controller 메서드를 직접 호출 — controller가 가진 책임만 본다:
 * <ul>
 *   <li>service delegation 인자 전달</li>
 *   <li>HTTP status (rename 200, move 200, delete 204, restore 200) + envelope 매핑</li>
 *   <li>service에서 던진 예외 그대로 propagate (envelope 매핑은 GlobalExceptionHandler 책임)</li>
 * </ul>
 *
 * <p>{@code @PreAuthorize} SpEL 가드는 {@code IbizDrivePermissionEvaluator}/SpEL 인프라가 검증 — 본 테스트에서
 * 재검증하지 않는다.
 */
class FileControllerTest {

    private static final UUID ACTOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FOLDER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID FILE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private FileMutationService service;
    private FileQueryService queryService;
    private FileController controller;
    private IbizDriveUserDetails principal;

    @BeforeEach
    void setUp() {
        service = mock(FileMutationService.class);
        queryService = mock(FileQueryService.class);
        controller = new FileController(service, queryService);

        User u = new User(
            ACTOR, "user@example.com", "User", "{bcrypt}$2a$12$dummy",
            Role.MEMBER, true, false, OffsetDateTime.now()
        );
        principal = new IbizDriveUserDetails(u);
    }

    // ── rename ─────────────────────────────────────────────────────────

    @Test
    void rename_returnsOk_andDelegates() {
        FileItem renamed = newFile(FILE_ID, FOLDER_ID, "renamed.txt");
        when(service.rename(eq(FILE_ID), eq("renamed.txt"), eq(ACTOR))).thenReturn(renamed);

        RenameFileRequest body = new RenameFileRequest("renamed.txt");
        ResponseEntity<Map<String, FileDto>> res = controller.rename(FILE_ID, body, principal);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        FileDto dto = res.getBody().get("file");
        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo(FILE_ID);
        assertThat(dto.folderId()).isEqualTo(FOLDER_ID);
        assertThat(dto.name()).isEqualTo("renamed.txt");
        verify(service).rename(FILE_ID, "renamed.txt", ACTOR);
    }

    @Test
    void rename_serviceThrowsNotFound_propagated() {
        when(service.rename(any(), any(), any()))
            .thenThrow(new FileNotFoundException("missing"));

        RenameFileRequest body = new RenameFileRequest("x.txt");
        assertThatThrownBy(() -> controller.rename(FILE_ID, body, principal))
            .isInstanceOf(FileNotFoundException.class);
    }

    @Test
    void rename_serviceThrowsConflict_propagated() {
        when(service.rename(any(), any(), any()))
            .thenThrow(new FileNameConflictException("dup"));

        RenameFileRequest body = new RenameFileRequest("x.txt");
        assertThatThrownBy(() -> controller.rename(FILE_ID, body, principal))
            .isInstanceOf(FileNameConflictException.class);
    }

    // ── move ───────────────────────────────────────────────────────────

    @Test
    void move_returnsOk_andDelegates() {
        UUID newFolder = UUID.fromString("44444444-4444-4444-4444-444444444444");
        FileItem moved = newFile(FILE_ID, newFolder, "doc.txt");
        when(service.move(eq(FILE_ID), eq(newFolder), eq(ACTOR))).thenReturn(moved);

        MoveFileRequest body = new MoveFileRequest(newFolder);
        ResponseEntity<Map<String, FileDto>> res = controller.move(FILE_ID, body, principal);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().get("file").folderId()).isEqualTo(newFolder);
        verify(service).move(FILE_ID, newFolder, ACTOR);
    }

    @Test
    void move_serviceThrowsConflict_propagated() {
        UUID newFolder = UUID.fromString("55555555-5555-5555-5555-555555555555");
        when(service.move(any(), any(), any()))
            .thenThrow(new FileNameConflictException("dup"));

        MoveFileRequest body = new MoveFileRequest(newFolder);
        assertThatThrownBy(() -> controller.move(FILE_ID, body, principal))
            .isInstanceOf(FileNameConflictException.class);
    }

    // ── delete ─────────────────────────────────────────────────────────

    @Test
    void delete_returnsNoContent_andDelegates() {
        ResponseEntity<Void> res = controller.delete(FILE_ID, principal);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(res.getBody()).isNull();
        verify(service).delete(FILE_ID, ACTOR);
    }

    @Test
    void delete_serviceThrowsNotFound_propagated() {
        org.mockito.Mockito.doThrow(new FileNotFoundException("missing"))
            .when(service).delete(any(), any());

        assertThatThrownBy(() -> controller.delete(FILE_ID, principal))
            .isInstanceOf(FileNotFoundException.class);
    }

    // ── restore ────────────────────────────────────────────────────────

    @Test
    void restore_returnsOk_andDelegates() {
        FileItem restored = newFile(FILE_ID, FOLDER_ID, "doc.txt");
        when(service.restore(eq(FILE_ID), eq(ACTOR), isNull())).thenReturn(restored);

        ResponseEntity<Map<String, FileDto>> res = controller.restore(FILE_ID, null, principal);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().get("file").id()).isEqualTo(FILE_ID);
        assertThat(res.getBody().get("file").folderId()).isEqualTo(FOLDER_ID);
        verify(service).restore(FILE_ID, ACTOR, null);
    }

    @Test
    void restore_withNewName_delegatesNewName() {
        FileItem restored = newFile(FILE_ID, FOLDER_ID, "renamed.txt");
        when(service.restore(eq(FILE_ID), eq(ACTOR), eq("renamed.txt"))).thenReturn(restored);

        ResponseEntity<Map<String, FileDto>> res = controller.restore(
            FILE_ID, new RestoreRequest("renamed.txt"), principal);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(service).restore(FILE_ID, ACTOR, "renamed.txt");
    }

    @Test
    void restore_serviceThrowsConflict_propagated() {
        when(service.restore(any(), any(), any()))
            .thenThrow(new FileRestoreConflictException("dup at original folder"));

        assertThatThrownBy(() -> controller.restore(FILE_ID, null, principal))
            .isInstanceOf(FileRestoreConflictException.class);
    }

    // ── get (Phase B P2) ──────────────────────────────────────────────

    @Test
    void get_returnsOk_andDelegatesToQueryService() {
        FileDto dto = FileDto.from(newFile(FILE_ID, FOLDER_ID, "보고서.pdf"));
        when(queryService.loadDetail(FILE_ID)).thenReturn(dto);

        ResponseEntity<Map<String, FileDto>> res = controller.get(FILE_ID);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().get("file")).isEqualTo(dto);
        verify(queryService).loadDetail(FILE_ID);
    }

    @Test
    void get_propagatesNotFoundFromQueryService() {
        when(queryService.loadDetail(FILE_ID))
            .thenThrow(new FileNotFoundException("file not found: " + FILE_ID));

        assertThatThrownBy(() -> controller.get(FILE_ID))
            .isInstanceOf(FileNotFoundException.class);
    }

    // ── helpers ────────────────────────────────────────────────────────

    private FileItem newFile(UUID id, UUID folderId, String name) {
        FileItem f = new FileItem();
        f.setId(id);
        f.setFolderId(folderId);
        f.setName(name);
        f.setNormalizedName(name);
        f.setOwnerId(ACTOR);
        f.setSizeBytes(0L);
        Instant now = Instant.now();
        f.setCreatedAt(now);
        f.setUpdatedAt(now);
        return f;
    }
}
