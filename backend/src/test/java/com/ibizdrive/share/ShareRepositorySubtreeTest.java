package com.ibizdrive.share;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class ShareRepositorySubtreeTest {

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

    @Autowired private ShareRepository shareRepo;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void findsActiveSharesByResourceIds() {
        UUID actor = seedUser("a8a@t");
        UUID subject = seedUser("a8s@t");
        UUID subject2 = seedUser("a8s2@t"); // V5 idx_permissions_unique — 같은 folder1에 두 share 두려면 subject 분리
        UUID folder1 = seedFolder(actor, "f8a");
        UUID folder2 = seedFolder(actor, "f8b");
        UUID otherFolder = seedFolder(actor, "f8c");

        // Active share on folder1, active share on folder2, revoked share on folder1 (다른 subject), share on otherFolder.
        seedShare("folder", folder1, subject, actor, "edit", false);
        seedShare("folder", folder2, subject, actor, "read", false);
        seedShare("folder", folder1, subject2, actor, "edit", true);                 // revoked, 다른 subject
        seedShare("folder", otherFolder, subject, actor, "edit", false);             // out of set

        List<Share> active = shareRepo.findActiveByResourceIn("folder", List.of(folder1, folder2));
        assertThat(active).hasSize(2);
    }

    @Test
    @Transactional
    void revokeByIdsUpdatesRowsAndIsReflectedInQuery() {
        UUID actor = seedUser("rb1@t");
        UUID subject1 = seedUser("rb2a@t");
        UUID subject2 = seedUser("rb2b@t"); // V5 idx_permissions_unique — 같은 folder에 두 share 두려면 subject 분리
        UUID folder = seedFolder(actor, "frb1");
        UUID otherFolder = seedFolder(actor, "frb2");

        UUID share1 = seedShare("folder", folder, subject1, actor, "edit", false);
        UUID share2 = seedShare("folder", folder, subject2, actor, "read", false);
        UUID otherShare = seedShare("folder", otherFolder, subject1, actor, "edit", false);

        UUID revoker = seedUser("rev@t");
        java.time.Instant now = java.time.Instant.now();
        int revoked = shareRepo.revokeByIds(List.of(share1, share2), revoker, now);
        assertThat(revoked).isEqualTo(2);

        List<Share> stillActiveOnFolder = shareRepo.findActiveByResourceIn("folder", List.of(folder));
        assertThat(stillActiveOnFolder).isEmpty();
        List<Share> stillActiveOnOther = shareRepo.findActiveByResourceIn("folder", List.of(otherFolder));
        assertThat(stillActiveOnOther).hasSize(1);                                                          // 다른 resource는 영향 없음
    }

    // ── helpers ──

    private UUID seedUser(String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)", id, email, email);
        return id;
    }

    /**
     * V6 shares.folder_id REFERENCES folders(id) — share INSERT 전에 폴더 row가 실제로 존재해야 한다.
     * V13 scope_type/scope_id NOT NULL — fixture root는 fake department scope를 가진다.
     * V14 idx_folders_root_per_scope (parent_id IS NULL AND deleted_at IS NULL) UNIQUE (scope_type, scope_id) —
     *     호출마다 새 scope_id 사용해 root 충돌 회피.
     */
    private UUID seedFolder(UUID ownerId, String name) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(java.time.Instant.now());
        UUID scopeId = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO folders(id, parent_id, name, normalized_name, slug, owner_id, audit_level, " +
            "scope_type, scope_id, created_at, updated_at) " +
            "VALUES (?, NULL, ?, ?, ?, ?, 'standard', 'department', ?, ?, ?)",
            id, name, name.toLowerCase(), name.toLowerCase(), ownerId, scopeId, now, now);
        return id;
    }

    private UUID seedShare(String resourceType, UUID resourceId, UUID subjectId, UUID sharedBy,
                           String preset, boolean revoked) {
        UUID permId = UUID.randomUUID();
        Timestamp now = Timestamp.from(java.time.Instant.now());
        jdbc.update(
            "INSERT INTO permissions(id, resource_type, resource_id, subject_type, subject_id, preset, granted_by, expires_at, created_at) "
            + "VALUES (?, ?, ?, 'user', ?, ?, ?, NULL, ?)",
            permId, resourceType, resourceId, subjectId, preset, sharedBy, now);
        UUID shareId = UUID.randomUUID();
        // V6 XOR constraint: exactly one of file_id / folder_id must be non-null.
        // resourceType='folder' → set folder_id; otherwise set file_id.
        String col = "folder".equals(resourceType) ? "folder_id" : "file_id";
        if (revoked) {
            jdbc.update(
                "INSERT INTO shares(id, " + col + ", permission_id, shared_by, expires_at, revoked_at, revoked_by, created_at) "
              + "VALUES (?, ?, ?, ?, NULL, ?, ?, ?)",
                shareId, resourceId, permId, sharedBy, new Timestamp(System.currentTimeMillis()), sharedBy, now);
        } else {
            jdbc.update(
                "INSERT INTO shares(id, " + col + ", permission_id, shared_by, expires_at, revoked_at, revoked_by, created_at) "
              + "VALUES (?, ?, ?, ?, NULL, NULL, NULL, ?)",
                shareId, resourceId, permId, sharedBy, now);
        }
        return shareId;
    }
}
