package com.ibizdrive.file;

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

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class FileItemRepositoryTest {

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
    private FileItemRepository fileItemRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void save_rejectsDuplicateActiveFileNameInFolder() {
        UUID ownerId = seedUser("file-active-owner@example.com");
        UUID folderId = seedFolder(ownerId, "Files");
        fileItemRepository.saveAndFlush(file(UUID.randomUUID(), folderId, "Plan.pdf", "plan.pdf", ownerId));

        FileItem duplicate = file(UUID.randomUUID(), folderId, "plan.pdf", "plan.pdf", ownerId);

        assertThrows(DataIntegrityViolationException.class, () -> fileItemRepository.saveAndFlush(duplicate));
    }

    @Test
    void save_allowsDuplicateNameWhenExistingFileIsDeleted() {
        UUID ownerId = seedUser("file-deleted-owner@example.com");
        UUID folderId = seedFolder(ownerId, "Deleted Files");
        FileItem deleted = file(UUID.randomUUID(), folderId, "Report.xlsx", "report.xlsx", ownerId);
        fileItemRepository.saveAndFlush(deleted);
        deleted.markDeleted(OffsetDateTime.now(), OffsetDateTime.now().plusDays(30));
        fileItemRepository.saveAndFlush(deleted);

        FileItem replacement = file(UUID.randomUUID(), folderId, "report.xlsx", "report.xlsx", ownerId);

        assertDoesNotThrow(() -> fileItemRepository.saveAndFlush(replacement));
        assertTrue(fileItemRepository.findActiveSibling(folderId, "report.xlsx").isPresent());
    }

    private UUID seedUser(String email) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO users(id, email, display_name, password_hash) VALUES (?, ?, ?, ?)",
            id, email, "Test User", "{bcrypt}$dummy$"
        );
        return id;
    }

    private UUID seedFolder(UUID ownerId, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO folders(id, parent_id, name, normalized_name, slug, owner_id) VALUES (?, NULL, ?, ?, ?, ?)",
            id, name, name.toLowerCase(), name, ownerId
        );
        return id;
    }

    private static FileItem file(UUID id, UUID folderId, String name, String normalizedName, UUID ownerId) {
        return new FileItem(id, folderId, name, normalizedName, ownerId, 1024L, "application/octet-stream");
    }
}
