package com.ibizdrive.team;

import com.ibizdrive.common.error.ResourceNotFoundException;
import com.ibizdrive.common.normalize.NormalizeUtil;
import com.ibizdrive.folder.Folder;
import com.ibizdrive.folder.FolderMutationService;
import com.ibizdrive.folder.ScopeType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * 팀 도메인 서비스 — Plan A Task 16~18.
 *
 * <p>spec docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §1, §2.
 * create는 Team + 초기 OWNER 멤버십 + root Folder를 단일 트랜잭션으로 생성.
 *
 * <p><b>audit 위임</b>: 직접 {@code AuditService.record}를 호출하지 않는다. 도메인 이벤트
 * ({@link TeamCreatedEvent} 등)를 publish하고 {@code TeamAuditListener} (Plan A Task 28)가
 * {@code @TransactionalEventListener(AFTER_COMMIT)}으로 audit_log row를 작성한다.
 * {@code TEAM_CREATED} audit이 "team + 초기 OWNER 생성"을 묶어 표현하므로 create는
 * {@code TeamMemberAddedEvent}를 발행하지 않는다 (Task 17 invite만 발행).
 *
 * <p><b>메서드 현황</b>: create + invite + remove + changeRole (Plan A2 T4). archive/restore는 Plan A2 T6/T7 이월.
 */
@Service
public class TeamService {

    private final TeamRepository teamRepo;
    private final TeamMembershipRepository memRepo;
    private final FolderMutationService folderService;
    private final ApplicationEventPublisher events;

    public TeamService(TeamRepository teamRepo, TeamMembershipRepository memRepo,
                       FolderMutationService folderService,
                       ApplicationEventPublisher events) {
        this.teamRepo = teamRepo;
        this.memRepo = memRepo;
        this.folderService = folderService;
        this.events = events;
    }

    /**
     * 신규 팀 생성 — Team + root Folder + 초기 OWNER membership을 단일 트랜잭션으로.
     *
     * @param name 팀 이름 (trim 후 1~100자, 비어있을 수 없음)
     * @param description optional
     * @param visibility PRIVATE 또는 INTERNAL (V12 CHECK)
     * @param creatorId OWNER가 될 user id
     * @return 생성된 Team
     * @throws TeamNameConflictException active team에 동일 normalized name 존재
     * @throws IllegalArgumentException name/visibility/creatorId가 유효하지 않음 (Team 생성자가 검증)
     */
    @Transactional
    public Team create(String name, String description,
                       Team.Visibility visibility, UUID creatorId) {
        String displayName = NormalizeUtil.normalizeFileName(name);
        String normalizedName = NormalizeUtil.normalizedNameForDedup(name);

        teamRepo.findActiveByNormalizedName(normalizedName)
            .ifPresent(existing -> { throw new TeamNameConflictException(displayName); });

        OffsetDateTime now = OffsetDateTime.now();
        UUID teamId = UUID.randomUUID();

        Team t = new Team(teamId, displayName, normalizedName, description,
            visibility, creatorId, now);
        teamRepo.save(t);

        // Root folder via FolderMutationService — same outer transaction
        Folder root = folderService.createRootForScope(ScopeType.TEAM, teamId, creatorId, displayName);
        t.attachRootFolder(root.getId());

        // Initial OWNER membership
        memRepo.save(new TeamMembership(teamId, creatorId,
            TeamMembership.Role.OWNER, null, now));

        // Audit delegated to TeamAuditListener via TeamCreatedEvent (Task 28)
        events.publishEvent(new TeamCreatedEvent(teamId, creatorId, displayName));
        return t;
    }

    /**
     * user를 team에 MEMBER로 초대 — idempotent.
     *
     * <p>이미 해당 team의 멤버인 경우 (role 무관) 기존 row를 반환하고 audit/event는 발행하지 않는다.
     * 신규 추가 시 role=MEMBER로 저장하고 {@link TeamMemberAddedEvent} publish — Task 28 listener가
     * AFTER_COMMIT으로 audit.
     *
     * <p>YAGNI: 권한 검증(invitedBy가 OWNER인지)은 controller layer 또는 Plan A2.
     *
     * @param teamId 대상 team
     * @param userId 초대할 user
     * @param invitedBy 초대 수행자 (audit용)
     * @return membership row (기존 또는 신규)
     * @throws IllegalArgumentException teamId/userId/invitedBy null 또는 TeamMembership 검증 실패
     */
    @Transactional
    public TeamMembership invite(UUID teamId, UUID userId, UUID invitedBy) {
        TeamMembershipId id = new TeamMembershipId(teamId, userId);
        return memRepo.findById(id).orElseGet(() -> {
            TeamMembership m = new TeamMembership(teamId, userId,
                TeamMembership.Role.MEMBER, invitedBy, OffsetDateTime.now());
            memRepo.save(m);
            events.publishEvent(new TeamMemberAddedEvent(teamId, userId, invitedBy));
            return m;
        });
    }

