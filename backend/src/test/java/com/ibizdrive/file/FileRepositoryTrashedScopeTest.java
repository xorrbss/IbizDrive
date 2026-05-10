package com.ibizdrive.file;

import com.ibizdrive.folder.ScopeType;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FileRepository#findTrashedPageByScope} scope filter 검증 (Plan E T1).
 *
 * <p>두 가지 검증:
 * <ol>
 *   <li><b>scope isolation</b> — DEPT scope row만 반환, TEAM scope row 제외.</li>
 *   <li><b>cursor pagination 회귀</b> — 커서 기준 이전(deleted_at ASC) row만 반환.</li>
 * </ol>
 *
 * <p>{@link com.ibizdrive.folder.FolderRepositoryTrashedScopeTest}의 File 버전.
 * Docker 미가용 환경에서는 {@code disabledWithoutDocker = true}로 자동 스킵.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class FileRepositoryTrashedScopeTest {

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
    private FileRepository fileRepository;

    @Autowired
    private JdbcTemplate jdbc;

    // ------------------- scope isolation -------------------

    /**
     * DEPT scope 2개, TEAM scope 2개 trashed fixture → findTrashedPageByScope(DEPT, deptId)는
     * 해당 DEPT의 2건만 반환하고 TEAM scope row를 포함하지 않아야 한다.
     */
    @Test
    void findTrashedPageByScope_returnsOnlyScopeMatchingRows() {
        UUID owner = insertUser("scope-file1@test", "scope-file-owner1");
        UUID deptId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID folderId = insertFolder(owner, "folder-scope-1");

        Instant t1 = Instant.now().truncatedTo(ChronoUnit.MICROS).minusSeconds(100);
        Instant t2 = t1.minusSeconds(10);

        // DEPT scope trashed files
        FileItem deptFile1 = trashedFile(owner, folderId, "dept-trash-a.txt", ScopeType.DEPARTMENT, deptId, t1);
        FileItem deptFile2 = trashedFile(owner, folderId, "dept-trash-b.txt", ScopeType.DEPARTMENT, deptId, t2);
        fileRepository.save(deptFile1);
        fileRepository.save(deptFile2);

        // TEAM scope trashed files (must not appear in DEPT query)
        FileItem teamFile1 = trashedFile(owner, folderId, "team-trash-a.txt", ScopeType.TEAM, teamId, t1);
        FileItem teamFile2 = trashedFile(owner, folderId, "team-trash-b.txt", ScopeType.TEAM, teamId, t2);
        fileRepository.save(teamFile1);
        fileRepository.save(teamFile2);

        fileRepository.flush();

        List<FileItem> result = fileRepository.findTrashedPageByScope(
            ScopeType.DEPARTMENT.dbValue(), deptId, null, null, 10
        );

        assertEquals(2, result.size(), "DEPT scope는 정확히 2건");
        assertTrue(result.stream().allMatch(f -> f.getScopeType() == ScopeType.DEPARTMENT),
            "반환된 모든 row는 DEPARTMENT scope여야 함");
        assertTrue(result.stream().allMatch(f -> deptId.equals(f.getScopeId())),
            "반환된 모든 row의 scope_id는 deptId와 일치해야 함");
    }

    /**
     * 다른 DEPT id의 row는 같은 scopeType이라도 제외되어야 한다.
     */
    @Test
    void findTrashedPageByScope_excludesDifferentScopeId() {
        UUID owner = insertUser("scope-file2@test", "scope-file-owner2");
        UUID deptA = UUID.randomUUID();
        UUID deptB = UUID.randomUUID();
        UUID folderId = insertFolder(owner, "folder-scope-2");

        Instant t = Instant.now().truncatedTo(ChronoUnit.MICROS).minusSeconds(200);

        fileRepository.save(trashedFile(owner, folderId, "dept-a-file.txt", ScopeType.DEPARTMENT, deptA, t));
        fileRepository.save(trashedFile(owner, folderId, "dept-b-file.txt", ScopeType.DEPARTMENT, deptB, t));
        fileRepository.flush();

        List<FileItem> result = fileRepository.findTrashedPageByScope(
            ScopeType.DEPARTMENT.dbValue(), deptA, null, null, 10
        );

        assertEquals(1, result.size(), "deptA만 1건");
        assertEquals(deptA, result.get(0).getScopeId());
    }

    // ------------------- cursor pagination -------------------

    /**
     * 커서 기준으로 이전 row만 반환하는 cursor pagination 회귀 검증.
     * deleted_at DESC, id DESC 정렬에서 커서 tuple보다 strictly less than인 row만 반환.
     */
    @Test
    void findTrashedPageByScope_cursorPagination_returnsRowsBefore() {
        UUID owner = insertUser("scope-file3@test", "scope-file-owner3");
        UUID deptId = UUID.randomUUID();
        UUID folderId = insertFolder(owner, "folder-scope-3");

        Instant t1 = Instant.now().truncatedTo(ChronoUnit.MICROS).minusSeconds(300);
        Instant t2 = t1.minusSeconds(30);
        Instant t3 = t2.minusSeconds(30);

        // 3개 삽입: t1 > t2 > t3 (deleted_at DESC 순서)
        FileItem f1 = trashedFile(owner, folderId, "cursor-file-1.txt", ScopeType.DEPARTMENT, deptId, t1);
        FileItem f2 = trashedFile(owner, folderId, "cursor-file-2.txt", ScopeType.DEPARTMENT, deptId, t2);
        FileItem f3 = trashedFile(owner, folderId, "cursor-file-3.txt", ScopeType.DEPARTMENT, deptId, t3);
        fileRepository.save(f1);
        fileRepository.save(f2);
        fileRepository.save(f3);
        fileRepository.flush();

        // cursor = f2의 (deletedAt, id) → f2보다 이전인 f3만 반환
        List<FileItem> page2 = fileRepository.findTrashedPageByScope(
            ScopeType.DEPARTMENT.dbValue(), deptId, f2.getDeletedAt(), f2.getId(), 10
        );

        assertEquals(1, page2.size(), "커서 이후 페이지는 f3 1건만");
        assertEquals(f3.getId(), page2.get(0).getId());
    }

    /**
     * limit이 결과 수를 제한해야 한다.
     */
    @Test
    void findTrashedPageByScope_respectsLimit() {
        UUID owner = insertUser("scope-file4@test", "scope-file-owner4");
        UUID deptId = UUID.randomUUID();
        UUID folderId = insertFolder(owner, "folder-scope-4");

        Instant base = Instant.now().truncatedTo(ChronoUnit.MICROS).minusSeconds(400);
        for (int i = 0; i < 5; i++) {
            fileRepository.save(
                trashedFile(owner, folderId, "limit-file-" + i + ".txt",
                    ScopeType.DEPARTMENT, deptId, base.minusSeconds(i * 10L))
            );
        }
        fileRepository.flush();

        List<FileItem> result = fileRepository.findTrashedPageByScope(
            ScopeType.DEPARTMENT.dbValue(), deptId, null, null, 3
        );

        assertEquals(3, result.size(), "limit=3이면 최대 3건만 반환");
    }

    // ====================== helpers ======================

    /**
     * Soft-deleted FileItem fixture.
     * V13 NOT NULL scope_type / scope_id 컬럼 포함.
     */
    private FileItem trashedFile(UUID ownerId, UUID folderId, String name,
                                 ScopeType scopeType, UUID scopeId, Instant deletedAt) {
        FileItem f = new FileItem();
        f.setId(UUID.randomUUID());
        f.setFolderId(folderId);
        f.setName(name);
        f.setNormalizedName(name);
        f.setOwnerId(ownerId);
        f.setSizeBytes(0);
        f.assignScope(scopeType, scopeId);
        f.setDeletedAt(deletedAt);
        f.setPurgeAfter(deletedAt.plusSeconds(60L * 60 * 24 * 30));
        f.setOriginalFolderId(folderId);
        f.setCreatedAt(deletedAt);
        f.setUpdatedAt(deletedAt);
        return f;
    }

    private UUID insertUser(String email, String displayName) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)",
            id, email, displayName
        );
        return id;
    }

    /**
     * Folder row — files.folder_id FK를 충족하기 위한 최소 fixture.
     * V13 NOT NULL scope_type / scope_id를 포함한다.
     */
    private UUID insertFolder(UUID ownerId, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO folders(id, name, normalized_name, slug, owner_id, audit_level, "
                + "scope_type, scope_id, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, 'standard', 'department', ?, NOW(), NOW())",
            id, name, name, name, ownerId, UUID.randomUUID()
        );
        return id;
    }
}
