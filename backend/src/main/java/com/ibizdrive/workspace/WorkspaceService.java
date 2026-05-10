package com.ibizdrive.workspace;

import com.ibizdrive.department.DepartmentRepository;
import com.ibizdrive.team.TeamMembership;
import com.ibizdrive.team.TeamMembershipRepository;
import com.ibizdrive.team.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 사용자의 workspace 목록 조회 서비스 — Plan A Task 14.
 *
 * <p>spec docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §1, §5.
 * 사이드바 트리 + permission evaluation 진입점이 사용. read-only 트랜잭션.
 *
 * <p><b>active 필터 정책</b>:
 * <ul>
 *   <li>Department: {@link com.ibizdrive.department.Department#isActive()} (deletedAt IS NULL) AND
 *       rootFolderId != null. root folder 미생성 부서는 Plan A2 backfill 대상이며 노출하지 않는다.</li>
 *   <li>Team: rootFolderId != null인 active team만. 멤버십 자체에는 active 플래그가 없으므로 행 존재 여부로
 *       판단; Team 자체의 archived는 향후 Plan A2에서 가드 (현재는 모든 team을 active로 간주).</li>
 * </ul>
 */
@Service
public class WorkspaceService {

    private final DepartmentRepository deptRepo;
    private final TeamRepository teamRepo;
    private final TeamMembershipRepository memRepo;
    private final UserDepartmentLookup userDeptLookup;

    public WorkspaceService(DepartmentRepository deptRepo,
                            TeamRepository teamRepo,
                            TeamMembershipRepository memRepo,
                            UserDepartmentLookup userDeptLookup) {
        this.deptRepo = deptRepo;
        this.teamRepo = teamRepo;
        this.memRepo = memRepo;
        this.userDeptLookup = userDeptLookup;
    }

    /**
     * user가 접근 가능한 workspace 목록을 조회.
     *
     * <p>department는 user의 소속 부서 (1개 max), teams는 멤버십 기반 (0개 이상). 모두 root folder 미보유시 제외.
     *
     * @param userId 조회 대상 사용자
     * @return department + teams listing — 둘 다 비어있을 수 있음
     */
    @Transactional
    public WorkspaceListing findForUser(UUID userId) {
        Optional<DepartmentWorkspace> dept = userDeptLookup.departmentIdOf(userId)
            .flatMap(deptRepo::findById)
            .filter(d -> d.isActive() && d.getRootFolderId() != null)
            .map(DepartmentWorkspace::new);

        List<UUID> teamIds = memRepo.findByUserId(userId).stream()
            .map(TeamMembership::getTeamId).toList();

        List<TeamWorkspace> teams = teamIds.isEmpty() ? List.of()
            : teamRepo.findAllById(teamIds).stream()
                .filter(t -> t.getRootFolderId() != null)
                .map(TeamWorkspace::new).toList();

        return new WorkspaceListing(dept, teams);
    }
}
