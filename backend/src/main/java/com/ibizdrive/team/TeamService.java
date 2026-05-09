package com.ibizdrive.team;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.audit.AuditEvent;
import com.ibizdrive.audit.AuditEventType;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.audit.AuditTargetType;
import com.ibizdrive.common.normalize.NormalizeUtil;
import com.ibizdrive.folder.Folder;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.folder.ScopeType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 팀 도메인 서비스 — Plan A Task 16~18.
 *
 * <p>spec docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §1, §2.
 * create는 Team + 초기 OWNER 멤버십 + root Folder를 단일 트랜잭션으로 생성.
 *
 * <p><b>audit emission</b>: {@link AuditService}는 {@code REQUIRES_NEW}로 outer tx와 독립.
 * 본 서비스 tx가 rollback되어도 audit row는 잔존하므로 audit emit 시점은 부수효과 commit 직전.
 *
 * <p><b>YAGNI</b>: archive/role-change/last-OWNER guard는 Plan A2 이월. 본 클래스는 create만.
 */
@Service
public class TeamService {

    private static final String DEFAULT_AUDIT_LEVEL = "standard";

    private final TeamRepository teamRepo;
    private final TeamMembershipRepository memRepo;
    private final FolderRepository folderRepo;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;
    private final ObjectMapper objectMapper;

    public TeamService(TeamRepository teamRepo, TeamMembershipRepository memRepo,
                       FolderRepository folderRepo, AuditService auditService,
                       ApplicationEventPublisher events, ObjectMapper objectMapper) {
        this.teamRepo = teamRepo;
        this.memRepo = memRepo;
        this.folderRepo = folderRepo;
        this.auditService = auditService;
        this.events = events;
        this.objectMapper = objectMapper;
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
        Instant nowInstant = Instant.now().truncatedTo(ChronoUnit.MICROS);
        UUID teamId = UUID.randomUUID();

        Team t = new Team(teamId, displayName, normalizedName, description,
            visibility, creatorId, now);
        teamRepo.save(t);

        // Root folder — same transaction
        UUID rootFolderId = UUID.randomUUID();
        Folder root = new Folder();
        root.setId(rootFolderId);
        root.setParentId(null);
        root.setName(displayName);
        root.setNormalizedName(normalizedName);
        root.setSlug(normalizedName);
        root.setOwnerId(creatorId);
        root.setAuditLevel(DEFAULT_AUDIT_LEVEL);
        root.assignScope(ScopeType.TEAM, teamId);
        root.setCreatedAt(nowInstant);
        root.setUpdatedAt(nowInstant);
        folderRepo.save(root);

        t.attachRootFolder(rootFolderId);

        // Initial OWNER membership
        TeamMembership ownerMembership = new TeamMembership(teamId, creatorId,
            TeamMembership.Role.OWNER, null, now);
        memRepo.save(ownerMembership);

        // Audits — TEAM_CREATED + TEAM_MEMBER_ADDED
        emitTeamCreated(teamId, creatorId, displayName, normalizedName, visibility, rootFolderId);
        emitMemberAdded(teamId, creatorId, creatorId, TeamMembership.Role.OWNER);

        events.publishEvent(new TeamCreatedEvent(teamId, creatorId, displayName));
        return t;
    }

    private void emitTeamCreated(UUID teamId, UUID actor, String name, String normalizedName,
                                  Team.Visibility visibility, UUID rootFolderId) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("name", name);
        after.put("normalizedName", normalizedName);
        after.put("visibility", visibility.dbValue());
        after.put("rootFolderId", rootFolderId.toString());
        auditService.record(new AuditEvent(
            AuditEventType.TEAM_CREATED, actor, null, null,
            AuditTargetType.TEAM, teamId, null, toJson(after), null));
    }

    private void emitMemberAdded(UUID teamId, UUID memberUserId, UUID actor, TeamMembership.Role role) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("userId", memberUserId.toString());
        after.put("role", role.name());
        auditService.record(new AuditEvent(
            AuditEventType.TEAM_MEMBER_ADDED, actor, null, null,
            AuditTargetType.TEAM, teamId, null, toJson(after), null));
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("audit afterState serialization failed", e);
        }
    }
}
