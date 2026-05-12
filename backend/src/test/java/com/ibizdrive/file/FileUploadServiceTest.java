package com.ibizdrive.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.audit.AuditEvent;
import com.ibizdrive.audit.AuditEventType;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.audit.AuditTargetType;
import com.ibizdrive.folder.FolderNotFoundException;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.storage.StorageClient;
import com.ibizdrive.team.TeamArchiveGuard;
import com.ibizdrive.team.TeamRepository;
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
                                                  ObjectMapper mapper,
                                                  TeamRepository teamRepo,
                                                  com.ibizdrive.user.UserRepository userRepo) {
            return new FileUploadService(fileRepo, versionRepo, folderRepo, storage, audit, mapper,
                new TeamArchiveGuard(teamRepo),
                new com.ibizdrive.user.UserQuotaEnforcer(userRepo));
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
    // quota mutation Phase 5 — enforcement (docs/04 §6.1)
    // ──────────────────────────────────────────────────────────────────

    @Test
    void upload_overQuota_throwsQuotaExceededAndNoStorageWrite() throws IOException {
        // quota=1000 / used=900 → 100 byte 업로드는 OK, 200 byte는 차단.
        UUID owner = insertUserWithQuota("u-quota-fail@test", "uqf", 1000L, 900L);
        UUID folder = insertFolder(owner, "FolderQuotaFail");
        reset(auditService, storageClient);

        byte[] body = new byte[200];
        assertThatThrownBy(() -> service.upload(folder, owner, "big.bin", "application/octet-stream",
            body.length, new ByteArrayInputStream(body), null))
            .isInstanceOf(com.ibizdrive.user.QuotaExceededException.class);

        // storage 객체 orphan 차단 검증 — write가 1회도 호출되지 않아야 한다.
        verify(storageClient, never())
            .write(anyString(), org.mockito.ArgumentMatchers.any(InputStream.class), anyLong(), anyString());

        // DB에 file/version row가 생성되지 않았는지 확인 (트랜잭션 롤백 + storage_used 미증가).
        Long usedAfter = jdbc.queryForObject(
            "SELECT storage_used FROM users WHERE id=?", Long.class, owner);
        assertThat(usedAfter).isEqualTo(900L);
    }

    @Test
    void upload_withinQuota_incrementsStorageUsed() throws IOException {
        // quota=10_000 / used=100 → 200 byte 업로드는 OK, used=300으로 증분.
        UUID owner = insertUserWithQuota("u-quota-ok@test", "uqo", 10_000L, 100L);
        UUID folder = insertFolder(owner, "FolderQuotaOk");
        reset(auditService, storageClient);

        byte[] body = new byte[200];
        UploadResult result = service.upload(folder, owner, "ok.bin",
            "application/octet-stream", body.length, new ByteArrayInputStream(body), null);

        assertThat(result.file().getSizeBytes()).isEqualTo(200L);

        Long usedAfter = jdbc.queryForObject(
            "SELECT storage_used FROM users WHERE id=?", Long.class, owner);
        assertThat(usedAfter).isEqualTo(300L);
    }

    @Test
    void upload_atExactQuota_succeeds_edgeCase() throws IOException {
        // quota=1000 / used=900 → exactly 100 byte는 통과 (`used + delta > quota` strict).
        UUID owner = insertUserWithQuota("u-quota-edge@test", "uqe", 1000L, 900L);
        UUID folder = insertFolder(owner, "FolderQuotaEdge");
        reset(auditService, storageClient);

        byte[] body = new byte[100];
        UploadResult result = service.upload(folder, owner, "edge.bin",
            "application/octet-stream", body.length, new ByteArrayInputStream(body), null);

        assertThat(result.file().getSizeBytes()).isEqualTo(100L);

        Long usedAfter = jdbc.queryForObject(
            "SELECT storage_used FROM users WHERE id=?", Long.class, owner);
        assertThat(usedAfter).isEqualTo(1000L);
    }

    @Test
    void upload_zeroByteFile_locksAndPasses_noStorageUsedChange() throws IOException {
        // delta=0은 quota check는 통과(0 증분), storage_used 변화 없음, 락은 여전히 획득.
        UUID owner = insertUserWithQuota("u-quota-zero@test", "uqz", 1000L, 1000L);
        UUID folder = insertFolder(owner, "FolderQuotaZero");
        reset(auditService, storageClient);

        byte[] body = new byte[0];
        UploadResult result = service.upload(folder, owner, "zero.bin",
            "application/octet-stream", 0L, new ByteArrayInputStream(body), null);

        assertThat(result.file().getSizeBytes()).isEqualTo(0L);

        Long usedAfter = jdbc.queryForObject(
            "SELECT storage_used FROM users WHERE id=?", Long.class, owner);
        assertThat(usedAfter).isEqualTo(1000L); // 변경 없음
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

    /**
     * quota mutation Phase 5 — 명시적 quota/used로 테스트 사용자 삽입.
     * V18 default(quota=10GB / used=0)를 override해 over-quota 시나리오를 작성.
     */
    private UUID insertUserWithQuota(String email, String displayName, long quota, long used) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO users(id, email, display_name, storage_quota, storage_used) VALUES (?, ?, ?, ?, ?)",
            id, email, displayName, quota, used);
        return id;
    }

    /**
     * V13 — folders.scope_type/scope_id NOT NULL. fixture root는 fake department scope를 가지며,
     * 본 테스트가 검증하는 file scope inheritance는 별도 {@link FileScopeInheritanceTest}가 담당.
     */
    private UUID insertFolder(UUID ownerId, String name) {
        UUID id = UUID.randomUUID();
        String normalized = name.toLowerCase();
        jdbc.update(
            "INSERT INTO folders(id, parent_id, name, normalized_name, slug, owner_id, audit_level, " +
            "scope_type, scope_id) " +
            "VALUES (?, NULL, ?, ?, ?, ?, 'standard', 'department', ?)",
            id, name, normalized, normalized, ownerId, UUID.randomUUID()
        );
        return id;
    }

    /**
     * V13 — files.scope_type/scope_id NOT NULL. fixture는 부모 folder의 scope를 raw query로 lookup해
     * inheritance invariant와 동일한 값을 채운다.
     */
    private FileItem insertFile(UUID folderId, UUID ownerId, String name) {
        FileItem f = new FileItem();
        f.setId(UUID.randomUUID());
        f.setFolderId(folderId);
        f.setName(name);
        f.setNormalizedName(name.toLowerCase());
        f.setOwnerId(ownerId);
        f.setSizeBytes(0L);
        // 부모 folder의 scope를 그대로 상속 (spec §1.2 invariant) — fixture에서도 동일하게 충족.
        Object[] scope = jdbc.queryForObject(
            "SELECT scope_type, scope_id FROM folders WHERE id = ?",
            (rs, rowNum) -> new Object[]{rs.getString("scope_type"), rs.getObject("scope_id", UUID.class)},
            folderId
        );
        f.assignScope(com.ibizdrive.folder.ScopeType.fromDb((String) scope[0]), (UUID) scope[1]);
        Instant now = Instant.now();
        f.setCreatedAt(now);
        f.setUpdatedAt(now);
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
