package com.ibizdrive.team;

import com.ibizdrive.folder.Folder;
import com.ibizdrive.folder.FolderMutationService;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.folder.ScopeType;
import com.ibizdrive.permission.Permission;
import com.ibizdrive.permission.PermissionResolver;
import com.ibizdrive.workspace.WorkspaceListing;
import com.ibizdrive.workspace.WorkspaceService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plan A Task 29 — team-centric-pivot 전체 플로우 E2E 통합 테스트.
 *
 * <p>spec docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §1, §3.
 * Phase 5/6/8/9 통합 동작 검증:
 * <ol>
 *   <li>Team 생성 (TeamService.create) — root Folder 자동 생성, OWNER membership</li>
 *   <li>Member invite — TeamMembership 추가</li>
 *   <li>WorkspaceService.findForUser — invited member의 listing에 team 노출</li>
 *   <li>Child folder 생성 (FolderMutationService.create) — parent의 scope 상속</li>
 *   <li>PermissionResolver.isGranted — workspace membership 기반 묵시적 권한</li>
 * </ol>
 *
 * <p><b>트랜잭션 정책</b>: {@code @Transactional}을 사용하지 않는다. AuditService.record는
 * {@code REQUIRES_NEW}로 별도 트랜잭션을 열어 audit_log를 INSERT하므로, 테스트 outer 트랜잭션이
 * rollback이라도 AuditService 내부 트랜잭션은 commit된다. 이때 outer 트랜잭션의 user/team 등이
 * 아직 commit되지 않은 상태라 FK 위반(actor_id, target_id 등)이 발생한다. E2E 테스트는 각 단계의
 * write를 commit해 후속 audit 트랜잭션이 FK를 만족하도록 해야 한다. 정리는 {@code @AfterEach}에서
 * 명시적으로 수행한다 (insert 순서의 역순으로 cascade 안전 삭제).
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class TeamPivotEndToEndTest {

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
    private TeamService teamSvc;

    @Autowired
    private FolderMutationService folderMut;

    @Autowired
    private FolderRepository folderRepo;

    @Autowired
    private WorkspaceService wsSvc;

    @Autowired
    private PermissionResolver permResolver;

    @Autowired
    private TeamMembershipRepository memRepo;

    @Autowired
    private JdbcTemplate jdbc;

    @PersistenceContext
    private EntityManager em;

    /** 테스트 종료 시 cascade 삭제 — non-transactional 테스트의 부산물 정리. */
    private final List<UUID> insertedUserIds = new ArrayList<>();
    private final List<UUID> insertedTeamIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        // audit_log → folders/files (cascade FK는 V12/V13에 정의) → team_memberships (CASCADE)
        // → teams → users 순서. user 삭제는 다른 FK 차단 가능성을 고려해 마지막.
        for (UUID userId : insertedUserIds) {
            jdbc.update("DELETE FROM audit_log WHERE actor_id = ? OR target_id = ?", userId, userId);
        }
        for (UUID teamId : insertedTeamIds) {
            jdbc.update("DELETE FROM audit_log WHERE target_id = ?", teamId);
            jdbc.update("DELETE FROM files WHERE folder_id IN (SELECT id FROM folders WHERE scope_type = 'team' AND scope_id = ?)", teamId);
            jdbc.update("DELETE FROM folders WHERE scope_type = 'team' AND scope_id = ?", teamId);
            jdbc.update("DELETE FROM team_memberships WHERE team_id = ?", teamId);
            jdbc.update("DELETE FROM teams WHERE id = ?", teamId);
        }
        for (UUID userId : insertedUserIds) {
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
        insertedUserIds.clear();
        insertedTeamIds.clear();
    }

    @Test
    void teamCreate_invite_childFolder_membershipPermissions_workEndToEnd() {
        UUID owner = persistUser("e2e-owner@t", "e2e-owner");
        UUID member = persistUser("e2e-member@t", "e2e-member");

        // 1) Create team — TeamService.create produces team + root folder + OWNER membership
        Team team = teamSvc.create("E2E", null, Team.Visibility.PRIVATE, owner);
        insertedTeamIds.add(team.getId());
        assertThat(team.getId()).isNotNull();
        assertThat(team.getRootFolderId()).isNotNull();
        assertThat(team.getName()).isEqualTo("E2E");
        assertThat(memRepo.findByTeamId(team.getId())).hasSize(1)
            .first().extracting(TeamMembership::getRole)
            .isEqualTo(TeamMembership.Role.OWNER);

        // 2) Invite member
        teamSvc.invite(team.getId(), member, owner);
        assertThat(memRepo.findByTeamId(team.getId())).hasSize(2);

        // 3) WorkspaceService — invited member의 listing에 team 노출.
        // em.flush() — outer test tx의 pending writes(team UPDATE root_folder_id 포함)를 DB에 반영.
        // TeamService.create의 attachRootFolder는 managed entity에 대해 dirty check로 잡히므로
        // 다음 query 시 auto-flush로 처리되나, 명시적 flush로 확정.
        em.flush();
        WorkspaceListing memberListing = wsSvc.findForUser(member);
        assertThat(memberListing.teams())
            .extracting(w -> w.id())
            .contains(team.getId());

        // 4) Child folder 생성 — root 아래 child는 parent의 scope 상속
        Folder root = folderRepo.findById(team.getRootFolderId()).orElseThrow();
        assertThat(root.getScopeType()).isEqualTo(ScopeType.TEAM);
        assertThat(root.getScopeId()).isEqualTo(team.getId());

        Folder child = folderMut.create(root.getId(), "Q1", owner, "standard", owner);
        assertThat(child.getScopeType()).isEqualTo(ScopeType.TEAM);
        assertThat(child.getScopeId()).isEqualTo(team.getId());
        assertThat(child.getParentId()).isEqualTo(root.getId());

        // 5) PermissionResolver — workspace membership 기반 묵시적 권한
        // MEMBER (invited member): READ + UPLOAD + EDIT
        assertThat(permResolver.isGranted(member, "folder", child.getId(), Permission.READ)).isTrue();
        assertThat(permResolver.isGranted(member, "folder", child.getId(), Permission.UPLOAD)).isTrue();
        assertThat(permResolver.isGranted(member, "folder", child.getId(), Permission.EDIT)).isTrue();
        // MEMBER does NOT have DELETE/SHARE
        assertThat(permResolver.isGranted(member, "folder", child.getId(), Permission.DELETE)).isFalse();
        assertThat(permResolver.isGranted(member, "folder", child.getId(), Permission.SHARE)).isFalse();

        // OWNER: READ + UPLOAD + EDIT + DELETE + SHARE
        assertThat(permResolver.isGranted(owner, "folder", child.getId(), Permission.READ)).isTrue();
        assertThat(permResolver.isGranted(owner, "folder", child.getId(), Permission.UPLOAD)).isTrue();
        assertThat(permResolver.isGranted(owner, "folder", child.getId(), Permission.EDIT)).isTrue();
        assertThat(permResolver.isGranted(owner, "folder", child.getId(), Permission.DELETE)).isTrue();
        assertThat(permResolver.isGranted(owner, "folder", child.getId(), Permission.SHARE)).isTrue();
    }

    private UUID persistUser(String email, String displayName) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)",
            id, email, displayName
        );
        insertedUserIds.add(id);
        return id;
    }
}
