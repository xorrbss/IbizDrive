package com.ibizdrive.folder;

import com.ibizdrive.file.FileRepository;
import com.ibizdrive.permission.Permission;
import com.ibizdrive.permission.PermissionRepository;
import com.ibizdrive.permission.PermissionResolver;
import com.ibizdrive.share.ShareRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import(CrossWorkspaceMoveServiceTest.TestConfig.class)
class CrossWorkspaceMoveServiceTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.flyway.enabled", () -> "true");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        PermissionResolver permissionResolver() {
            return mock(PermissionResolver.class);
        }

        @Bean
        CrossWorkspaceMoveService crossWorkspaceMoveService(FolderRepository folderRepo,
                                                            FileRepository fileRepo,
                                                            PermissionResolver permissionResolver,
                                                            PermissionRepository permRepo,
                                                            ShareRepository shareRepo) {
            return new CrossWorkspaceMoveService(
                folderRepo, fileRepo, permissionResolver,
                mock(ApplicationEventPublisher.class),
                permRepo, shareRepo
            );
        }
    }

    @Autowired private CrossWorkspaceMoveService service;
    @Autowired private FolderRepository folderRepo;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PermissionResolver permissionResolver;

    // ── fixtures ──

    private UUID insertUser(String emailPrefix) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)",
            id, emailPrefix + "@t.test", emailPrefix);
        return id;
    }

    private UUID insertFolder(UUID parentId, String name, UUID ownerId, String scopeType, UUID scopeId) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        if (parentId == null) {
            jdbc.update(
                "INSERT INTO folders(id, parent_id, name, normalized_name, slug, owner_id, audit_level, scope_type, scope_id, created_at, updated_at) "
                + "VALUES (?, NULL, ?, ?, ?, ?, 'standard', ?, ?, ?, ?)",
                id, name, name, name, ownerId, scopeType, scopeId, now, now);
        } else {
            jdbc.update(
                "INSERT INTO folders(id, parent_id, name, normalized_name, slug, owner_id, audit_level, scope_type, scope_id, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, 'standard', ?, ?, ?, ?)",
                id, parentId, name, name, name, ownerId, scopeType, scopeId, now, now);
        }
        return id;
    }

    // ── tests ──

    @Test
    void rejectsWhenDestinationLacksUpload() {
        UUID actor = insertUser("dest-upload");
        UUID scopeA = UUID.randomUUID();
        UUID scopeB = UUID.randomUUID();
        UUID sourceFolder = insertFolder(null, "src-" + UUID.randomUUID(), actor, "department", scopeA);
        UUID destFolder   = insertFolder(null, "dst-" + UUID.randomUUID(), actor, "team", scopeB);

        when(permissionResolver.resolveFor(actor, "folder", sourceFolder))
            .thenReturn(EnumSet.of(Permission.EDIT, Permission.SHARE));
        when(permissionResolver.resolveFor(actor, "folder", destFolder))
            .thenReturn(EnumSet.of(Permission.READ)); // UPLOAD 없음

        assertThatThrownBy(() -> service.moveFolder(sourceFolder, destFolder, actor))
            .isInstanceOf(DestWorkspaceDeniedException.class)
            .hasMessageContaining("UPLOAD");
    }

    @Test
    void rejectsWhenSourceLacksEditShare() {
        UUID actor = insertUser("src-editshare");
        UUID scopeA = UUID.randomUUID();
        UUID scopeB = UUID.randomUUID();
        UUID sourceFolder = insertFolder(null, "src-" + UUID.randomUUID(), actor, "department", scopeA);
        UUID destFolder   = insertFolder(null, "dst-" + UUID.randomUUID(), actor, "team", scopeB);

        when(permissionResolver.resolveFor(actor, "folder", sourceFolder))
            .thenReturn(EnumSet.of(Permission.READ, Permission.UPLOAD)); // EDIT/SHARE 없음
        when(permissionResolver.resolveFor(actor, "folder", destFolder))
            .thenReturn(EnumSet.of(Permission.UPLOAD));

        assertThatThrownBy(() -> service.moveFolder(sourceFolder, destFolder, actor))
            .isInstanceOf(DestWorkspaceDeniedException.class)
            .hasMessageContaining("EDIT and SHARE");
    }

    @Test
    void rejectsOnNameConflict() {
        UUID actor = insertUser("name-conflict");
        UUID scopeA = UUID.randomUUID();
        UUID scopeB = UUID.randomUUID();
        String sharedName = "conflicting-name";
        UUID sourceFolder = insertFolder(null, sharedName, actor, "department", scopeA);
        UUID destRoot     = insertFolder(null, "dst-root-" + UUID.randomUUID(), actor, "team", scopeB);
        // existing child in dest with same name
        insertFolder(destRoot, sharedName, actor, "team", scopeB);

        when(permissionResolver.resolveFor(actor, "folder", sourceFolder))
            .thenReturn(EnumSet.of(Permission.EDIT, Permission.SHARE));
        when(permissionResolver.resolveFor(actor, "folder", destRoot))
            .thenReturn(EnumSet.of(Permission.UPLOAD));

        assertThatThrownBy(() -> service.moveFolder(sourceFolder, destRoot, actor))
            .isInstanceOf(FolderNameConflictException.class)
            .hasMessageContaining(sharedName);
    }

    @Test
    void rejectsSameScopeMove() {
        UUID actor = insertUser("same-scope");
        UUID scope = UUID.randomUUID();
        UUID sourceFolder = insertFolder(null, "src-ss-" + UUID.randomUUID(), actor, "department", scope);
        UUID destFolder   = insertFolder(null, "dst-ss-" + UUID.randomUUID(), actor, "department", scope);

        // same scopeType + scopeId → should reject before permission check
        assertThatThrownBy(() -> service.moveFolder(sourceFolder, destFolder, actor))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("FolderMutationService.move");
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void subtreeScopeFlippedToDestination() {
        UUID actor = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)", actor, "csm12@t", "csm12");

        UUID scopeA = UUID.randomUUID();
        UUID scopeB = UUID.randomUUID();
        UUID rootA = insertFakeRoot(actor, "department", scopeA);
        UUID rootB = insertFakeRoot(actor, "department", scopeB);

        UUID childInA = UUID.randomUUID();
        UUID grandchildInA = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO folders(id, parent_id, name, normalized_name, slug, owner_id, audit_level, scope_type, scope_id, created_at, updated_at) VALUES (?, ?, 'sub12', 'sub12', 'sub12', ?, 'standard', 'department', ?, ?, ?)",
            childInA, rootA, actor, scopeA, now, now);
        jdbc.update("INSERT INTO folders(id, parent_id, name, normalized_name, slug, owner_id, audit_level, scope_type, scope_id, created_at, updated_at) VALUES (?, ?, 'gc12', 'gc12', 'gc12', ?, 'standard', 'department', ?, ?, ?)",
            grandchildInA, childInA, actor, scopeA, now, now);

        UUID fileId = UUID.randomUUID();
        jdbc.update("INSERT INTO files(id, folder_id, name, normalized_name, owner_id, size_bytes, mime_type, storage_key, scope_type, scope_id, created_at, updated_at) VALUES (?, ?, '12.txt', '12.txt', ?, 0, 'text/plain', ?, 'department', ?, ?, ?)",
            fileId, grandchildInA, actor, UUID.randomUUID().toString(), scopeA, now, now);

        when(permissionResolver.resolveFor(actor, "folder", childInA))
            .thenReturn(java.util.EnumSet.of(Permission.EDIT, Permission.SHARE));
        when(permissionResolver.resolveFor(actor, "folder", rootB))
            .thenReturn(java.util.EnumSet.of(Permission.UPLOAD));

        service.moveFolder(childInA, rootB, actor);

        String childScope = jdbc.queryForObject("SELECT scope_id FROM folders WHERE id = ?", String.class, childInA);
        assertThat(UUID.fromString(childScope)).isEqualTo(scopeB);
        String grandchildScope = jdbc.queryForObject("SELECT scope_id FROM folders WHERE id = ?", String.class, grandchildInA);
        assertThat(UUID.fromString(grandchildScope)).isEqualTo(scopeB);
        String fileScope = jdbc.queryForObject("SELECT scope_id FROM files WHERE id = ?", String.class, fileId);
        assertThat(UUID.fromString(fileScope)).isEqualTo(scopeB);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void subtreePermissionsDeleted() {
        UUID actor = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)", actor, "csm13@t", "csm13");

        UUID scopeA = UUID.randomUUID();
        UUID scopeB = UUID.randomUUID();
        UUID rootA = insertFakeRoot(actor, "department", scopeA);
        UUID rootB = insertFakeRoot(actor, "department", scopeB);

        UUID childInA = UUID.randomUUID();
        UUID grandchildInA = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO folders(id, parent_id, name, normalized_name, slug, owner_id, audit_level, scope_type, scope_id, created_at, updated_at) VALUES (?, ?, 'sub13', 'sub13', 'sub13', ?, 'standard', 'department', ?, ?, ?)",
            childInA, rootA, actor, scopeA, now, now);
        jdbc.update("INSERT INTO folders(id, parent_id, name, normalized_name, slug, owner_id, audit_level, scope_type, scope_id, created_at, updated_at) VALUES (?, ?, 'gc13', 'gc13', 'gc13', ?, 'standard', 'department', ?, ?, ?)",
            grandchildInA, childInA, actor, scopeA, now, now);

        UUID fileId = UUID.randomUUID();
        jdbc.update("INSERT INTO files(id, folder_id, name, normalized_name, owner_id, size_bytes, mime_type, storage_key, scope_type, scope_id, created_at, updated_at) VALUES (?, ?, '13.txt', '13.txt', ?, 0, 'text/plain', ?, 'department', ?, ?, ?)",
            fileId, grandchildInA, actor, UUID.randomUUID().toString(), scopeA, now, now);

        // Permissions: 1 on grandchild folder, 1 on file
        jdbc.update("INSERT INTO permissions(id, resource_type, resource_id, subject_type, subject_id, preset, granted_by, expires_at, created_at) VALUES (?, 'folder', ?, 'user', ?, 'edit', ?, NULL, ?)",
            UUID.randomUUID(), grandchildInA, actor, actor, now);
        jdbc.update("INSERT INTO permissions(id, resource_type, resource_id, subject_type, subject_id, preset, granted_by, expires_at, created_at) VALUES (?, 'file', ?, 'user', ?, 'edit', ?, NULL, ?)",
            UUID.randomUUID(), fileId, actor, actor, now);

        when(permissionResolver.resolveFor(actor, "folder", childInA))
            .thenReturn(java.util.EnumSet.of(Permission.EDIT, Permission.SHARE));
        when(permissionResolver.resolveFor(actor, "folder", rootB))
            .thenReturn(java.util.EnumSet.of(Permission.UPLOAD));

        service.moveFolder(childInA, rootB, actor);

        Integer remaining = jdbc.queryForObject(
            "SELECT COUNT(*) FROM permissions WHERE resource_id IN (?, ?, ?)",
            Integer.class, childInA, grandchildInA, fileId);
        assertThat(remaining).isZero();
    }

    // ── private helpers ──

    private UUID insertFakeRoot(UUID ownerId, String scopeType, UUID scopeId) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update(
            "INSERT INTO folders(id, parent_id, name, normalized_name, slug, owner_id, audit_level, scope_type, scope_id, created_at, updated_at) VALUES (?, NULL, ?, ?, ?, ?, 'standard', ?, ?, ?, ?)",
            id, "root-" + id, "root-" + id, "root-" + id, ownerId, scopeType, scopeId, now, now
        );
        return id;
    }
}
