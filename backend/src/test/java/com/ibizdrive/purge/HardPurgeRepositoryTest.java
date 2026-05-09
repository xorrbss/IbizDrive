package com.ibizdrive.purge;

import com.ibizdrive.file.FileRepository;
import com.ibizdrive.file.FileVersionRepository;
import com.ibizdrive.folder.FolderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A7.1 — hard purge 후보 조회 / cascade delete / parent map 보조 메서드의 V5 schema 정합 검증.
 *
 * <p>{@link FileVersionRepositoryTest}와 동일한 Testcontainers 패턴
 * ({@code @DataJpaTest} + Postgres 15-alpine + {@code disabledWithoutDocker=true}).
 *
 * <p>검증 대상:
 * <ul>
 *   <li>{@link FileRepository#findExpiredFileIds} — purge_after <= now + 정렬 + limit + soft-deleted 한정</li>
 *   <li>{@link FileRepository#hardDeleteByIds} — file row 영구 삭제 (cascade는 호출자 책임)</li>
 *   <li>{@link FolderRepository#findExpiredFolderIds} — 동일 패턴 (folder)</li>
 *   <li>{@link FolderRepository#hardDeleteByIds} — folder row 영구 삭제 (FK RESTRICT 만족 가정)</li>
 *   <li>{@link FolderRepository#findIdAndParentIdByIds} — leaf-first 위상정렬 입력</li>
 *   <li>{@link FileVersionRepository#findStorageKeysByFileIds} / {@link FileVersionRepository#deleteByFileIds}
 *       — A7 audit orphan 수집 + cascade 삭제</li>
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class HardPurgeRepositoryTest {

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

    @Autowired private FileRepository fileRepository;
    @Autowired private FolderRepository folderRepository;
    @Autowired private FileVersionRepository fileVersionRepository;
    @Autowired private JdbcTemplate jdbc;

    // -------------------- file: find expired --------------------

    @Test
    void findExpiredFileIds_returnsOnlySoftDeletedAndPastPurgeAfter() {
        UUID owner = insertUser("ef@test", "ef");
        UUID folder = insertFolder(owner, "eff");

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        UUID active = insertFile(owner, folder, "active");          // deleted_at NULL → 미포함
        UUID expired = softDeleteFile(insertFile(owner, folder, "expired"), now.minus(1, ChronoUnit.DAYS));
        UUID future = softDeleteFile(insertFile(owner, folder, "future"), now.plus(1, ChronoUnit.DAYS));

        List<UUID> ids = fileRepository.findExpiredFileIds(now, 100);

        assertTrue(ids.contains(expired), "purge_after <= now 만 포함");
        assertFalse(ids.contains(future), "future purge_after는 제외");
        assertFalse(ids.contains(active), "활성 file은 제외");
    }

    @Test
    void findExpiredFileIds_respectsLimit_andOrderByPurgeAfter() {
        UUID owner = insertUser("lim@test", "lim");
        UUID folder = insertFolder(owner, "limf");
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

        UUID oldest = softDeleteFile(insertFile(owner, folder, "f1"), now.minus(3, ChronoUnit.DAYS));
        UUID middle = softDeleteFile(insertFile(owner, folder, "f2"), now.minus(2, ChronoUnit.DAYS));
        UUID newest = softDeleteFile(insertFile(owner, folder, "f3"), now.minus(1, ChronoUnit.DAYS));

        List<UUID> top2 = fileRepository.findExpiredFileIds(now, 2);

        assertEquals(2, top2.size(), "limit 적용");
        assertEquals(oldest, top2.get(0), "purge_after ASC — 가장 오래된 것 우선");
        assertEquals(middle, top2.get(1));
        assertFalse(top2.contains(newest));
    }

    // -------------------- file: hard delete --------------------

    @Test
    void hardDeleteByIds_removesFileRows() {
        UUID owner = insertUser("hd@test", "hd");
        UUID folder = insertFolder(owner, "hdf");
        Instant past = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
        UUID f1 = softDeleteFile(insertFile(owner, folder, "h1"), past);
        UUID f2 = softDeleteFile(insertFile(owner, folder, "h2"), past);

        int n = fileRepository.hardDeleteByIds(List.of(f1, f2));
        fileRepository.flush();

        assertEquals(2, n);
        assertEquals(0, countFiles(f1), "f1 row 영구 삭제됨");
        assertEquals(0, countFiles(f2), "f2 row 영구 삭제됨");
    }

    // -------------------- folder: find expired + parent map --------------------

    @Test
    void findExpiredFolderIds_andParentIdMap_supportTopoSort() {
        UUID owner = insertUser("fp@test", "fp");
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

        UUID root = insertFolder(owner, "froot");
        UUID child = insertFolderWithParent(owner, "fchild", root);
        UUID grand = insertFolderWithParent(owner, "fgrand", child);

        // root, child, grand 모두 만료 — leaf-first는 grand → child → root 순
        Instant past = now.minus(1, ChronoUnit.DAYS);
        softDeleteFolder(grand, past);
        softDeleteFolder(child, past);
        softDeleteFolder(root, past);

        List<UUID> expired = folderRepository.findExpiredFolderIds(now, 100);
        assertTrue(expired.containsAll(List.of(root, child, grand)));

        List<Object[]> rows = folderRepository.findIdAndParentIdByIds(List.of(root, child, grand));
        Map<UUID, UUID> parents = new HashMap<>();
        for (Object[] r : rows) {
            parents.put((UUID) r[0], (UUID) r[1]);
        }
        assertEquals(3, parents.size());
        assertNull(parents.get(root), "root.parent_id IS NULL");
        assertEquals(root, parents.get(child));
        assertEquals(child, parents.get(grand));
    }

    @Test
    void hardDeleteFolder_succeeds_whenChildrenAlreadyPurged() {
        UUID owner = insertUser("dh@test", "dh");
        UUID parent = insertFolder(owner, "dhp");
        UUID child = insertFolderWithParent(owner, "dhc", parent);
        Instant past = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
        softDeleteFolder(parent, past);
        softDeleteFolder(child, past);

        // leaf-first: 자식 먼저 삭제
        folderRepository.hardDeleteByIds(List.of(child));
        folderRepository.flush();
        // 그 후 부모 삭제 가능 (parent_id ON DELETE RESTRICT 만족)
        folderRepository.hardDeleteByIds(List.of(parent));
        folderRepository.flush();

        assertEquals(0, countFolders(child));
        assertEquals(0, countFolders(parent));
    }

    // -------------------- file_versions: cascade --------------------

    @Test
    void findStorageKeysByFileIds_collectsAllVersionsKeys() {
        UUID owner = insertUser("sk@test", "sk");
        UUID folder = insertFolder(owner, "skf");
        UUID file = insertFile(owner, folder, "skfile");
        UUID k1 = saveVersion(file, 1, owner);
        UUID k2 = saveVersion(file, 2, owner);

        List<UUID> keys = fileVersionRepository.findStorageKeysByFileIds(List.of(file));

        assertEquals(2, keys.size());
        assertTrue(keys.contains(k1));
        assertTrue(keys.contains(k2));
    }

    @Test
    void deleteByFileIds_removesAllVersionsForFile() {
        UUID owner = insertUser("dv@test", "dv");
        UUID folder = insertFolder(owner, "dvf");
        UUID file = insertFile(owner, folder, "dvfile");
        saveVersion(file, 1, owner);
        saveVersion(file, 2, owner);

        int n = fileVersionRepository.deleteByFileIds(List.of(file));
        fileVersionRepository.flush();

        assertEquals(2, n);
        assertEquals(0, fileVersionRepository.findByFileIdOrderByVersionNumberDesc(file).size());

        // version cascade 후 file row 자체도 hard delete 가능
        // (current_version_id self-FK는 DEFERRABLE INITIALLY DEFERRED — docs/02 §2.5 line 177)
        jdbc.update("UPDATE files SET current_version_id = NULL WHERE id = ?", file);
        // file row는 이 테스트의 검증 범위 밖 — version 삭제 자체만 검증
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

    private UUID softDeleteFile(UUID fileId, Instant purgeAfter) {
        jdbc.update("UPDATE files SET deleted_at = ?, purge_after = ? WHERE id = ?",
            java.sql.Timestamp.from(purgeAfter), java.sql.Timestamp.from(purgeAfter), fileId);
        return fileId;
    }

    private UUID softDeleteFolder(UUID folderId, Instant purgeAfter) {
        jdbc.update("UPDATE folders SET deleted_at = ?, purge_after = ? WHERE id = ?",
            java.sql.Timestamp.from(purgeAfter), java.sql.Timestamp.from(purgeAfter), folderId);
        return folderId;
    }

    /** {@link com.ibizdrive.file.FileVersion} 생성자가 패키지 외부에서 protected이므로 raw INSERT 사용. */
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
