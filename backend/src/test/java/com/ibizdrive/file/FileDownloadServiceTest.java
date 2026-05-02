package com.ibizdrive.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.audit.AuditEvent;
import com.ibizdrive.audit.AuditEventType;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.audit.AuditTargetType;
import com.ibizdrive.storage.StorageClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A15.5 — {@link FileDownloadService} 단위 테스트 (mock-only).
 *
 * <p>Download는 read-only + stream open 단순 흐름이므로 Testcontainers 없이 repository/storage/audit
 * mock으로 충분. ({@code FileUploadServiceTest}는 V5 unique 제약 검증이 핵심이라 Testcontainers 사용.)
 *
 * <p>책임 검증:
 * <ul>
 *   <li>활성 파일 + current_version_id 존재 → {@link DownloadHandle} 반환 + storage stream 전달</li>
 *   <li>{@code FILE_DOWNLOADED} audit emission (versionId 포함)</li>
 *   <li>not-found 시나리오 — 파일 부재 / soft-deleted / current_version_id null / version row missing</li>
 *   <li>storage I/O 실패 → {@link IllegalStateException}</li>
 * </ul>
 */
class FileDownloadServiceTest {

    private static final UUID ACTOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FILE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID VERSION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID FOLDER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID STORAGE_KEY = UUID.fromString("55555555-5555-5555-5555-555555555555");

    private FileRepository fileRepository;
    private FileVersionRepository fileVersionRepository;
    private StorageClient storageClient;
    private AuditService auditService;
    private FileDownloadService service;

    @BeforeEach
    void setUp() {
        fileRepository = mock(FileRepository.class);
        fileVersionRepository = mock(FileVersionRepository.class);
        storageClient = mock(StorageClient.class);
        auditService = mock(AuditService.class);
        service = new FileDownloadService(
            fileRepository, fileVersionRepository, storageClient, auditService, new ObjectMapper());
    }

    // ──────────────────────────────────────────────────────────────────
    // happy path
    // ──────────────────────────────────────────────────────────────────

    @Test
    void download_returnsHandleAndEmitsAudit() throws IOException {
        FileItem file = newFile(FILE_ID, FOLDER_ID, "Hello.txt", VERSION_ID);
        FileVersion version = newVersion(VERSION_ID, FILE_ID, "text/plain", 5, STORAGE_KEY);
        InputStream stream = new ByteArrayInputStream("hello".getBytes());
        when(fileRepository.findByIdAndDeletedAtIsNull(FILE_ID)).thenReturn(Optional.of(file));
        when(fileVersionRepository.findById(VERSION_ID)).thenReturn(Optional.of(version));
        when(storageClient.read(STORAGE_KEY.toString())).thenReturn(stream);

        DownloadHandle handle = service.download(FILE_ID, ACTOR);

        assertThat(handle.file()).isSameAs(file);
        assertThat(handle.version()).isSameAs(version);
        assertThat(handle.stream()).isSameAs(stream);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        AuditEvent event = captor.getValue();
        assertThat(event.eventType()).isEqualTo(AuditEventType.FILE_DOWNLOADED);
        assertThat(event.actorId()).isEqualTo(ACTOR);
        assertThat(event.targetType()).isEqualTo(AuditTargetType.FILE);
        assertThat(event.targetId()).isEqualTo(FILE_ID);
        assertThat(event.afterState()).contains(VERSION_ID.toString());
    }

    // ──────────────────────────────────────────────────────────────────
    // not-found scenarios
    // ──────────────────────────────────────────────────────────────────

