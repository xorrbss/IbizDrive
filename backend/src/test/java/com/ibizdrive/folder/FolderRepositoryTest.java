package com.ibizdrive.folder;

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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link FolderRepository} entity persistence + V5 schema 제약 가드 검증 (A4.5).
 *
 * <p>{@link V5MigrationIT}는 raw {@code JdbcTemplate}으로 schema-level 제약을 검증하는 반면,
 * 본 테스트는 <strong>{@link Folder} JPA entity ↔ V5 schema 매핑</strong>을 검증한다 — 즉
 * {@code ddl-auto=validate} 부팅 + entity persist를 통해 같은 제약이 entity 경로로도 enforce되는지 확인.
 *
 * <p>{@link com.ibizdrive.permission.PermissionRepositoryTest}와 동일한 Testcontainers 패턴.
 * Docker 미가용 환경에서는 {@code disabledWithoutDocker = true}로 자동 스킵.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class FolderRepositoryTest {

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
    private FolderRepository folderRepository;

    @Autowired
    private JdbcTemplate jdbc;

    // -------------------- happy path --------------------

    @Test
    void saveRootFolder_assignsId_andFindsByIdAndDeletedAtIsNull() {
        UUID owner = insertUser("owner1@test", "owner1");
        Folder folder = newFolder(null, "root1", owner);

        Folder saved = folderRepository.save(folder);

        assertNotNull(saved.getId(), "JPA persist 후 id 채워져야 함");
        Optional<Folder> found = folderRepository.findByIdAndDeletedAtIsNull(saved.getId());
        assertTrue(found.isPresent(), "활성 폴더는 findByIdAndDeletedAtIsNull로 조회 가능");
        assertEquals("root1", found.get().getNormalizedName());
        assertNull(found.get().getParentId(), "root는 parent_id NULL");
    }

    @Test
    void findByParentIdAndDeletedAtIsNull_returnsOnlyActiveChildren() {
        UUID owner = insertUser("owner2@test", "owner2");
        Folder parent = folderRepository.save(newFolder(null, "parent2", owner));
        folderRepository.save(newFolder(parent.getId(), "child-a", owner));
        folderRepository.save(newFolder(parent.getId(), "child-b", owner));
        folderRepository.flush();

        // soft delete child-b
        jdbc.update(
            "UPDATE folders SET deleted_at = NOW(), purge_after = NOW() + INTERVAL '30 days' " +
            "WHERE parent_id = ? AND normalized_name = 'child-b'",
            parent.getId()
        );

        var children = folderRepository.findByParentIdAndDeletedAtIsNull(parent.getId());
        assertEquals(1, children.size(), "soft-deleted child는 활성 목록에서 제외");
        assertEquals("child-a", children.get(0).getNormalizedName());
    }

    // -------------------- UNIQUE (partial index) --------------------

    @Test
    void saveSameParent_sameNormalizedName_violatesUnique() {
        UUID owner = insertUser("owner3@test", "owner3");
        Folder parent = folderRepository.save(newFolder(null, "parent3", owner));
        folderRepository.save(newFolder(parent.getId(), "dup", owner));
        folderRepository.flush();

        // entity 경유 두 번째 INSERT는 partial unique index 위반.
        assertThrows(DataIntegrityViolationException.class, () -> {
            folderRepository.save(newFolder(parent.getId(), "dup", owner));
            folderRepository.flush();
        }, "동일 parent + normalized_name 중복은 entity 경로에서도 차단");
    }

    @Test
    void softDeleteThenSaveSameName_succeeds() {
        UUID owner = insertUser("owner4@test", "owner4");
        Folder parent = folderRepository.save(newFolder(null, "parent4", owner));
        Folder first = folderRepository.save(newFolder(parent.getId(), "doc", owner));
        folderRepository.flush();

        jdbc.update(
            "UPDATE folders SET deleted_at = NOW(), purge_after = NOW() + INTERVAL '30 days' WHERE id = ?",
            first.getId()
        );

        assertDoesNotThrow(() -> {
            folderRepository.save(newFolder(parent.getId(), "doc", owner));
            folderRepository.flush();
        }, "soft-deleted row는 partial unique index에서 제외 — 같은 이름 재생성 허용");
    }

    // -------------------- CHECK ((deleted_at IS NULL) = (purge_after IS NULL)) --------------------

    @Test
    void deletedAtSet_butPurgeAfterNull_violatesCheck() {
        UUID owner = insertUser("owner5@test", "owner5");
        Folder folder = newFolder(null, "check5", owner);
        folder.setDeletedAt(Instant.now());
        // purgeAfter는 의도적으로 set하지 않음 — CHECK 위반 유도.

        assertThrows(DataIntegrityViolationException.class, () -> {
            folderRepository.save(folder);
            folderRepository.flush();
        }, "deleted_at NOT NULL + purge_after NULL은 folders_deleted_purge_check 위반");
    }

    // ====================== helpers ======================

    /**
     * Test helper — V5 schema가 요구하는 NOT NULL 컬럼을 모두 채워 minimal Folder를 만든다.
     * id/createdAt/updatedAt는 application 레벨에서 명시 set (entity 정책 — DB DEFAULT 의존 회피).
     *
     * <p>V13 (Plan A Task 2/24) — scope_type/scope_id NOT NULL. 본 repository test는 scope 의미가
     * 없으므로 임의의 department + random UUID를 채워 NOT NULL을 충족한다.
     */
    private Folder newFolder(UUID parentId, String normalizedName, UUID ownerId) {
        Folder f = new Folder();
        f.setId(UUID.randomUUID());
        f.setParentId(parentId);
        f.setName(normalizedName);
        f.setNormalizedName(normalizedName);
        f.setSlug(normalizedName);
        f.setOwnerId(ownerId);
        f.setAuditLevel("standard");
        f.assignScope(ScopeType.DEPARTMENT, UUID.randomUUID());
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        f.setCreatedAt(now);
        f.setUpdatedAt(now);
        f.assignScope(com.ibizdrive.folder.ScopeType.DEPARTMENT, java.util.UUID.randomUUID());
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
}
