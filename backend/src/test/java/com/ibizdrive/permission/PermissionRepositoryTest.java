package com.ibizdrive.permission;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link PermissionRepository#findEffective(UUID, String, UUID)}의 재귀 CTE 동작 검증
 * (ADR #28 — grant 우선 lookup, 명시 deny 처리 없음).
 *
 * <p>{@link com.ibizdrive.folder.V5MigrationIT}와 동일한 Testcontainers 패턴.
 * Docker 미가용 환경에서는 {@code disabledWithoutDocker = true}로 자동 스킵.
 *
 * <p>{@code @DataJpaTest}는 main class({@code com.ibizdrive.IbizDriveApplication}) 패키지 기준으로
 * entity와 repository를 자동 스캔 — 명시적 {@code @EnableJpaRepositories}/{@code @ComponentScan} 불필요.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class PermissionRepositoryTest {

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
    private PermissionRepository permissionRepository;

    @Autowired
    private JdbcTemplate jdbc;

    // ---------- direct grant ----------

    @Test
    void findEffective_directFolderGrant_returnsRow() {
        UUID user = insertUser("u1@test", "u1");
        UUID folder = insertFolder(null, "root1", user);
        insertPermission("folder", folder, "user", user, "read", user);

        List<PermissionRow> rows = permissionRepository.findEffective(user, "folder", folder);

        assertEquals(1, rows.size(), "동일 폴더에 user grant가 있으면 1행 반환");
        assertEquals("read", rows.get(0).getPreset());
        assertEquals(user, rows.get(0).getSubjectId());
    }

    // ---------- inheritance ----------

    @Test
    void findEffective_parentGrant_inheritsToChildFolder() {
        UUID user = insertUser("u2@test", "u2");
        UUID parent = insertFolder(null, "parent2", user);
        UUID child = insertFolder(parent, "child2", user);
        insertPermission("folder", parent, "user", user, "edit", user);

        List<PermissionRow> rows = permissionRepository.findEffective(user, "folder", child);

        assertEquals(1, rows.size(), "부모 grant는 자식 폴더로 상속되어야 함 (재귀 CTE)");
        assertEquals("edit", rows.get(0).getPreset());
        assertEquals(parent, rows.get(0).getResourceId(), "상속된 grant는 부모 resource_id 보존");
    }

    @Test
    void findEffective_noGrantOnAncestorChain_returnsEmpty() {
        UUID user = insertUser("u3@test", "u3");
        UUID lonely = insertFolder(null, "lonely", user);

        List<PermissionRow> rows = permissionRepository.findEffective(user, "folder", lonely);

        assertTrue(rows.isEmpty(), "조상 어디에도 grant 없으면 빈 결과 (ADR #28 grant 우선)");
    }

    // ---------- everyone subject ----------

    @Test
    void findEffective_everyoneGrant_appliesToAnyUser() {
        UUID granter = insertUser("granter4@test", "granter4");
        UUID anyUser = insertUser("u4@test", "u4");
        UUID folder = insertFolder(null, "open4", granter);
        insertPermissionEveryone("folder", folder, "read", granter);

        List<PermissionRow> rows = permissionRepository.findEffective(anyUser, "folder", folder);

        assertEquals(1, rows.size(), "everyone subject grant는 임의 사용자에게 적용");
        assertEquals("everyone", rows.get(0).getSubjectType());
        assertNull(rows.get(0).getSubjectId(), "everyone grant는 subject_id NULL");
    }

    // ---------- expiry ----------

    @Test
    void findEffective_expiredGrant_excluded() {
        UUID user = insertUser("u5@test", "u5");
        UUID folder = insertFolder(null, "exp5", user);
        Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
        insertPermissionWithExpiry("folder", folder, "user", user, "read", user, past);

        List<PermissionRow> rows = permissionRepository.findEffective(user, "folder", folder);

        assertTrue(rows.isEmpty(), "만료된 grant는 제외 (expires_at <= NOW)");
    }

    // ---------- file resource (file → folder → ancestors) ----------

    @Test
    void findEffective_fileResource_walksFolderAncestry() {
        UUID user = insertUser("u6@test", "u6");
        UUID parent = insertFolder(null, "fparent6", user);
        UUID child = insertFolder(parent, "fchild6", user);
        UUID file = insertFile(child, "doc.txt", user);
        // grant는 child의 부모(parent)에 부여 → file까지 상속되어야 함
        insertPermission("folder", parent, "user", user, "edit", user);

        List<PermissionRow> rows = permissionRepository.findEffective(user, "file", file);

        assertEquals(1, rows.size(), "file 리소스는 folder→조상 체인을 따라가야 함");
        assertEquals(parent, rows.get(0).getResourceId());
    }

    // ---------- soft-deleted ancestor breaks inheritance ----------

    @Test
    void findEffective_softDeletedAncestor_breaksInheritance() {
        UUID user = insertUser("u7@test", "u7");
        UUID parent = insertFolder(null, "deleted-parent", user);
        UUID child = insertFolder(parent, "child7", user);
        insertPermission("folder", parent, "user", user, "read", user);
        // 부모 폴더 soft delete
        jdbc.update(
            "UPDATE folders SET deleted_at = NOW(), purge_after = NOW() + INTERVAL '30 days' WHERE id = ?",
            parent
        );

        List<PermissionRow> rows = permissionRepository.findEffective(user, "folder", child);

        assertTrue(rows.isEmpty(),
            "soft-deleted 부모 폴더는 조상 체인에서 제외 — 휴지통 폴더가 권한 상속 경로를 끊음");
    }

    // ---------- subject filtering (other user's grant excluded) ----------

    @Test
    void findEffective_otherUsersGrant_excluded() {
        UUID granter = insertUser("granter8@test", "granter8");
        UUID otherUser = insertUser("other8@test", "other8");
        UUID me = insertUser("me8@test", "me8");
        UUID folder = insertFolder(null, "shared8", granter);
        insertPermission("folder", folder, "user", otherUser, "read", granter);

        List<PermissionRow> rows = permissionRepository.findEffective(me, "folder", folder);

        assertTrue(rows.isEmpty(), "다른 사용자 user-grant는 본인 결과에 포함되지 않아야 함");
    }

    // ---------- findExpiredActiveIds (permissions-expired-cron) ----------

    @Test
    void findExpiredActiveIds_returnsOnlyExpiredOldestFirst() {
        UUID owner = insertUser("expo1@test", "expo1");
        UUID subject1 = insertUser("expu1a@test", "expu1a");
        UUID subject2 = insertUser("expu1b@test", "expu1b");
        UUID subject3 = insertUser("expu1c@test", "expu1c");
        UUID folder = insertFolder(null, "exp-folder1", owner);
        Instant now = Instant.now();

        // 두 개 만료 (서로 다른 시각), 한 개 미만료, 한 개 NULL.
        // idx_permissions_unique=(resource_type, resource_id, subject_type, subject_id) — 동일 resource×subject 중복 금지.
        // 따라서 subject를 3명으로 분리해야 동일 (folder, user) tuple에 3 row 공존 가능.
        UUID idOldExpired = insertPermissionWithExpiryReturning(
            "folder", folder, "user", subject1, "read", owner,
            now.minus(2, ChronoUnit.HOURS)
        );
        UUID idRecentExpired = insertPermissionWithExpiryReturning(
            "folder", folder, "user", subject2, "edit", owner,
            now.minus(10, ChronoUnit.MINUTES)
        );
        insertPermissionWithExpiry(
            "folder", folder, "user", subject3, "admin", owner,
            now.plus(1, ChronoUnit.HOURS) // 미만료
        );
        insertPermission("file", insertFile(folder, "x.txt", owner), "everyone", null, "read", owner);

        List<UUID> ids = permissionRepository.findExpiredActiveIds(now, PageRequest.of(0, 100));

        // oldest-first 정렬, 미만료/NULL 제외.
        assertThat(ids).containsExactly(idOldExpired, idRecentExpired);
    }

    @Test
    void findExpiredActiveIds_respectsLimit() {
        UUID user = insertUser("expu2@test", "expu2");
        UUID folder = insertFolder(null, "exp-folder2", user);
        Instant now = Instant.now();
        for (int i = 0; i < 5; i++) {
            insertPermissionWithExpiryReturning(
                "file", insertFile(folder, "f" + i + ".txt", user), "user", user, "read", user,
                now.minus(i + 1, ChronoUnit.MINUTES)
            );
        }

        List<UUID> ids = permissionRepository.findExpiredActiveIds(now, PageRequest.of(0, 3));

        assertThat(ids).hasSize(3);
    }

    @Test
    void findExpiredActiveIds_boundaryExactNowIncluded() {
        UUID user = insertUser("expu3@test", "expu3");
        UUID folder = insertFolder(null, "exp-folder3", user);
        // expires_at = NOW() 정확히 같은 row — `<= NOW()` boundary 검증.
        Instant boundary = Instant.now().minus(1, ChronoUnit.SECONDS);
        UUID exactlyExpired = insertPermissionWithExpiryReturning(
            "folder", folder, "user", user, "read", user, boundary
        );

        List<UUID> ids = permissionRepository.findExpiredActiveIds(boundary, PageRequest.of(0, 10));

        assertThat(ids).contains(exactlyExpired);
    }

    @Test
    void findExpiredActiveIds_emptyWhenNoneExpired() {
        UUID user = insertUser("expu4@test", "expu4");
        UUID folder = insertFolder(null, "exp-folder4", user);
        Instant now = Instant.now();
        insertPermissionWithExpiry(
            "folder", folder, "user", user, "read", user,
            now.plus(1, ChronoUnit.DAYS)
        );

        List<UUID> ids = permissionRepository.findExpiredActiveIds(now, PageRequest.of(0, 10));

        assertThat(ids).isEmpty();
    }

    // ====================== helpers ======================

    private UUID insertUser(String email, String displayName) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)",
            id, email, displayName
        );
        return id;
    }

    private UUID insertFolder(UUID parentId, String name, UUID ownerId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO folders(id, parent_id, name, normalized_name, slug, owner_id) " +
            "VALUES (?, ?, ?, ?, ?, ?)",
            id, parentId, name, name, name, ownerId
        );
        return id;
    }

    private UUID insertFile(UUID folderId, String name, UUID ownerId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO files(id, folder_id, name, normalized_name, owner_id, size_bytes) " +
            "VALUES (?, ?, ?, ?, ?, ?)",
            id, folderId, name, name, ownerId, 0L
        );
        return id;
    }

    private void insertPermission(String resourceType, UUID resourceId,
                                  String subjectType, UUID subjectId,
                                  String preset, UUID grantedBy) {
        jdbc.update(
            "INSERT INTO permissions(id, resource_type, resource_id, subject_type, subject_id, " +
            "preset, granted_by) VALUES (?, ?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(), resourceType, resourceId, subjectType, subjectId, preset, grantedBy
        );
    }

    private void insertPermissionEveryone(String resourceType, UUID resourceId,
                                          String preset, UUID grantedBy) {
        jdbc.update(
            "INSERT INTO permissions(id, resource_type, resource_id, subject_type, subject_id, " +
            "preset, granted_by) VALUES (?, ?, ?, ?, NULL, ?, ?)",
            UUID.randomUUID(), resourceType, resourceId, "everyone", preset, grantedBy
        );
    }

    private void insertPermissionWithExpiry(String resourceType, UUID resourceId,
                                            String subjectType, UUID subjectId,
                                            String preset, UUID grantedBy, Instant expiresAt) {
        jdbc.update(
            "INSERT INTO permissions(id, resource_type, resource_id, subject_type, subject_id, " +
            "preset, granted_by, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(), resourceType, resourceId, subjectType, subjectId, preset, grantedBy,
            java.sql.Timestamp.from(expiresAt)
        );
    }

    /** Returning variant — findExpiredActiveIds 결과 매칭 검증용. */
    private UUID insertPermissionWithExpiryReturning(String resourceType, UUID resourceId,
                                                     String subjectType, UUID subjectId,
                                                     String preset, UUID grantedBy, Instant expiresAt) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO permissions(id, resource_type, resource_id, subject_type, subject_id, " +
            "preset, granted_by, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            id, resourceType, resourceId, subjectType, subjectId, preset, grantedBy,
            java.sql.Timestamp.from(expiresAt)
        );
        return id;
    }
}
