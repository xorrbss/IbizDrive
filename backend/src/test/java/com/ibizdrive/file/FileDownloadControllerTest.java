package com.ibizdrive.file;

import com.ibizdrive.user.IbizDriveUserDetails;
import com.ibizdrive.user.Role;
import com.ibizdrive.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A15.5 — {@link FileDownloadController} 직접 호출 단위 테스트 ({@link FileUploadControllerTest} 패턴 답습).
 *
 * <p>controller가 가진 책임만 본다:
 * <ul>
 *   <li>service delegation (fileId, actorId 전달)</li>
 *   <li>응답 헤더 — Content-Type/Content-Length/Content-Disposition(RFC 5987)/ETag</li>
 *   <li>service에서 던진 예외 그대로 propagate (envelope 매핑은 GlobalExceptionHandler 책임)</li>
 * </ul>
 *
 * <p>{@code @PreAuthorize} SpEL 가드는 {@code IbizDrivePermissionEvaluator}/SpEL 인프라가 검증 — 본
 * 테스트에서 재검증하지 않는다.
 */
class FileDownloadControllerTest {

    private static final UUID ACTOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FILE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID VERSION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID FOLDER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private FileDownloadService service;
    private FileDownloadController controller;
    private IbizDriveUserDetails principal;

    @BeforeEach
    void setUp() {
        service = mock(FileDownloadService.class);
        controller = new FileDownloadController(service);

        User u = new User(
            ACTOR, "user@example.com", "User", "{bcrypt}$2a$12$dummy",
            Role.MEMBER, true, false, OffsetDateTime.now()
        );
        principal = new IbizDriveUserDetails(u);
    }

    // ──────────────────────────────────────────────────────────────────
    // happy path — ASCII filename, mime_type set
    // ──────────────────────────────────────────────────────────────────

    @Test
    void download_asciiFilename_returnsOkWithHeaders() {
        byte[] payload = "hello world".getBytes();
        FileItem file = newFile(FILE_ID, FOLDER_ID, "Hello.txt");
        FileVersion version = newVersion(VERSION_ID, FILE_ID, "text/plain", payload.length);
        InputStream stream = new ByteArrayInputStream(payload);
        when(service.download(eq(FILE_ID), eq(ACTOR)))
            .thenReturn(new DownloadHandle(file, version, stream));

        ResponseEntity<InputStreamResource> res = controller.download(FILE_ID, principal);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        HttpHeaders h = res.getHeaders();
        assertThat(h.getContentType()).isEqualTo(MediaType.parseMediaType("text/plain"));
        assertThat(h.getContentLength()).isEqualTo(payload.length);
        assertThat(h.getETag()).isEqualTo("\"" + VERSION_ID + "\"");
        assertThat(h.getFirst(HttpHeaders.CONTENT_DISPOSITION))
            .isEqualTo("attachment; filename=\"Hello.txt\"; filename*=UTF-8''Hello.txt");
        assertThat(res.getBody()).isNotNull();
        verify(service).download(eq(FILE_ID), eq(ACTOR));
    }

    // ──────────────────────────────────────────────────────────────────
    // RFC 5987 — non-ASCII filename
    // ──────────────────────────────────────────────────────────────────

    @Test
    void download_nonAsciiFilename_percentEncoded() {
        byte[] payload = "x".getBytes();
        FileItem file = newFile(FILE_ID, FOLDER_ID, "한글 파일.txt");
        FileVersion version = newVersion(VERSION_ID, FILE_ID, "text/plain", payload.length);
        when(service.download(eq(FILE_ID), eq(ACTOR)))
            .thenReturn(new DownloadHandle(file, version, new ByteArrayInputStream(payload)));

        ResponseEntity<InputStreamResource> res = controller.download(FILE_ID, principal);

        String cd = res.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(cd).startsWith("attachment; filename=\"");
        // ASCII fallback: 비ASCII 문자(한글, 공백 위치의 한글 사이 공백)는 _로 치환 (공백은 0x20 < 0x7F이므로 보존).
        assertThat(cd).contains("filename=\"__ __.txt\"");
        // RFC 5987 percent-encoded UTF-8 — 한글 + 공백(%20).
        assertThat(cd).contains("filename*=UTF-8''");
        assertThat(cd).contains("%20");                              // 공백
        assertThat(cd).contains("%ED%95%9C%EA%B8%80");                // "한글" UTF-8 bytes
    }

    // ──────────────────────────────────────────────────────────────────
    // mime_type fallback
    // ──────────────────────────────────────────────────────────────────

    @Test
    void download_nullMimeType_defaultsToOctetStream() {
        FileItem file = newFile(FILE_ID, FOLDER_ID, "blob.bin");
        FileVersion version = newVersion(VERSION_ID, FILE_ID, null, 4);
        when(service.download(eq(FILE_ID), eq(ACTOR)))
            .thenReturn(new DownloadHandle(file, version, new ByteArrayInputStream(new byte[]{1, 2, 3, 4})));

        ResponseEntity<InputStreamResource> res = controller.download(FILE_ID, principal);

        assertThat(res.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
    }

    @Test
    void download_invalidMimeType_defaultsToOctetStream() {
        FileItem file = newFile(FILE_ID, FOLDER_ID, "blob.bin");
        FileVersion version = newVersion(VERSION_ID, FILE_ID, "not a/valid/mime", 1);
        when(service.download(eq(FILE_ID), eq(ACTOR)))
            .thenReturn(new DownloadHandle(file, version, new ByteArrayInputStream(new byte[]{1})));

        ResponseEntity<InputStreamResource> res = controller.download(FILE_ID, principal);

        assertThat(res.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
    }

    // ──────────────────────────────────────────────────────────────────
    // service exception propagation
    // ──────────────────────────────────────────────────────────────────

    @Test
    void download_fileNotFound_propagated() {
        when(service.download(eq(FILE_ID), eq(ACTOR)))
            .thenThrow(new FileNotFoundException("not found: " + FILE_ID));

        assertThatThrownBy(() -> controller.download(FILE_ID, principal))
            .isInstanceOf(FileNotFoundException.class);
    }

    @Test
    void download_storageFailure_propagated() {
        when(service.download(eq(FILE_ID), eq(ACTOR)))
            .thenThrow(new IllegalStateException("storage read failed"));

        assertThatThrownBy(() -> controller.download(FILE_ID, principal))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("storage read failed");
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
        f.assignScope(com.ibizdrive.folder.ScopeType.DEPARTMENT, java.util.UUID.randomUUID());
        return f;
    }

    private FileVersion newVersion(UUID id, UUID fileId, String mimeType, long sizeBytes) {
        FileVersion v = new FileVersion();
        v.setId(id);
        v.setFileId(fileId);
        v.setVersionNumber(1);
        v.setStorageKey(UUID.randomUUID());
        v.setSizeBytes(sizeBytes);
        v.setChecksumSha256("0".repeat(64));
        v.setMimeType(mimeType);
        v.setScanStatus(VersionScanStatus.PENDING);
        v.setUploadedBy(ACTOR);
        v.setUploadedAt(Instant.now());
        return v;
    }
}
