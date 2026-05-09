package com.ibizdrive.team;

import com.ibizdrive.user.IbizDriveUserDetails;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * {@code @PreAuthorize} SpEL helper for Team endpoints — Plan A Task 19.
 *
 * <p>Spring Security expression bean: {@code @teamAuthz.isOwner(#teamId, principal)} 형태로 사용.
 * principal은 {@link IbizDriveUserDetails} 인스턴스 (project 인증 모델).
 *
 * <p>YAGNI: ADMIN bypass / cross-team admin은 Plan A2 또는 별도 admin endpoint.
 */
@Component("teamAuthz")
public class TeamAuthz {

    private final TeamMembershipRepository memRepo;

    public TeamAuthz(TeamMembershipRepository memRepo) {
        this.memRepo = memRepo;
    }

    /** principal이 team의 OWNER인지. */
    public boolean isOwner(UUID teamId, Object principal) {
        UUID userId = principalUserId(principal);
        if (userId == null) return false;
        return memRepo.findById(new TeamMembershipId(teamId, userId))
            .map(m -> m.getRole() == TeamMembership.Role.OWNER)
            .orElse(false);
    }

    /** principal이 OWNER이거나 자기 자신을 대상으로 한 경우 (self-leave). */
    public boolean isOwnerOrSelf(UUID teamId, UUID userId, Object principal) {
        UUID actor = principalUserId(principal);
        if (actor == null) return false;
        if (actor.equals(userId)) return true;
        return isOwner(teamId, principal);
    }

    private UUID principalUserId(Object principal) {
        if (principal instanceof IbizDriveUserDetails uds) {
            return uds.getUser().getId();
        }
        return null;
    }
}
