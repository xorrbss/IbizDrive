package com.ibizdrive.folder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.file.FileRepository;
import com.ibizdrive.team.TeamArchiveGuard;
import com.ibizdrive.team.TeamArchivedException;
import com.ibizdrive.team.TeamRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Plan A T3 — {@link FolderMutationService}의 5개 write 진입점에 대한
 * {@link TeamArchiveGuard} 통합 검증.
 *
 * <p>spec docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §2.2 (archive lifecycle =
 * 콘텐츠 read-only), §5.4 ({@code TEAM_ARCHIVED} 423).
 *
 * <p>archived 팀의 root folder 아래에서 create/rename/move/delete/restore 시도 시
 * {@link TeamArchivedException}이 발생함을 검증한다. active 팀 케이스는 smoke regression — 동일 setup으로
 * 가드를 통과하고 정상 동작함을 확인.
 *
 * <p>{@link FolderMutationServiceTest}와 동일 슬라이스(@DataJpaTest + Testcontainers Postgres) —
 * 실제 V12 teams 테이블 + V13 folders.scope_type/scope_id NOT NULL을 활용. Team row는 raw JDBC로 직접
 * 삽입(active/archived 시나리오 분기). DEPARTMENT scope에 대한 회귀는 기존
 * {@link FolderMutationServiceTest}가 광범위하게 커버하므로 여기서는 TEAM scope만 다룬다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import(FolderArchivedTeamGuardTest.TestConfig.class)
class FolderArchivedTeamGuardTest {

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

        @Bean TeamArchiveGuard teamArchiveGuard(TeamRepository teamRepository) {
            return new TeamArchiveGuard(teamRepository);
        }

