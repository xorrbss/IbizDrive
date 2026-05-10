package com.ibizdrive.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.folder.ScopeType;
import com.ibizdrive.storage.StorageClient;
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

import java.io.ByteArrayInputStream;
import java.sql.Timestamp;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Plan A Task 26 — {@link FileUploadService} scope inheritance at file creation.
 *
 * <p>spec docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §1.2 (scope invariant) —
 * 새로 생성되는 file은 부모 folder의 {@code (scope_type, scope_id)}를 그대로 상속한다.
 *
 * <p>"Fake workspace root"는 raw JDBC로 직접 INSERT — 정상 운영에서는 DepartmentService(Task 20)가
 * root를 만든다. 본 테스트는 file-creation 경로의 scope 상속만 격리 검증한다.
 *
 * <p>{@link FileUploadServiceTest}와 동일한 Testcontainers + DataJpaTest 슬라이스 — V13 NOT NULL +
 * CHECK 제약을 실제 Postgres에서 검증한다. {@link FolderCreateScopeInheritanceTest} 와 동일 패턴.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import(FileScopeInheritanceTest.TestConfig.class)
class FileScopeInheritanceTest {

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

    @TestConfiguration
    static class TestConfig {
        @Bean ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean AuditService auditService() {
            return mock(AuditService.class);
        }

        @Bean StorageClient storageClient() {
            return mock(StorageClient.class);
        }

        @Bean FileUploadService fileUploadService(FileRepository fileRepo,
                                                  FileVersionRepository versionRepo,
                                                  FolderRepository folderRepo,
                                                  StorageClient storage,
                                                  AuditService audit,
                                                  ObjectMapper mapper) {
            return new FileUploadService(fileRepo, versionRepo, folderRepo, storage, audit, mapper);
        }
    }

    @Autowired private FileUploadService service;
    @Autowired private FileRepository fileRepository;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void newFileInheritsScopeFromParentDepartment() {
        UUID owner = insertUser("file-scope1@test", "file-scope1");
        UUID scopeId = UUID.randomUUID();
        UUID rootId = insertFakeRoot(owner, "department", scopeId);

        byte[] body = "hello".getBytes();
        UploadResult result = service.upload(rootId, owner, "Hello.txt", "text/plain",
            body.length, new ByteArrayInputStream(body), null);

        assertThat(result.newFile()).isTrue();
        assertThat(result.file().getFolderId()).isEqualTo(rootId);
        assertThat(result.file().getScopeType()).isEqualTo(ScopeType.DEPARTMENT);
        assertThat(result.file().getScopeId()).isEqualTo(scopeId);

        // entity getter 외에도 raw column으로 진실의 출처(DB) 확인.
        FileItem fromDb = fileRepository.findById(result.file().getId()).orElseThrow();
        assertThat(fromDb.getScopeType()).isEqualTo(ScopeType.DEPARTMENT);
        assertThat(fromDb.getScopeId()).isEqualTo(scopeId);
    }

    @Test
    void newFileInheritsScopeFromParentTeam() {
        UUID owner = insertUser("file-scope2@test", "file-scope2");
        UUID scopeId = UUID.randomUUID();
        UUID rootId = insertFakeRoot(owner, "team", scopeId);

        byte[] body = "team body".getBytes();
        UploadResult result = service.upload(rootId, owner, "TeamDoc.txt", "text/plain",
            body.length, new ByteArrayInputStream(body), null);

        assertThat(result.file().getScopeType()).isEqualTo(ScopeType.TEAM);
        assertThat(result.file().getScopeId()).isEqualTo(scopeId);
    }

    // ──────────────────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────────────────

    private UUID insertUser(String email, String displayName) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)", id, email, displayName);
        return id;
    }

    /**
     * service의 root 생성 차단 가드(Task 20 lifecycle)를 우회하여 raw JDBC로 fake workspace root를
     * INSERT. 정상 운영에서는 DepartmentService/TeamService가 root를 만든다.
     */
    private UUID insertFakeRoot(UUID ownerId, String scopeType, UUID scopeId) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(java.time.Instant.now());
        jdbc.update(
            "INSERT INTO folders(id, parent_id, name, normalized_name, slug, owner_id, audit_level, " +
            "scope_type, scope_id, created_at, updated_at) " +
            "VALUES (?, NULL, ?, ?, ?, ?, 'standard', ?, ?, ?, ?)",
            id, "root-" + id, "root-" + id, "root-" + id, ownerId, scopeType, scopeId, now, now
        );
        return id;
    }
}
