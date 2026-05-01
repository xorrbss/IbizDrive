package com.ibizdrive.file;

import com.ibizdrive.file.dto.UploadResponse;
import com.ibizdrive.folder.FolderNotFoundException;
import com.ibizdrive.user.IbizDriveUserDetails;
import com.ibizdrive.user.Role;
import com.ibizdrive.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A15.4 — {@link FileUploadController} 직접 호출 단위 테스트 ({@link FileControllerTest} 패턴 답습).
 *
 * <p>{@code @WebMvcTest} 슬라이스가 아니라 controller 메서드를 직접 호출 — controller가 가진 책임만 본다:
 * <ul>
 *   <li>multipart parsing → service delegation (filename, contentType, sizeBytes, stream 전달)</li>
 *   <li>resolution 파싱 ({@code "new_version"}/{@code "rename"}/null/unknown)</li>
 *   <li>HTTP status (신규 201, NEW_VERSION 200) + envelope 매핑</li>
 *   <li>service에서 던진 예외 그대로 propagate (envelope 매핑은 GlobalExceptionHandler 책임)</li>
 * </ul>
 *
 * <p>{@code @PreAuthorize} SpEL 가드는 {@code IbizDrivePermissionEvaluator}/SpEL 인프라가 검증 — 본 테스트에서
 * 재검증하지 않는다.
 */
class FileUploadControllerTest {

    private static final UUID ACTOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FOLDER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID FILE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID VERSION_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private FileUploadService service;
    private FileUploadController controller;
    private IbizDriveUserDetails principal;

    @BeforeEach
    void setUp() {
        service = mock(FileUploadService.class);
        controller = new FileUploadController(service);

        User u = new User(
            ACTOR, "user@example.com", "User", "{bcrypt}$2a$12$dummy",
            Role.MEMBER, true, false, OffsetDateTime.now()
        );
        principal = new IbizDriveUserDetails(u);
    }

    // ──────────────────────────────────────────────────────────────────
    // happy path
    // ──────────────────────────────────────────────────────────────────

    @Test
    void upload_newFile_returnsCreated() throws IOException {
        MultipartFile mf = new MockMultipartFile(
            "file", "Hello.txt", "text/plain", "hello".getBytes()
        );
        FileItem file = newFile(FILE_ID, FOLDER_ID, "Hello.txt");
        FileVersion version = newVersion(VERSION_ID, FILE_ID, 1);
        when(service.upload(eq(FOLDER_ID), eq(ACTOR), eq("Hello.txt"), eq("text/plain"),
            eq(5L), any(InputStream.class), eq((UploadResolution) null)))
            .thenReturn(new UploadResult(file, version, true));

        ResponseEntity<UploadResponse> res = controller.upload(FOLDER_ID, null, mf, principal);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UploadResponse body = res.getBody();
        assertThat(body).isNotNull();
        assertThat(body.newFile()).isTrue();
        assertThat(body.file().id()).isEqualTo(FILE_ID);
        assertThat(body.versionId()).isEqualTo(VERSION_ID);
        assertThat(body.versionNumber()).isEqualTo(1);
        verify(service).upload(eq(FOLDER_ID), eq(ACTOR), eq("Hello.txt"), eq("text/plain"),
            eq(5L), any(InputStream.class), eq((UploadResolution) null));
    }

    @Test
    void upload_newVersion_returnsOk() throws IOException {
        MultipartFile mf = new MockMultipartFile(
            "file", "Doc.txt", "text/plain", "v2".getBytes()
        );
        FileItem file = newFile(FILE_ID, FOLDER_ID, "Doc.txt");
        FileVersion version = newVersion(VERSION_ID, FILE_ID, 2);
        when(service.upload(any(), any(), any(), any(), eq(2L), any(),
            eq(UploadResolution.NEW_VERSION)))
            .thenReturn(new UploadResult(file, version, false));

        ResponseEntity<UploadResponse> res =
            controller.upload(FOLDER_ID, "new_version", mf, principal);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        UploadResponse body = res.getBody();
        assertThat(body.newFile()).isFalse();
        assertThat(body.versionNumber()).isEqualTo(2);
    }