    @Test
    void download_fileMissing_throwsFileNotFound_noAudit() throws IOException {
        when(fileRepository.findByIdAndDeletedAtIsNull(FILE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.download(FILE_ID, ACTOR))
            .isInstanceOf(FileNotFoundException.class);

        verify(storageClient, never()).read(anyString());
        verify(auditService, never()).record(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void download_currentVersionIdNull_throwsFileNotFound() {
        FileItem file = newFile(FILE_ID, FOLDER_ID, "Orphan.txt", null);
        when(fileRepository.findByIdAndDeletedAtIsNull(FILE_ID)).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> service.download(FILE_ID, ACTOR))
            .isInstanceOf(FileNotFoundException.class)
            .hasMessageContaining("no current version");
    }

    @Test
    void download_versionRowMissing_throwsFileNotFound() {
        FileItem file = newFile(FILE_ID, FOLDER_ID, "Hello.txt", VERSION_ID);
        when(fileRepository.findByIdAndDeletedAtIsNull(FILE_ID)).thenReturn(Optional.of(file));
        when(fileVersionRepository.findById(VERSION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.download(FILE_ID, ACTOR))
            .isInstanceOf(FileNotFoundException.class)
            .hasMessageContaining("current version not found");
    }

    // ──────────────────────────────────────────────────────────────────
    // storage failure
    // ──────────────────────────────────────────────────────────────────

    @Test
    void download_storageIoFailure_throwsIllegalState_noAudit() throws IOException {
        FileItem file = newFile(FILE_ID, FOLDER_ID, "Hello.txt", VERSION_ID);
        FileVersion version = newVersion(VERSION_ID, FILE_ID, "text/plain", 5, STORAGE_KEY);
        when(fileRepository.findByIdAndDeletedAtIsNull(FILE_ID)).thenReturn(Optional.of(file));
        when(fileVersionRepository.findById(VERSION_ID)).thenReturn(Optional.of(version));
        when(storageClient.read(STORAGE_KEY.toString())).thenThrow(new IOException("disk gone"));

        assertThatThrownBy(() -> service.download(FILE_ID, ACTOR))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("storage read failed");

        verify(auditService, never()).record(org.mockito.ArgumentMatchers.any());
    }

    // ──────────────────────────────────────────────────────────────────
    // input validation
    // ──────────────────────────────────────────────────────────────────

    @Test
    void download_nullFileId_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.download(null, ACTOR))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void download_nullActorId_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.download(FILE_ID, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ──────────────────────────────────────────────────────────────────
    // M-RP.2.1 — downloadVersion (version-pin)
    // ──────────────────────────────────────────────────────────────────

    @Test
    void downloadVersion_returnsHandleAndEmitsVersionDownloaded() throws IOException {
        FileItem file = newFile(FILE_ID, FOLDER_ID, "Hello.txt", VERSION_ID);
        UUID otherVersionId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        FileVersion version = newVersion(otherVersionId, FILE_ID, "text/plain", 5, STORAGE_KEY);
        InputStream stream = new ByteArrayInputStream("hello".getBytes());
        when(fileRepository.findByIdAndDeletedAtIsNull(FILE_ID)).thenReturn(Optional.of(file));
        when(fileVersionRepository.findById(otherVersionId)).thenReturn(Optional.of(version));
        when(storageClient.read(STORAGE_KEY.toString())).thenReturn(stream);

        DownloadHandle handle = service.downloadVersion(FILE_ID, otherVersionId, ACTOR);

        assertThat(handle.file()).isSameAs(file);
        assertThat(handle.version()).isSameAs(version);
        assertThat(handle.stream()).isSameAs(stream);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        AuditEvent event = captor.getValue();
        assertThat(event.eventType()).isEqualTo(AuditEventType.VERSION_DOWNLOADED);
        assertThat(event.actorId()).isEqualTo(ACTOR);
        assertThat(event.targetType()).isEqualTo(AuditTargetType.FILE);
        assertThat(event.targetId()).isEqualTo(FILE_ID);
        assertThat(event.afterState()).contains(otherVersionId.toString());
    }

    @Test
    void downloadVersion_fileMissing_throwsFileNotFound_noAudit() throws IOException {
        when(fileRepository.findByIdAndDeletedAtIsNull(FILE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.downloadVersion(FILE_ID, VERSION_ID, ACTOR))
            .isInstanceOf(FileNotFoundException.class);

        verify(storageClient, never()).read(anyString());
        verify(auditService, never()).record(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void downloadVersion_versionMissing_throwsFileNotFound() {
        FileItem file = newFile(FILE_ID, FOLDER_ID, "Hello.txt", VERSION_ID);
        when(fileRepository.findByIdAndDeletedAtIsNull(FILE_ID)).thenReturn(Optional.of(file));
        when(fileVersionRepository.findById(VERSION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.downloadVersion(FILE_ID, VERSION_ID, ACTOR))
            .isInstanceOf(FileNotFoundException.class)
            .hasMessageContaining("version not found");
    }

    @Test
    void downloadVersion_crossFileVersion_throwsFileNotFound_noAudit() throws IOException {
        // version의 fileId가 path fileId와 다르면 cross-file 우회 시도 → 404.
        FileItem file = newFile(FILE_ID, FOLDER_ID, "Hello.txt", VERSION_ID);
        UUID otherFileId = UUID.fromString("77777777-7777-7777-7777-777777777777");
        FileVersion strayVersion = newVersion(VERSION_ID, otherFileId, "text/plain", 5, STORAGE_KEY);
        when(fileRepository.findByIdAndDeletedAtIsNull(FILE_ID)).thenReturn(Optional.of(file));
        when(fileVersionRepository.findById(VERSION_ID)).thenReturn(Optional.of(strayVersion));

        assertThatThrownBy(() -> service.downloadVersion(FILE_ID, VERSION_ID, ACTOR))
            .isInstanceOf(FileNotFoundException.class)
            .hasMessageContaining("does not belong to file");

        verify(storageClient, never()).read(anyString());
        verify(auditService, never()).record(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void downloadVersion_storageIoFailure_throwsIllegalState_noAudit() throws IOException {
        FileItem file = newFile(FILE_ID, FOLDER_ID, "Hello.txt", VERSION_ID);
        FileVersion version = newVersion(VERSION_ID, FILE_ID, "text/plain", 5, STORAGE_KEY);
        when(fileRepository.findByIdAndDeletedAtIsNull(FILE_ID)).thenReturn(Optional.of(file));
        when(fileVersionRepository.findById(VERSION_ID)).thenReturn(Optional.of(version));
        when(storageClient.read(STORAGE_KEY.toString())).thenThrow(new IOException("disk gone"));

        assertThatThrownBy(() -> service.downloadVersion(FILE_ID, VERSION_ID, ACTOR))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("storage read failed");

        verify(auditService, never()).record(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void downloadVersion_nullVersionId_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.downloadVersion(FILE_ID, null, ACTOR))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ──────────────────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────────────────

    private FileItem newFile(UUID id, UUID folderId, String name, UUID currentVersionId) {
        FileItem f = new FileItem();
        f.setId(id);
        f.setFolderId(folderId);
        f.setName(name);
        f.setNormalizedName(name.toLowerCase());
        f.setOwnerId(ACTOR);
        f.setSizeBytes(0L);
        f.setCurrentVersionId(currentVersionId);
        Instant now = Instant.now();
        f.setCreatedAt(now);
        f.setUpdatedAt(now);
        return f;
    }

    private FileVersion newVersion(UUID id, UUID fileId, String mimeType, long sizeBytes, UUID storageKey) {
        FileVersion v = new FileVersion();
        v.setId(id);
        v.setFileId(fileId);
        v.setVersionNumber(1);
        v.setStorageKey(storageKey);
        v.setSizeBytes(sizeBytes);
        v.setChecksumSha256("0".repeat(64));
        v.setMimeType(mimeType);
        v.setScanStatus(VersionScanStatus.PENDING);
        v.setUploadedBy(ACTOR);
        v.setUploadedAt(Instant.now());
        return v;
    }
}
