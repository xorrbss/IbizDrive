package com.ibizdrive.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.audit.AuditEvent;
import com.ibizdrive.audit.AuditEventType;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.audit.AuditTargetType;
import com.ibizdrive.folder.FolderNotFoundException;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.storage.StorageClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * A15.2 — {@link FileUploadService} 통합 슬라이스. {@link FileMutationServiceTest} 패턴 답습:
 * 실제 Postgres ({@link Testcontainers}) + V5 마이그레이션, {@link AuditService} +
 * {@link StorageClient}만 mock해서 service 단의 emission/storage write contract와 entity 경로를 검증.
 *
 * <p>RED 단계 (A15.2): {@link FileUploadService#upload}가 {@link UnsupportedOperationException}을
 * 던지는 skeleton 상태 — 모든 happy/branch 시나리오가 실패하면 명세가 사실상 RED 잠금된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import(FileUploadServiceTest.TestConfig.class)
class FileUploadServiceTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @TestConfiguration
    static class TestConfig {
        @Bean ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean AuditService auditService() {
            return mock(AuditService.class);
        }

        @Bean StorageClient storageClient() {
            return mock(StorageClient.class);
        }

        @Bean FileUploadService fileUploadService(FileRepository fileRepo,
                                                  FileVersionRepository versionRepo,
                                                  FolderRepository folderRepo,
                                                  StorageClient storage,
                                                  AuditService audit,
                                                  ObjectMapper mapper) {
            return new FileUploadService(fileRepo, versionRepo, folderRepo, storage, audit, mapper);
        }
    }

    @Autowired private FileUploadService service;
    @Autowired private FileRepository fileRepository;
    @Autowired private FileVersionRepository fileVersionRepository;
    @Autowired private AuditService auditService;
    @Autowired private StorageClient storageClient;
    @Autowired private JdbcTemplate jdbc;

    // ──────────────────────────────────────────────────────────────────
    // happy path
    // ──────────────────────────────────────────────────────────────────

    @Test
    void upload_newFile_persistsRowsAndEmitsFileUploaded() throws IOException {
        UUID owner = insertUser("u1@test", "u1");
        UUID folder = insertFolder(owner, "FolderU1");
        reset(auditService, storageClient);

        byte[] body = "hello world".getBytes();
        UploadResult result = service.upload(folder, owner, "Hello.txt", "text/plain",
            body.length, new ByteArrayInputStream(body), null);

        assertThat(result.newFile()).isTrue();
        assertThat(result.file().getFolderId()).isEqualTo(folder);
        assertThat(result.file().getOwnerId()).isEqualTo(owner);
        assertThat(result.file().getName()).isEqualTo("Hello.txt");
        assertThat(result.file().getNormalizedName()).isEqualTo("hello.txt");
        assertThat(result.file().getSizeBytes()).isEqualTo(body.length);
        assertThat(result.file().getCurrentVersionId()).isEqualTo(result.version().getId());

        assertThat(result.version().getFileId()).isEqualTo(result.file().getId());
        assertThat(result.version().getVersionNumber()).isEqualTo(1);
        assertThat(result.version().getStorageKey()).isNotNull();
        assertThat(result.version().getUploadedBy()).isEqualTo(owner);

        // storage write 호출 — key는 storageKey UUID와 동일해야 함 (ADR #5)
        verify(storageClient, times(1))
            .write(anyString(), org.mockito.ArgumentMatchers.any(InputStream.class), anyLong(), anyString());
        verifyAuditEmitted(AuditEventType.FILE_UPLOADED, result.file().getId(), owner);
    }

    // ──────────────────────────────────────────────────────────────────
    // folder gating
    // ──────────────────────────────────────────────────────────────────

    @Test
    void upload_folderNotFound_throwsAndSkipsStorage() throws IOException {
        UUID owner = insertUser("u2@test", "u2");
        UUID missing = UUID.randomUUID();
        reset(auditService, storageClient);

        byte[] body = "x".getBytes();
        assertThatThrownBy(() -> service.upload(missing, owner, "X.txt", "text/plain",
            body.length, new ByteArrayInputStream(body), null))
            .isInstanceOf(FolderNotFoundException.class);

        verify(storageClient, never())
            .write(anyString(), org.mockito.ArgumentMatchers.any(InputStream.class), anyLong(), anyString());
        verify(auditService, never()).record(anyAuditEvent());
    }

    @Test
    void upload_folderSoftDeleted_throwsFolderNotFound() throws IOException {
        UUID owner = insertUser("u3@test", "u3");
        UUID folder = insertFolder(owner, "FolderU3");
        softDeleteFolder(folder);
        reset(auditService, storageClient);

        byte[] body = "x".getBytes();
        assertThatThrownBy(() -> service.upload(folder, owner, "X.txt", "text/plain",
            body.length, new ByteArrayInputStream(body), null))
            .isInstanceOf(FolderNotFoundException.class);

        verify(storageClient, never())
            .write(anyString(), org.mockito.ArgumentMatchers.any(InputStream.class), anyLong(), anyString());
    }

    // ──────────────────────────────────────────────────────────────────
    // conflict resolution
    // ──────────────────────────────────────────────────────────────────

    @Test
    void upload_conflict_nullResolution_throwsConflict() throws IOException {
        UUID owner = insertUser("u4@test", "u4");
        UUID folder = insertFolder(owner, "FolderU4");
        insertFile(folder, owner, "Dup.txt");
        reset(auditService, storageClient);

        byte[] body = "x".getBytes();
        assertThatThrownBy(() -> service.upload(folder, owner, "Dup.txt", "text/plain",
            body.length, new ByteArrayInputStream(body), null))
            .isInstanceOf(FileNameConflictException.class);

        verify(storageClient, never())
            .write(anyString(), org.mockito.ArgumentMatchers.any(InputStream.class), anyLong(), anyString());
        verify(auditService, never()).record(anyAuditEvent());
    }

    @Test
    void upload_conflict_newVersion_appendsVersionAndEmitsVersionCreated() throws IOException {
        UUID owner = insertUser("u5@test", "u5");
        UUID folder = insertFolder(owner, "FolderU5");
        FileItem existing = insertFile(folder, owner, "Doc.txt");
        FileVersion v1 = insertVersion(existing.getId(), 1, owner);
        existing.setCurrentVersionId(v1.getId());
        fileRepository.saveAndFlush(existing);
        reset(auditService, storageClient);

        byte[] body = "v2 body".getBytes();
        UploadResult result = service.upload(folder, owner, "Doc.txt", "text/plain",
            body.length, new ByteArrayInputStream(body), UploadResolution.NEW_VERSION);

        assertThat(result.newFile()).isFalse();
        assertThat(result.file().getId()).isEqualTo(existing.getId());          // 같은 파일 행
        assertThat(result.version().getFileId()).isEqualTo(existing.getId());
        assertThat(result.version().getVersionNumber()).isEqualTo(2);            // append
        assertThat(result.file().getCurrentVersionId()).isEqualTo(result.version().getId());
        assertThat(result.file().getSizeBytes()).isEqualTo(body.length);         // size 갱신

        verify(storageClient, times(1))
            .write(anyString(), org.mockito.ArgumentMatchers.any(InputStream.class), anyLong(), anyString());
        verifyAuditEmitted(AuditEventType.VERSION_CREATED, existing.getId(), owner);
    }

    @Test
    void upload_conflict_rename_createsNewFileRowWithSuffix() throws IOException {
        UUID owner = insertUser("u6@test", "u6");
        UUID folder = insertFolder(owner, "FolderU6");
        insertFile(folder, owner, "Pic.jpg");
        reset(auditService, storageClient);

        byte[] body = "binary".getBytes();
        UploadResult result = service.upload(folder, owner, "Pic.jpg", "image/jpeg",
            body.length, new ByteArrayInputStream(body), UploadResolution.RENAME);

        assertThat(result.newFile()).isTrue();
        assertThat(result.file().getName()).contains("(1)");                     // suffix 부여
        assertThat(result.version().getVersionNumber()).isEqualTo(1);
        verify(storageClient, times(1))
            .write(anyString(), org.mockito.ArgumentMatchers.any(InputStream.class), anyLong(), anyString());
        verifyAuditEmitted(AuditEventType.FILE_UPLOADED, result.file().getId(), owner);

        // 동일 폴더에 두 행 (원본 + suffix) 활성 상태 공존
        List<FileItem> active = fileRepository.findByFolderIdAndDeletedAtIsNull(folder);
        assertThat(active).hasSize(2);
    }

    // ──────────────────────────────────────────────────────────────────
    // input validation
    // ──────────────────────────────────────────────────────────────────

    @Test
    void upload_blankFilename_throws() throws IOException {
        UUID owner = insertUser("u7@test", "u7");
        UUID folder = insertFolder(owner, "FolderU7");
        reset(auditService, storageClient);

        byte[] body = "x".getBytes();
        assertThatThrownBy(() -> service.upload(folder, owner, "   ", "text/plain",
            body.length, new ByteArrayInputStream(body), null))
            .isInstanceOf(RuntimeException.class);

        verify(storageClient, never())
            .write(anyString(), org.mockito.ArgumentMatchers.any(InputStream.class), anyLong(), anyString());
    }

    // ──────────────────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────────────────

    private void verifyAuditEmitted(AuditEventType expectedType, UUID expectedTargetId, UUID expectedActorId) {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService, times(1)).record(captor.capture());
        AuditEvent ev = captor.getValue();
        assertThat(ev.eventType()).isEqualTo(expectedType);
        assertThat(ev.targetType()).isEqualTo(AuditTargetType.FILE);
        assertThat(ev.targetId()).isEqualTo(expectedTargetId);
        assertThat(ev.actorId()).isEqualTo(expectedActorId);
    }

    private UUID insertUser(String email, String displayName) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)", id, email, displayName);
        return id;
    }

    private UUID insertFolder(UUID ownerId, String name) {
        UUID id = UUID.randomUUID();
        String normalized = name.toLowerCase();
        jdbc.update(
            "INSERT INTO folders(id, parent_id, name, normalized_name, slug, owner_id, audit_level, scope_type, scope_id) " +
            "VALUES (?, NULL, ?, ?, ?, ?, 'standard', 'department', ?)",
            id, name, normalized, normalized, ownerId, java.util.UUID.randomUUID()
        );
        return id;
    }

    private FileItem insertFile(UUID folderId, UUID ownerId, String name) {
        FileItem f = new FileItem();
        f.setId(UUID.randomUUID());
        f.setFolderId(folderId);
        f.setName(name);
        f.setNormalizedName(name.toLowerCase());
        f.setOwnerId(ownerId);
        f.setSizeBytes(0L);
        Instant now = Instant.now();
        f.setCreatedAt(now);
        f.setUpdatedAt(now);
        f.assignScope(com.ibizdrive.folder.ScopeType.DEPARTMENT, java.util.UUID.randomUUID());
        return fileRepository.saveAndFlush(f);
    }

    /** file_versions fixture — V5 NOT NULL 컬럼 모두 채움 (storage_key, checksum, scan_status 포함). */
    private FileVersion insertVersion(UUID fileId, int versionNumber, UUID uploaderId) {
        FileVersion v = new FileVersion();
        v.setId(UUID.randomUUID());
        v.setFileId(fileId);
        v.setVersionNumber(versionNumber);
        v.setStorageKey(UUID.randomUUID());
        v.setSizeBytes(0L);
        v.setChecksumSha256("0".repeat(64));
        v.setScanStatus(VersionScanStatus.PENDING);
        v.setUploadedBy(uploaderId);
        v.setUploadedAt(Instant.now());
        return fileVersionRepository.saveAndFlush(v);
    }

    private void softDeleteFolder(UUID folderId) {
        jdbc.update(
            "UPDATE folders SET deleted_at = NOW(), purge_after = NOW() + INTERVAL '30 days' WHERE id = ?",
            folderId
        );
    }

    private static AuditEvent anyAuditEvent() {
        return org.mockito.ArgumentMatchers.any(AuditEvent.class);
    }
}
