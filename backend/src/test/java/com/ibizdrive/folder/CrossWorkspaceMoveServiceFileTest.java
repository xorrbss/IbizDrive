package com.ibizdrive.folder;

import com.ibizdrive.file.FileNameConflictException;
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

import java.sql.Timestamp;
import java.util.EnumSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import(CrossWorkspaceMoveServiceFileTest.TestConfig.class)
class CrossWorkspaceMoveServiceFileTest {

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
        ApplicationEventPublisher applicationEventPublisher() {
            return mock(ApplicationEventPublisher.class);
        }

        @Bean
        CrossWorkspaceMoveService crossWorkspaceMoveService(FolderRepository folderRepo,
                                                            FileRepository fileRepo,
                                                            PermissionResolver permissionResolver,
                                                            ApplicationEventPublisher applicationEventPublisher,
                                                            PermissionRepository permRepo,
                                                            ShareRepository shareRepo) {
            return new CrossWorkspaceMoveService(
                folderRepo, fileRepo, permissionResolver,
                applicationEventPublisher,
                permRepo, shareRepo
            );
        }
    }

    @Autowired private CrossWorkspaceMoveService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PermissionResolver permissionResolver;
    @Autowired private ApplicationEventPublisher applicationEventPublisher;

    // ── fixtures ──

    private UUID insertFakeRoot(UUID ownerId, String scopeType, UUID scopeId) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(java.time.Instant.now());
        jdbc.update(
            "INSERT INTO folders(id, parent_id, name, normalized_name, slug, owner_id, audit_level, scope_type, scope_id, created_at, updated_at) "
            + "VALUES (?, NULL, ?, ?, ?, ?, 'standard', ?, ?, ?, ?)",
            id, "root-" + id, "root-" + id, "root-" + id, ownerId, scopeType, scopeId, now, now
        );
        return id;
    }

    private UUID insertFile(UUID folderId, String name, UUID ownerId, String scopeType, UUID scopeId) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(java.time.Instant.now());
        jdbc.update(
            "INSERT INTO files(id, folder_id, name, normalized_name, owner_id, size_bytes, mime_type, storage_key, scope_type, scope_id, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, 0, 'text/plain', ?, ?, ?, ?, ?)",
            id, folderId, name, name, ownerId, UUID.randomUUID().toString(), scopeType, scopeId, now, now
        );
        return id;
    }

    // ── tests ──

    @Test
    @org.springframework.transaction.annotation.Transactional
    void fileCrossWorkspaceMoveFlipsScopeAndCleansPermsShares() {
        UUID actor = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)", actor, "f18a@t", "f18a");
        UUID subject = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)", subject, "f18b@t", "f18b");

        UUID scopeA = UUID.randomUUID();
        UUID scopeB = UUID.randomUUID();
        UUID rootA = insertFakeRoot(actor, "department", scopeA);
        UUID rootB = insertFakeRoot(actor, "department", scopeB);

        UUID fileId = UUID.randomUUID();
        Timestamp now = Timestamp.from(java.time.Instant.now());
        jdbc.update(
            "INSERT INTO files(id, folder_id, name, normalized_name, owner_id, size_bytes, mime_type, storage_key, scope_type, scope_id, created_at, updated_at) "
            + "VALUES (?, ?, 'f18.txt', 'f18.txt', ?, 0, 'text/plain', ?, 'department', ?, ?, ?)",
            fileId, rootA, actor, UUID.randomUUID().toString(), scopeA, now, now);

        // Permission + share on file
        UUID permId = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO permissions(id, resource_type, resource_id, subject_type, subject_id, preset, granted_by, expires_at, created_at) "
            + "VALUES (?, 'file', ?, 'user', ?, 'edit', ?, NULL, ?)",
            permId, fileId, subject, actor, now);
        UUID shareId = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO shares(id, permission_id, shared_by, folder_id, file_id, expires_at, revoked_at, revoked_by, created_at) "
            + "VALUES (?, ?, ?, NULL, ?, NULL, NULL, NULL, ?)",
            shareId, permId, actor, fileId, now);

        when(permissionResolver.resolveFor(actor, "file", fileId))
            .thenReturn(EnumSet.of(Permission.EDIT, Permission.SHARE));
        when(permissionResolver.resolveFor(actor, "folder", rootB))
            .thenReturn(EnumSet.of(Permission.UPLOAD));

        service.moveFile(fileId, rootB, actor);

        String fileScope = jdbc.queryForObject("SELECT scope_id FROM files WHERE id = ?", String.class, fileId);
        assertThat(UUID.fromString(fileScope)).isEqualTo(scopeB);
        String fileFolder = jdbc.queryForObject("SELECT CAST(folder_id AS varchar) FROM files WHERE id = ?", String.class, fileId);
        assertThat(UUID.fromString(fileFolder)).isEqualTo(rootB);
        Integer permLeft = jdbc.queryForObject("SELECT COUNT(*) FROM permissions WHERE resource_id = ?", Integer.class, fileId);
        assertThat(permLeft).isZero();
        // shares row cascade-deleted when permission deleted (ON DELETE CASCADE); count of
        // share rows joined to any active permission for this file must be zero
        Integer activeShares = jdbc.queryForObject(
            "SELECT COUNT(*) FROM shares s INNER JOIN permissions p ON p.id = s.permission_id "
          + "WHERE p.resource_id = ? AND s.revoked_at IS NULL", Integer.class, fileId);
        assertThat(activeShares).isZero();

        verify(applicationEventPublisher).publishEvent(any(CrossWorkspaceMoveCompletedEvent.class));
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void fileMoveRejectsWhenSourceLacksEditShare() {
        UUID actor = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)", actor, "f18c@t", "f18c");

        UUID scopeA = UUID.randomUUID();
        UUID scopeB = UUID.randomUUID();
        UUID rootA = insertFakeRoot(actor, "department", scopeA);
        UUID rootB = insertFakeRoot(actor, "department", scopeB);

        UUID fileId = insertFile(rootA, "f18c.txt", actor, "department", scopeA);

        when(permissionResolver.resolveFor(actor, "file", fileId))
            .thenReturn(EnumSet.of(Permission.READ, Permission.UPLOAD)); // no EDIT/SHARE
        when(permissionResolver.resolveFor(actor, "folder", rootB))
            .thenReturn(EnumSet.of(Permission.UPLOAD));

        assertThatThrownBy(() -> service.moveFile(fileId, rootB, actor))
            .isInstanceOf(DestWorkspaceDeniedException.class)
            .hasMessageContaining("EDIT and SHARE");
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void fileMoveRejectsWhenDestLacksUpload() {
        UUID actor = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)", actor, "f18d@t", "f18d");

        UUID scopeA = UUID.randomUUID();
        UUID scopeB = UUID.randomUUID();
        UUID rootA = insertFakeRoot(actor, "department", scopeA);
        UUID rootB = insertFakeRoot(actor, "department", scopeB);

        UUID fileId = insertFile(rootA, "f18d.txt", actor, "department", scopeA);

        when(permissionResolver.resolveFor(actor, "file", fileId))
            .thenReturn(EnumSet.of(Permission.EDIT, Permission.SHARE));
        when(permissionResolver.resolveFor(actor, "folder", rootB))
            .thenReturn(EnumSet.of(Permission.READ)); // no UPLOAD

        assertThatThrownBy(() -> service.moveFile(fileId, rootB, actor))
            .isInstanceOf(DestWorkspaceDeniedException.class)
            .hasMessageContaining("UPLOAD");
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void fileMoveRejectsOnNameConflict() {
        UUID actor = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)", actor, "f18e@t", "f18e");

        UUID scopeA = UUID.randomUUID();
        UUID scopeB = UUID.randomUUID();
        UUID rootA = insertFakeRoot(actor, "department", scopeA);
        UUID rootB = insertFakeRoot(actor, "department", scopeB);

        String sharedName = "conflict.txt";
        UUID fileId = insertFile(rootA, sharedName, actor, "department", scopeA);
        // existing file in dest with same normalized_name
        insertFile(rootB, sharedName, actor, "department", scopeB);

        when(permissionResolver.resolveFor(actor, "file", fileId))
            .thenReturn(EnumSet.of(Permission.EDIT, Permission.SHARE));
        when(permissionResolver.resolveFor(actor, "folder", rootB))
            .thenReturn(EnumSet.of(Permission.UPLOAD));

        assertThatThrownBy(() -> service.moveFile(fileId, rootB, actor))
            .isInstanceOf(FileNameConflictException.class)
            .hasMessageContaining(sharedName);
    }
}
