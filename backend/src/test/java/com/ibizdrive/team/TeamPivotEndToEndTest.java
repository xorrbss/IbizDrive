package com.ibizdrive.team;

import com.ibizdrive.folder.Folder;
import com.ibizdrive.folder.FolderMutationService;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.folder.ScopeType;
import com.ibizdrive.permission.Permission;
import com.ibizdrive.permission.PermissionResolver;
import com.ibizdrive.workspace.WorkspaceListing;
import com.ibizdrive.workspace.WorkspaceService;
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
 * <p><b>note</b>: 본 클래스는 {@code @Transactional}을 의도적으로 사용하지 않는다 —
 * {@code FolderMutationService.create}의 {@code FOLDER_CREATED} audit이 직접
 * {@code REQUIRES_NEW} 트랜잭션으로 commit하는데, 테스트 tx의 raw JDBC user INSERT가
 * 미commit 상태라 {@code audit_log_actor_id_fkey} FK 위반이 발생함. 각 테스트는
 * unique UUID 사용 + Testcontainers 컨테이너가 매 run마다 fresh PG라 데이터 누수 무관.
 * Plan D의 {@code CrossWorkspaceMoveE2ETest} 등 다른 E2E 테스트와 동일 패턴.
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

    @Test
    void teamCreate_invite_childFolder_membershipPermissions_workEndToEnd() {
        UUID owner = persistUser("e2e-owner@t", "e2e-owner");
        UUID member = persistUser("e2e-member@t", "e2e-member");

        // 1) Create team — TeamService.create produces team + root folder + OWNER membership
        Team team = teamSvc.create("E2E", null, Team.Visibility.PRIVATE, owner);
        assertThat(team.getId()).isNotNull();
        assertThat(team.getRootFolderId()).isNotNull();
        assertThat(team.getName()).isEqualTo("E2E");
        assertThat(memRepo.findByTeamId(team.getId())).hasSize(1)
            .first().extracting(TeamMembership::getRole)
            .isEqualTo(TeamMembership.Role.OWNER);

        // 2) Invite member
        teamSvc.invite(team.getId(), member, owner);
        assertThat(memRepo.findByTeamId(team.getId())).hasSize(2);

        // 3) WorkspaceService — invited member의 listing에 team 노출
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
        return id;
    }
}
