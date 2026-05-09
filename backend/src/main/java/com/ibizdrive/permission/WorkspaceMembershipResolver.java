package com.ibizdrive.permission;

import com.ibizdrive.folder.ScopeType;
import com.ibizdrive.team.TeamMembership;
import com.ibizdrive.team.TeamMembershipId;
import com.ibizdrive.team.TeamMembershipRepository;
import com.ibizdrive.workspace.UserDepartmentLookup;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Workspace 멤버십 → 묵시적 권한 매핑 — Plan A Task 22.
 *
 * <p>spec docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §3.2.
 * 권한 평가 파이프라인의 첫 단계 — explicit grants/shares 평가 전에 user-workspace 관계만으로
 * 결정되는 default 권한을 노출.
 *
 * <p><b>매핑 규칙</b>:
 * <ul>
 *   <li>Department 멤버 (user.department_id == scopeId): READ + UPLOAD</li>
 *   <li>Team MEMBER: READ + UPLOAD + EDIT</li>
 *   <li>Team OWNER: READ + UPLOAD + EDIT + DELETE + SHARE</li>
 *   <li>비멤버: 빈 집합 (default deny — 후속 단계에서 explicit/share grants가 채움)</li>
 * </ul>
 *
 * <p><b>YAGNI</b>: ADMIN cross-workspace bypass는 기존 PermissionResolver의 admin 분기에서 처리.
 * 본 resolver는 멤버십만 본다.
 */
@Component
public class WorkspaceMembershipResolver {

    private final UserDepartmentLookup userDeptLookup;
    private final TeamMembershipRepository memRepo;

    public WorkspaceMembershipResolver(UserDepartmentLookup userDeptLookup,
                                       TeamMembershipRepository memRepo) {
        this.userDeptLookup = userDeptLookup;
        this.memRepo = memRepo;
    }

    /**
     * user의 workspace에 대한 묵시적 권한 집합을 반환.
     *
     * @param userId 평가 대상 사용자
     * @param scopeType 워크스페이스 종류 (DEPARTMENT 또는 TEAM)
     * @param scopeId 워크스페이스 id
     * @return 권한 집합 (불변 보증 안 함 — caller가 union 후 재할당)
     */
    public Set<Permission> resolve(UUID userId, ScopeType scopeType, UUID scopeId) {
        return switch (scopeType) {
            case DEPARTMENT -> userDeptLookup.departmentIdOf(userId)
                .filter(id -> id.equals(scopeId))
                .<Set<Permission>>map(id -> EnumSet.of(Permission.READ, Permission.UPLOAD))
                .orElseGet(() -> EnumSet.noneOf(Permission.class));
            case TEAM -> memRepo.findById(new TeamMembershipId(scopeId, userId))
                .<Set<Permission>>map(this::permsForRole)
                .orElseGet(() -> EnumSet.noneOf(Permission.class));
        };
    }

    private Set<Permission> permsForRole(TeamMembership m) {
        return switch (m.getRole()) {
            case OWNER -> EnumSet.of(Permission.READ, Permission.UPLOAD,
                Permission.EDIT, Permission.DELETE, Permission.SHARE);
            case MEMBER -> EnumSet.of(Permission.READ, Permission.UPLOAD, Permission.EDIT);
        };
    }
}
