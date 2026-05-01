package com.ibizdrive.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Storage orphan cleanup E2E — 실 Postgres + 실 LocalFs + 실 service 결합 검증.
 *
 * <p><b>시나리오</b>:
 * <ol>
 *   <li>file_versions에 storage_key={live} 행 1개 삽입(active file 소속).</li>
 *   <li>LocalFs root에 객체 3개 배치 + mtime 조작:
 *       (a) live key 객체(grace 통과) — 보존 대상,
 *       (b) orphan UUID 객체(grace 통과) — 삭제 대상,
 *       (c) orphan UUID 객체(grace 미통과 = 신규) — 보존(in-flight 보호).</li>
 *   <li>{@link StorageOrphanCleanupJob#run()} 호출.</li>
 *   <li>(b)만 삭제, (a)(c) 보존, audit 1건 발행.</li>
 * </ol>
 *
 * <p>{@code @Testcontainers(disabledWithoutDocker = true)} → Docker 미가용 환경 자동 skip.
 *
 * <p>{@code ibizdrive.storage.local.root}는 {@code @TempDir}로 부팅 전 결정 — Spring 의 부트 시점
 * @{@code DynamicPropertySource}가 정적 호출되므로 임시 디렉터리를 미리 생성해 등록.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
    "app.storage.orphan-cleanup.enabled=true",
    "app.storage.orphan-cleanup.cron=0 0 1 * * *",
    "app.storage.orphan-cleanup.zone=Asia/Seoul",
    "app.storage.orphan-cleanup.max-per-run=1000",
    "app.storage.orphan-cleanup.grace-hours=24",
    "ibizdrive.storage.type=local"
})
class StorageOrphanCleanupIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @TempDir
    static Path storageRoot;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("ibizdrive.storage.local.root", () -> storageRoot.toString());
    }

    @Autowired StorageOrphanCleanupService service;
    @Autowired StorageOrphanCleanupJob job;
    @Autowired LocalFsStorageClient storageClient;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationContext ctx;

    @Test
    void jobBeanRegistered_whenEnabledTrue() {
        assertThat(ctx.getBeansOfType(StorageOrphanCleanupJob.class)).hasSize(1);
    }

    @Test
    void runDailyCleanup_deletesOrphansOnly_preservesLiveAndInFlight() throws IOException {
        // 1) DB seed — owner / folder / file / file_version with storage_key=liveKey.
        UUID owner = insertUser("oc5@test", "oc5");
        UUID folder = insertFolder(owner, "oc5f");
        UUID file = insertFile(owner, folder, "oc5file");
        UUID liveKey = UUID.randomUUID();
        insertFileVersion(file, 1, owner, liveKey);

        // 2) Storage seed — 3 objects.
        Instant olderThanGrace = Instant.now().minus(Duration.ofHours(48));
        Instant withinGrace = Instant.now().minus(Duration.ofMinutes(5));

        UUID orphanOld = UUID.randomUUID();
        UUID orphanNew = UUID.randomUUID();

        writeAt(storageClient, "2026/05/" + liveKey, olderThanGrace);
        writeAt(storageClient, "2026/05/" + orphanOld, olderThanGrace);
        writeAt(storageClient, "2026/05/" + orphanNew, withinGrace);

        // 3) Run.
        StorageOrphanCleanupResult result = service.runDailyCleanup(1000, 24);

        // 4) Assertions.
        assertThat(result.scanned()).isEqualTo(2); // grace 통과만 walk
        assertThat(result.candidates()).isEqualTo(1); // orphanOld만 candidate
        assertThat(result.deleted()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(result.truncated()).isFalse();

        assertThat(storageClient.exists("2026/05/" + liveKey)).isTrue();
        assertThat(storageClient.exists("2026/05/" + orphanOld)).isFalse();
        assertThat(storageClient.exists("2026/05/" + orphanNew)).isTrue();
    }

    // ====================== helpers ======================

    private void writeAt(LocalFsStorageClient client, String key, Instant mtime) throws IOException {
        client.write(key, new ByteArrayInputStream(new byte[]{0x42}), 1, "application/octet-stream");
        Files.setLastModifiedTime(storageRoot.resolve(key), FileTime.from(mtime));
    }

    private void insertFileVersion(UUID fileId, int versionNumber, UUID uploadedBy, UUID storageKey) {
        // raw INSERT — FileVersion entity 생성자는 protected (다른 패키지 접근 불가).
        // V5 NOT NULL + CHECK 제약을 모두 채워야 한다.
        jdbc.update(
            "INSERT INTO file_versions(id, file_id, version_number, storage_key, size_bytes, " +
            "checksum_sha256, mime_type, scan_status, uploaded_by, uploaded_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, 'pending', ?, NOW())",
            UUID.randomUUID(), fileId, versionNumber, storageKey, 1024L,
            "0".repeat(64), "application/octet-stream", uploadedBy
        );
    }

    private UUID insertUser(String email, String displayName) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)",
            id, email, displayName);
        return id;
    }

    private UUID insertFolder(UUID ownerId, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO folders(id, parent_id, name, normalized_name, slug, owner_id, audit_level) " +
            "VALUES (?, NULL, ?, ?, ?, ?, 'standard')",
            id, name, name, name, ownerId);
        return id;
    }

    private UUID insertFile(UUID ownerId, UUID folderId, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO files(id, folder_id, name, normalized_name, owner_id, size_bytes) " +
            "VALUES (?, ?, ?, ?, ?, ?)",
            id, folderId, name, name, ownerId, 0L);
        return id;
    }
}
