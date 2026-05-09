package com.ibizdrive.team;

import com.ibizdrive.common.normalize.NormalizeUtil;
import com.ibizdrive.folder.Folder;
import com.ibizdrive.folder.FolderMutationService;
import com.ibizdrive.folder.ScopeType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
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
 * <p><b>YAGNI</b>: archive/role-change/last-OWNER guard는 Plan A2 이월. 본 클래스는 create + invite + remove만.
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
}
