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

import java.time.Instant;
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
        jdbc.update("INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)", granter, "g@t", "g");
        jdbc.update("INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)", subject, "s@t", "s");

        UUID folder1 = UUID.randomUUID();
        UUID folder2 = UUID.randomUUID();
        UUID otherFolder = UUID.randomUUID();
        Instant now = Instant.now();

        // active grants on folder1 (1) and folder2 (1), 1 expired on folder1, 1 on otherFolder (excluded).
        jdbc.update("INSERT INTO permissions(id, resource_type, resource_id, subject_type, subject_id, preset, granted_by, expires_at, created_at) VALUES (?, 'folder', ?, 'user', ?, 'edit', ?, NULL, ?)",
            UUID.randomUUID(), folder1, subject, granter, now);
        jdbc.update("INSERT INTO permissions(id, resource_type, resource_id, subject_type, subject_id, preset, granted_by, expires_at, created_at) VALUES (?, 'folder', ?, 'user', ?, 'read', ?, ?, ?)",
            UUID.randomUUID(), folder1, subject, granter, Instant.now().minusSeconds(3600), now);
        jdbc.update("INSERT INTO permissions(id, resource_type, resource_id, subject_type, subject_id, preset, granted_by, expires_at, created_at) VALUES (?, 'folder', ?, 'user', ?, 'edit', ?, NULL, ?)",
            UUID.randomUUID(), folder2, subject, granter, now);
        jdbc.update("INSERT INTO permissions(id, resource_type, resource_id, subject_type, subject_id, preset, granted_by, expires_at, created_at) VALUES (?, 'folder', ?, 'user', ?, 'edit', ?, NULL, ?)",
            UUID.randomUUID(), otherFolder, subject, granter, now);

        List<PermissionRow> rows = repo.findActiveByResourceIn("folder", List.of(folder1, folder2));
        assertThat(rows).hasSize(2);
        assertThat(rows).allMatch(r -> r.getResourceType().equals("folder"));
    }
}
