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

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void save_rejectsDuplicateActiveRootFolderName() {
        UUID ownerId = seedUser("folder-root-owner@example.com");
        folderRepository.saveAndFlush(folder(UUID.randomUUID(), null, "Sales", "sales", ownerId));

        Folder duplicate = folder(UUID.randomUUID(), null, "sales", "sales", ownerId);

        assertThrows(DataIntegrityViolationException.class, () -> folderRepository.saveAndFlush(duplicate));
    }

    @Test
    void save_allowsDuplicateNameWhenExistingFolderIsDeleted() {
        UUID ownerId = seedUser("folder-deleted-owner@example.com");
        Folder deleted = folder(UUID.randomUUID(), null, "Archive", "archive", ownerId);
        folderRepository.saveAndFlush(deleted);
        deleted.markDeleted(OffsetDateTime.now(), OffsetDateTime.now().plusDays(30));
        folderRepository.saveAndFlush(deleted);

        Folder replacement = folder(UUID.randomUUID(), null, "archive", "archive", ownerId);

        assertDoesNotThrow(() -> folderRepository.saveAndFlush(replacement));
        assertTrue(folderRepository.findActiveSibling(null, "archive").isPresent());
    }

    private UUID seedUser(String email) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO users(id, email, display_name, password_hash) VALUES (?, ?, ?, ?)",
            id, email, "Test User", "{bcrypt}$dummy$"
        );
        return id;
    }

    private static Folder folder(UUID id, UUID parentId, String name, String normalizedName, UUID ownerId) {
        return new Folder(id, parentId, name, normalizedName, name, ownerId);
    }
}
