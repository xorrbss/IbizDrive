package com.ibizdrive.permission;

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

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class PermissionRepositorySubtreeTest {

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

    @Autowired private PermissionRepository repo;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void findsActiveByResourceIdsExcludingExpired() {
        UUID granter = UUID.randomUUID();
        UUID subject = UUID.randomUUID();
        UUID expiredSubject = UUID.randomUUID(); // V5 idx_permissions_unique(resource, subject) — expired row는 다른 subject로 분리
        jdbc.update("INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)", granter, "g@t", "g");
        jdbc.update("INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)", subject, "s@t", "s");
        jdbc.update("INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)", expiredSubject, "se@t", "se");

        UUID folder1 = UUID.randomUUID();
        UUID folder2 = UUID.randomUUID();
        UUID otherFolder = UUID.randomUUID();
        Timestamp now = Timestamp.from(java.time.Instant.now());

        // active grants on folder1 (1) and folder2 (1), 1 expired on folder1 (다른 subject), 1 on otherFolder (excluded).
        jdbc.update("INSERT INTO permissions(id, resource_type, resource_id, subject_type, subject_id, preset, granted_by, expires_at, created_at) VALUES (?, 'folder', ?, 'user', ?, 'edit', ?, NULL, ?)",
            UUID.randomUUID(), folder1, subject, granter, now);
        jdbc.update("INSERT INTO permissions(id, resource_type, resource_id, subject_type, subject_id, preset, granted_by, expires_at, created_at) VALUES (?, 'folder', ?, 'user', ?, 'read', ?, ?, ?)",
            UUID.randomUUID(), folder1, expiredSubject, granter, new Timestamp(System.currentTimeMillis() - 3_600_000L), now);
        jdbc.update("INSERT INTO permissions(id, resource_type, resource_id, subject_type, subject_id, preset, granted_by, expires_at, created_at) VALUES (?, 'folder', ?, 'user', ?, 'edit', ?, NULL, ?)",
            UUID.randomUUID(), folder2, subject, granter, now);
        jdbc.update("INSERT INTO permissions(id, resource_type, resource_id, subject_type, subject_id, preset, granted_by, expires_at, created_at) VALUES (?, 'folder', ?, 'user', ?, 'edit', ?, NULL, ?)",
            UUID.randomUUID(), otherFolder, subject, granter, now);

        List<PermissionRow> rows = repo.findActiveByResourceIn("folder", List.of(folder1, folder2));
        assertThat(rows).hasSize(2);
        assertThat(rows).allMatch(r -> r.getResourceType().equals("folder"));
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void deletesByResourceIdsRemovesRowsFromTable() {
        UUID granter = UUID.randomUUID();
        UUID subject1 = UUID.randomUUID();
        UUID subject2 = UUID.randomUUID(); // V5 idx_permissions_unique(resource, subject) — 같은 폴더에 두 grant 두려면 subject 분리
        jdbc.update("INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)", granter, "g2@t", "g2");
        jdbc.update("INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)", subject1, "s2a@t", "s2a");
        jdbc.update("INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)", subject2, "s2b@t", "s2b");

        UUID folder = UUID.randomUUID();
        UUID otherFolder = UUID.randomUUID();
        Timestamp now = Timestamp.from(java.time.Instant.now());

        UUID folderGrant1 = UUID.randomUUID();
        UUID folderGrant2 = UUID.randomUUID();
        UUID otherGrant = UUID.randomUUID();

        jdbc.update("INSERT INTO permissions(id, resource_type, resource_id, subject_type, subject_id, preset, granted_by, expires_at, created_at) VALUES (?, 'folder', ?, 'user', ?, 'edit', ?, NULL, ?)",
            folderGrant1, folder, subject1, granter, now);
        jdbc.update("INSERT INTO permissions(id, resource_type, resource_id, subject_type, subject_id, preset, granted_by, expires_at, created_at) VALUES (?, 'folder', ?, 'user', ?, 'read', ?, NULL, ?)",
            folderGrant2, folder, subject2, granter, now);
        jdbc.update("INSERT INTO permissions(id, resource_type, resource_id, subject_type, subject_id, preset, granted_by, expires_at, created_at) VALUES (?, 'folder', ?, 'user', ?, 'edit', ?, NULL, ?)",
            otherGrant, otherFolder, subject1, granter, now);

        int deleted = repo.deleteByResourceIn("folder", List.of(folder));
        assertThat(deleted).isEqualTo(2);

        Integer remaining = jdbc.queryForObject(
            "SELECT COUNT(*) FROM permissions WHERE resource_id = ?", Integer.class, folder);
        assertThat(remaining).isZero();
        Integer otherRemaining = jdbc.queryForObject(
            "SELECT COUNT(*) FROM permissions WHERE resource_id = ?", Integer.class, otherFolder);
        assertThat(otherRemaining).isEqualTo(1);                                  // 다른 resource는 영향 없음
    }
}