    @Test
    void upload_rename_passesEnumThrough() throws IOException {
        MultipartFile mf = new MockMultipartFile(
            "file", "Pic.jpg", "image/jpeg", "binary".getBytes()
        );
        FileItem file = newFile(FILE_ID, FOLDER_ID, "Pic (1).jpg");
        FileVersion version = newVersion(VERSION_ID, FILE_ID, 1);
        when(service.upload(any(), any(), any(), any(), any(Long.class), any(),
            eq(UploadResolution.RENAME)))
            .thenReturn(new UploadResult(file, version, true));

        ResponseEntity<UploadResponse> res =
            controller.upload(FOLDER_ID, "rename", mf, principal);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    // ──────────────────────────────────────────────────────────────────
    // resolution parsing
    // ──────────────────────────────────────────────────────────────────

    @Test
    void upload_blankResolution_treatedAsNull() throws IOException {
        MultipartFile mf = new MockMultipartFile("file", "X.txt", "text/plain", "x".getBytes());
        when(service.upload(any(), any(), any(), any(), any(Long.class), any(),
            eq((UploadResolution) null)))
            .thenReturn(new UploadResult(newFile(FILE_ID, FOLDER_ID, "X.txt"),
                newVersion(VERSION_ID, FILE_ID, 1), true));

        ResponseEntity<UploadResponse> res = controller.upload(FOLDER_ID, "  ", mf, principal);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void upload_unknownResolution_throwsBadRequest() {
        MultipartFile mf = new MockMultipartFile("file", "X.txt", "text/plain", "x".getBytes());

        assertThatThrownBy(() -> controller.upload(FOLDER_ID, "OVERWRITE", mf, principal))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unknown resolution");
    }

    // ──────────────────────────────────────────────────────────────────
    // input validation
    // ──────────────────────────────────────────────────────────────────

    @Test
    void upload_emptyFile_throwsBadRequest() {
        MultipartFile mf = new MockMultipartFile("file", "X.txt", "text/plain", new byte[0]);

        assertThatThrownBy(() -> controller.upload(FOLDER_ID, null, mf, principal))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("file part is required");
    }

    // ──────────────────────────────────────────────────────────────────
    // service exception propagation
    // ──────────────────────────────────────────────────────────────────

    @Test
    void upload_serviceThrowsConflict_propagated() {
        MultipartFile mf = new MockMultipartFile("file", "Dup.txt", "text/plain", "x".getBytes());
        when(service.upload(any(), any(), any(), any(), any(Long.class), any(), any()))
            .thenThrow(new FileNameConflictException("dup"));

        assertThatThrownBy(() -> controller.upload(FOLDER_ID, null, mf, principal))
            .isInstanceOf(FileNameConflictException.class);
    }

    @Test
    void upload_serviceThrowsFolderNotFound_propagated() {
        MultipartFile mf = new MockMultipartFile("file", "X.txt", "text/plain", "x".getBytes());
        when(service.upload(any(), any(), any(), any(), any(Long.class), any(), any()))
            .thenThrow(new FolderNotFoundException("missing"));

        assertThatThrownBy(() -> controller.upload(FOLDER_ID, null, mf, principal))
            .isInstanceOf(FolderNotFoundException.class);
    }

    // ──────────────────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────────────────

    private FileItem newFile(UUID id, UUID folderId, String name) {
        FileItem f = new FileItem();
        f.setId(id);
        f.setFolderId(folderId);
        f.setName(name);
        f.setNormalizedName(name.toLowerCase());
        f.setOwnerId(ACTOR);
        f.setSizeBytes(0L);
        Instant now = Instant.now();
        f.setCreatedAt(now);
        f.setUpdatedAt(now);
        return f;
    }

    private FileVersion newVersion(UUID id, UUID fileId, int versionNumber) {
        FileVersion v = new FileVersion();
        v.setId(id);
        v.setFileId(fileId);
        v.setVersionNumber(versionNumber);
        v.setStorageKey(UUID.randomUUID());
        v.setSizeBytes(0L);
        v.setChecksumSha256("0".repeat(64));
        v.setScanStatus(VersionScanStatus.PENDING);
        v.setUploadedBy(ACTOR);
        v.setUploadedAt(Instant.now());
        return v;
    }
}