    /**
     * user를 team에서 제거 — idempotent, last-OWNER guard 포함.
     *
     * <p>해당 멤버십이 없으면 silent no-op (예외/event 없음). 존재하면 last-OWNER 여부를 확인한 뒤
     * row 삭제 후 {@link TeamMemberRemovedEvent} publish — Task 28 listener가 AFTER_COMMIT으로 audit.
     *
     * <p>YAGNI: 권한 검증(actorId가 OWNER인지)은 controller layer 또는 Plan A3.
     *
     * @param teamId  대상 team
     * @param userId  제거할 user
     * @param actorId 제거 수행자 (audit용)
     * @throws LastOwnerRequiredException 팀의 유일한 OWNER를 제거하려 할 때
     */
    @Transactional
    public void remove(UUID teamId, UUID userId, UUID actorId) {
        TeamMembershipId id = new TeamMembershipId(teamId, userId);
        Optional<TeamMembership> opt = memRepo.findById(id);
        if (opt.isEmpty()) {
            return;
        }
        TeamMembership existing = opt.get();
        if (existing.getRole() == TeamMembership.Role.OWNER
                && memRepo.countByTeamIdAndRole(teamId, TeamMembership.Role.OWNER) == 1) {
            throw new LastOwnerRequiredException(teamId);
        }
        memRepo.deleteById(id);
        events.publishEvent(new TeamMemberRemovedEvent(teamId, userId, actorId));
    }

    /**
     * 팀 멤버의 role을 변경한다 — last-OWNER 강등 차단 포함.
     *
     * <p>동일 role로 호출하면 idempotent no-op (event 미발행). 멤버십이 없으면
     * {@link ResourceNotFoundException}. OWNER → MEMBER 강등 시 해당 유저가 유일한 OWNER라면
     * {@link LastOwnerRequiredException}.
     *
     * <p>audit 위임: {@link TeamMemberRoleChangedEvent}를 publish하고 {@code TeamAuditListener}
     * (Task 28)가 AFTER_COMMIT으로 {@code TEAM_MEMBER_ROLE_CHANGED} audit_log를 기록한다.
     *
     * <p>YAGNI: 권한 검증(actorId가 OWNER인지)은 controller layer 또는 Plan A3.
     *
     * @param teamId  대상 team
     * @param userId  역할을 변경할 user
     * @param newRole 변경 후 역할 (null 불가)
     * @param actorId 변경 수행자 (audit용, null 불가)
     * @return 갱신된 (또는 변경 없는) TeamMembership row
     * @throws IllegalArgumentException       newRole 또는 actorId가 null
     * @throws ResourceNotFoundException      해당 멤버십이 존재하지 않음
     * @throws LastOwnerRequiredException     마지막 OWNER를 MEMBER로 강등하려 할 때
     */
    @Transactional
    public TeamMembership changeRole(UUID teamId, UUID userId,
                                     TeamMembership.Role newRole, UUID actorId) {
        if (newRole == null) {
            throw new IllegalArgumentException("newRole must not be null");
        }
        if (actorId == null) {
            throw new IllegalArgumentException("actorId must not be null");
        }

        TeamMembershipId id = new TeamMembershipId(teamId, userId);
        TeamMembership existing = memRepo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                "team membership not found: team=" + teamId + " user=" + userId));

        if (existing.getRole() == newRole) {
            return existing;
        }

        if (existing.getRole() == TeamMembership.Role.OWNER
                && newRole == TeamMembership.Role.MEMBER
                && memRepo.countByTeamIdAndRole(teamId, TeamMembership.Role.OWNER) == 1) {
            throw new LastOwnerRequiredException(teamId);
        }

        TeamMembership.Role oldRole = existing.getRole();
        existing.changeRole(newRole);
        memRepo.save(existing);
        events.publishEvent(new TeamMemberRoleChangedEvent(teamId, userId, oldRole, newRole, actorId));
        return existing;
    }
}
