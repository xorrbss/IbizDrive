package com.ibizdrive.folder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.file.FileRepository;
import com.ibizdrive.folder.dto.MovePreviewResponse;
import com.ibizdrive.permission.Permission;
import com.ibizdrive.permission.PermissionRepository;
import com.ibizdrive.permission.WorkspaceMembershipResolver;
import com.ibizdrive.share.ShareRepository;
import org.junit.jupiter.api.Test;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import(MovePreviewServiceTest.TestConfig.class)
class MovePreviewServiceTest {

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
        @Bean WorkspaceMembershipResolver membershipResolver() { return mock(WorkspaceMembershipResolver.class); }
        @Bean MovePreviewService movePreviewService(FolderRepository fr, FileRepository fileRepo,
                                                     PermissionRepository permRepo, ShareRepository shareRepo,
                                                     WorkspaceMembershipResolver mr) {
            return new MovePreviewService(fr, fileRepo, permRepo, shareRepo, mr);
        }
    }

    @Autowired private MovePreviewService service;
    @Autowired private FolderRepository folderRepo;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private WorkspaceMembershipResolver membershipResolver;

    @Test
    void folderPreviewCountsSubtreeAndPermissions() {
        UUID actor = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)", actor, "a@t", "a");

        UUID scopeA = UUID.randomUUID();
        UUID scopeB = UUID.randomUUID();
        UUID rootA = insertFakeRoot(actor, "department", scopeA);
        UUID rootB = insertFakeRoot(actor, "department", scopeB);

        UUID childInA = UUID.randomUUID();
        UUID grandchildInA = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO folders(id, parent_id, name, normalized_name, slug, owner_id, audit_level, scope_type, scope_id, created_at, updated_at) VALUES (?, ?, 'sub', 'sub', 'sub', ?, 'standard', 'department', ?, ?, ?)",
            childInA, rootA, actor, scopeA, now, now);
        jdbc.update("INSERT INTO folders(id, parent_id, name, normalized_name, slug, owner_id, audit_level, scope_type, scope_id, created_at, updated_at) VALUES (?, ?, 'gc', 'gc', 'gc', ?, 'standard', 'department', ?, ?, ?)",
            grandchildInA, childInA, actor, scopeA, now, now);

        // 1 file inside grandchild
        UUID fileId = UUID.randomUUID();
        jdbc.update("INSERT INTO files(id, folder_id, name, normalized_name, owner_id, size_bytes, mime_type, storage_key, scope_type, scope_id, created_at, updated_at) VALUES (?, ?, 'a.txt', 'a.txt', ?, 0, 'text/plain', ?, 'department', ?, ?, ?)",
            fileId, grandchildInA, actor, UUID.randomUUID().toString(), scopeA, now, now);

        // 1 permission on grandchild
        jdbc.update("INSERT INTO permissions(id, resource_type, resource_id, subject_type, subject_id, preset, granted_by, expires_at, created_at) VALUES (?, 'folder', ?, 'user', ?, 'edit', ?, NULL, ?)",
            UUID.randomUUID(), grandchildInA, actor, actor, now);

        when(membershipResolver.resolve(actor, com.ibizdrive.folder.ScopeType.DEPARTMENT, scopeB))
            .thenReturn(java.util.EnumSet.of(Permission.READ, Permission.UPLOAD));

        MovePreviewResponse preview = service.previewFolder(childInA, rootB, actor);

        // child + grandchild + file = 3
        assertThat(preview.itemCount()).isEqualTo(3);
        assertThat(preview.removedPermissions()).hasSize(1);
        assertThat(preview.targetMembershipDefaults())
            .containsExactlyInAnyOrder(Permission.READ, Permission.UPLOAD);
        assertThat(preview.nameConflict()).isNull();
    }

    @Test
    void rejectsDescendantDestination() {
        UUID actor = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)", actor, "a2@t", "a2");
        UUID scope = UUID.randomUUID();
        UUID root = insertFakeRoot(actor, "department", scope);
        UUID parent = UUID.randomUUID();
        UUID child = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO folders(id, parent_id, name, normalized_name, slug, owner_id, audit_level, scope_type, scope_id, created_at, updated_at) VALUES (?, ?, 'p', 'p', 'p', ?, 'standard', 'department', ?, ?, ?)",
            parent, root, actor, scope, now, now);
        jdbc.update("INSERT INTO folders(id, parent_id, name, normalized_name, slug, owner_id, audit_level, scope_type, scope_id, created_at, updated_at) VALUES (?, ?, 'c', 'c', 'c', ?, 'standard', 'department', ?, ?, ?)",
            child, parent, actor, scope, now, now);

        assertThatThrownBy(() -> service.previewFolder(parent, child, actor))
            .isInstanceOf(InvalidMoveDestinationException.class);
    }

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
