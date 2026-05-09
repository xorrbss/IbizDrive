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

import java.time.Instant;
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
        UUID folder1 = UUID.randomUUID();
        UUID folder2 = UUID.randomUUID();
        UUID otherFolder = UUID.randomUUID();

        // Active share on folder1, active share on folder2, revoked share on folder1, share on otherFolder.
        seedShare("folder", folder1, subject, actor, "edit", false);
        seedShare("folder", folder2, subject, actor, "read", false);
        seedShare("folder", folder1, subject, actor, "edit", true);                  // revoked
        seedShare("folder", otherFolder, subject, actor, "edit", false);             // out of set

        List<Share> active = shareRepo.findActiveByResourceIn("folder", List.of(folder1, folder2));
        assertThat(active).hasSize(2);
    }

    @Test
    @Transactional
    void revokeByIdsUpdatesRowsAndIsReflectedInQuery() {
        UUID actor = seedUser("rb1@t");
        UUID subject = seedUser("rb2@t");
        UUID folder = UUID.randomUUID();
        UUID otherFolder = UUID.randomUUID();

        UUID share1 = seedShare("folder", folder, subject, actor, "edit", false);
        UUID share2 = seedShare("folder", folder, subject, actor, "read", false);
        UUID otherShare = seedShare("folder", otherFolder, subject, actor, "edit", false);

        UUID revoker = seedUser("rev@t");
        Instant now = Instant.now();
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

    private UUID seedShare(String resourceType, UUID resourceId, UUID subjectId, UUID sharedBy,
                           String preset, boolean revoked) {
        UUID permId = UUID.randomUUID();
        Instant now = Instant.now();
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
                shareId, resourceId, permId, sharedBy, Instant.now(), sharedBy, now);
        } else {
            jdbc.update(
                "INSERT INTO shares(id, " + col + ", permission_id, shared_by, expires_at, revoked_at, revoked_by, created_at) "
              + "VALUES (?, ?, ?, ?, NULL, NULL, NULL, ?)",
                shareId, resourceId, permId, sharedBy, now);
        }
        return shareId;
    }
}
