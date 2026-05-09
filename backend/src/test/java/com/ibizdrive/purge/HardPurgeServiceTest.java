package com.ibizdrive.purge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.audit.AuditEvent;
import com.ibizdrive.audit.AuditEventType;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.audit.AuditTargetType;
import com.ibizdrive.file.FileRepository;
import com.ibizdrive.file.FileVersionRepository;
import com.ibizdrive.folder.FolderRepository;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * A7.2 — {@link HardPurgeService} 통합 검증 (V5 schema + JPA + audit emit).
 *
 * <p>실제 Postgres ({@link Testcontainers})에 V5 마이그레이션을 적용. {@link AuditService}는 mock —
 * service 단 emission contract(eventType/targetType/after_state JSON) 만 verify.
 * AuditService 자체의 REQUIRES_NEW + JSONB INSERT는 {@code AuditServiceTest}가 별도 검증.
 *
 * <p>커버리지:
 * <ol>
 *   <li>expired files + versions hard-delete + audit emit</li>
 *   <li>expired folder leaf hard-delete</li>
 *   <li>cascade — 만료 폴더 + 후손 파일이 한 트랜잭션에서 함께 삭제</li>
 *   <li>future purge_after는 skip</li>
 *   <li>limit 도달 시 truncated=true</li>
 *   <li>audit after_state JSON에 7개 필드 정확 직렬화</li>
 * </ol>
 *
 * <p>Docker 미가용 환경에서는 {@code disabledWithoutDocker = true}로 자동 스킵.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import(HardPurgeServiceTest.TestConfig.class)
class HardPurgeServiceTest {

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