        @Bean FolderMutationService folderMutationService(FolderRepository repo,
                                                          FileRepository fileRepo,
                                                          AuditService audit,
                                                          ObjectMapper mapper,
                                                          TeamArchiveGuard guard) {
            return new FolderMutationService(repo, fileRepo, audit, mapper,
                new com.ibizdrive.trash.TrashRetentionProperties(30),
                org.mockito.Mockito.mock(com.ibizdrive.folder.CrossWorkspaceMoveService.class),
                guard);
        }
    }

    @Autowired private FolderMutationService service;
    @Autowired private FolderRepository folderRepository;
    @Autowired private JdbcTemplate jdbc;
    @PersistenceContext private EntityManager em;

    // ──────────────────────────────────────────────────────────────────
    // create
    // ──────────────────────────────────────────────────────────────────

    @Test
    void create_underArchivedTeam_throwsTeamArchived() {
        UUID owner = insertUser("fa-c1@test", "fa-c1");
        UUID teamId = insertTeam(owner, /* archived */ true);
        UUID rootId = insertFakeRoot(owner, "team", teamId);

        assertThatThrownBy(() ->
            service.create(rootId, "blockedChildC1", owner, "standard", owner))
            .isInstanceOf(TeamArchivedException.class)
            .satisfies(ex -> assertThat(((TeamArchivedException) ex).getTeamId()).isEqualTo(teamId));
    }

    @Test
    void create_underActiveTeam_succeeds() {
        UUID owner = insertUser("fa-c2@test", "fa-c2");
        UUID teamId = insertTeam(owner, /* archived */ false);
        UUID rootId = insertFakeRoot(owner, "team", teamId);

        Folder created = service.create(rootId, "okChildC2", owner, "standard", owner);

        assertThat(created.getScopeType()).isEqualTo(ScopeType.TEAM);
        assertThat(created.getScopeId()).isEqualTo(teamId);
    }

    // ──────────────────────────────────────────────────────────────────
    // rename
    // ──────────────────────────────────────────────────────────────────

    @Test
    void rename_underArchivedTeam_throwsTeamArchived_evenForNoOpName() {
        // spec §2.2: 시도 자체가 write — 정규화 결과가 동일해도 차단되어야 함.
        UUID owner = insertUser("fa-r1@test", "fa-r1");
        UUID teamId = insertTeam(owner, /* archived */ false);
        UUID rootId = insertFakeRoot(owner, "team", teamId);
        Folder child = service.create(rootId, "RenameTargetR1", owner, "standard", owner);
        archiveTeam(teamId);

        // 같은 이름으로 rename 시도(no-op 시나리오) — 가드가 단락 이전에 트리거되어야 함.
        assertThatThrownBy(() ->
            service.rename(child.getId(), "RenameTargetR1", owner))
            .isInstanceOf(TeamArchivedException.class)
            .satisfies(ex -> assertThat(((TeamArchivedException) ex).getTeamId()).isEqualTo(teamId));
    }

    @Test
    void rename_underActiveTeam_succeeds() {
        UUID owner = insertUser("fa-r2@test", "fa-r2");
        UUID teamId = insertTeam(owner, /* archived */ false);
        UUID rootId = insertFakeRoot(owner, "team", teamId);
        Folder child = service.create(rootId, "OldR2", owner, "standard", owner);

        Folder renamed = service.rename(child.getId(), "NewR2", owner);

        assertThat(renamed.getName()).isEqualTo("NewR2");
        assertThat(renamed.getNormalizedName()).isEqualTo("newr2");
    }

    // ──────────────────────────────────────────────────────────────────
    // move
    // ──────────────────────────────────────────────────────────────────

    @Test
    void move_underArchivedTeam_throwsTeamArchived() {
        UUID owner = insertUser("fa-m1@test", "fa-m1");
        UUID teamId = insertTeam(owner, /* archived */ false);
        UUID rootId = insertFakeRoot(owner, "team", teamId);
        Folder src = service.create(rootId, "SrcM1", owner, "standard", owner);
        Folder dst = service.create(rootId, "DstM1", owner, "standard", owner);
        Folder child = service.create(src.getId(), "ChildM1", owner, "standard", owner);
        archiveTeam(teamId);

        assertThatThrownBy(() -> service.move(child.getId(), dst.getId(), owner))
            .isInstanceOf(TeamArchivedException.class)
            .satisfies(ex -> assertThat(((TeamArchivedException) ex).getTeamId()).isEqualTo(teamId));
    }

    @Test
    void move_underActiveTeam_succeeds() {
        UUID owner = insertUser("fa-m2@test", "fa-m2");
        UUID teamId = insertTeam(owner, /* archived */ false);
        UUID rootId = insertFakeRoot(owner, "team", teamId);
        Folder src = service.create(rootId, "SrcM2", owner, "standard", owner);
        Folder dst = service.create(rootId, "DstM2", owner, "standard", owner);
        Folder child = service.create(src.getId(), "ChildM2", owner, "standard", owner);

        Folder moved = service.move(child.getId(), dst.getId(), owner);

        assertThat(moved.getParentId()).isEqualTo(dst.getId());
    }

    // ──────────────────────────────────────────────────────────────────
    // delete
    // ──────────────────────────────────────────────────────────────────

    @Test
    void delete_underArchivedTeam_throwsTeamArchived() {
        UUID owner = insertUser("fa-d1@test", "fa-d1");
        UUID teamId = insertTeam(owner, /* archived */ false);
        UUID rootId = insertFakeRoot(owner, "team", teamId);
        Folder child = service.create(rootId, "ToDelD1", owner, "standard", owner);
        archiveTeam(teamId);

        assertThatThrownBy(() -> service.delete(child.getId(), owner))
            .isInstanceOf(TeamArchivedException.class)
            .satisfies(ex -> assertThat(((TeamArchivedException) ex).getTeamId()).isEqualTo(teamId));

        // 차단되었으므로 row는 여전히 활성.
        assertThat(folderRepository.findByIdAndDeletedAtIsNull(child.getId())).isPresent();
    }

    @Test
    void delete_underActiveTeam_succeeds() {
        UUID owner = insertUser("fa-d2@test", "fa-d2");
        UUID teamId = insertTeam(owner, /* archived */ false);
        UUID rootId = insertFakeRoot(owner, "team", teamId);
        Folder child = service.create(rootId, "ToDelD2", owner, "standard", owner);

        assertThatNoException().isThrownBy(() -> service.delete(child.getId(), owner));
        assertThat(folderRepository.findByIdAndDeletedAtIsNull(child.getId())).isEmpty();
    }

    // ──────────────────────────────────────────────────────────────────
    // restore (3-arg overload — 1-arg는 위임이므로 동일하게 차단됨을 함께 검증)
    // ──────────────────────────────────────────────────────────────────

    @Test
    void restore_underArchivedTeam_throwsTeamArchived() {
        UUID owner = insertUser("fa-rs1@test", "fa-rs1");
        UUID teamId = insertTeam(owner, /* archived */ false);
        UUID rootId = insertFakeRoot(owner, "team", teamId);
        Folder child = service.create(rootId, "ToRestoreRs1", owner, "standard", owner);
        service.delete(child.getId(), owner);
        archiveTeam(teamId);

        assertThatThrownBy(() -> service.restore(child.getId(), owner))
            .isInstanceOf(TeamArchivedException.class)
            .satisfies(ex -> assertThat(((TeamArchivedException) ex).getTeamId()).isEqualTo(teamId));
    }

    @Test
    void restore_underActiveTeam_succeeds() {
        UUID owner = insertUser("fa-rs2@test", "fa-rs2");
        UUID teamId = insertTeam(owner, /* archived */ false);
        UUID rootId = insertFakeRoot(owner, "team", teamId);
        Folder child = service.create(rootId, "ToRestoreRs2", owner, "standard", owner);
        service.delete(child.getId(), owner);

        Folder restored = service.restore(child.getId(), owner);

        assertThat(restored.getDeletedAt()).isNull();
    }

    // ──────────────────────────────────────────────────────────────────
    // helpers (FolderMoveSameScopeTest와 동일 패턴 — Team row만 추가)
    // ──────────────────────────────────────────────────────────────────

    private UUID insertUser(String email, String displayName) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)", id, email, displayName);
        return id;
    }

    /**
     * Team row 직접 INSERT — TeamService를 거치지 않고 V12 schema에 맞춰 raw row 생성.
     * archived 모드는 archived_at/archived_by를 채우면 {@link com.ibizdrive.team.Team#isActive()}=false.
     */
    private UUID insertTeam(UUID createdBy, boolean archived) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        // normalized_name은 V12 partial unique idx_teams_name_active에 따라 active 팀 사이에서만 unique.
        // 본 테스트는 매번 random uuid suffix로 충돌 회피.
        String normName = "team-" + id.toString().substring(0, 8);
        jdbc.update(
            "INSERT INTO teams(id, name, normalized_name, visibility, created_by, "
                + "archived_at, archived_by, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'private', ?, ?, ?, ?, ?)",
            id, normName, normName, createdBy,
            archived ? now : null,
            archived ? createdBy : null,
            now, now
        );
        return id;
    }

    /**
     * 활성 팀을 archive — TeamService.archive를 거치지 않고 V12 row만 직접 갱신.
     *
     * <p>raw JDBC UPDATE이므로 JPA L1 persistence context는 자동 갱신되지 않는다. 이전에 가드가
     * Team을 로드한 적이 있으면(예: setup 단계의 service.create가 가드를 트리거) 캐시에 active 상태가
     * 남아 있어 이후 가드 호출이 stale 데이터를 반환한다. {@code em.flush() + em.clear()}로 캐시를
     * 비워 다음 lookup이 DB를 재조회하도록 강제한다.
     */
    private void archiveTeam(UUID teamId) {
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update(
            "UPDATE teams SET archived_at = ?, archived_by = "
                + "(SELECT created_by FROM teams WHERE id = ?), updated_at = ? WHERE id = ?",
            now, teamId, now, teamId
        );
        em.flush();
        em.clear();
    }

    /**
     * Workspace root folder 직접 INSERT — service.create가 root를 거부하므로 raw JDBC.
     * scope_type/scope_id는 V13 NOT NULL 제약에 따라 호출자가 명시 (team scope ⇒ scope_id = team.id).
     *
     * <p>created_at/updated_at는 TIMESTAMPTZ — JDBC 드라이버가 {@code OffsetDateTime}으로 binding 가능
     * (CI Postgres 환경 호환). {@code java.time.Instant} 직접 binding은 SQL 타입 추론 실패.
     */
    private UUID insertFakeRoot(UUID ownerId, String scopeType, UUID scopeId) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update(
            "INSERT INTO folders(id, parent_id, name, normalized_name, slug, owner_id, audit_level, "
                + "scope_type, scope_id, created_at, updated_at) "
                + "VALUES (?, NULL, ?, ?, ?, ?, 'standard', ?, ?, ?, ?)",
            id, "root-" + id, "root-" + id, "root-" + id, ownerId, scopeType, scopeId, now, now
        );
        return id;
    }
}
