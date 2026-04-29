package com.ibizdrive.file;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FileVersionRepository} entity persistence + V5 schema 제약 가드 검증 (A5.1).
 *
 * <p>{@link com.ibizdrive.folder.FolderRepositoryTest}와 동일한 Testcontainers 패턴.
 * Docker 미가용 환경에서는 자동 스킵({@code disabledWithoutDocker=true}).
 *
 * <p>검증 대상:
 * <ul>
 *   <li>INSERT 후 {@code findByFileIdOrderByVersionNumberDesc} DESC 정렬</li>
 *   <li>{@code (file_id, version_number)} UNIQUE 위반</li>
 *   <li>{@code storage_key} UNIQUE 위반 + {@link FileVersionRepository#existsByStorageKey}</li>
 *   <li>{@code version_number > 0} CHECK 위반</li>
 *   <li>{@code scan_status} CHECK 위반 (enum 외 값은 raw JdbcTemplate으로 유도)</li>
 *   <li>{@link VersionScanStatus} converter 라운드트립</li>
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class FileVersionRepositoryTest {

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

    @Autowired
    private FileVersionRepository fileVersionRepository;

    @Autowired
    private JdbcTemplate jdbc;

    // -------------------- happy path --------------------

    @Test
    void saveVersions_listOrderByVersionNumberDesc_returnsDescending() {
        UUID owner = insertUser("owner1@test", "owner1");
        UUID folder = insertFolder(owner, "folder1");
        UUID file = insertFile(owner, folder, "file1");

        fileVersionRepository.save(newVersion(file, 1, owner));
        fileVersionRepository.save(newVersion(file, 2, owner));
        fileVersionRepository.save(newVersion(file, 3, owner));
        fileVersionRepository.flush();

        List<FileVersion> versions = fileVersionRepository.findByFileIdOrderByVersionNumberDesc(file);

        assertEquals(3, versions.size());
        assertEquals(3, versions.get(0).getVersionNumber(), "DESC 정렬: 최신 버전 먼저");
        assertEquals(2, versions.get(1).getVersionNumber());
        assertEquals(1, versions.get(2).getVersionNumber());
    }

    @Test
    void scanStatusConverter_roundtrip_persistsLowercase() {
        UUID owner = insertUser("conv@test", "conv");
        UUID folder = insertFolder(owner, "convf");
        UUID file = insertFile(owner, folder, "convfile");

        FileVersion v = newVersion(file, 1, owner);
        v.setScanStatus(VersionScanStatus.CLEAN);
        FileVersion saved = fileVersionRepository.save(v);
        fileVersionRepository.flush();

        // DB raw 값은 lowercase여야 한다 (CHECK 제약)
        String raw = jdbc.queryForObject(
            "SELECT scan_status FROM file_versions WHERE id = ?",
            String.class, saved.getId()
        );
        assertEquals("clean", raw, "converter는 lowercase로 영속화");

        // entity 라운드트립
        FileVersion reloaded = fileVersionRepository.findById(saved.getId()).orElseThrow();
        assertEquals(VersionScanStatus.CLEAN, reloaded.getScanStatus(), "라운드트립 시 enum 복원");
    }

    @Test
    void existsByStorageKey_returnsTrue_whenPersisted() {
        UUID owner = insertUser("sk@test", "sk");
        UUID folder = insertFolder(owner, "skf");
        UUID file = insertFile(owner, folder, "skfile");

        FileVersion v = newVersion(file, 1, owner);
        UUID key = v.getStorageKey();
        fileVersionRepository.save(v);
        fileVersionRepository.flush();

        assertTrue(fileVersionRepository.existsByStorageKey(key));
        assertFalse(fileVersionRepository.existsByStorageKey(UUID.randomUUID()));
    }

    // -------------------- UNIQUE / CHECK 가드 --------------------

    @Test
    void duplicateFileIdAndVersionNumber_violatesUnique() {
        UUID owner = insertUser("u2@test", "u2");
        UUID folder = insertFolder(owner, "u2f");
        UUID file = insertFile(owner, folder, "u2file");

        fileVersionRepository.save(newVersion(file, 1, owner));
        fileVersionRepository.flush();

        assertThrows(DataIntegrityViolationException.class, () -> {
            fileVersionRepository.save(newVersion(file, 1, owner));
            fileVersionRepository.flush();
        }, "(file_id, version_number) UNIQUE는 entity 경로에서도 차단");
    }

    @Test
    void duplicateStorageKey_violatesUnique() {
        UUID owner = insertUser("u3@test", "u3");
        UUID folder = insertFolder(owner, "u3f");
        UUID fileA = insertFile(owner, folder, "u3a");
        UUID fileB = insertFile(owner, folder, "u3b");

        FileVersion first = newVersion(fileA, 1, owner);
        UUID sharedKey = first.getStorageKey();
        fileVersionRepository.save(first);
        fileVersionRepository.flush();

        FileVersion second = newVersion(fileB, 1, owner);
        second.setStorageKey(sharedKey);

        assertThrows(DataIntegrityViolationException.class, () -> {
            fileVersionRepository.save(second);
            fileVersionRepository.flush();
        }, "storage_key UNIQUE는 entity 경로에서도 차단");
    }

    @Test
    void versionNumberZero_violatesCheck() {
        UUID owner = insertUser("u4@test", "u4");
        UUID folder = insertFolder(owner, "u4f");
        UUID file = insertFile(owner, folder, "u4file");

        FileVersion v = newVersion(file, 0, owner);

        assertThrows(DataIntegrityViolationException.class, () -> {
            fileVersionRepository.save(v);
            fileVersionRepository.flush();
        }, "version_number > 0 CHECK 위반");
    }

    @Test
    void scanStatusInvalidValue_violatesCheck() {
        UUID owner = insertUser("u5@test", "u5");
        UUID folder = insertFolder(owner, "u5f");
        UUID file = insertFile(owner, folder, "u5file");

        // enum 외 값은 entity 경로로 만들 수 없으므로 raw INSERT로 CHECK 위반 유도.
        assertThrows(Exception.class, () -> jdbc.update(
            "INSERT INTO file_versions(id, file_id, version_number, storage_key, size_bytes, " +
            "checksum_sha256, scan_status, uploaded_by, uploaded_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, 'unknown', ?, NOW())",
            UUID.randomUUID(), file, 1, UUID.randomUUID(), 100L,
            "0".repeat(64), owner
        ), "scan_status CHECK ('pending','clean','infected','error') 위반");
    }

    // ====================== helpers ======================

    /** 테스트용 minimal {@link FileVersion} — V5가 요구하는 NOT NULL 컬럼을 모두 채운다. */
    private FileVersion newVersion(UUID fileId, int versionNumber, UUID uploadedBy) {
        FileVersion v = new FileVersion();
        v.setId(UUID.randomUUID());
        v.setFileId(fileId);
        v.setVersionNumber(versionNumber);
        v.setStorageKey(UUID.randomUUID());
        v.setSizeBytes(1024L);
        v.setChecksumSha256("0".repeat(64));
        v.setMimeType("application/octet-stream");
        v.setScanStatus(VersionScanStatus.PENDING);
        v.setUploadedBy(uploadedBy);
        v.setUploadedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        return v;
    }

    private UUID insertUser(String email, String displayName) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)",
            id, email, displayName
        );
        return id;
    }

    private UUID insertFolder(UUID ownerId, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO folders(id, parent_id, name, normalized_name, slug, owner_id, audit_level) " +
            "VALUES (?, NULL, ?, ?, ?, ?, 'standard')",
            id, name, name, name, ownerId
        );
        assertNotNull(id);
        return id;
    }

    private UUID insertFile(UUID ownerId, UUID folderId, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO files(id, folder_id, name, normalized_name, owner_id, size_bytes) " +
            "VALUES (?, ?, ?, ?, ?, ?)",
            id, folderId, name, name, ownerId, 0L
        );
        return id;
    }
}