        @Bean HardPurgeService hardPurgeService(FileRepository fileRepo,
                                                FolderRepository folderRepo,
                                                FileVersionRepository versionRepo,
                                                AuditService audit,
                                                ObjectMapper mapper) {
            return new HardPurgeService(fileRepo, folderRepo, versionRepo, audit, mapper);
        }
    }

    @Autowired private HardPurgeService service;
    @Autowired private FileRepository fileRepository;
    @Autowired private FolderRepository folderRepository;
    @Autowired private FileVersionRepository fileVersionRepository;
    @Autowired private AuditService auditService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;

    // ====================== tests ======================

    @Test
    void runDailyPurge_hardDeletesExpiredFile_andVersions_andEmitsAudit() throws Exception {
        reset(auditService);
        UUID owner = insertUser("svc1@test", "svc1");
        UUID folder = insertFolder(owner, "svc1f");
        UUID file = insertFile(owner, folder, "svc1file");
        UUID k1 = saveVersion(file, 1, owner);
        UUID k2 = saveVersion(file, 2, owner);
        Instant past = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
        softDeleteFile(file, past);

        PurgeResult result = service.runDailyPurge(100);

        assertThat(result.purgedFiles()).isEqualTo(1);
        assertThat(result.purgedFolders()).isEqualTo(0);
        assertThat(result.orphanStorageKeys()).containsExactlyInAnyOrder(k1, k2);
        assertThat(result.orphanStorageKeysTruncated()).isFalse();
        assertThat(result.truncated()).isFalse();

        // file row 영구 삭제됨
        assertThat(countFiles(file)).isZero();
        // version row도 cascade 삭제됨
        assertThat(fileVersionRepository.findByFileIdOrderByVersionNumberDesc(file)).isEmpty();

        // audit emit 1회
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService, times(1)).record(captor.capture());
        AuditEvent ev = captor.getValue();
        assertThat(ev.eventType()).isEqualTo(AuditEventType.SYSTEM_PURGE_EXECUTED);
        assertThat(ev.targetType()).isEqualTo(AuditTargetType.SYSTEM);
        assertThat(ev.targetId()).isNull();
        assertThat(ev.actorId()).isNull();
    }

    @Test
    void runDailyPurge_hardDeletesExpiredLeafFolder() {
        reset(auditService);
        UUID owner = insertUser("svc2@test", "svc2");
        UUID leaf = insertFolder(owner, "leaf2");
        Instant past = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
        softDeleteFolder(leaf, past);

        PurgeResult result = service.runDailyPurge(100);

        assertThat(result.purgedFolders()).isEqualTo(1);
        assertThat(countFolders(leaf)).isZero();
        verify(auditService, times(1)).record(any(AuditEvent.class));
    }

    @Test
    void runDailyPurge_cascadesFolderTreeAndFiles_inOneTransaction() {
        reset(auditService);
        UUID owner = insertUser("casc@test", "casc");
        UUID parent = insertFolder(owner, "cparent");
        UUID child = insertFolderWithParent(owner, "cchild", parent);
        // 후손 파일도 같은 batch에서 만료
        UUID file = insertFile(owner, child, "cfile");
        saveVersion(file, 1, owner);

        Instant past = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
        softDeleteFolder(parent, past);
        softDeleteFolder(child, past);
        softDeleteFile(file, past);

        PurgeResult result = service.runDailyPurge(100);

        // file 1 + folders 2 모두 삭제
        assertThat(result.purgedFiles()).isEqualTo(1);
        assertThat(result.purgedFolders()).isEqualTo(2);
        assertThat(countFiles(file)).isZero();
        assertThat(countFolders(child)).isZero();
        assertThat(countFolders(parent)).isZero();
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void runDailyPurge_skipsRowsWithFuturePurgeAfter() {
        reset(auditService);
        UUID owner = insertUser("fut@test", "fut");
        UUID folder = insertFolder(owner, "futf");
        UUID file = insertFile(owner, folder, "futfile");
        // soft-deleted but purge_after = now + 1 day → 스킵되어야 함
        Instant future = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
        softDeleteFile(file, future);

        PurgeResult result = service.runDailyPurge(100);

        assertThat(result.purgedFiles()).isEqualTo(0);
        assertThat(countFiles(file)).isEqualTo(1);
        // file row 그대로 남아있음 (soft-delete 상태 유지)
        Boolean stillSoftDeleted = jdbc.queryForObject(
            "SELECT deleted_at IS NOT NULL FROM files WHERE id = ?", Boolean.class, file);
        assertThat(stillSoftDeleted).isTrue();
        // audit은 여전히 emit (0건 run도 기록)
        verify(auditService, times(1)).record(any(AuditEvent.class));
    }

    @Test
    void runDailyPurge_setsTruncatedWhenLimitReached() {
        reset(auditService);
        UUID owner = insertUser("trunc@test", "trunc");
        UUID folder = insertFolder(owner, "tnf");
        Instant past = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);

        // 3개 만료 row 준비
        UUID f1 = insertFile(owner, folder, "t1");
        UUID f2 = insertFile(owner, folder, "t2");
        UUID f3 = insertFile(owner, folder, "t3");
        softDeleteFile(f1, past.minus(3, ChronoUnit.HOURS));
        softDeleteFile(f2, past.minus(2, ChronoUnit.HOURS));
        softDeleteFile(f3, past.minus(1, ChronoUnit.HOURS));

        // limit=2 → 2건 처리, truncated=true
        PurgeResult result = service.runDailyPurge(2);

        assertThat(result.purgedFiles()).isEqualTo(2);
        assertThat(result.truncated()).isTrue();
        // 가장 오래된 2건이 삭제, 마지막 1건은 다음 run으로 이월
        assertThat(countFiles(f1)).isZero();
        assertThat(countFiles(f2)).isZero();
        assertThat(countFiles(f3)).isEqualTo(1);
    }

    @Test
    void runDailyPurge_auditAfterStateContainsAllSevenFields() throws Exception {
        reset(auditService);
        UUID owner = insertUser("af@test", "af");
        UUID folder = insertFolder(owner, "aff");
        UUID file = insertFile(owner, folder, "affile");
        UUID key = saveVersion(file, 1, owner);
        Instant past = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
        softDeleteFile(file, past);

        PurgeResult result = service.runDailyPurge(50);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService, times(1)).record(captor.capture());
        String afterJson = captor.getValue().afterState();
        assertThat(afterJson).isNotNull();

        JsonNode node = objectMapper.readTree(afterJson);
        assertThat(node.has("runId")).isTrue();
        assertThat(node.get("runId").asText()).isEqualTo(result.runId().toString());
        assertThat(node.get("purgedFiles").asInt()).isEqualTo(1);
        assertThat(node.get("purgedFolders").asInt()).isEqualTo(0);
        assertThat(node.get("orphanStorageKeys").isArray()).isTrue();
        assertThat(node.get("orphanStorageKeys").size()).isEqualTo(1);
        assertThat(node.get("orphanStorageKeys").get(0).asText()).isEqualTo(key.toString());
        assertThat(node.get("orphanStorageKeysTruncated").asBoolean()).isFalse();
        assertThat(node.has("durationMs")).isTrue();
        assertThat(node.get("durationMs").asLong()).isGreaterThanOrEqualTo(0L);
        assertThat(node.get("truncated").asBoolean()).isFalse();
    }

    @Test
    void runDailyPurge_rejectsNonPositiveMaxPerRun() {
        reset(auditService);
        assertThatThrownBy(() -> service.runDailyPurge(0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.runDailyPurge(-1))
            .isInstanceOf(IllegalArgumentException.class);
        verify(auditService, never()).record(any(AuditEvent.class));
    }

    // ====================== helpers ======================

    private UUID insertUser(String email, String displayName) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)",
            id, email, displayName);
        return id;
    }

    private UUID insertFolder(UUID owner, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO folders(id, parent_id, name, normalized_name, slug, owner_id, audit_level, scope_type, scope_id) " +
            "VALUES (?, NULL, ?, ?, ?, ?, 'standard', 'department', ?)",
            id, name, name, name, owner, java.util.UUID.randomUUID());
        return id;
    }

    private UUID insertFolderWithParent(UUID owner, String name, UUID parent) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO folders(id, parent_id, name, normalized_name, slug, owner_id, audit_level, scope_type, scope_id) " +
            "VALUES (?, ?, ?, ?, ?, ?, 'standard', 'department', ?)",
            id, parent, name, name, name, owner, java.util.UUID.randomUUID());
        return id;
    }

    private UUID insertFile(UUID owner, UUID folder, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO files(id, folder_id, name, normalized_name, owner_id, size_bytes, scope_type, scope_id) " +
            "VALUES (?, ?, ?, ?, ?, ?, 'department', ?)",
            id, folder, name, name, owner, 0L, java.util.UUID.randomUUID());
        return id;
    }

    private void softDeleteFile(UUID fileId, Instant purgeAfter) {
        jdbc.update("UPDATE files SET deleted_at = ?, purge_after = ? WHERE id = ?",
            java.sql.Timestamp.from(purgeAfter), java.sql.Timestamp.from(purgeAfter), fileId);
    }

    private void softDeleteFolder(UUID folderId, Instant purgeAfter) {
        jdbc.update("UPDATE folders SET deleted_at = ?, purge_after = ? WHERE id = ?",
            java.sql.Timestamp.from(purgeAfter), java.sql.Timestamp.from(purgeAfter), folderId);
    }

    private UUID saveVersion(UUID fileId, int versionNumber, UUID uploadedBy) {
        UUID storageKey = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO file_versions(id, file_id, version_number, storage_key, size_bytes, " +
            "checksum_sha256, mime_type, scan_status, uploaded_by, uploaded_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, 'pending', ?, NOW())",
            UUID.randomUUID(), fileId, versionNumber, storageKey, 1024L,
            "0".repeat(64), "application/octet-stream", uploadedBy);
        return storageKey;
    }

    private int countFiles(UUID id) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM files WHERE id = ?", Integer.class, id);
        return n == null ? 0 : n;
    }

    private int countFolders(UUID id) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM folders WHERE id = ?", Integer.class, id);
        return n == null ? 0 : n;
    }
}
